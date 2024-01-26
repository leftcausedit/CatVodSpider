package com.github.catvod.debug;

import android.app.Activity;
import android.os.Bundle;

import com.github.catvod.R;
import com.github.catvod.crawler.Spider;
import com.github.catvod.spider.Init;
import com.github.catvod.spider.Recommend;

import java.util.HashMap;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Logger.addLogAdapter(new AndroidLogAdapter());
        Init.init(getApplicationContext());
        new Thread(() -> {
            try {
                Spider spider = new Recommend();
//                spider.init(Init.context(), "");
                spider.categoryContent("discovery", "1", false, new HashMap<>());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}