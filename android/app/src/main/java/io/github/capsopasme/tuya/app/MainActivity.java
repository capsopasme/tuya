package io.github.capsopasme.tuya.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.FrameMetrics;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import io.github.capsopasme.tuya.game.Tilt;

/**
 * 原生外壳：全屏一个 {@link GameView}，加速度计在单独的线程上读，刷新率按设置申请 120 / 60Hz。
 * 不需要任何权限。
 */
public final class MainActivity extends Activity implements SensorEventListener, GameView.Host {
    private GameView view;
    private Store store;
    private Sfx sfx;
    private SensorManager sensors;
    private Sensor accel;
    private HandlerThread sensorThread;
    private Handler sensorHandler;
    private int wantHz = 120;
    private OnBackInvokedCallback backCb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setDecorFitsSystemWindows(false);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        w.setAttributes(lp);

        store = new Store(this);
        sfx = new Sfx(this);
        sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accel = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorThread = new HandlerThread("tilt");
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

        view = new GameView(this, store, sfx, this);
        setContentView(view);
        view.requestFocus();
        wantHz = store.fps;
        applyFps(wantHz);

        // 第一次启动新版：把旧版（网页版）存的最高分、排行榜、设置搬过来
        Migrate.run(this, store, () -> {
            view.game.best = store.best;
            view.applySettings();
            applyFps(store.fps);
            view.ui.layoutDirty = true;
            view.poke();
        });

        if (Build.VERSION.SDK_INT >= 33) {
            backCb = () -> {
                if (!view.onBack()) finish();
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCb);
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        String s = i == null ? null : i.getStringExtra("scene");
        if (s != null) view.requestScene(s);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (!view.onBack()) super.onBackPressed();
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
            view.syncClock();
            view.tilt.snap();
        } else {
            view.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyFps(wantHz); // 切回来时系统可能重置了刷新率偏好
        if (accel != null) sensors.registerListener(this, accel, 10_000, sensorHandler); // 100Hz
        view.start();
    }

    @Override
    protected void onPause() {
        if (accel != null) sensors.unregisterListener(this);
        view.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCb != null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCb);
        setPerfListening(false);
        sfx.release();
        sensorThread.quitSafely();
        super.onDestroy();
    }

    // ---------- 传感器 ----------
    private volatile double tiltDeg;
    private volatile boolean tiltHas;

    @Override
    public void onSensorChanged(SensorEvent e) {
        // 竖屏下 x 轴指向屏幕右侧：手机向右歪时重力加速度的 x 分量为负。这里只写一个 volatile，主循环每帧读最新值
        tiltDeg = Tilt.accelToDeg(e.values[0]);
        tiltHas = true;
    }

    @Override
    public void pollTilt(Tilt t) {
        if (tiltHas) t.setRaw(tiltDeg);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ---------- 刷新率 ----------
    /** 选一个分辨率和当前一样、刷新率最接近 hz 的显示模式（比 preferredRefreshRate 更能让系统照办） */
    @Override
    public void applyFps(int hz) {
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
        // Android 15 起系统按 View 的“投票”动态选刷新率，普通 View 默认只投 60Hz（手指在屏幕上时才临时提高）；
        // 这个游戏全程靠倾斜、几乎不碰屏幕，所以让画面明确投 hz，并在 120 档关掉窗口的省电降帧
        if (Build.VERSION.SDK_INT >= 35 && view != null) {
            try {
                view.setRequestedFrameRate(hz);
                w.setFrameRatePowerSavingsBalanced(hz <= 61);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void quit() {
        finish();
    }

    @Override
    public float displayHz() {
        Display d = getDisplay();
        return d == null ? 0 : d.getRefreshRate();
    }

    // ---------- 渲染线程耗时（FrameMetrics，只在打开“性能信息”时监听） ----------
    private volatile float gpuMs;
    private Window.OnFrameMetricsAvailableListener fmListener;

    @Override
    public float gpuMs() {
        return gpuMs;
    }

    @Override
    public void setPerfListening(boolean on) {
        Window w = getWindow();
        if (on && fmListener == null) {
            fmListener = (win, m, dropped) -> {
                // 渲染线程（同步 + 发命令）+ GPU 实际执行
                long rt = m.getMetric(FrameMetrics.SYNC_DURATION) + m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION);
                long gpu = Build.VERSION.SDK_INT >= 31 ? m.getMetric(FrameMetrics.GPU_DURATION) : 0;
                float ms = Math.max(rt, gpu) / 1e6f;
                gpuMs += (ms - gpuMs) * 0.1f;
            };
            w.addOnFrameMetricsAvailableListener(fmListener, sensorHandler);
        } else if (!on && fmListener != null) {
            try {
                w.removeOnFrameMetricsAvailableListener(fmListener);
            } catch (RuntimeException ignored) {
            }
            fmListener = null;
        }
    }
}
