package com.github.catvod.danmaku;

import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkResult;
import com.github.catvod.utils.Utils;

import org.json.JSONObject;

public class Danmaku {
    public static String getDanmaku(String title, int episodeNumber) throws Exception {
        JSONObject extend = new JSONObject();
        return getDanmaku(title, episodeNumber, extend);
    }
    
    public static String getDanmaku(String title, int episodeNumber, JSONObject extend) {
        try {
            JSONObject searchParamsOb = new JSONObject();
            searchParamsOb.put("name", title);
            searchParamsOb.put("pos", episodeNumber);
            
            String danmuUrl = extend.optString("danmuUrl", "https://dmku.leftcuz.top:8443");
            String searchUrl = danmuUrl + "/searchdm?params=" + searchParamsOb.toString();
            String danmaku;
            
            if (extend.has("danmuUrlTest")) return extend.optString("danmuUrlTest","");
            
            String animeDanmaku = String.format("%s/animedanmu?name=%s&pos=%s", danmuUrl, title, episodeNumber);
            if (OkHttp.getResult(animeDanmaku).getCode() == 200) return animeDanmaku;

            JSONObject searchOb = new JSONObject(OkHttp.string(searchUrl));
            danmaku = danmuUrl + "/danmu?params=" + searchOb.toString();
            if (OkHttp.getResult(danmaku).getCode() == 200) return danmaku;
            
            return danmaku;           
        } catch (Exception e) {
            return "";
        }
    }
}