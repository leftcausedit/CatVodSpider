package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.bean.Result;
import com.github.catvod.bean.Sub;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Syparse extends Spider {

    @Override
    public void init(Context context, String extend) {
        
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String name = ids.get(0);
        Vod vod = new Vod();
        vod.setVodName(name);
        vod = genParseUrl(vod);
        return Result.string(vod);
    }
    
    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }
    
    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        if (!pg.equals("1")) return "";
        List<Vod> list = new ArrayList<>();
        Vod vod = new Vod();
        vod.setVodName(key);
        vod.setVodId(key);
        list.add(vod);
        return Result.string(list);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        if (flag.equals("直連")) return Result.get().url(id).string();
        if (flag.equals("嗅探")) return Result.get().parse().url(id).string();
        if (flag.equals("解析")) return Result.get().parse().jx().url(id).string();
        return Result.get().parse().jx().url(id).string();
    }
    
    private Vod genParseUrl(Vod vod) throws Exception {
        String searchUrl = "https://dmku.leftcuz.top:8443/searchplayurl?name=" + vod.getVodName();
        String url = OkHttp.string(searchUrl);
        vod.setVodPlayUrl(TextUtils.join("$$$", Arrays.asList("播放$" + url, "播放$" + url, "播放$" + url)));
        vod.setVodPlayFrom(TextUtils.join("$$$", Arrays.asList("解析", "嗅探", "直連")));
        return vod;
    }
    
}