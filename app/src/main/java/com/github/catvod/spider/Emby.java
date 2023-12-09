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
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

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
        this.getItemUrl = this.siteUrl + "/Users/" + this.userId + "/Items" + this.apikey + "&Recursive=true&limit=20&Fields=MediaStreams&IncludeItemTypes=";
        this.homeContentType = siteOb.optString("homeContentType");
        this.isDanmu = siteOb.optString("danmu");
        this.landscapeStyle = Vod.Style.rect(1.777f);
        this.defaultParentId = siteOb.optString("defaultParentId", "");
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
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        
        String searchUrl = getItemUrl + homeContentType + "&SortBy=Random" + "&parentid=" + defaultParentId;
        JSONArray items = new JSONObject(OkHttp.string(searchUrl)).optJSONArray("Items");
        return Result.get().vod(parseVodList(items)).classes(classes).filters(myFilters).string(); 
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
        String cateUrl, itemType, sortOrder, sortBy, parentId;
        sortOrder = extend.get("order") == null ? "Ascending" : extend.get("order");
        sortBy = extend.get("sort") == null ? "SortName" : extend.get("sort");
        parentId = extend.get("parentId") == null ? defaultParentId : extend.get("parentId");
        switch (tid) {
            case "link": //mixed
                linkSort = linkMark.equals("PhotoAlbum") ? "&SortBy=Container,SortName" : linkSort;
                linkSort = linkMark.equals("Season") ? "" : linkSort;
                // cateUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Fields=MediaStreams" + linkSort + "&parentid=" + linkValue + "&limit=20&startindex=" + start + "&ExcludeItemIds=" + linkValue;
                cateUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Fields=MediaStreams" + linkSort + "&parentid=" + linkValue + "&ExcludeItemIds=" + linkValue ;
                break;
            case "Favourite":
                itemType = extend.get("type") == null ? "PhotoAlbum,Movie,Series" : extend.get("type");
                cateUrl = siteUrl + "/Users/" + userId + "/Items" + apikey + "&Recursive=true&Fields=MediaStreams&SortBy=Random&limit=20&Filters=IsFavorite&IncludeItemTypes=" + itemType + "&startindex=" + start + "&parentid=" + parentId;
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
        String vodPlayUrl;
        int customVodCount = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            Vod vod = parseVod(item).getKey();
            boolean skip = parseVod(item).getValue();
            if (skip == true) continue;

            if (ifAddCustomVod && vod.getTypeName().equals("Video")) {
                customVodCount++;
                vodPlayUrl = siteUrl + "/Videos/"+ item.optString("Id") +"/stream."+item.optString("Container") + apikey + "&static=true";
                customVodUrlBuilder.append(item.optString("Name")).append("$").append(vodPlayUrl).append("#");
            }

            list.add(vod);
        }
        if (ifAddCustomVod && customVodCount > 0) {
            customVod.setVodName("Emby");
            customVod.setVodId(Utils.substring(customVodUrlBuilder.toString(),1) + "/{customVod}");
            customVod.setVodRemarks("视频合集");
            customVod.setVodPic("https://cdn-icons-png.flaticon.com/512/1179/1179120.png");
            list.add(0, customVod);
        }
        return list;
    }
    
    private Map.Entry<Vod, Boolean> parseVod(JSONObject item) {
        Vod vod = new Vod();
        vod.setVodName(item.optString("Name"));       
        String picUrl = siteUrl + "/Items/"+item.optString("Id")+"/Images/Primary";
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
        } else if (vodType.equals("Movie")) {
            vod.setVodRemarks("电影");
        } else if (vodType.equals("Episode")) {
            String remark = item.optString("SeriesName") + "  " + item.optString("SeasonName");
            vod.setVodRemarks(remark);
            vod.setStyle(landscapeStyle);
            
        } else if (item.optString("IsFolder").equals("true")) {
            vod.setVodTag("folder");
            vod.setVodId("link/"+item.optString("Id")+"/{link}");
            if (vodType.equals("Series")) {
                vod.setVodRemarks("剧集");
            } else if (vodType.equals("Season")) {
                vod.setVodId("link@Season/"+item.optString("Id")+"/{link}");
                vod.setVodRemarks(item.optString("SeriesName"));
            } else if (vodType.equals("PhotoAlbum")) {
                picUrl = siteUrl + "/Items/"+item.optString("PrimaryImageItemId")+"/Images/Primary";
                vod.setVodPic(picUrl);
                vod.setVodRemarks("合集");
                vod.setVodId("link@PhotoAlbum/"+item.optString("Id")+"/{link}");
            } else {
                vod.setVodRemarks(item.optString("Type"));
            }
        } 
        return new AbstractMap.SimpleEntry<>(vod, false);
    }


    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        if (id.endsWith("/{customVod}")) {
            Vod vod = new Vod();
            vod.setVodName("All Videos");
            vod.setVodPlayFrom("Emby");
            vod.setVodPlayUrl(Utils.substring(id,12));
            return Result.string(vod);
        }
        String searchUrl = siteUrl + "/Users/" + userId + "/Items/" + id + apikey;
        JSONObject item = new JSONObject(OkHttp.string(searchUrl));
        Vod vod = new Vod();
        vod.setVodName(item.optString("Name"));
        currentVodName = vod.getVodName();
        String remark = timeConvert(item.optString("RunTimeTicks", "")); 
        vod.setVodRemarks(remark);
        String vodType = item.optString("Type");
        if (vodType.equals("Photo")) {
            String picUrl = siteUrl + "/Items/"+item.optString("Id")+"/Images/Primary";
            vod.setVodPic(picUrl);
            return Result.string(vod);
        } else if (vodType.equals("Episode")) {
            vod.setVodName(item.optString("SeriesName") + " " + "第" + toChineseNumber(item.optString("ParentIndexNumber")) + "季");
            if (!item.optString("ParentIndexNumber").equals("1")) currentVodName = vod.getVodName();
            vod.setVodRemarks("E" + item.optString("IndexNumber") + ": " + item.optString("Name") + " " + vod.getVodRemarks());
        }
        String vodPlayUrl = siteUrl + "/Videos/"+id+"/stream."+item.optString("Container") + apikey + "&static=true";       
        JSONArray mediaArray = item.optJSONArray("MediaSources");
        StringBuilder urlBuilder = new StringBuilder();
        StringBuilder fromBuilder = new StringBuilder();
        StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append(item.optString("Overview")).append("\n");
        for (int i = 0; i < mediaArray.length(); i++) {
            JSONObject media = mediaArray.optJSONObject(i);
            fromBuilder.append(media.optString("Name")).append(" [").append(fileSizeConvert(item.optString("Size", ""))).append("]").append("$$$");
            String tempPlayUrl = vodPlayUrl + "&MediaSourceId=" + media.optString("Id");
            urlBuilder.append(tempPlayUrl).append("$$$");
            contentBuilder.append(media.optString("Path")).append("\n");
        }
        vod.setVodPlayUrl(Util.substring(urlBuilder.toString(),3));
        vod.setVodPlayFrom(Util.substring(fromBuilder.toString(),3));
        vod.setVodContent(contentBuilder.toString());
        currentEpisodeNumber = item.optString("IndexNumber", "1");
        return Result.string(vod);
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
            // String picUrl = siteUrl + "/Items/"+item.optString("Id")+"/Images/Primary";
            // vod.setVodPic(picUrl);
            list.add(vod);
        }
        return Result.get().vod(list).page().string();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (this.isDanmu.equals("true")) return Result.get().url(id).danmaku(Danmaku.getDanmaku(currentVodName, Integer.parseInt(currentEpisodeNumber))).string();
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

            return String.format("%.2f %s", sizeInBytes / Math.pow(1024, digitGroups), units[digitGroups]);
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