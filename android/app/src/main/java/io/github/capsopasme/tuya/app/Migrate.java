package io.github.capsopasme.tuya.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 从旧版（WebView 网页游戏）迁移最高分、排行榜和设置：旧版存在 https://appassets.androidplatform.net 这个源的 localStorage 里。
 * 只在第一次启动新版、且旧版的 WebView 数据目录存在时跑一次：建一个不显示的 WebView 打开同源的空白页读出来，用完即毁。
 */
final class Migrate {
    private static final String URL = "https://appassets.androidplatform.net/assets/index.html";

    interface Done {
        void run();
    }

    @SuppressLint("SetJavaScriptEnabled")
    static void run(Context ctx, Store store, Done done) {
        if (store.migrated()) return;
        File wv = new File(ctx.getApplicationInfo().dataDir, "app_webview");
        if (!wv.isDirectory()) {
            store.setMigrated();
            return;
        }
        try {
            final WebView w = new WebView(ctx);
            w.getSettings().setJavaScriptEnabled(true);
            w.getSettings().setDomStorageEnabled(true);
            w.setWebViewClient(new WebViewClient() {
                boolean read;

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                    byte[] html = "<!doctype html><title>m</title>".getBytes(StandardCharsets.UTF_8);
                    return new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(html));
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    if (read) return;
                    read = true;
                    view.evaluateJavascript(
                            "JSON.stringify({b:localStorage.getItem('tuya.best'),s:localStorage.getItem('tuya.scores'),st:localStorage.getItem('tuya.settings')})",
                            value -> {
                                try {
                                    apply(store, value);
                                } catch (Exception e) {
                                    Log.w("tuya", "迁移旧版成绩失败", e);
                                }
                                store.setMigrated();
                                w.destroy();
                                done.run();
                            });
                }
            });
            w.loadUrl(URL);
        } catch (RuntimeException e) {
            // 系统没有可用的 WebView：放弃迁移，不影响游戏
            Log.w("tuya", "WebView 不可用，跳过迁移", e);
            store.setMigrated();
        }
    }

    static void apply(Store store, String value) throws Exception {
        // evaluateJavascript 返回的是 JSON 编码过的字符串
        Object v = new JSONTokener(value).nextValue();
        if (!(v instanceof String)) return;
        JSONObject o = new JSONObject((String) v);
        int best = 0;
        if (!o.isNull("b")) best = (int) Math.max(0, Math.floor(Double.parseDouble(o.getString("b"))));
        int[] scores = new int[0];
        if (!o.isNull("s")) {
            JSONArray a = new JSONArray(o.getString("s"));
            int[] tmp = new int[a.length()];
            int n = 0;
            for (int i = 0; i < a.length(); i++) {
                double d = a.optDouble(i, 0);
                if (d > 0 && d < 1e9) tmp[n++] = (int) d;
            }
            scores = java.util.Arrays.copyOf(tmp, n);
        }
        if (best > 0 || scores.length > 0) store.mergeOld(best, scores);
        if (!o.isNull("st")) {
            JSONObject st = new JSONObject(o.getString("st"));
            if (st.has("sound")) store.sound = st.optBoolean("sound", true);
            if (st.has("sens")) {
                int s = st.optInt("sens", 1);
                if (s >= 0 && s <= 2) store.sens = s;
            }
            if (st.has("hero3d")) store.hero3d = st.optBoolean("hero3d", true);
            if (st.has("chill")) store.chill = st.optBoolean("chill", false);
            if (st.has("fps")) {
                int f = st.optInt("fps", 120);
                if (f == 60 || f == 120) store.fps = f;
            }
            if (st.has("perf")) store.perf = st.optBoolean("perf", false);
            store.saveSettings();
        }
        Log.i("tuya", "已迁移旧版数据：最高分 " + store.best + "，排行榜 " + store.scores.length + " 条");
    }
}
