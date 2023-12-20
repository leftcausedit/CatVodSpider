/*
    Movie/TV lists from Trakt TMDB ...
*/

package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Recommend extends Spider {
    private JSONObject extendOb;
    private final String traktClientId = "8111469842a563dd678e4210ff597eb9265f7e7ed6357eef22fe3373b4a71ac9";
    private String traktAccessToken;
    private final String traktApiUrl = "https://api.trakt.tv";
    private final String tmdbApiUrl = "https://api.themoviedb.org/3";
    private final String tmdbImageUrl = "https://image.tmdb.org/t/p/original";
    private final int TRAKT_RECOMMEND_ITEM_NUMBER = 10;


    @Override
    public void init(Context context, String extend) throws Exception {
//        if (extend.startsWith("http")) extend = OkHttp.string(extend);
//        JSONObject extendOb = new JSONObject(extend);
//        this.extendOb = extendOb;
        this.traktAccessToken = Prefers.getString("trakt_access_token", "");
    }
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList();
        List<String> typeNames = Arrays.asList();
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));

        String traktRecommendMoviesUrl = traktApiUrl + "/recommendations/movies?ignore_collected=false&ignore_watchlisted=false";
        String traktRecommendShowsUrl = traktApiUrl + "/recommendations/shows?ignore_collected=false&ignore_watchlisted=false";
        JSONArray movieItems = new JSONArray(OkHttp.string(traktRecommendMoviesUrl, getTraktHeaders()));
        JSONArray showItems = new JSONArray(OkHttp.string(traktRecommendShowsUrl, getTraktHeaders()));
        List<Vod> movieList = parseTraktArray(movieItems, true);
        List<Vod> showList = parseTraktArray(showItems,false);
        List<Vod> list = new ArrayList<>();
        for (int  i = 0; i < TRAKT_RECOMMEND_ITEM_NUMBER; i++) {
            list.add(movieList.get(i));
            list.add(showList.get(i));
        }
//        return Result.string(classes, list, filter ? extendOb : null);
        return Result.string(classes, list);
    }
//
//    @Override
//    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
//        String sort = extend.get("sort") == null ? "T" : extend.get("sort");
//        String tags = URLEncoder.encode(getTags(extend));
//        int start = (Integer.parseInt(pg) - 1) * 20;
//        String cateUrl;
//        String itemKey = "items";
//        switch (tid) {
//            case "hot_gaia":
//                sort = extend.get("sort") == null ? "recommend" : extend.get("sort");
//                String area = extend.get("area") == null ? "全部" : extend.get("area");
//                sort = sort + "&area=" + URLEncoder.encode(area);
//                cateUrl = siteUrl + "/movie/hot_gaia" + apikey + "&sort=" + sort + "&start=" + start + "&count=20";
//                break;
//            case "tv_hot":
//                String type = extend.get("type") == null ? "tv_hot" : extend.get("type");
//                cateUrl = siteUrl + "/subject_collection/" + type + "/items" + apikey + "&start=" + start + "&count=20";
//                itemKey = "subject_collection_items";
//                break;
//            case "show_hot":
//                String showType = extend.get("type") == null ? "show_hot" : extend.get("type");
//                cateUrl = siteUrl + "/subject_collection/" + showType + "/items" + apikey + "&start=" + start + "&count=20";
//                itemKey = "subject_collection_items";
//                break;
//            case "tv":
//                cateUrl = siteUrl + "/tv/recommend" + apikey + "&sort=" + sort + "&tags=" + tags + "&start=" + start + "&count=20";
//                break;
//            case "rank_list_movie":
//                String rankMovieType = extend.get("榜单") == null ? "movie_real_time_hotest" : extend.get("榜单");
//                cateUrl = siteUrl + "/subject_collection/" + rankMovieType + "/items" + apikey + "&start=" + start + "&count=20";
//                itemKey = "subject_collection_items";
//                break;
//            case "rank_list_tv":
//                String rankTVType = extend.get("榜单") == null ? "tv_real_time_hotest" : extend.get("榜单");
//                cateUrl = siteUrl + "/subject_collection/" + rankTVType + "/items" + apikey + "&start=" + start + "&count=20";
//                itemKey = "subject_collection_items";
//                break;
//            default:
//                cateUrl = siteUrl + "/movie/recommend" + apikey + "&sort=" + sort + "&tags=" + tags + "&start=" + start + "&count=20";
//                break;
//        }
//        JSONObject object = new JSONObject(OkHttp.string(cateUrl, getHeader()));
//        JSONArray array = object.getJSONArray(itemKey);
//        List<Vod> list = parseVodListFromJSONArray(array);
//        int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 20, total = Integer.MAX_VALUE;
//        return Result.get().vod(list).page(page, count, limit, total).string();
//    }

    private List<Vod> parseTraktArray(JSONArray items, boolean isMovie) throws InterruptedException {
        List<Vod> list = new ArrayList<>();

        ExecutorService executorService = Executors.newFixedThreadPool(items.length());
        for (int i = 0; i < items.length(); i++) {
            int finalI = i;
            executorService.execute(() -> {
                JSONObject item = items.optJSONObject(finalI);
                int tmdbid = item.optJSONObject("ids").optInt("tmdb");
                Vod vod = null;
                try {
                    vod = parseItemFromTMDB(getItemFromTMDB(tmdbid, isMovie), isMovie);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                list.add(vod);
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        return list;
    }

    private JSONObject getItemFromTMDB (int id, boolean isMovie) throws JSONException {
        String typeUri = isMovie ? "/movie/" : "/tv/";
        String url = tmdbApiUrl + typeUri + id + "?language=zh-CN";
        return new JSONObject(OkHttp.string(url, getTMDBHeaders()));
    }

    private Vod parseItemFromTMDB (JSONObject item, boolean isMovie) {
        Vod vod = new Vod();
        String name = isMovie ? item.optString("title") : item.optString("name");
        vod.setVodName(name);
        vod.setVodRemarks(generateTMDBRemarks(item, isMovie));
        vod.setVodPic(tmdbImageUrl + item.optString("poster_path"));
        vod.setVodId(item.optString("id"));
        return vod;
    }

    private String generateTMDBRemarks (JSONObject item, boolean isMovie) {
        String type = isMovie ? "🎬" : "📺";
        String rating = String.format(Locale.CHINA,"%.1f" ,item.optDouble("vote_average"));
        String hit = "\uD83D\uDD25" + item.optInt("popularity");
        String remarks = type + rating + " " + hit + " " + combineTMDBGenres(item);
        return remarks;
    }

    private String combineTMDBGenres (JSONObject item) {
        JSONArray genres = item.optJSONArray("genres");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < genres.length(); i++) {
            String genre = genres.optJSONObject(i).optString("name");
            builder.append(genre).append(" ");
        }
        return Utils.substring(builder.toString());
    }
    private Map<String, String> getTraktHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + traktAccessToken);
        headers.put("trakt-api-version", "2");
        headers.put("trakt-api-key", traktClientId);
        return headers;
    }

    private Map<String, String> getTMDBHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("accept", "application/json");
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJlNjU3ZTY3ZTU3N2FkMTliM2U0NDk2YTM5YmUxMWQwNSIsInN1YiI6IjYzZDU0YzkxMTJiMTBlMDA5M2U3OGZjOCIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.KPdFvId1UDpbqu9CvqYC2v4FrTodBII_9EOLlQUmTSU");
        return headers;
    }
}
