package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.danmaku.Danmaku;

import com.github.catvod.utils.Util;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;



public class Douban extends Spider {
    private final String siteUrl = "https://frodo.douban.com/api/v2";
    private final String siteUrlWithCookie = "https://m.douban.com/rexxar/api/v2";
    private final String apikey = "?apikey=0ac44ae016490db2204ce0a042db2916";
    private JSONObject extend;
    private String myCookie;
    private String tagName = "";
    private Vod currentVod;
    private String isCmsOrdered;
    private String myFilter;
    private JSONArray cmsArray;

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("Host", "frodo.douban.com");
        header.put("Connection", "Keep-Alive");
        header.put("Referer", "https://servicewechat.com/wx2f9b06c1de1ccfca/84/page-frame.html");
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat");
        return header;
    }

    private Map<String, String> getHeaderWithCookie() {
        Map<String, String> header = new HashMap<>();
        header.put("Connection", "Keep-Alive");
        header.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat");

        header.put("Host", "m.douban.com");
        header.put("Referer", "https://movie.douban.com");
        header.put("Cookie", myCookie);
        return header;
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (extend.startsWith("http")) extend = OkHttp.string(extend);
        JSONObject extendOb = new JSONObject(extend);
        this.myCookie = extendOb.optString("cookie","");
        this.isCmsOrdered = extendOb.optString("isCmsOrdered","false");
        this.myFilter = extendOb.optJSONObject("filter").toString();
        this.cmsArray = extendOb.optJSONArray("cms");
        this.extend = extendOb;

    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("hot", "filter", "newlist", "search", "rank_list");
        List<String> typeNames = Arrays.asList("热门", "筛选", "片单", "搜索", "榜单");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));
        String recommendUrl = "http://api.douban.com/api/v2/subject_collection/subject_real_time_hotest/items" + apikey;
        JSONObject jsonObject = new JSONObject(OkHttp.string(recommendUrl, getHeader()));
        JSONArray items = jsonObject.optJSONArray("subject_collection_items");
        return Result.string(classes, parseVodListFromJSONArray(items), filter ? JsonParser.parseString(myFilter) : null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String linkValue = "";
        if (tid.endsWith("/{link}")) {
            linkValue = tid.split("/")[1];
            tid = tid.split("/")[0];
        }
        String sort = extend.get("sort") == null ? "U" : extend.get("sort");
        String tags = URLEncoder.encode(getTags(extend));
        int start = (Integer.parseInt(pg) - 1) * 20;
        String cateUrl;
        String itemKey = "items";
        int cookieFlag = 0;
        switch (tid) {
            case "chart":
                cateUrl = siteUrl + "/subject_collection/" + linkValue + "/items" + apikey + "&start=" + start + "&count=20";
                itemKey = "subject_collection_items";
                break;
            case "celebs":
                return celebsContent(linkValue, true, pg);
            case "doulist":
                return doulistContent(linkValue, true, pg);

            case "rank_list":
                return chartContent(pg);
            case "search":
                return searchContent(tagName, true, pg, 20);
            case "newlist":
                String category = extend.get("category") == null ? "all" : extend.get("category");
                String subjectType = extend.get("subject_type") == null ? "" : extend.get("subject_type");
                return newlistContent(category, subjectType, pg);
            case "hot":
                String hotType = extend.get("type") == null ? "movie" : extend.get("type");
                switch (hotType) {
                    case "tv" :
                        String tvType = extend.get("tv_type") == null ? "tv_hot" : extend.get("tv_type");
                        cateUrl = siteUrl + "/subject_collection/" + tvType + "/items" + apikey + "&start=" + start + "&count=20";
                        itemKey = "subject_collection_items";
                        break;
                    case "show" :
                        String showType = extend.get("show_type") == null ? "show_hot" : extend.get("show_type");
                        cateUrl = siteUrl + "/subject_collection/" + showType + "/items" + apikey + "&start=" + start + "&count=20";
                        itemKey = "subject_collection_items";
                        break;
                    case "movie" :
                    default:
                        String movieSort = extend.get("movie_sort") == null ? "recommend" : extend.get("movie_sort");
                        String movieArea = extend.get("movie_area") == null ? "全部" : extend.get("movie_area");
                        movieSort = movieSort + "&area=" + URLEncoder.encode(movieArea);
                        cateUrl = siteUrl + "/movie/hot_gaia" + apikey + "&sort=" + movieSort + "&start=" + start + "&count=20";
                        break;
                }
                break;
            case "filter" :
            default:
                String filterType = extend.get("tv_type") == null ? "movie" : (extend.get("movie_type") == null ? "tv" : "movie");
                switch (filterType) {
                    case "tv":
                        if (myCookie.isEmpty()) {
                            cateUrl = siteUrl + "/tv/recommend" + apikey + "&sort=" + sort + "&tags=" + tagName + "," + tags + "&start=" + start + "&count=20";
                        } else {
                            cookieFlag = 1;
                            cateUrl = siteUrlWithCookie + "/tv/recommend?refresh=0&start=" + start + "&count=20&selected_categories=%7B%7D&uncollect=false&sort=" + sort + "&tags=" + tagName + "," + tags + "&ck=Q5T8";
                        }
                        break;
                    case "movie":
                    default:
                        if (myCookie.isEmpty()) {
                            cateUrl = siteUrl + "/movie/recommend" + apikey + "&sort=" + sort + "&tags=" + tagName + "," + tags + "&start=" + start + "&count=20";
                        } else {
                            cookieFlag = 1;
                            cateUrl = siteUrlWithCookie + "/movie/recommend?refresh=0&start=" + start + "&count=20&selected_categories=%7B%7D&uncollect=false&sort=" + sort + "&tags=" + tagName + "," + tags + "&ck=Q5T8";
                        }
                        break;
                }
                break;


        }
        JSONObject object;
        if (cookieFlag == 1) {
          object = new JSONObject(OkHttp.string(cateUrl, getHeaderWithCookie()));
        } else {
          object = new JSONObject(OkHttp.string(cateUrl, getHeader()));
        }
        JSONArray array = object.getJSONArray(itemKey);
        List<Vod> list = parseVodListFromJSONArray(array);
        int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 20, total = Integer.MAX_VALUE;
        return Result.get().vod(list).page(page, count, limit, total).string();
    }

    private List<Vod> parseVodListFromJSONArray(JSONArray items) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);

            String emoji = "";
            String vodType = getType(item);
            String vodId = item.optString("id");
            String name = item.optString("title");
            String pic = getPic(item);


            if (name == null || name.isEmpty()) continue;//过滤广告
            if (vodType.equals("playlist")) {
                emoji = "️📇";
                String remark = emoji + item.optString("subtitle");
                vodId = "doulist/" + item.optString("id") + "/{link}";
                pic = item.optString("cover_url") + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
                list.add(new Vod(vodId, name, pic, remark, true));//true表示是文件夹
                continue;
            }
            if (vodType.equals("movie")) {
                emoji = "🎬";
            }
            if (vodType.equals("tv")) {
                emoji = "📺";
            }
            String remark = emoji + getRating(item) + " " + getCard(item);
            if (vodType.equals("tags")) {
                emoji = "🏷️";
                remark = emoji + getTagName(item);
                vodId = "movie_tag/" + getTagName(item) + "/{link}";
                pic = item.optString("cover_url") + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
                list.add(new Vod(vodId, name, pic, remark, true));//true表示是文件夹
                continue;
            }
            list.add(new Vod(vodId, name, pic, remark));
        }
        return list;
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        if (id.endsWith("/{cmsSingle}")) {
            int cmsOrder = Integer.parseInt(id.split("/")[1]);
            String itemId = id.split("/")[0];
            String cmsDetailUrl = cmsArray.optJSONObject(cmsOrder).optString("api") + "?ac=detail&ids=" + itemId;
            JSONObject item  = new JSONObject(OkHttp.string(cmsDetailUrl)).optJSONArray("list").optJSONObject(0);
            Vod vod = new Vod();
            vod.setVodName(item.optString("vod_name"));
            vod.setTypeName(item.optString("vod_class", ""));
            vod.setVodDirector(item.optString("vod_director", ""));
            vod.setVodActor(item.optString("vod_actor", ""));
            vod.setVodId(itemId);
            vod.setVodArea(item.optString("vod_area", ""));
            vod.setVodRemarks("Update: " + item.optString("vod_time"));
            vod.setVodContent(item.optString("vod_content", ""));
            vod.setVodPlayFrom(item.optString("vod_play_from"));
            vod.setVodPlayUrl(item.optString("vod_play_url"));
            currentVod = vod;
            return Result.string(vod);
        }

        String idUrl = siteUrl + "/subject/" + id + apikey;
        JSONObject item = new JSONObject(OkHttp.string(idUrl, getHeader()));

        Vod vod = new Vod();
        vod.setVodId(item.optString("id"));
        vod.setVodName(item.optString("title"));
        vod.setVodRemarks(item.optString("episodes_info") + " " + getJAS(item, "pubdate", " "));
        vod.setVodPic(item.optString("cover_url"));
        vod.setVodYear(item.optString("year"));
        vod.setVodArea(getJAS(item, "countries", " "));
        vod.setVodTag(item.optString("card_subtitle"));
        vod.setTypeName(getJAS(item, "genres", " "));
        vod.setVodDirector(getCelebsLink(item, "directors", " ", "celebs"));
        vod.setVodActor(getCelebsLink(item, "actors", " ", "celebs"));
        vod.setVodPlayFrom(getJAJ(item, "vendors", "title", "$$$"));
        vod.setVodPlayUrl(getJAJ(item, "vendors", "url", "$$$"));
        String itemType = item.optString("type");
        String desc = item.optString("url") + "\n" + getJASLink(item, "countries", " ", itemType + "_tag") + " " + getJASLink(item, "genres", " ", itemType + "_tag") + "\n" + item.optString("intro");
        vod.setVodContent(desc);
        vod = cmsHandler(vod);
        //vod = genParseUrl(vod);
        currentVod = vod;
        return Result.string(vod);
    }

    private Vod genParseUrl(Vod vod) throws Exception {
        String searchUrl = "https://dmku.leftcuz.top:8443/searchplayurl?name=" + vod.getVodName();
        vod.setVodPlayUrl(vod.getVodPlayUrl() + "$$$" + OkHttp.string(searchUrl));
        vod.setVodPlayFrom(vod.getVodPlayFrom() + "$$$解析");
        return vod;
    }

    private Vod cmsHandler(Vod vod) {
        return cmsHandler(vod, true);
    }
    private Vod cmsHandler(Vod vod, boolean quick) {
        try {
            String vodName = vod.getVodName();
            //JSONArray cmsArray = extend.optJSONArray("cms");
            //Util.notify("cms");
            StringBuilder playFromBuilder = new StringBuilder();
            StringBuilder lastPlayFromBuilder = new StringBuilder();
            StringBuilder playUrlBuilder = new StringBuilder();
            AtomicInteger total = new AtomicInteger(cmsArray.length());

            StringBuilder[] playFromBuilderArray = IntStream.range(0, total.get())
                    .mapToObj(i -> new StringBuilder())
                    .toArray(StringBuilder[]::new);
            StringBuilder[] playUrlBuilderArray = IntStream.range(0, total.get())
                    .mapToObj(i -> new StringBuilder())
                    .toArray(StringBuilder[]::new);

            // Create a list of CompletableFuture for parallel HTTP requests
            List<CompletableFuture<Void>> futures = IntStream.range(0, total.get())
                    .mapToObj(i -> CompletableFuture.runAsync(() -> {
                        try {
                            String cmsUrl = cmsArray.optJSONObject(i).optString("api");
                            String cmsSearchUrl = cmsUrl + "?wd=" + vodName + "&quick=" + Boolean.toString(quick);
                            String cmsName = cmsArray.optJSONObject(i).optString("name");

                            //获取名字完全一致的影片id
                            String cmsVodId = "";
                            JSONObject cmsQueryOb = new JSONObject(OkHttp.string(cmsSearchUrl));
                            for (int j = 0; j < cmsQueryOb.optInt("total"); j++) {
                                if (cmsQueryOb.optJSONArray("list").optJSONObject(j).optString("vod_name").equals(vodName)) {
                                    cmsVodId = cmsQueryOb.optJSONArray("list").optJSONObject(j).optString("vod_id");
                                    break;
                                }
                            }

                            String cmsItemUrl = cmsUrl + "?ac=detail&ids=" + cmsVodId;
                            JSONObject cmsItemOb = new JSONObject(OkHttp.string(cmsItemUrl)).optJSONArray("list").optJSONObject(0);
                            if (isCmsOrdered.equals("true")) {
                                playFromBuilderArray[i].append(cmsName).append("$$$");
                                playUrlBuilderArray[i].append(cmsItemOb.optString("vod_play_url")).append("$$$");
                            } else {
                                playFromBuilder.append(cmsName).append("$$$");
                                playUrlBuilder.append(cmsItemOb.optString("vod_play_url")).append("$$$");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }))
                    .collect(Collectors.toList());

            CompletableFuture<Void> lastFuture = CompletableFuture.runAsync(() -> {
                String searchUrl = "https://dmku.leftcuz.top:8443/searchplayurl?name=" + vod.getVodName();
                lastPlayFromBuilder.append(OkHttp.string(searchUrl));
            });

            futures.add(lastFuture);
            // Wait for all requests to complete
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

            // Wait for all requests to complete
            allOf.join();

            if (isCmsOrdered.equals("ture")) {
                for (int i = 0; i < total.get(); i++) {
                    playFromBuilder.append(playFromBuilderArray[i].toString());
                    playUrlBuilder.append(playUrlBuilderArray[i].toString());
                }
            }
            vod.setVodPlayUrl(playUrlBuilder.toString() + lastPlayFromBuilder.toString());
            vod.setVodPlayFrom(playFromBuilder.toString() + "解析");
            return vod;
        } catch (Exception e) {
            return new Vod();
        }
    }

    private String getRating(JSONObject item) {
        try {
            return item.getJSONObject("rating").optString("value");
        } catch (Exception e) {
            return "";
        }
    }

    private String getTag(JSONObject item) {
        try {
          if (item.getJSONArray("tags").length() >= 2) {
            return item.optJSONArray("tags").optJSONObject(1).optString("name", "");
          }
            return item.optJSONArray("tags").optJSONObject(0).optString("name", "");
        } catch (Exception e) {
            return "";
        }
    }

    private String getTagName(JSONObject item) {
        try {
            JSONArray jsonArray = new JSONObject(item.optString("alg_json")).optJSONArray("id");
            StringBuilder resultStringBuilder = new StringBuilder();
            for (int i = 0; i < jsonArray.length(); i++) {
                resultStringBuilder.append(jsonArray.optString(i));
                if (i < jsonArray.length() - 1) {
                    resultStringBuilder.append(","); // 添加逗号分隔符
                }
            }
            return resultStringBuilder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getJAS(JSONObject item, String tableid, String spliter) {
        try {
            JSONArray jsonArray = item.optJSONArray(tableid);
            StringBuilder resultStringBuilder = new StringBuilder();
            for (int i = 0; i < jsonArray.length(); i++) {
                resultStringBuilder.append(jsonArray.optString(i));
                if (i < jsonArray.length() - 1) {
                    resultStringBuilder.append(spliter); // 添加逗号分隔符
                }
            }
            return resultStringBuilder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getJASLink(JSONObject item, String tableid, String spliter, String category) {
        try {
            JSONArray jsonArray = item.optJSONArray(tableid);
            StringBuilder resultStringBuilder = new StringBuilder();
            String formatStr;
            String itemStr;
            for (int i = 0; i < jsonArray.length(); i++) {
                itemStr = jsonArray.optString(i);
                formatStr = String.format("[a=cr:{\"id\":\"%s\",\"name\":\"%s\"}/]%s[/a]", category + "/" + itemStr + "/{link}", itemStr, itemStr);
                resultStringBuilder.append(formatStr);
                if (i < jsonArray.length() - 1) {
                    resultStringBuilder.append(spliter); // 添加逗号分隔符
                }
            }
            return resultStringBuilder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getJAJ(JSONObject item, String tableid1, String tableid2, String spliter) {
        try {
            JSONArray jsonArray = item.optJSONArray(tableid1);
            StringBuilder resultStringBuilder = new StringBuilder();
            for (int i = 0; i < jsonArray.length(); i++) {
                resultStringBuilder.append(jsonArray.optJSONObject(i).optString(tableid2));
                if (i < jsonArray.length() - 1) {
                    resultStringBuilder.append(spliter); // 添加分隔符
                }
            }
            return resultStringBuilder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    //actors and directors
    private String getCelebsLink(JSONObject item, String celebsType, String spliter, String category) {
        try {
            String celebsUrl = siteUrl + "/" + item.optString("type") + "/" + item.optString("id") + "/celebrities" + apikey;
            JSONObject celebsOb = new JSONObject(OkHttp.string(celebsUrl, getHeader()));
            JSONArray celebsArray = celebsOb.optJSONArray(celebsType);
            StringBuilder resultStringBuilder = new StringBuilder();
            for (int i = 0; i < celebsArray.length(); i++) {
                JSONObject celeb = celebsArray.optJSONObject(i);
                String celebName = celeb.optString("name");
                String celebId = celeb.optString("id");
                String formatStr = String.format("[a=cr:{\"id\":\"%s\",\"name\":\"%s\"}/]%s[/a]", category + "/" + celebId + "/{link}", celebName, celebName);
                resultStringBuilder.append(formatStr);
                if (i < celebsArray.length() - 1) {
                    resultStringBuilder.append(spliter); // 添加分隔符
                }
            }
            return resultStringBuilder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String getType(JSONObject item) {
        try {
            return item.optString("type");
        } catch (Exception e) {
            return "";
        }
    }

    private String getCard(JSONObject item) {
        try {
            String card = item.optString("card_subtitle");
            String[] cards = card.split(" / ");
            String cardStr = "";
            if (cards.length >= 3) {
                cardStr = cards[2] + " " + cards[0];
            } else {
                cardStr = cards[0];
            }
            return cardStr;
        } catch (Exception e) {
            return "";
        }
    }

    private String getPic(JSONObject item) {
        try {
            return item.getJSONObject("pic").optString("normal") + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
        } catch (Exception e) {
            return "";
        }
    }

    private String getTags(HashMap<String, String> extend) {
        try {
            StringBuilder tags = new StringBuilder();
            for (String key : extend.keySet()) if (!key.equals("sort")) tags.append(extend.get(key)).append(",");
            return Util.substring(tags.toString());
        } catch (Exception e) {
            return "";
        }
    }

    public String newlistContent (String category, String subjectType, String pg) throws Exception {
        int start = (Integer.parseInt(pg) - 1) * 20;
        String newlistUrl = siteUrl + "/skynet/new_playlists" + apikey + "&count=20&start=" + start + "&category=" + category +"&subject_type=" + subjectType;
        JSONArray items = new JSONObject(OkHttp.string(newlistUrl, getHeader())).optJSONArray("data").optJSONObject(0).optJSONArray("items");

        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            Vod vod = new Vod();
            String type = item.optString("type");
            if (type.equals("chart")) {
                vod.setVodId("chart/" + item.optString("id") +"/{link}");
            } else if (type.equals("doulist")) {
                vod.setVodId("doulist/" + item.optString("id") +"/{link}");
            }
            vod.setVodPic(item.optString("cover_url"));
            vod.setVodName(item.optString("title"));
            vod.setVodTag("folder");
            list.add(vod);
        }

        return Result.get().vod(list).string();
    }

    public String chartContent(String pg) throws Exception {
        //if (!pg.equals("1")) return Result.string(new Vod());
        JSONArray array = extend.optJSONArray("chart");
        List<Vod> list = new ArrayList<>();
        Vod.Style listStyle = Vod.Style.list();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            Vod vod = new Vod();
            vod.setVodId("chart/" + item.optString("v") +"/{link}");
            vod.setVodName(item.optString("n"));
            vod.setStyle(listStyle);
            vod.setVodTag("folder");
            list.add(vod);
        }

        return Result.get().vod(list).page().string();
    }

    public String doulistContent(String tid, boolean quick, String pg) throws Exception {
        int start = (Integer.parseInt(pg) - 1) * 10;
        String doulistUrl = siteUrl + "/doulist/" + tid + "/items" + apikey + "&count=10&start=" + start;
        JSONArray array = new JSONObject(OkHttp.string(doulistUrl, getHeader())).optJSONArray("items");
        List<Vod> list = parseVodListFromJSONArrayDoulist(array);

        return Result.string(list);
    }

    private List<Vod> parseVodListFromJSONArrayDoulist(JSONArray items) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            String emoji = "";
            String vodType = item.optString("type");
            String vodId = item.optString("target_id");
            String name = item.optString("title");
            String pic = item.optString("cover_url");
            if (name == null || name.isEmpty()) continue;//过滤广告
            if (vodType.equals("movie")) {
                emoji = "🎬";
            }
            if (vodType.equals("tv")) {
                emoji = "📺";
            }
            String[] subtitles = item.optString("subtitle").split("/");
            String reSubtitle = " "+subtitles[2]+" "+subtitles[0]+" "+subtitles[1];
            String remark = emoji + getRating(item) + reSubtitle;
            list.add(new Vod(vodId, name, pic, remark));
        }
        return list;
    }


    public String celebsContent(String tid, boolean quick, String pg) throws Exception {
        int start = (Integer.parseInt(pg) - 1) * 10;
        String celebsUrl = siteUrl + "/celebrity/" + tid + "/works" + apikey + "&count=10&start=" + start;
        JSONArray array = new JSONObject(OkHttp.string(celebsUrl, getHeader())).optJSONArray("works");
        List<Vod> list = parseVodListFromJSONArrayCelebs(array);

        return Result.string(list);
    }

    private List<Vod> parseVodListFromJSONArrayCelebs(JSONArray items) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            JSONObject target = item.optJSONObject("work");
            String emoji = "";
            String vodType = target.optString("type");
            String vodId = target.optString("id");
            String name = target.optString("title");
            String pic = getPic(target);
            if (name == null || name.isEmpty()) continue;//过滤广告
            if (vodType.equals("playlist")) continue;
            if (vodType.equals("movie")) {
                emoji = "🎬";
            }
            if (vodType.equals("tv")) {
                emoji = "📺";
            }
            String remark = emoji + getRating(target) + " " + getCard(target);
            list.add(new Vod(vodId, name, pic, remark));
        }
        return list;
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        return searchContent(key, quick, pg, 5);
    }

    public String searchContent(String key, boolean quick, String pg, int count) throws Exception {
        tagName = key; //搜索分类页
        int start = (Integer.parseInt(pg) - 1) * count;
        String searchUrl = siteUrl + "/search/subjects" + apikey + "&q=" + key + "&count=" + Integer.toString(count) + "&start=" + start;
        JSONArray array = new JSONObject(OkHttp.string(searchUrl, getHeader())).optJSONArray("items");
        List<Vod> list = parseVodListFromJSONArraySearch(array);
        ExecutorService executorService = Executors.newFixedThreadPool(cmsArray.length());
        for (int i = 0; i < cmsArray.length(); i++) {
            String cmsUrl = cmsArray.optJSONObject(i).optString("api");
            String cmsName = cmsArray.optJSONObject(i).optString("name");
            String cmsSearchUrl = cmsUrl + "?quick=true&wd=" + key;
            int index = i;
            executorService.execute(() -> {
                try {
                    JSONArray cmsResultArray = new JSONObject(OkHttp.string(cmsSearchUrl)).optJSONArray("list");
                    list.addAll(parseVodListFromJSONArrayCmsResult(cmsResultArray, cmsName, index));
                } catch (Exception e) {
                }
            });
        }
        executorService.shutdown();

        try {
            // 等待所有线程完成
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Result.string(list);
    }

    private List<Vod> parseVodListFromJSONArrayCmsResult(JSONArray items, String sourceName, int cmsOrder) throws Exception {
        List<Vod> list = new ArrayList<>();
        StringBuilder idsBuilder = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            idsBuilder.append(item.optString("vod_id")).append(",");
        }

        String detailSearchUrl = cmsArray.optJSONObject(cmsOrder).optString("api") + "?ac=detail&ids=" + Utils.substring(idsBuilder.toString(),1);
        JSONArray detailItems = new JSONObject(OkHttp.string(detailSearchUrl)).optJSONArray("list");
        for (int i = 0; i < detailItems.length(); i++) {
            JSONObject item = detailItems.optJSONObject(i);
            Vod vod = new Vod();
            vod.setVodName(item.optString("vod_name"));
            vod.setVodPic(item.optString("vod_pic"));
            vod.setVodRemarks(sourceName + " " + item.optString("vod_year"));
            vod.setVodId(item.optString("vod_id") + "/" + cmsOrder + "/{cmsSingle}");
            list.add(vod);
        }
        return list;
    }

    private List<Vod> parseVodListFromJSONArraySearch(JSONArray items) throws Exception {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            JSONObject target = item.optJSONObject("target");
            String emoji = "";
            String vodType = item.optString("target_type");
            String vodId = target.optString("id");
            String name = target.optString("title");
            String pic = target.optString("cover_url") + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
            if (name == null || name.isEmpty()) continue;//过滤广告
            if (vodType.equals("chart")) {
                emoji = "️📇";
                String remark = emoji + "豆瓣片单" + target.optString("subtitle");
                vodId = "chart/" + target.optString("id") + "/{link}";
                pic = target.optString("cover_url") + "@Referer=https://api.douban.com/@User-Agent=" + Util.CHROME;
                list.add(new Vod(vodId, name, pic, remark, true));//true表示是文件夹
                continue;
            }
            if (vodType.equals("movie")) {
                emoji = "🎬";
            }
            if (vodType.equals("tv")) {
                emoji = "📺";
            }
            String remark = emoji + getRating(target) + " " + getCard(target);
            list.add(new Vod(vodId, name, pic, remark));
        }
        return list;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        //Util.notify(flag+id+String.join(",",vipFlags));
        String title = currentVod.getVodName();
        String routeNames = currentVod.getVodPlayFrom();
        String routeValues = currentVod.getVodPlayUrl();
        // 目标线路名称和 URL
        String targetRouteName = flag;
        String targetUrl = id;

        if (flag.equals("解析")) return Result.get().parse().jx().url(id).danmaku(Danmaku.getDanmaku(title, findEpisode(routeNames, routeValues, targetRouteName, targetUrl), extend)).string();
        return Result.get().url(id).danmaku(Danmaku.getDanmaku(title, findEpisode(routeNames, routeValues, targetRouteName, targetUrl), extend)).string();
    }

    private int findEpisode(String routeNames, String routeValues, String targetRouteName, String targetUrl) {
        String[] routeNamesArray = routeNames.split("\\$\\$\\$");
        int targetRouteIndex = Arrays.asList(routeNamesArray).indexOf(targetRouteName);

        if (targetRouteIndex != -1) {
            String[] routeValuesArray = routeValues.split("\\$\\$\\$");
            if (targetRouteIndex >= 0 && targetRouteIndex < routeValuesArray.length) {
                String targetRouteValues = routeValuesArray[targetRouteIndex];

                String[] episodes = targetRouteValues.split("#");

                for (int i = 0; i < episodes.length; i++) {
                    String[] parts = episodes[i].split("\\$");

                    if (parts.length == 2 && parts[1].equals(targetUrl)) {
                        return i + 1; // Return episode number (1-based index)
                    }
                }
            }
        }

        return -1; // Indicates that the URL was not found
    }
}

