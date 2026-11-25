package io.github.capsopasme.tuya.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.Choreographer;
import android.view.DisplayCutout;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;

import java.util.List;

import java.util.Locale;

import io.github.capsopasme.tuya.game.Bot;
import io.github.capsopasme.tuya.game.C;
import io.github.capsopasme.tuya.game.Entities;
import io.github.capsopasme.tuya.game.Game;
import io.github.capsopasme.tuya.game.Hero;
import io.github.capsopasme.tuya.game.Rng;
import io.github.capsopasme.tuya.game.Snd;
import io.github.capsopasme.tuya.game.Tilt;
import io.github.capsopasme.tuya.gfx.Pen;
import io.github.capsopasme.tuya.gfx.Scene;

/**
 * 游戏画面 + 主循环。Choreographer 每次垂直同步回调一次：先按固定步长（1/120 秒）推进物理，
 * 再 invalidate() 让同一帧里的 onDraw 录制显示列表；光栅化在系统的 RenderThread 上由 GPU（Skia）完成。
 * 60/90/120Hz 屏幕、偶尔掉帧时，画面都在两个物理步之间插值，运动始终匀速平滑。
 */
@SuppressLint("ViewConstructor")
final class GameView extends View implements Choreographer.FrameCallback {
    static final double STEP = 1.0 / 120;

    interface Host {
        void applyFps(int fps);

        void quit();

        float displayHz();

        /** 刷新率诊断：系统给这个应用报告的刷新率 / 当前显示模式 / 屏幕支持的最高刷新率 */
        String hzInfo();

        float gpuMs();

        void setPerfListening(boolean on);

        void pollTilt(Tilt t);
    }

    final Store store;
    final Tilt tilt = new Tilt();
    final Rng rng = new Rng((int) System.nanoTime());
    final Game game = new Game(rng);
    final Scene scene = new Scene();
    final Ui ui;
    final Sfx sfx;
    final Host host;

    private boolean running;
    private long last, lastDraw;
    private double acc;
    private float alpha = 1;
    private double shownScore;
    private boolean newBest;
    private long dirtyUntil;
    private int pausePointer = -1;

    // CI / 调试场景
    private String pendingScene;
    private boolean ciBot, ciFrozen, ciLoop;

    GameView(Context ctx, Store store, Sfx sfx, Host host) {
        super(ctx);
        this.store = store;
        this.sfx = sfx;
        this.host = host;
        ui = new Ui(scene, store, tilt);
        applySettings();
        game.best = store.best;
        setFocusable(true);
        setHapticFeedbackEnabled(false);
    }

    void applySettings() {
        tilt.sens = Store.SENS[store.sens];
        sfx.enabled = store.sound;
        scene.hero.use3d = store.hero3d;
        host.setPerfListening(store.perf);
    }

    // ---------- 生命周期 ----------
    void start() {
        if (running) return;
        running = true;
        syncClock();
        host.pollTilt(tilt);
        tilt.snap();
        Choreographer.getInstance().postFrameCallback(this);
        poke();
    }

    void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        pause();
    }

    void syncClock() {
        last = System.nanoTime();
        acc = 0;
    }

    void poke() {
        dirtyUntil = System.nanoTime() + 900_000_000L;
    }

    // ---------- 挖孔 / 圆角：HUD 要避开 ----------
    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets in) {
        readInsets(in);
        return super.onApplyWindowInsets(in);
    }

    private void readInsets(WindowInsets in) {
        if (in == null) return;
        int[] loc = new int[2];
        getLocationInWindow(loc);
        float[] cuts = new float[0];
        DisplayCutout dc = in.getDisplayCutout();
        if (dc != null) {
            List<Rect> rs = dc.getBoundingRects();
            cuts = new float[rs.size() * 4];
            for (int i = 0; i < rs.size(); i++) {
                Rect r = rs.get(i);
                cuts[i * 4] = r.left - loc[0];
                cuts[i * 4 + 1] = r.top - loc[1];
                cuts[i * 4 + 2] = r.right - loc[0];
                cuts[i * 4 + 3] = r.bottom - loc[1];
            }
        }
        float[] corners = new float[6];
        RoundedCorner tl = in.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
        RoundedCorner tr = in.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
        if (tl != null && tl.getRadius() > 0) {
            Point c = tl.getCenter();
            corners[0] = c.x - loc[0];
            corners[1] = c.y - loc[1];
            corners[2] = tl.getRadius();
        }
        if (tr != null && tr.getRadius() > 0) {
            Point c = tr.getCenter();
            corners[3] = c.x - loc[0];
            corners[4] = c.y - loc[1];
            corners[5] = tr.getRadius();
        }
        scene.setInsets(cuts, corners);
        poke();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        scene.resize(w, h);
        readInsets(getRootWindowInsets());
        game.H = scene.v.H;
        ui.layoutDirty = true;
        ui.layout();
        poke();
        if (pendingScene != null && w > 0) {
            String s = pendingScene;
            pendingScene = null;
            ciScene(s);
        }
    }

    // ---------- 状态切换 ----------
    void startGame() {
        game.H = scene.v.H;
        game.chill = store.chill;
        game.reset();
        game.best = store.best;
        newBest = false;
        game.clearShots();
        tilt.snap();
        shownScore = 0;
        syncClock();
        alpha = 1;
        ui.go(Ui.PLAY, false);
    }

    void pause() {
        if (ui.screen != Ui.PLAY || game.phase != Game.PLAY || ciBot) return;
        game.clearShots();
        ui.go(Ui.PAUSE, false);
        poke();
    }

    void resume() {
        game.clearShots();
        tilt.snap();
        syncClock();
        ui.go(Ui.PLAY, true);
    }

    void toMenu() {
        ui.go(Ui.MENU, false);
    }

    void finishGame() {
        int score = game.score;
        newBest = store.addScore(score);
        game.best = store.best;
        game.clearShots();
        ui.go(Ui.OVER, false);
    }

    void act(int id) {
        sfx.play(Snd.CLICK);
        switch (id) {
            case Ui.B_PLAY:
            case Ui.B_AGAIN:
                startGame();
                break;
            case Ui.B_SCORES:
                ui.go(Ui.SCORES, false);
                break;
            case Ui.B_SETTINGS:
                ui.go(Ui.SETTINGS, false);
                break;
            case Ui.B_BACK:
                ui.go(Ui.MENU, false);
                break;
            case Ui.B_MENU:
                toMenu();
                break;
            case Ui.B_RESUME:
                resume();
                break;
            case Ui.B_SOUND:
                store.sound = !store.sound;
                changed();
                break;
            case Ui.B_HERO:
                store.hero3d = !store.hero3d;
                changed();
                break;
            case Ui.B_CHILL:
                store.chill = !store.chill; // 下一局开始生效
                changed();
                break;
            case Ui.B_FPS:
                store.fps = store.fps == 120 ? 60 : 120;
                host.applyFps(store.fps);
                changed();
                break;
            case Ui.B_PERF:
                store.perf = !store.perf;
                changed();
                break;
            case Ui.B_SENS:
                store.sens = (store.sens + 1) % Store.SENS.length;
                changed();
                break;
            default:
                break;
        }
        poke();
    }

    private void changed() {
        store.saveSettings();
        applySettings();
        ui.layoutDirty = true;
    }

    /** 返回键：true = 游戏内部处理了；false = 可以退出 */
    boolean onBack() {
        poke();
        if (ui.screen == Ui.PLAY) {
            pause();
            return true;
        }
        if (ui.screen == Ui.PAUSE) {
            resume();
            return true;
        }
        if (ui.screen == Ui.SCORES || ui.screen == Ui.SETTINGS || ui.screen == Ui.OVER) {
            ui.go(Ui.MENU, false);
            return true;
        }
        return false;
    }

    // ---------- 主循环 ----------
    @Override
    public void doFrame(long now) {
        if (!running) return;
        Choreographer.getInstance().postFrameCallback(this);
        if (store.perf) {
            vsyncs[vHead] = now;
            vHead = (vHead + 1) % vsyncs.length;
            if (vCount < vsyncs.length) vCount++;
        }
        // 帧率上限：60 档时（屏幕没能切到 60Hz 的话）每隔一帧才画一次
        if (now - lastDraw < (long) (1e9 / store.fps) - 2_500_000L) {
            perfTick(now, false);
            return;
        }
        // 静止界面：进场动画放完、也没人碰屏幕，就什么都不画（画面保持上一帧）
        if (ui.still(newBest) && now > dirtyUntil) {
            last = now;
            perfTick(now, false);
            return;
        }
        lastDraw = now;
        long t0 = System.nanoTime();
        double dt = Math.min(0.1, Math.max(0, (now - last) / 1e9));
        last = now;
        scene.clock = now / 1e9;
        ui.t += dt;
        ui.tick(dt);

        host.pollTilt(tilt);
        if (ui.screen == Ui.PLAY && !ciFrozen) {
            double axis = tilt.axis(dt);
            acc += dt;
            int n = 0;
            while (acc >= STEP && n < 20) {
                if (ciBot) {
                    axis = Bot.axis(game);
                    if (n == 0) Bot.shoot(game);
                }
                game.update(STEP, axis);
                acc -= STEP;
                n++;
                if (game.phase == Game.OVER) break;
            }
            if (n >= 20) acc = 0; // 卡得太厉害就放弃追赶
            alpha = game.phase == Game.OVER ? 1 : (float) (acc / STEP);
            for (int i = 0; i < game.nEvents; i++) sfx.play(game.events[i]);
            game.nEvents = 0;
            double d = game.score - shownScore;
            shownScore = Math.abs(d) < 0.5 ? game.score : shownScore + d * (1 - Math.exp(-dt * 16));
            if (game.phase == Game.OVER) {
                if (ciLoop) startGame();
                else finishGame();
            }
        }
        cpuNs += System.nanoTime() - t0;
        invalidate();
        perfTick(now, true);
    }

    private long cpuNs;

    @Override
    protected void onDraw(Canvas c) {
        long t0 = System.nanoTime();
        int s = ui.screen;
        if (s == Ui.PLAY) {
            scene.draw(c, game, alpha, false, (int) Math.round(shownScore), store.perf ? perfText : null);
            // 开局时场景从纸色里淡入
            if (ui.tIn < 0.35) c.drawColor(Pen.rgba(0xf7f4e1, 1 - (float) ui.tIn / 0.35f));
        } else if (s == Ui.PAUSE || s == Ui.OVER) {
            scene.draw(c, game, s == Ui.OVER ? 1 : alpha, true, (int) Math.round(shownScore), null);
            ui.draw(c, game.score, newBest);
        } else {
            ui.draw(c, game.score, newBest);
            if (s == Ui.MENU) scene.warm(c);
        }
        cpuNs += System.nanoTime() - t0;
    }

    // ---------- 性能信息 ----------
    private final long[] frames = new long[512];
    // 每次垂直同步回调的时间：数一秒内有多少次 = 系统实际给这个应用的刷新率（和画没画无关）
    private final long[] vsyncs = new long[512];
    private int vHead, vCount;
    private int fHead, fCount;
    private long perfT;
    private double cpuAvg;
    String perfText = "";

    private void perfTick(long now, boolean drew) {
        if (!store.perf) {
            cpuNs = 0;
            return;
        }
        if (drew) {
            frames[fHead] = now;
            fHead = (fHead + 1) % frames.length;
            if (fCount < frames.length) fCount++;
            cpuAvg += (cpuNs / 1e6 - cpuAvg) * 0.1;
        }
        cpuNs = 0;
        if (now - perfT < 250_000_000L) return;
        perfT = now;
        // 最近 1 秒画了几帧；最近 2 秒里相邻两帧间隔超过目标间隔 1.5 倍的次数（= 错过了至少一次屏幕刷新）
        int drawn = 0, jank = 0;
        double expect = 1e9 / store.fps * 1.5;
        long prev = 0;
        for (int i = 0; i < fCount; i++) {
            long t = frames[(fHead - fCount + i + frames.length) % frames.length];
            if (t > now - 1_000_000_000L) drawn++;
            if (prev != 0 && t > now - 2_000_000_000L && t - prev > expect) jank++;
            prev = t;
        }
        int vs = 0;
        for (int i = 0; i < vCount; i++) if (vsyncs[(vHead - vCount + i + vsyncs.length) % vsyncs.length] > now - 1_000_000_000L) vs++;
        float gpu = host.gpuMs();
        perfText = String.format(Locale.ROOT, "%s · vsync %d/秒\n画 %d 帧/秒 · 卡顿 %d/2秒\nCPU %.1fms · 渲染线程 %.1fms · %s", host.hzInfo(), vs, drawn, jank,
                cpuAvg, gpu, scene.hero.use3d && scene.has3d() ? "3D" : "2D");
    }

    // ---------- 触屏 ----------
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        poke();
        int act = e.getActionMasked();
        int idx = e.getActionIndex();
        float x = scene.v.toX(e.getX(idx)), y = scene.v.toY(e.getY(idx));
        int pid = e.getPointerId(idx);
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (ui.screen == Ui.PLAY) {
                    if (Math.abs(x - scene.pauseCx) < 28 && y < 40) pausePointer = pid;
                    else game.shootAt(x, y); // 点击只负责射击，移动完全靠倾斜
                } else if (act == MotionEvent.ACTION_DOWN) {
                    ui.pressed = ui.hit(x, y);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (ui.screen != Ui.PLAY && ui.pressed != 0) {
                    float mx = scene.v.toX(e.getX(0)), my = scene.v.toY(e.getY(0));
                    if (ui.hit(mx, my) != ui.pressed) ui.pressed = 0;
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (ui.screen == Ui.PLAY) {
                    if (pausePointer == pid) {
                        pausePointer = -1;
                        if (Math.abs(x - scene.pauseCx) < 28 && y < 40) pause();
                    }
                } else if (act == MotionEvent.ACTION_UP) {
                    int id = ui.pressed;
                    ui.pressed = 0;
                    if (id != 0 && ui.hit(x, y) == id) act(id);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pausePointer = -1;
                ui.pressed = 0;
                return true;
            default:
                return true;
        }
    }

    // ---------- CI / 调试场景（adb shell am start ... --es scene xxx） ----------
    void requestScene(String s) {
        if (getWidth() > 0) ciScene(s);
        else pendingScene = s;
    }

    private void fastForward(double seconds) {
        int n = (int) (seconds / STEP);
        for (int i = 0; i < n && game.phase == Game.PLAY; i++) {
            Bot.shoot(game);
            game.update(STEP, Bot.axis(game));
        }
        game.nEvents = 0;
    }

    private void steps(double seconds) {
        int n = (int) (seconds / STEP);
        for (int i = 0; i < n && game.phase != Game.OVER; i++) game.update(STEP, 0);
        game.nEvents = 0;
    }

    private void ciScene(String s) {
        Log.i("tuya", "CI 场景：" + s);
        boolean twoD = s.startsWith("2d-");
        if (twoD) s = s.substring(3);
        store.hero3d = !twoD;
        store.perf = s.equals("live");
        applySettings();
        rng.seed(20261023);
        switch (s) {
            case "menu":
                ui.go(Ui.MENU, true);
                return;
            case "settings":
                ui.go(Ui.SETTINGS, true);
                return;
            case "scores":
                ui.go(Ui.SCORES, true);
                return;
            default:
                break;
        }
        startGame();
        ui.tIn = 9;
        switch (s) {
            case "live":
                ciBot = true;
                ciLoop = true;
                fastForward(0.5);
                return;
            case "play":
                fastForward(6);
                break;
            case "spring":
            case "tramp":
            case "prop":
            case "jet":
            case "wings":
            case "dark": {
                fastForward(4);
                int type = s.equals("spring") ? Entities.I_SPRING : s.equals("tramp") ? Entities.I_TRAMP : s.equals("prop") ? Entities.I_PROP
                        : s.equals("jet") ? Entities.I_JET : s.equals("wings") ? Entities.I_WINGS : Entities.I_DARK;
                game.debugGive(type);
                steps(type <= Entities.I_TRAMP ? 0.12 : type >= Entities.I_WINGS ? 0.5 : 0.35);
                break;
            }
            case "dragon":
                // 变身那一瞬间（火焰炸开、巨龙蹦出来）
                fastForward(4);
                game.debugGive(Entities.I_DRAGON);
                steps(0.3);
                break;
            case "dragon2": {
                // 飞行中：前面有怪物，龙息火球正在飞
                fastForward(4);
                game.debugGive(Entities.I_DRAGON);
                steps(1.6);
                Hero d = game.dd;
                game.dd.vx = 160;
                Entities.Monster m = game.makeMonster(Entities.M_FLYER, d.x - 90, d.y - 300);
                game.monsters.add(m);
                Entities.Monster m2 = game.makeMonster(Entities.M_BLOB, d.x + 70, d.y - 420);
                m2.amp = 0;
                game.monsters.add(m2);
                steps(0.06);
                break;
            }
            case "dragon-end":
                fastForward(4);
                game.debugGive(Entities.I_DRAGON);
                steps(C.DRAGON_T + 0.15);
                break;
            case "life":
                fastForward(4);
                game.debugGive(Entities.I_LIFE);
                game.debugGive(Entities.I_LIFE);
                steps(0.15);
                break;
            case "luck":
                fastForward(4);
                game.debugGive(Entities.I_LIFE);
                game.debugGive(Entities.I_LUCK);
                steps(0.2);
                break;
            case "revive": {
                // 有一次复活机会时掉出屏幕：从底下被弹回来
                fastForward(4);
                game.lives = 1;
                game.platforms.clear();
                game.dd.vy = 900;
                game.dd.glide = 0;
                for (int i = 0; i < 400 && game.lives > 0; i++) game.update(STEP, 0);
                steps(0.22);
                break;
            }
            case "items": {
                // 三种新道具摆在眼前的平台上
                fastForward(3);
                double cy = game.camY;
                int[] types = {Entities.I_LIFE, Entities.I_LUCK, Entities.I_DRAGON};
                double[] xs = {60, 160, 260};
                for (int i = 0; i < 3; i++) {
                    Entities.Platform p = game.addPlatform(Entities.P_NORMAL, xs[i], cy + 150 + (i == 1 ? 60 : 0));
                    p.vx = 0;
                    p.item = new Entities.Item(types[i], 0);
                }
                game.lives = 2;
                game.luck = true;
                game.luckLeftT = C.LUCK_T * 0.6;
                game.dd.y = cy + game.H - 60;
                game.dd.vy = -200;
                game.dd.x = 200;
                steps(0.02);
                break;
            }
            case "hud": {
                // 顶栏挤满：大分数 + 7 条命 + 幸运果实 + 畅快标签（配合模拟器的挖孔检查有没有交叠）
                fastForward(4);
                game.chill = true;
                game.lives = 7;
                game.debugGive(Entities.I_LUCK);
                steps(0.6);
                game.luckLeftT = C.LUCK_T * 0.5;
                game.score = 1234567;
                break;
            }
            case "glide":
                fastForward(4);
                game.debugGive(Entities.I_JET);
                steps(C.JET_T + 0.55);
                break;
            case "shield":
                fastForward(4);
                game.debugGive(Entities.I_WINGS);
                steps(C.WING_T + 0.2);
                break;
            case "monsters": {
                fastForward(4);
                double cy = game.camY;
                Entities.Monster m = game.makeMonster(Entities.M_BLOB, 70, cy + 120);
                m.amp = 0;
                game.monsters.add(m);
                game.monsters.add(game.makeMonster(Entities.M_FLYER, 250, cy + 110));
                game.monsters.add(game.makeMonster(Entities.M_UFO, 200, cy + 230));
                game.holes.add(new Entities.Hole(60, cy + 260, 28));
                game.dd.x = 120;
                steps(0.05);
                game.shootAt(70, 120);
                steps(0.08);
                break;
            }
            case "dead": {
                fastForward(4);
                Entities.Monster m = game.makeMonster(Entities.M_BLOB, game.dd.x, game.dd.y - 20);
                m.amp = 0;
                game.monsters.add(m);
                steps(0.3);
                break;
            }
            case "pause":
                fastForward(3);
                ciFrozen = false;
                ui.screen = Ui.PLAY;
                pause();
                ui.tIn = 9;
                return;
            case "over":
                fastForward(5);
                finishGame();
                ui.tIn = 9;
                return;
            default:
                return;
        }
        game.snapshot();
        alpha = 1;
        shownScore = game.score;
        ciFrozen = true;
    }

    Hero hero() {
        return game.dd;
    }
}
