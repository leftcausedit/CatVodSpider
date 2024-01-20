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
import com.github.catvod.utils.Util;

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
import java.util.concurrent.atomic.AtomicReference;

public class Recommend extends Spider {
    private JSONObject extendOb;
    private final String traktClientId = "8111469842a563dd678e4210ff597eb9265f7e7ed6357eef22fe3373b4a71ac9";
    private String traktAccessToken;
    private final String traktApiUrl = "https://api.trakt.tv";
    private final String tmdbApiUrl = "https://api.themoviedb.org/3";
    private final String tmdbImageUrl = "https://image.tmdb.org/t/p/w500"; // smaller image size than original but still clear;
    private final int TRAKT_ITEM_LIMIT_PER_PAGE = 10;


    @Override
    public void init(Context context, String extend) throws Exception {
        if (extend.startsWith("http")) extend = OkHttp.string(extend);
        JSONObject extendOb = new JSONObject(extend);
        this.extendOb = extendOb;
        this.traktAccessToken = Prefers.getString("trakt_access_token", "");
    }
    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        List<String> typeIds = Arrays.asList("hot", "list");
        List<String> typeNames = Arrays.asList("热门", "片单");
        for (int i = 0; i < typeIds.size(); i++) classes.add(new Class(typeIds.get(i), typeNames.get(i)));



        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<List<Vod>> movieListReference = new AtomicReference<>();
        AtomicReference<List<Vod>> showListReference = new AtomicReference<>();
        executor.execute(() -> {
            try {
                movieListReference.set(getTMDBVodList("/trending/movie/day", "1"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        executor.execute(() -> {
            try {
                showListReference.set(getTMDBVodList("/trending/tv/day", "1"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        List<Vod> movieList = movieListReference.get();
        List<Vod> showList = showListReference.get();
//        Util.notify("80");

        List<Vod> list = new ArrayList<>();
        int length = Math.max(movieList.size(), showList.size());
        for (int  i = 0; i < length; i++) {
            try {
                if (movieList.size() > i) list.add(movieList.get(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                if (showList.size() > i) list.add(showList.get(i));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
//        Util.notify("88");
        return Result.string(classes, list, filter ? extendOb : null);
    }

        @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws JSONException, InterruptedException {
        String folderId = tid.endsWith("/{traktFolder}") ? tid.split("/")[0] : "";
        tid = tid.endsWith("/{traktFolder}") ? "traktFolder" : tid;
        List<Vod> list = new ArrayList<>();
        String url;
        JSONArray items;
        switch (tid) {
            case "hot":
                String site;
                if (extend.get("tmdb") != null) site = "tmdb";
                else if (extend.get("trakt") != null) site = "trakt";
                else site = "tmdb";

                switch (site) {
                    case "tmdb":
                        list = getTMDBVodList(getTMDBSitePath(extend), pg);
                        break;
                    case "trakt":
                        int traktRecommendItemNumber = 2 * TRAKT_ITEM_LIMIT_PER_PAGE;
                        switch (extend.get("trakt")) {
                            case "movie":
                                String traktRecommendMoviesUrl = traktApiUrl + "/recommendations/movies?ignore_collected=false&ignore_watchlisted=false&limit=" + traktRecommendItemNumber;
                                JSONArray movieItems = new JSONArray(OkHttp.string(traktRecommendMoviesUrl, getTraktHeaders()));
                                list = parseTraktArray(movieItems, true);
                                break;
                            case "show":
                                String traktRecommendShowsUrl = traktApiUrl + "/recommendations/shows?ignore_collected=false&ignore_watchlisted=false&limit=" + traktRecommendItemNumber;
                                JSONArray showItems = new JSONArray(OkHttp.string(traktRecommendShowsUrl, getTraktHeaders()));
                                list = parseTraktArray(showItems,false);
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
                break;
            case "list":
                // get lists from trakt
                url = traktApiUrl + "/lists/trending?limit" + TRAKT_ITEM_LIMIT_PER_PAGE + "&page=" + pg;
                items = new JSONArray(OkHttp.string(url, getTraktHeaders()));
                list = parseTraktListArray(items);
                break;
            case "traktFolder":
                url = traktApiUrl + "/lists/" + folderId + "/items/movie,show?limit=" + TRAKT_ITEM_LIMIT_PER_PAGE + "&page=" + pg;
                items = new JSONArray(OkHttp.string(url, getTraktHeaders()));
                list = parseTraktArray(items, true, true);
                break;
            default:
                break;
        }

        int page = Integer.parseInt(pg), count = Integer.MAX_VALUE, limit = 20, total = Integer.MAX_VALUE;
        return Result.get().vod(list).page(page, count, limit, total).string();
    }

    private List<Vod> getTMDBVodList(String path, String pg) throws JSONException {
        String url = tmdbApiUrl + path + "?page=" + pg + "&language=zh-CN";
        JSONArray items = new JSONObject(OkHttp.string(url, getTMDBHeaders())).optJSONArray("results");
        List<Vod> list = parseItemArrayFromTMDB(items);
        return list;
    }

    private String getTMDBSitePath(HashMap<String, String> extend) {
        return extend.get("tmdb") == null ? "/trending/tv/week" : extend.get("tmdb");
    }

    // trakt lists
    private List<Vod> parseTraktListArray(JSONArray items) {
        List<Vod> list = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i).optJSONObject("list");
            assert item != null;
            list.add(parseTraktListItem(item));
        }
        return list;
    }

    private Vod parseTraktListItem(JSONObject item) {
        Vod vod = new Vod();
        vod.setVodName(item.optString("name"));
        vod.setStyle(Vod.Style.list());
        String id = item.optJSONObject("ids").optString("trakt");
        vod.setVodId(id + "/{traktFolder}");
        String itemCount = item.optString("item_count");
        String likes = item.optString("likes");
        vod.setVodRemarks(String.format("共%s项，%s人喜欢", itemCount, likes));
        vod.setVodTag("folder");
        return vod;
    }

    private List<Vod> parseTraktArray(JSONArray items, boolean isMovie) throws InterruptedException {
        return parseTraktArray(items, isMovie, false);
    }
    // trakt movies/shows
    private List<Vod> parseTraktArray(JSONArray items, boolean isMovie, boolean isItemInChild) throws InterruptedException {
        List<Vod> list = new ArrayList<>();
        Vod[] vodArray = new Vod[items.length()];

        ExecutorService executorService = Executors.newFixedThreadPool(items.length());
        for (int i = 0; i < items.length(); i++) {
            int finalI = i;
            executorService.execute(() -> {
                JSONObject item = items.optJSONObject(finalI);
                boolean finalIsMovie = isItemInChild ? (item.optString("type").equals("movie")) : isMovie;
                item = isItemInChild ? item.optJSONObject(item.optString("type")) : item;
                int tmdbId = item.optJSONObject("ids").optInt("tmdb");
                try {
                    vodArray[finalI] = parseItemFromTMDB(getItemFromTMDB(tmdbId, finalIsMovie), finalIsMovie);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

        list = new ArrayList<>(Arrays.asList(vodArray));
        return list;
    }

    private JSONObject getItemFromTMDB(int id, boolean isMovie) throws JSONException {
        String typeUri = isMovie ? "/movie/" : "/tv/";
        String url = tmdbApiUrl + typeUri + id + "?language=zh-CN";
        return new JSONObject(OkHttp.string(url, getTMDBHeaders()));
    }

    private List<Vod> parseItemArrayFromTMDB(JSONArray items) {
        List<Vod> list = new ArrayList<>();
//        if (items == null) Util.notify("234.2");
        try {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                boolean isMovie = item.optString("media_type").equals("movie");
                list.add(parseItemFromTMDB(item, isMovie));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private Vod parseItemFromTMDB(JSONObject item, boolean isMovie) {
        Vod vod = new Vod();
        String name = isMovie ? item.optString("title") : item.optString("name");
        vod.setVodName(name);
        vod.setVodRemarks(generateTMDBRemarks(item, isMovie));
        vod.setVodPic(tmdbImageUrl + item.optString("poster_path"));
        vod.setVodId(item.optString("id"));
        vod.setVodTag("detail");
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
        try {
            JSONArray genres = item.optJSONArray("genres");
            StringBuilder builder = new StringBuilder();
//            if (genres == null) Util.notify("270.combineTMDBGenres.isnull");
            for (int i = 0; i < genres.length(); i++) {
                String genre = genres.optJSONObject(i).optString("name");
                genre = genreTranslate(genre);
                builder.append(genre).append(" ");
            }
            return Util.substring(builder.toString());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
    private String genreTranslate(String key) {
        Map<String, String> dictionary = new HashMap<>();
        dictionary.put("Sci-Fi & Fantasy", "科幻");

        return dictionary.get(key) == null ? key : dictionary.get(key);
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
