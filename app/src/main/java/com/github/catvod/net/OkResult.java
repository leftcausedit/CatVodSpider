package com.github.catvod.net;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OkResult {

    private final int code;
    private final String body;
    private final Map<String, List<String>> resp;

    public OkResult() {
        this.code = 500;
        this.body = "";
        this.resp = new HashMap<>();
    }

    public OkResult(int code, String body, Map<String, List<String>> resp) {
        this.code = code;
        this.body = body;
        this.resp = resp;
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return TextUtils.isEmpty(body) ? "" : body;
    }

    public Map<String, List<String>> getResp() {
        return resp;
    }

    public boolean isError() {
        // 根据你的需要定义超时的判定条件，例如 code == 500 或者其他条件
        return this.code == 500; // 假设500表示超时
    }
}
