package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;
import com.github.catvod.bean.Class;
import com.github.catvod.danmaku.Danmaku;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Emby extends Spider {
    private String siteUrl = "";
    private String apikey = "";
    private String getItemUrl;
    private String searchKey;
    private String userId = "";
    private String homeContentType = "Movie,Series";
    private JSONObject myFilters;
    private Vod.Style landscapeStyle;
    private String currentVodName;
    private String currentEpisodeNumber;
    private String isDanmu;
    private String defaultParentId;
    private Vod danmuVod; // detailContent
    public boolean enableTrakt;
    private String imageUri = "/Images/Primary?maxHeight=500&maxWidth=500&quality=90";

    @Override
    public void init(Context context, String extend) throws Exception{
        String site = extend.split("\\$\\$\\$")[1];
        extend = extend.split("\\$\\$\\$")[0];
        JSONObject siteOb, filterOb;
        if (extend.startsWith("http")) extend = OkHttp.string(extend);
        try {
            siteOb = new JSONObject(extend).optJSONObject("sites").optJSONObject(site);
            filterOb = new JSONObject(extend).optJSONObject("filters");
            this.myFilters = filterOb;
        } catch (Exception e) {
            siteOb = new JSONObject();
            this.myFilters = new JSONObject();
        }
        this.siteUrl = siteOb.optString("url");
        if (!siteOb.optString("auth","").isEmpty()) {
            String authContent = siteOb.optJSONObject("auth").toString();
            String authUrl = siteUrl + "/Users/AuthenticateByName";
            String authBody = OkHttp.post(authUrl, authContent, postHeader()).getBody();
            JSONObject authObject = new JSONObject(authBody);
            this.apikey = "?X-Emby-Token=" + authObject.optString("AccessToken");
            this.userId = authObject.optJSONObject("User").optString("Id");
        }
        this.enableTrakt = siteOb.optString("enableTrakt").isEmpty() || (!siteOb.optString("enableTrakt").equals("false"));
        this.getItemUrl = this.siteUrl + "/Users/" + this.userId + "/Items" + this.apikey + "&Recursive=true&limit=20&Fields=MediaStreams,parentId,CommunityRating&IncludeItemTypes=";
        this.homeContentType = siteOb.optString("homeContentType");
        this.isDanmu = siteOb.optString("danmu");
        this.landscapeStyle = Vod.Style.rect(1.777f);
        this.defaultParentId = siteOb.optString("defaultParentId", "");
        this.danmuVod = new Vod();
    }

    @Override
    public boolean enableTrakt() {
        return this.enableTrakt;
    }

    private Map<String, String> postHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("X-Emby-Authorization", "Emby UserId=\"\", Client=\"android\", Device=\"TvboxYingShi\", DeviceId=\"tvbox_yingshi\", Version=\"1\", Token=\"00000\"");
        header.put("accept", "application/json");
        header.put("Content-Type", "application/json");
        return header;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("HomeMedia","Movie&Series","Favourite","Search");
        List<String> typeNames = Arrays.asList("家庭媒体", "电影剧集","最爱", "搜索");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i), "1"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        final JSONArray[] items = new JSONArray[1];
        final JSONArray[] parents = new JSONArray[1];
        executor.execute(() -> {
            String searchUrl = getItemUrl + homeContentType + "&SortBy=DateCreated,DateLastContentAdded&SortOrder=Descending" + "&parentid=" + defaultParentId;
            try {
                items[0] = new JSONObject(OkHttp.string(searchUrl)).optJSONArray("Items");
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        
        executor.execute(() -> {
            String filterUrl = siteUrl + "/Library/SelectableMediaFolders" + apikey;
            try {
                parents[0] = new JSONArray(OkHttp.string(filterUrl));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);



        JSONArray filterValueArray = new JSONArray();
        for (int i = 0; i < parents[0].length(); i++) {
            JSONObject parent = parents[0].optJSONObject(i);
            JSONObject tempObject = new JSONObject();
            tempObject.put("n", parent.optString("Name"));
            tempObject.put("v", parent.optString("Id"));
            filterValueArray.put(tempObject);
        }
        JSONObject filterObject = new JSONObject();
        filterObject.put("key", "parentId");
        filterObject.put("name", "parentId");
        filterObject.put("value", filterValueArray);
        Iterator<String> filterKeys = myFilters.keys();
        while (filterKeys.hasNext()) {
            myFilters.optJSONArray(filterKeys.next()).put(filterObject);
        }

        return Result.get().vod(parseVodList(items[0])).classes(classes).filters(myFilters).string();
    }
    
    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String linkValue = "";
        String linkSort = ""; //episode视图 空排序。photoalbum视图传值自定义
        String linkMark = "";
        if (tid.endsWith("/{link}")) {
            linkValue = tid.split("/")[1];
            tid = tid.split("/")[0];
            linkMark = !tid.contains("@") ? "" : tid.split("@")[1];
            linkSort = !tid.contains("@") ? "&SortBy=SortName" : "&SortBy=" + tid.split("@")[1];
            tid = !tid.contains("@") ? tid : tid.split("@")[0];
        }
        int start = (Integer.parseInt(pg) - 1) * 20;
        String cateUrl, itemType, sortOrder = "Ascending", sortBy = "SortName", parentId;
        if (extend.get("sort") != null) { sortBy = extend.get("sort"); sortOrder = "Descending"; }
        sortOrder = extend.get("order") == null ? sortOrder : extend.get("order");
        parentId = extend.get("parentId") == null ? defaultParentId : extend.get("parentId");
        switch (tid) {
            case "link": //mixed
                linkSort = linkMark.equals("PhotoAlbum") ? "&SortBy=Container,SortName&SortOrder=Ascending" : linkSort;
                linkSort = linkMark.equals("Season") ? "" : linkSort;
                // cateUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Fields=MediaStreams,parentId,CommunityRating" + linkSort + "&parentid=" + linkValue + "&limit=20&startindex=" + start + "&ExcludeItemIds=" + linkValue;
                cateUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Fields=MediaStreams,parentId,CommunityRating" + linkSort + "&parentid=" + linkValue + "&ExcludeItemIds=" + linkValue ;
                break;
            case "Favourite":
                itemType = extend.get("type") == null ? "PhotoAlbum,Movie,Series" : extend.get("type");
                cateUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Recursive=true&Fields=MediaStreams,parentId,CommunityRating&SortBy=Random&limit=20&Filters=IsFavorite&IncludeItemTypes=" + itemType + "&startindex=" + start + "&parentid=" + parentId;
                break;
            case "search":
                return searchContent(searchKey, true, pg);
            case "Movie&Series":
                itemType = extend.get("type") == null ? "Movie,Series" : extend.get("type");
                cateUrl = getItemUrl + itemType + "&startindex=" + start + "&SortOrder=" + sortOrder + "&SortBy=" + sortBy + "&parentid=" + parentId;
                break;
            case "HomeMedia":
                itemType = extend.get("type") == null ? "PhotoAlbum" : extend.get("type");
                cateUrl = getItemUrl + itemType + "&startindex=" + start + "&SortOrder=" + sortOrder + "&SortBy=" + sortBy + "&parentid=" + parentId;
                break;
            default:
                cateUrl = getItemUrl + tid + "&startindex=" + start + "&parentid=" + parentId;
                break;

        }
        JSONArray items = new JSONObject(OkHttp.string(cateUrl)).optJSONArray("Items");
        List<Vod> list = new ArrayList<Vod>();
        if (tid.equals("link")) {
            list = parseVodList(items, true);
            return Result.get().vod(list).page().string();
        } else {
            list = parseVodList(items, false);
        }
        int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 20, total = Integer.MAX_VALUE;
        return Result.get().vod(list).page(page, count, limit, total).string();
    }

    private List<Vod> parseVodList(JSONArray items) throws Exception{
        return parseVodList(items, false);
    }

    private List<Vod> parseVodList(JSONArray items, boolean ifAddCustomVod) throws Exception {
        List<Vod> list = new ArrayList<>();
        Vod customVod = new Vod();
        StringBuilder customVodUrlBuilder = new StringBuilder();
        String vodPlayUrl, firstVideoName = "", firstVideoPic = "";
        int customVodCount = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            Vod vod = parseVod(item).getKey();
            boolean skip = parseVod(item).getValue();
            if (skip) continue;

            if (ifAddCustomVod && vod.getTypeName().equals("Video")) {
                customVodCount++;
                vodPlayUrl = siteUrl + "/Videos/"+ item.optString("Id") +"/stream."+item.optString("Container") + apikey + "&static=true";
                customVodUrlBuilder.append(item.optString("Name")).append("$").append(vodPlayUrl).append("#");
                firstVideoName = firstVideoName.isEmpty() ? item.optString("Name") : firstVideoName;
                firstVideoPic = firstVideoPic.isEmpty() ? (siteUrl + "/Items/" + item.optString("Id") + imageUri) : firstVideoPic;
            }

            list.add(vod);
        }
        if (ifAddCustomVod && customVodCount > 0) {
            customVod.setVodName(firstVideoName);
            JSONObject item = items.optJSONObject(0);
            String tempVodId =
                    firstVideoName + "$$" + firstVideoPic + "###" +
                    Utils.substring(customVodUrlBuilder.toString(),1) +
                    "$$$" + item.optString("ParentId") +
                    "$$${customVod}";
            tempVodId = Base64.getEncoder().encodeToString(tempVodId.getBytes()) + "0123456789";
            customVod.setVodId(tempVodId);
            customVod.setVodRemarks("视频合集");
            customVod.setVodPic(firstVideoPic);
            list.add(0, customVod);
        }
        return list;
    }
    
    private Map.Entry<Vod, Boolean> parseVod(JSONObject item) {
        Vod vod = new Vod();
        vod.setVodName(item.optString("Name"));       
        String picUrl = siteUrl + "/Items/" + item.optString("Id") + imageUri;
        vod.setVodPic(picUrl);
        String vodType = item.optString("Type");
        vod.setTypeName(vodType);
        vod.setVodId(item.optString("Id"));
        if (vodType.equals("Video")) {
            String durationString = item.optString("RunTimeTicks", "");
            if (durationString.isEmpty()) return new AbstractMap.SimpleEntry<>(vod, true);
            long durationInSeconds = Long.parseLong(durationString) / 10000000;
            long minutes = durationInSeconds / 60;
            long seconds = durationInSeconds % 60;
            vod.setVodRemarks("视频 时长: " + minutes + " 分 " + seconds + " 秒");
        } else if (vodType.equals("Photo")) {
            vod.setVodRemarks("图片");
            vod.setVodTag("photo");
            vod.setVodPic(siteUrl + "/Items/" + item.optString("Id") + "/Images/Primary");
        } else if (vodType.equals("Movie")) {
            vod.setVodRemarks("电影 " + item.optString("CommunityRating"));
        } else if (vodType.equals("Episode")) {
            String remark = item.optString("SeriesName") + "  " + item.optString("SeasonName");
            vod.setVodRemarks(remark);
            vod.setStyle(landscapeStyle);
            
        } else if (item.optString("IsFolder").equals("true")) {
            vod.setVodTag("folder");
            vod.setVodId("link/"+item.optString("Id")+"/{link}");
            if (vodType.equals("Series")) {
                vod.setVodRemarks("剧集 " + item.optString("CommunityRating"));
            } else if (vodType.equals("Season")) {
//                vod.setVodId("link@Season/"+item.optString("Id")+"/{link}");
//                vod.setVodRemarks(item.optString("SeriesName"));

                // 虽然 series 是 folder 但是按照 vod 处理它， 下面的 vod 代表 s1 s2 ...
                vod.setVodTag("");
                vod.setVodId(item.optString("Id"));
                vod.setVodName(item.optString("SeriesName") + " 第" + arabicToChinese(item.optString("IndexNumber")) + "季");

            } else if (vodType.equals("PhotoAlbum")) {
                picUrl = siteUrl + "/Items/" + item.optString("PrimaryImageItemId") + imageUri;
                vod.setVodPic(picUrl);
                vod.setVodRemarks("合集");
                vod.setVodId("link@PhotoAlbum/" + item.optString("Id") + "/{link}");
            } else if (vodType.equals("Folder")) {
                picUrl = siteUrl + "/Items/" + item.optString("PrimaryImageItemId") + imageUri;
                vod.setVodPic(picUrl);
                vod.setVodRemarks("文件夹");
            } else {
                vod.setVodRemarks(item.optString("Type"));
            }
        } 
        return new AbstractMap.SimpleEntry<>(vod, false);
    }

    private String arabicToChinese(String arabic) {
        Map<String, String> arabicToChinese = new HashMap<>();
        arabicToChinese.put("0", "零");
        arabicToChinese.put("1", "一");
        arabicToChinese.put("2", "二");
        arabicToChinese.put("3", "三");
        arabicToChinese.put("4", "四");
        arabicToChinese.put("5", "五");
        arabicToChinese.put("6", "六");
        arabicToChinese.put("7", "七");
        arabicToChinese.put("8", "八");
        arabicToChinese.put("9", "九");
        arabicToChinese.put("10", "十");
        arabicToChinese.put("11", "十一");
        arabicToChinese.put("12", "十二");
        arabicToChinese.put("13", "十三");
        return arabicToChinese.get(arabic);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        if (id.endsWith("0123456789")) {
            id = Utils.substring(id, 10);
            id = new String(Base64.getDecoder().decode(id));
            Vod vod = new Vod();
            vod.setVodName(id.split("###")[0].split("\\$\\$")[0]);
            vod.setVodPic(id.split("###")[0].split("\\$\\$")[1]);
            vod.setVodPlayFrom("Emby");
            String text = id.replace("$$${customVod}", "");
            text = text.split("###")[1];
            String playUrl = text.split("\\$\\$\\$")[0];
            vod.setVodPlayUrl(playUrl);
            if (text.split("\\$\\$\\$")[1] != null) {
                String parentId = text.split("\\$\\$\\$")[1];
                String url = siteUrl + "/Users/" + userId + "/Items/" + parentId + apikey;
                JSONObject parentItem = new JSONObject(OkHttp.string(url));
                String serverId = parentItem.optString("ServerId");
                String parentIdOfParent = parentItem.optString("ParentId");
                String linkOfFolder = "上一级链接：[hyperlink=emby]emby://items/" + serverId + "/" + parentIdOfParent + "[/hyperlink]";
                String linkOfParentOfFolder = "上上级链接：[hyperlink=emby_folder]emby://items/" + serverId + "/" + parentIdOfParent + "[/hyperlink]";

                vod.setVodContent(linkOfFolder + "\n" + linkOfParentOfFolder);
            }
            return Result.string(vod);
        }
        String searchUrl = siteUrl + "/Users/" + userId + "/Items/" + id + apikey;
        JSONObject item = new JSONObject(OkHttp.string(searchUrl));
        Vod vod = new Vod();

        String vodType = item.optString("Type");
        if (vodType.equals("Photo")) {
            String picUrl = siteUrl + "/Items/"+item.optString("Id")+imageUri;
            vod.setVodPic(picUrl);
            return Result.string(vod);
//        } else if (vodType.equals("Episode")) {
//            vod.setVodName(item.optString("SeriesName") + " " + "第" + toChineseNumber(item.optString("ParentIndexNumber")) + "季");
//            if (!item.optString("ParentIndexNumber").equals("1")) currentVodName = vod.getVodName();
//            vod.setVodRemarks("E" + item.optString("IndexNumber") + ": " + item.optString("Name") + " " + vod.getVodRemarks());
        } else if (vodType.equals("Season")) {
            vod.setVodName(item.optString("SeriesName") + " 第" + arabicToChinese(item.optString("IndexNumber")) + "季");
            String hyperlink = "链接：[hyperlink=emby]emby://items/" + item.optString("ServerId") + "/" + item.optString("Id") + "[/hyperlink]";
            String vodContent = item.optString("Overview") +
                    "\n\n路径：" + generatePath(item.optString("Path"), item.optString("ParentId")) +
                    "\n" + hyperlink;
            vod.setVodContent(vodContent);
            vod.setVodYear(item.optString("ProductionYear"));
            vod.setVodRemarks(item.optString("CommunityRating"));

            String episodesUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Fields=MediaStreams,parentId,CommunityRating" + "&parentid=" + item.optString("Id") + "&ExcludeItemIds=" + item.optString("Id");
            JSONArray episodes = new JSONObject(OkHttp.string(episodesUrl)).optJSONArray("Items");

            StringBuilder urlBuilder = new StringBuilder();
            StringBuilder fromBuilder = new StringBuilder();

            Map<String, StringBuilder> builderMap = new HashMap<>();
            Map<String, String> fromBuilderMap = new HashMap<>();

            for (int i = 0; i < (episodes != null ? episodes.length() : 0); i++) {
                JSONObject episode = episodes.optJSONObject(i);

                JSONArray mediaArray = episode.optJSONArray("MediaSources");
                for (int j = 0; j < (mediaArray != null ? mediaArray.length() : 0); j++) {
                    JSONObject media = mediaArray.optJSONObject(j);
                    String mediaName = media.optString("Name");

                    if (!builderMap.containsKey(Integer.toString(j))) {
                        builderMap.put(Integer.toString(j), new StringBuilder());
                        fromBuilderMap.put(Integer.toString(j), mediaName);
                    }

//                    String episodeName = "第 " + (i + 1) + " 集 " + episode.optString("Name");
                    String episodeName = episode.optString("Name");
                    String episodeIndexNumber = episode.optString("IndexNumber");
                    String episodePlayUrl = siteUrl + "/Videos/" + episode.optString("Id") + "/stream." + media.optString("Container") + apikey + "&static=true" + "&MediaSourceId=" + media.optString("Id");
                    builderMap.get(Integer.toString(j)).append(episodeIndexNumber).append("^^").append(episodeName).append("$").append(episodePlayUrl).append("#");
                }
            }

            for (String key : builderMap.keySet()) {
                builderMap.get(key).deleteCharAt(builderMap.get(key).length() - 1);
                fromBuilder.append(fromBuilderMap.get(key)).append("$$$");
                urlBuilder.append(builderMap.get(key).toString()).append("$$$");
            }
            vod.setVodPlayFrom(Util.substring(fromBuilder.toString(), 3));
            vod.setVodPlayUrl(Util.substring(urlBuilder.toString(), 3));
//                currentEpisodeNumber = item.optString("IndexNumber", "1");

            danmuVod.setVodName(currentVodName = !item.optString("IndexNumber").equals("1") ? vod.getVodName() : item.optString("SeriesName"));
            danmuVod.setVodPlayFrom(vod.getVodPlayFrom());
            danmuVod.setVodPlayUrl(vod.getVodPlayUrl());
            danmuVod.setVodMediaType("tv");
            return Result.string(vod);
        } else {
            vod.setVodName(item.optString("Name"));
//            currentVodName = vod.getVodName();
            String remark = item.optString("CommunityRating", "xx") + "┃" + timeConvert(item.optString("RunTimeTicks", ""));
            vod.setVodRemarks(remark);


            String vodPlayUrl = siteUrl + "/Videos/"+id+"/stream."+item.optString("Container") + apikey + "&static=true";
            JSONArray mediaArray = item.optJSONArray("MediaSources");
            StringBuilder urlBuilder = new StringBuilder();
            StringBuilder fromBuilder = new StringBuilder();
            StringBuilder contentBuilder = new StringBuilder();
            contentBuilder.append(item.optString("Overview")).append("\n\n");
            for (int i = 0; i < mediaArray.length(); i++) {
                JSONObject media = mediaArray.optJSONObject(i);
                fromBuilder.append(media.optString("Name")).append(" [").append(fileSizeConvert(item.optString("Size", ""))).append("]").append("$$$");
                String tempPlayUrl = vodPlayUrl + "&MediaSourceId=" + media.optString("Id");
                urlBuilder.append(tempPlayUrl).append("$$$");
            }
            String path = generatePath(item.optString("Path"), item.optString("ParentId"));
            contentBuilder.append("路径：").append(path).append("\n");
            vod.setVodPlayUrl(Util.substring(urlBuilder.toString(),3));
            vod.setVodPlayFrom(Util.substring(fromBuilder.toString(),3));
            String hyperlink = "链接：[hyperlink=emby]emby://items/" + item.optString("ServerId") + "/" + item.optString("Id") + "[/hyperlink]";
            String hyperlink2 = "上一级链接：[hyperlink=emby_folder]emby://items/" + item.optString("ServerId") + "/" + item.optString("ParentId") + "[/hyperlink]";
            String url = siteUrl + "/Users/" + userId + "/Items/" + item.optString("ParentId") + apikey;
            String parentIdOfParent = new JSONObject(OkHttp.string(url)).optString("ParentId");
            String hyperlink3 = "上上级链接：[hyperlink=folder_list]emby://items/" + item.optString("ServerId") + "/" + parentIdOfParent + "[/hyperlink]";
            vod.setVodContent(contentBuilder.append(hyperlink).append("\n").append(hyperlink2).append("\n").append(hyperlink3).toString());
//        currentEpisodeNumber = item.optString("IndexNumber", "1");

            danmuVod.setVodName(vod.getVodName());
            danmuVod.setVodMediaType("movie");
            return Result.string(vod);
        }
    }

    private String generatePath(String path, String parentId) {
        String[] folders = path.split("/");
        StringBuilder pathBuilder = new StringBuilder();
        for (int i = 0; i < folders.length - 2; i++) {
            pathBuilder.append(folders[i]).append("/");
        }

        String link = String.format("[a=cr:{\"id\":\"%s\", \"name\":\"%s\"}/]%s[/a]", "link/" + parentId + "/{link}", folders[folders.length -2], folders[folders.length -2]);
        pathBuilder.append(link).append("/").append(folders[folders.length -1]);
        return pathBuilder.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }
    
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        searchKey = key;
        List<Vod> list = new ArrayList<>();
        int start = (Integer.parseInt(pg) - 1) * 10;
        String searchUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Recursive=true&limit=10&SearchTerm=" + key + "&StartIndex=" + start + "&ExcludeItemTypes=Episode";
        JSONArray items = new JSONObject(OkHttp.string(searchUrl)).optJSONArray("Items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            Vod vod = parseVod(item).getKey();
            boolean skip = parseVod(item).getValue();
            if (skip == true) continue;
            // vod.setVodName(item.optString("Name"));
            // vod.setVodId(item.optString("Id"));
            // String picUrl = siteUrl + "/Items/"+item.optString("Id")+imageUri;
            // vod.setVodPic(picUrl);
            list.add(vod);
        }
        return Result.get().vod(list).page().string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (this.isDanmu.equals("true")) {
            String title = danmuVod.getVodName();
            String routeNames = danmuVod.getVodPlayFrom();
            String routeValues = danmuVod.getVodPlayUrl();
            // 目标线路名称和 URL
            int episodeNumber = "tv".equals(danmuVod.getVodMediaType()) ? Douban.findEpisode(routeNames, routeValues, flag, id) : 1;
            String danmaku = Danmaku.getDanmaku(title, episodeNumber);
            return Result.get().url(id).danmaku(danmaku).string();
        }
        else return Result.get().url(id).string();
    }
    
    private String timeConvert(String durationString){
        long durationInSeconds = Long.parseLong(durationString) / 10000000;
        long minutes = durationInSeconds / 60;
        long seconds = durationInSeconds % 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours == 0) return (minutes + " 分 " + seconds + " 秒");
        return (hours + " 时 " + minutes + " 分 " + seconds + " 秒");
    }

    private String fileSizeConvert (String sizeInBytesStr){
        try {
            long sizeInBytes = Long.parseLong(sizeInBytesStr);
            if (sizeInBytes <= 0) {
                return "0 B";
            }

            final String[] units = {"B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};
            int digitGroups = (int) (Math.log10(sizeInBytes) / Math.log10(1024));

            return String.format(Locale.CHINA, "%.2f %s", sizeInBytes / Math.pow(1024, digitGroups), units[digitGroups]);
        } catch (NumberFormatException e) {
            return "Invalid input"; // Handle the case when input is not a valid number
        }
    }

    public static String toChineseNumber(String numberStr) {
        String[] CN_NUMERIC = {"零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

        try {
            int num = Integer.parseInt(numberStr);
            if (num >= 1 && num <= 9) {
                return CN_NUMERIC[num];
            } else {
                return "X";
            }
        } catch (NumberFormatException e) {
            return "X";
        }
    }
}