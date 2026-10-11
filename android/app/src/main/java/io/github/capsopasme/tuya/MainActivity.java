package io.github.capsopasme.tuya;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.util.Collections;

/**
 * 极简外壳：一个全屏 WebView 加载 assets 里的游戏，外加原生加速度计。
 * 游戏本体（index.html / css / js）在仓库根目录，构建时复制进 assets。
 */
public class MainActivity extends Activity implements SensorEventListener {
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";

    private WebView web;
    private SensorManager sensors;
    private Sensor accel;
    // 向右倾斜为正（度）。由传感器线程写、JS 桥线程读
    private volatile float tiltDeg = 0f;

    /** 暴露给网页：window.NativeTilt.get() 返回当前左右倾斜角（度） */
    public final class TiltBridge {
        @JavascriptInterface
        public float get() {
            return tiltDeg;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        // 向系统申请最高刷新率：游戏动画按 120Hz 渲染插值，系统默认可能把 WebView 降到 60/90Hz
        lp.preferredRefreshRate = 120f;
        w.setAttributes(lp);

        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        web.setBackgroundColor(0xFFF7F4E1);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); // 最高分、设置存在 localStorage 里
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // 渲染进程被系统回收时重建界面，避免整个应用崩溃
                view.destroy();
                recreate();
                return true;
            }
        });

        if (accel != null) {
            web.addJavascriptInterface(new TiltBridge(), "NativeTilt");
        }

        // 触屏操控模式下，按住屏幕左右边缘时不要触发系统的返回手势
        web.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                v.setSystemGestureExclusionRects(Collections.singletonList(new Rect(0, 0, r - l, b - t))));

        setContentView(web);
        web.loadUrl(HOME);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        if (accel != null) {
            sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
        // 通知游戏：切回来了，立刻按当前握持姿势对齐倾斜，并重置计时
        web.evaluateJavascript("(window.DJ && DJ.onResume) ? DJ.onResume() : 0", null);
    }

    @Override
    protected void onPause() {
        if (accel != null) {
            sensors.unregisterListener(this);
        }
        // 切到后台时让游戏自动暂停
        web.evaluateJavascript("window.dispatchEvent(new Event('blur'))", null);
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 先交给游戏处理（暂停 / 返回菜单），游戏说“不用管”才退出
        web.evaluateJavascript("(window.DJ && DJ.onBack) ? DJ.onBack() : false", value -> {
            if (!"true".equals(value)) {
                finish();
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        // 竖屏下 x 轴指向屏幕右侧：手机向右歪时重力加速度的 x 分量为负
        double s = -e.values[0] / SensorManager.GRAVITY_EARTH;
        s = Math.max(-1.0, Math.min(1.0, s));
        tiltDeg = (float) Math.toDegrees(Math.asin(s));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
