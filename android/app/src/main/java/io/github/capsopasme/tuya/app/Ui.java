package io.github.capsopasme.tuya.app;

import static io.github.capsopasme.tuya.gfx.Pen.INK;
import static io.github.capsopasme.tuya.gfx.Pen.clamp;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;

import io.github.capsopasme.tuya.game.Entities;
import io.github.capsopasme.tuya.game.Hero;
import io.github.capsopasme.tuya.game.Tilt;
import io.github.capsopasme.tuya.gfx.Grad;
import io.github.capsopasme.tuya.gfx.Pen;
import io.github.capsopasme.tuya.gfx.Scene;

/** 菜单、设置、排行榜、暂停、结算界面：全部画在同一块画布上，按钮带弹出动画和按压弹簧。 */
final class Ui {
    static final int MENU = 0, PLAY = 1, PAUSE = 2, OVER = 3, SCORES = 4, SETTINGS = 5;
    // 按钮
    static final int B_PLAY = 1, B_SCORES = 2, B_SETTINGS = 3, B_RESUME = 4, B_MENU = 5, B_AGAIN = 6, B_BACK = 7,
            B_CHILL = 8, B_SOUND = 9, B_SENS = 10, B_HERO = 11, B_FPS = 12, B_PERF = 13;
    static final String[] SENS_LABEL = {"低", "中", "高"};

    static final class Btn {
        int id;
        String label;
        float x, y, w, h;
        boolean big, small;
    }

    int screen = MENU;
    double t, dt, tIn = 1;
    int pressed;
    final Btn[] buttons = new Btn[8];
    int nButtons;
    final float[] springX = new float[16], springV = new float[16];
    float meterX;
    boolean layoutDirty = true;

    final Scene scene;
    final Pen pen;
    final Store store;
    final Tilt tilt;
    final Hero fake = new Hero();
    private final Paint titleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient[] btnGrad = new LinearGradient[8];
    private float[] btnGradH = new float[8];
    private boolean[] btnGradP = new boolean[8];
    private int nBtnGrad;

    Ui(Scene scene, Store store, Tilt tilt) {
        this.scene = scene;
        this.pen = scene.pen;
        this.store = store;
        this.tilt = tilt;
        for (int i = 0; i < buttons.length; i++) buttons[i] = new Btn();
        for (int i = 0; i < springX.length; i++) springX[i] = 1;
        titleFill.setTypeface(Pen.BLACK);
        titleFill.setTextAlign(Paint.Align.CENTER);
    }

    float H() {
        return scene.v.H;
    }

    float W() {
        return scene.v.W;
    }

    static float setY(float H) {
        return H < 520 ? 0.2f : H < 600 ? 0.21f : 0.24f;
    }

    static float setRow(float H) {
        return H < 520 ? 42 : H < 600 ? 47 : 54;
    }

    static final int SET_N = 6;

    /** 切换界面：所有进场动画从头开始。quiet = 不要进场动画 */
    void go(int s, boolean quiet) {
        screen = s;
        tIn = quiet ? 9 : 0;
        pressed = 0;
        layoutDirty = true;
        layout();
    }

    void tick(double dt) {
        this.dt = dt;
        tIn += dt;
        int n = Math.max(1, (int) Math.ceil(dt / 0.004));
        float h = (float) dt / n;
        for (int i = 0; i < nButtons; i++) {
            int id = buttons[i].id;
            float tg = pressed == id ? 0.92f : 1;
            for (int k = 0; k < n; k++) {
                springV[id] += (-(springX[id] - tg) * 900 - springV[id] * 26) * h;
                springX[id] += springV[id] * h;
            }
        }
    }

    private void btn(int id, String label, float y, float w, float h, boolean big, boolean small) {
        Btn b = buttons[nButtons++];
        b.id = id;
        b.label = label;
        b.x = (W() - w) / 2;
        b.y = y;
        b.w = w;
        b.h = h;
        b.big = big;
        b.small = small;
    }

    void layout() {
        if (!layoutDirty) return;
        layoutDirty = false;
        nButtons = 0;
        float H = H();
        if (screen == MENU) {
            btn(B_PLAY, "开始游戏", H * 0.34f, 156, 48, true, false);
            btn(B_SCORES, "排行榜", H * 0.34f + 60, 156, 44, false, false);
            btn(B_SETTINGS, "设置", H * 0.34f + 114, 156, 44, false, false);
        } else if (screen == PAUSE) {
            btn(B_RESUME, "继续", H * 0.4f, 156, 46, false, false);
            btn(B_MENU, "菜单", H * 0.4f + 58, 156, 44, false, false);
        } else if (screen == OVER) {
            btn(B_AGAIN, "再玩一次", H * 0.56f, 156, 48, true, false);
            btn(B_MENU, "菜单", H * 0.56f + 60, 156, 44, false, false);
        } else if (screen == SCORES) {
            btn(B_BACK, "返回", H - 90, 140, 44, false, false);
        } else if (screen == SETTINGS) {
            float y0 = H * setY(H);
            float R = setRow(H);
            float bh = R < 45 ? 38 : R < 50 ? 42 : 46;
            boolean has3d = scene.has3d();
            btn(B_CHILL, "畅快模式：" + (store.chill ? "开" : "关"), y0, 190, bh, false, false);
            btn(B_SOUND, "声音：" + (store.sound ? "开" : "关"), y0 + R, 190, bh, false, false);
            btn(B_SENS, "灵敏度：" + SENS_LABEL[store.sens], y0 + R * 2, 190, bh, false, false);
            btn(B_HERO, "主角：" + (store.hero3d && has3d ? "3D" : "2D") + (has3d ? "" : "（不支持3D）"), y0 + R * 3, 190, bh, false, !has3d);
            btn(B_FPS, store.fps == 60 ? "帧率：60（省电）" : "帧率：120", y0 + R * 4, 190, bh, false, store.fps == 60);
            btn(B_PERF, "性能信息：" + (store.perf ? "开" : "关"), y0 + R * 5, 190, bh, false, false);
            btn(B_BACK, "返回", y0 + R * (SET_N - 1) + R + 4, 140, bh < 42 ? 38 : 42, false, false);
        }
    }

    int hit(float x, float y) {
        layout();
        for (int i = 0; i < nButtons; i++) {
            Btn b = buttons[i];
            if (x >= b.x && x <= b.x + b.w && y >= b.y - 4 && y <= b.y + b.h + 4) return b.id;
        }
        return 0;
    }

    static float easeOutBack(float p) {
        float c1 = 1.3f, c3 = c1 + 1, q = p - 1;
        return 1 + c3 * q * q * q + c1 * q * q;
    }

    static float easeOut(float p) {
        return 1 - (float) Math.pow(1 - p, 3);
    }

    private LinearGradient btnGradient(float h, boolean pressed) {
        for (int i = 0; i < nBtnGrad; i++) if (btnGradH[i] == h && btnGradP[i] == pressed) return btnGrad[i];
        LinearGradient g = pressed ? Grad.linear(0, 0, 0, h, 0xff8fcb4a, 0xff4f9a26) : Grad.linear(0, 0, 0, h, 0xffb4ea66, 0xff5fb331);
        if (nBtnGrad < btnGrad.length) {
            btnGradH[nBtnGrad] = h;
            btnGradP[nBtnGrad] = pressed;
            btnGrad[nBtnGrad++] = g;
        }
        return g;
    }

    void drawButton(Canvas c, Btn b, int index) {
        float p = clamp(((float) tIn - index * 0.05f) / 0.38f, 0, 1);
        if (p <= 0) return;
        float e = easeOutBack(p);
        boolean isPressed = pressed == b.id;
        float sc = (0.85f + 0.15f * e) * springX[b.id];
        float cx = b.x + b.w / 2, cy = b.y + b.h / 2;
        c.save();
        float ga0 = pen.ga;
        pen.ga = clamp(p * 1.6f, 0, 1);
        c.translate(cx, cy + (1 - e) * 10);
        c.scale(sc, sc);
        c.translate(-b.w / 2, -b.h / 2);
        pen.rrShader(0, 0, b.w, b.h, 9, btnGradient(b.h, isPressed), 1);
        pen.rrStroke(0, 0, b.w, b.h, 9, 0xff2f6b17, 2.4f);
        pen.line(12, 6, b.w - 12, 6, 0x73ffffff, 2);
        pen.text(b.label, b.w / 2, b.h / 2 + 1, b.big ? 25 : b.small ? 16 : 21, INK, Paint.Align.CENTER, Pen.BOLD);
        pen.ga = ga0;
        c.restore();
    }

    private static final String TITLE = "涂鸦蹦蹦";
    private final String[] titleChars = {"涂", "鸦", "蹦", "蹦"};
    private LinearGradient titleGrad;
    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    /** 手绘风标题：每个字单独描边、轻微歪斜、上下浮动 */
    void title(Canvas c, float cx, float cy, float size, float t) {
        float step = size * 1.02f;
        float x0 = cx - ((titleChars.length - 1) * step) / 2;
        if (titleGrad == null) titleGrad = Grad.linear(0, -size / 2, 0, size / 2, 0xffb9ee5c, 0xff5aae2c);
        titleFill.setTextSize(size);
        titleFill.setShader(titleGrad);
        titleFill.setAlpha(Pen.clamp255(pen.ga));
        titleFill.getFontMetrics(fm);
        float dy = -(fm.ascent + fm.descent) / 2;
        for (int i = 0; i < titleChars.length; i++) {
            c.save();
            c.translate(x0 + i * step, cy + (float) Math.sin(t * 2.2f + i * 1.3f) * 2.5f);
            c.rotate((float) Math.toDegrees((i % 2 != 0 ? 1 : -1) * 0.07f + Math.sin(t * 1.6f + i) * 0.02f));
            pen.textStroke.setTypeface(Pen.BLACK);
            pen.textStroke.setTextSize(size);
            pen.textStroke.setTextAlign(Paint.Align.CENTER);
            pen.textStroke.setStrokeWidth(size * 0.17f);
            pen.textStroke.setColor(pen.mul(INK, 1));
            c.drawText(titleChars[i], 0, dy, pen.textStroke);
            c.drawText(titleChars[i], 0, dy, titleFill);
            c.restore();
        }
    }

    void text(String s, float x, float y, float size, int color, Paint.Align align) {
        pen.text(s, x, y, size, color, align, Pen.BOLD);
    }

    void menuScene(Canvas c) {
        float H = H(), W = W();
        scene.platformAt(c, Entities.P_MOVING, 44, H * 0.58f);
        scene.platformAt(c, Entities.P_NORMAL, 278, H * 0.5f);
        scene.platformAt(c, Entities.P_BROWN, 270, H * 0.72f);
        scene.platformAt(c, Entities.P_WHITE, 50, H * 0.8f);
        float by = H - 52;
        scene.platformAt(c, Entities.P_NORMAL, W / 2, by);
        double period = 0.95;
        double u = (t % period) / period;
        double h = 62;
        double y = by - h * 4 * u * (1 - u);
        double vy = (u < 0.5 ? -1 : 1) * 400 * Math.abs(1 - 2 * u);
        int facing = ((int) Math.floor(t / period / 3)) % 2 == 0 ? 1 : -1;
        fake.fake(W / 2, y, facing, vy, u * period, t);
        scene.heroAt(fake, W / 2, (float) y);
    }

    private String tiltLabel = "";
    private double tiltLabelV = Double.NaN;

    void tiltMeter(Canvas c, float y) {
        float W = W();
        float full = (float) (Tilt.FULL / tilt.sens);
        float track = 100;
        pen.line(W / 2 - track, y, W / 2 + track, y, 0x40506e64, 7);
        pen.line(W / 2, y - 7, W / 2, y + 7, 0x80506e64, 2);
        float tx = !tilt.has ? 0 : clamp((float) tilt.raw / full, -1, 1) * track;
        meterX += (tx - meterX) * (1 - (float) Math.exp(-dt * 18));
        pen.circle(W / 2 + meterX, y, 9, Math.abs(tx) >= track - 0.5f ? 0xfff0733f : 0xff7cc944);
        pen.circleStroke(W / 2 + meterX, y, 9, INK, 2);
        double r = tilt.has ? Math.round(tilt.raw * 10) / 10.0 : Double.NaN;
        if (!(r == tiltLabelV) || tiltLabel.isEmpty()) {
            tiltLabelV = r;
            tiltLabel = !tilt.has ? "还没收到传感器数据，晃一下手机试试"
                    : "当前倾斜 " + (r >= 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.1f", r) + "°（两端为满速）";
        }
        text(tiltLabel, W / 2, y + 26, 13, 0xff6b6b60, Paint.Align.CENTER);
    }

    private int overShown = -1;
    private String overScoreS = "", overBestS = "";
    private int overBest = -1;
    private final String[] rankS = {"1.", "2.", "3.", "4.", "5."};

    /** 画当前界面（play 由 Scene 负责；暂停/结算时先画好静止场景，这里只盖一层纸色并画内容）。画布在像素坐标下 */
    void draw(Canvas c, int score, boolean newBest) {
        layout();
        float H = H(), W = W();
        int s = screen;
        float fade = clamp((float) tIn / 0.35f, 0, 1);
        pen.c = c;
        pen.ga = 1;
        if (s == MENU || s == SCORES || s == SETTINGS) scene.background(c, 0, 0, 0);
        c.save();
        scene.enterLogical(c);
        if (s == MENU) {
            c.save();
            c.clipRect(0, 0, W, H);
            menuScene(c);
            c.restore();
        } else if (s == PAUSE || s == OVER) {
            pen.rectFill(scene.v.x0, scene.v.y0, scene.v.x1 - scene.v.x0, scene.v.y1 - scene.v.y0,
                    Pen.rgba(0xf7f4e1, (s == OVER ? 0.88f : 0.74f) * easeOut(fade)));
        }

        // 内容层：整体淡入 + 轻微上浮放大
        float p = clamp((float) tIn / 0.4f, 0, 1);
        float e = easeOutBack(p);
        c.save();
        pen.ga = clamp((float) tIn / 0.22f, 0, 1);
        c.translate(W / 2, H * 0.4f + (1 - e) * 12);
        c.scale(0.94f + 0.06f * e, 0.94f + 0.06f * e);
        c.translate(-W / 2, -H * 0.4f);
        if (s == MENU) {
            title(c, W / 2, H * 0.15f, 54, (float) t);
        } else if (s == SCORES) {
            text("排行榜", W / 2, 70, 38, INK, Paint.Align.CENTER);
            int[] list = store.scores;
            if (list.length == 0) text("还没有成绩，快去玩一局吧", W / 2, 128 + 5 * 42 + 16, 18, 0xff6b6b60, Paint.Align.CENTER);
            for (int i = 0; i < 5; i++) {
                float y = 128 + i * 42;
                text(rankS[i], 70, y, 24, 0xff6b6b60, Paint.Align.LEFT);
                text(i < list.length ? Integer.toString(list[i]) : "—", 250, y, 26, i == 0 ? 0xff3c8a1a : INK, Paint.Align.RIGHT);
                pen.line(60, y + 20, 260, y + 20, 0x59506e64, 1.4f);
            }
        } else if (s == SETTINGS) {
            float R = setRow(H);
            text("设置", W / 2, H * setY(H) - (R < 50 ? 42 : 52), R < 50 ? 34 : 38, INK, Paint.Align.CENTER);
            float yb = H * setY(H) + R * SET_N + 4;
            text("左右倾斜手机移动，点击屏幕射击", W / 2, yb + 58, 14, 0xff6b6b60, Paint.Align.CENTER);
            tiltMeter(c, yb + 86);
        } else if (s == PAUSE) {
            text("已暂停", W / 2, H * 0.4f - 56, 40, INK, Paint.Align.CENTER);
        } else if (s == OVER) {
            text("游戏结束!", W / 2, H * 0.17f, 44, INK, Paint.Align.CENTER);
            int shown = Math.round(score * easeOut(clamp(((float) tIn - 0.15f) / 0.8f, 0, 1)));
            if (shown != overShown) {
                overShown = shown;
                overScoreS = "得分：" + shown;
            }
            if (store.best != overBest) {
                overBest = store.best;
                overBestS = "最高分：" + store.best;
            }
            text(overScoreS, W / 2, H * 0.28f, 28, 0xff3d3d34, Paint.Align.CENTER);
            text(overBestS, W / 2, H * 0.28f + 38, 22, 0xff6b6b60, Paint.Align.CENTER);
            if (newBest) {
                c.save();
                c.translate(W / 2, H * 0.28f + 80);
                float pop = easeOutBack(clamp(((float) tIn - 0.9f) / 0.4f, 0, 1));
                c.rotate((float) Math.toDegrees(-0.06f + Math.sin(t * 5) * 0.02f));
                c.scale(Math.max(0.001f, pop), Math.max(0.001f, pop));
                text("新纪录!", 0, 0, 26, 0xffe0483c, Paint.Align.CENTER);
                c.restore();
            }
        }
        pen.ga = 1;
        c.restore();

        for (int i = 0; i < nButtons; i++) drawButton(c, buttons[i], i);
        if (s == MENU || s == SCORES || s == SETTINGS) scene.letterbox(c);
        c.restore();
    }

    /** 静止界面（暂停、排行榜、普通结算）进场动画放完后可以不再重画 */
    boolean still(boolean newBest) {
        return (screen == PAUSE || screen == SCORES || (screen == OVER && !newBest)) && tIn > 1.4 && pressed == 0;
    }
}
