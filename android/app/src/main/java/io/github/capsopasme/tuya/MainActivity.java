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
import android.view.Display;
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

    /** 暴露给网页：window.NativeApp.setFps(60 | 120)，设置里切换帧率时调用，让屏幕本身也切到对应刷新率（60Hz 更省电） */
    public final class AppBridge {
        @JavascriptInterface
        public void setFps(final float hz) {
            runOnUiThread(() -> applyRefreshRate(hz));
        }

        /** 屏幕当前实际的刷新率（Hz），给设置里的“性能信息”显示用 */
        @JavascriptInterface
        public float getHz() {
            try {
                Display d = getDisplay();
                return d == null ? 0f : d.getRefreshRate();
            } catch (RuntimeException e) {
                return 0f;
            }
        }
    }

    private float wantHz = 120f;

    // 通过反射调用较新系统才有的方法：老系统上没有就什么都不做，也不会因为 compileSdk 不够新而编译失败
    private static void call(Object target, String name, Class<?> type, Object arg) {
        if (target == null) return;
        try {
            target.getClass().getMethod(name, type).invoke(target, arg);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    // 选一个分辨率和当前一样、刷新率最接近 hz 的显示模式；preferredDisplayModeId 比 preferredRefreshRate 更能让系统照办
    private void applyRefreshRate(float hz) {
        wantHz = hz;
        Window w = getWindow();
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.preferredRefreshRate = hz;
        try {
            Display d = getDisplay();
            if (d != null) {
                Display.Mode cur = d.getMode();
                Display.Mode best = null;
                for (Display.Mode m : d.getSupportedModes()) {
                    if (m.getPhysicalWidth() != cur.getPhysicalWidth() || m.getPhysicalHeight() != cur.getPhysicalHeight()) continue;
                    if (best == null || Math.abs(m.getRefreshRate() - hz) < Math.abs(best.getRefreshRate() - hz)) best = m;
                }
                if (best != null) lp.preferredDisplayModeId = best.getModeId();
            }
        } catch (RuntimeException ignored) {
        }
        w.setAttributes(lp);
        // Android 15 起系统会按 View 的“投票”动态选刷新率：普通 View 默认只投 60Hz，只有手指在屏幕上时才临时提到高刷。
        // 这个游戏全程靠倾斜操作、几乎不碰屏幕，所以不额外声明的话会一直被压在 60Hz —— 这里让 WebView 明确投 hz，
        // 并在 120 档时关掉窗口的“为省电平衡刷新率”
        boolean high = hz > 61f;
        call(web, "setRequestedFrameRate", float.class, hz);
        call(w, "setFrameRatePowerSavingsBalanced", boolean.class, !high);
    }

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
        w.setAttributes(lp);
        // 先按 120Hz 申请（游戏动画按 120Hz 渲染插值，系统默认可能把 WebView 降到 60/90Hz）；
        // 网页加载后会按设置里的帧率再调用一次 NativeApp.setFps
        applyRefreshRate(120f);

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
        web.addJavascriptInterface(new AppBridge(), "NativeApp");
        applyRefreshRate(wantHz); // WebView 建好了，把它的刷新率投票也补上

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
        applyRefreshRate(wantHz); // 切回来时系统可能重置了刷新率偏好
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
        // 游戏每帧只读一次最新值，这里只做一次除法和 asin，不分配任何对象
        // 竖屏下 x 轴指向屏幕右侧：手机向右歪时重力加速度的 x 分量为负
        double s = -e.values[0] / SensorManager.GRAVITY_EARTH;
        s = Math.max(-1.0, Math.min(1.0, s));
        tiltDeg = (float) Math.toDegrees(Math.asin(s));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
