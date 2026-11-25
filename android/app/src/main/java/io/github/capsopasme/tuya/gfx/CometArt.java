package io.github.capsopasme.tuya.gfx;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;

import io.github.capsopasme.tuya.game.Game;
import io.github.capsopasme.tuya.game.Hero;

import static io.github.capsopasme.tuya.gfx.Pen.clamp;
import static io.github.capsopasme.tuya.gfx.Art.deg;

/**
 * 暗夜斗篷：小海星化作一道水墨彗星。
 * <p>
 * 尾迹由 Game 每一步记录的“彗星头轨迹”生成，分成好几层飘带：
 * 外层是宽而淡的墨晕，往里是浓墨、靛蓝的光、最里面一根月白色的亮芯，再加两根细细的“飞白”墨丝。
 * 每一层都带着一点被甩出去时的惯性（kx：横向速度 × 年龄 × kx），所以左右摆动时，
 * 越外层的飘带被甩得越开、越柔，拐弯处自然散成一层层错开的曲线；再叠一点沿尾迹流动的轻微波动，像丝带在风里飘。
 * <p>
 * 全部是每帧重建的几条路径 + 渐变着色器（着色器只建一次，挪局部矩阵对齐到尾迹方向），不分配对象。
 */
final class CometArt {
    private final Pen p;

    CometArt(Pen p) {
        this.p = p;
    }

    /** 尾迹最长持续的秒数（越老越淡，到这里消失） */
    static final float MAX_AGE = 0.62f;
    /** 斗篷结束后尾迹原地淡掉的时间 */
    static final float FADE_T = 0.5f;

    // 每一层：半宽, kx（横向惯性）, ky（跟着彗星往上收的比例：越大这一层越短、弯得越急）, 长度比例, 变宽（越老越宽）, 波动幅度, 波动相位
    // 尾迹点要是完全钉在世界坐标里，竖直速度（2100）远大于横向速度，弯都会被拉直；
    // 所以每一层都整体往彗星那边收一部分，横向的摆动保留下来，曲线才明显，而且各层收得不一样多，自然错开成好几层。
    private static final float[][] LAYERS = {
            {36, 0.36f, 0.72f, 1.00f, 2.6f, 12, 0.3f}, // 最外层的水汽（很淡，柔化边缘）
            {25, 0.30f, 0.66f, 0.98f, 2.2f, 10, 0.0f}, // 0 淡墨晕
            {15, 0.22f, 0.60f, 0.95f, 1.4f, 7, 0.9f}, // 1 墨晕
            {8.5f, 0.12f, 0.55f, 0.88f, 0.8f, 4, 1.7f}, // 2 浓墨
            {1.3f, 0.42f, 0.70f, 0.85f, 0.3f, 15, 2.6f}, // 3 飞白墨丝 A（甩得最开）
            {1.0f, -0.16f, 0.50f, 0.78f, 0.3f, 12, 4.2f}, // 4 飞白墨丝 B（反方向）
            {6.0f, 0.05f, 0.52f, 0.74f, 0.25f, 2.5f, 0.6f}, // 5 靛蓝光
            {2.5f, 0.0f, 0.44f, 0.60f, -0.3f, 0, 0}, // 6 月白亮芯
    };
    private static final int[][] COLS = {
            {0x00202650, 0x1a202650, 0x14202650, 0x00202650},
            {0x00161a33, 0x2e161a33, 0x24161a33, 0x00161a33},
            {0x0011142a, 0x6611142a, 0x4011142a, 0x0011142a},
            {0x200b0d1f, 0xcc0b0d1f, 0x660b0d1f, 0x000b0d1f},
            {0x000b0d1f, 0xb30b0d1f, 0x590b0d1f, 0x000b0d1f},
            {0x000b0d1f, 0x990b0d1f, 0x4d0b0d1f, 0x000b0d1f},
            {0x664f6cff, 0xe66a8bff, 0x80526ef0, 0x00526ef0},
            {0xffffffff, 0xfff0f8ff, 0x99b4dcff, 0x00b4dcff},
    };
    private static final float[] STOPS = {0, 0.1f, 0.45f, 1};
    private static final int NL = LAYERS.length;
    private final LinearGradient[] grads = new LinearGradient[NL];
    private RadialGradient headGlow, inkHalo;

    // 采样后的轨迹（下标 0 = 头）
    private static final int MAXP = 84;
    private final float[] ex = new float[MAXP * 2], ey = new float[MAXP * 2];
    private final float[] bx = new float[MAXP], by = new float[MAXP], bvx = new float[MAXP], bvy = new float[MAXP], age = new float[MAXP];
    private int np;
    // 每一层算出来的中心线
    private final float[] lx = new float[MAXP], ly = new float[MAXP];
    private final Path[] paths = new Path[NL];
    private final float[] gx0 = new float[NL], gy0 = new float[NL], gLen = new float[NL], gDeg = new float[NL];
    private final boolean[] has = new boolean[NL];

    private void init() {
        if (grads[0] != null) return;
        for (int i = 0; i < NL; i++) {
            grads[i] = Grad.linear(0, 0, 1, 0, COLS[i], STOPS);
            paths[i] = new Path();
        }
        headGlow = Grad.radial(0, 0, 1, new int[]{0xffffffff, 0xccbfe0ff, 0x558fa8ff, 0x008fa8ff}, new float[]{0, 0.18f, 0.5f, 1});
        inkHalo = Grad.radial(0, 0, 1, new int[]{0x6611142a, 0x3311142a, 0x0011142a}, new float[]{0.3f, 0.65f, 1});
    }

    /**
     * 画尾迹 + 彗星头。active = 正在飞遁（头跟着插值后的主角位置走）；否则尾迹原地淡掉。
     * (hx, hy) = 头的位置（世界坐标，已插值）；now = 渲染时刻（和 Game.t 同一时钟）
     */
    void draw(Game g, boolean active, float hx, float hy, float hvx, float hvy, double now, float W, float viewTop, float viewBot) {
        int n = g.cometN;
        if (n <= 0) return;
        init();
        int newest = g.cometNew;
        float newestAge = (float) (now - g.cometT[newest]);
        float fade = active ? 1 : clamp(1 - (newestAge - 0.02f) / FADE_T, 0, 1);
        if (fade <= 0) return;
        Canvas c = p.c;
        double baseXu = g.cometXu[newest];
        float anchorX = active ? hx : (float) g.cometRawX;

        // 采样：从最新往回，每隔一个记录点取一个，直到超过最长年龄
        np = 0;
        if (active) {
            bx[0] = hx;
            by[0] = hy;
            bvx[0] = hvx;
            bvy[0] = hvy;
            age[0] = 0;
            np = 1;
        }
        for (int k = 0; k < n && np < MAXP; k++) {
            int i = (newest - k + Game.COMET_N) % Game.COMET_N;
            float a = (float) (now - g.cometT[i]);
            if (a < 0) a = 0;
            if (a > MAX_AGE) break;
            if (active && np == 1 && a < 0.004f) continue;
            bx[np] = anchorX + (float) (g.cometXu[i] - baseXu);
            by[np] = (float) g.cometY[i];
            bvx[np] = (float) g.cometVx[i];
            bvy[np] = (float) g.cometVy[i];
            age[np] = a;
            np++;
        }
        if (np < 2) {
            if (active) head(hx, hy, hvx, hvy, now, fade, W);
            return;
        }
        // 斗篷结束后不再记录新点：越来越多的点超过最长年龄被丢掉，尾迹从尾巴往回缩，同时整体淡掉

        float minX = 1e9f, maxX = -1e9f;
        float tclk = (float) now;
        for (int L = 0; L < NL; L++) {
            float[] cfg = LAYERS[L];
            float half = cfg[0], kx = cfg[1], ky = cfg[2], lenK = cfg[3], spread = cfg[4], wob = cfg[5], ph = cfg[6];
            float maxA = MAX_AGE * lenK;
            // 这一层用到的点
            int m = 0;
            for (int i = 0; i < np; i++) {
                float a = age[i];
                if (a > maxA) {
                    // 最后一点插值到正好 maxA，尾巴不会一截一截地跳
                    if (i > 0 && m > 0) {
                        float a0 = age[i - 1];
                        float u = (maxA - a0) / Math.max(1e-4f, a - a0);
                        float px = bx[i - 1] + (bx[i] - bx[i - 1]) * u, py = by[i - 1] + (by[i] - by[i - 1]) * u;
                        lx[m] = px + bvx[i] * kx * maxA;
                        ly[m] = py + bvy[i] * ky * maxA;
                        m++;
                    }
                    break;
                }
                float w = wob == 0 ? 0 : wob * (float) Math.sin(a * 13 - tclk * 5 + ph) * Math.min(1, a / (maxA * 0.35f));
                lx[m] = bx[i] + bvx[i] * kx * a + w;
                ly[m] = by[i] + bvy[i] * ky * a;
                m++;
            }
            has[L] = false;
            if (m < 2) continue;
            Path path = paths[L];
            path.rewind();
            int ne = 0;
            // 左边从头到尾，右边从尾到头
            for (int side = 0; side < 2; side++) {
                for (int jj = 0; jj < m; jj++) {
                    int j = side == 0 ? jj : m - 1 - jj;
                    int j0 = Math.max(0, j - 1), j1 = Math.min(m - 1, j + 1);
                    float tx = lx[j1] - lx[j0], ty = ly[j1] - ly[j0];
                    float tl = (float) Math.sqrt(tx * tx + ty * ty);
                    if (tl < 1e-3f) {
                        tx = 0;
                        ty = 1;
                        tl = 1;
                    }
                    float nx = -ty / tl, ny = tx / tl;
                    float u = clamp((float) j / (m - 1), 0, 1);
                    float prof = Math.min(1, u / 0.09f + 0.22f) * (float) Math.pow(1 - u, 0.85) * Math.max(0.2f, 1 + spread * u);
                    float hw = half * prof * (side == 0 ? 1 : -1);
                    float px = lx[j] + nx * hw, py = ly[j] + ny * hw;
                    ex[ne] = px;
                    ey[ne++] = py;
                    if (px < minX) minX = px;
                    if (px > maxX) maxX = px;
                }
            }
            // 用中点二次曲线把折线磨圆
            path.moveTo(ex[0], ey[0]);
            for (int i = 1; i < ne - 1; i++) path.quadTo(ex[i], ey[i], (ex[i] + ex[i + 1]) * 0.5f, (ey[i] + ey[i + 1]) * 0.5f);
            path.lineTo(ex[ne - 1], ey[ne - 1]);
            path.close();
            float dx = lx[m - 1] - lx[0], dy = ly[m - 1] - ly[0];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1) continue;
            gx0[L] = lx[0];
            gy0[L] = ly[0];
            gLen[L] = len;
            gDeg[L] = (float) Math.toDegrees(Math.atan2(dy, dx));
            has[L] = true;
        }

        // 跨屏幕边缘：另一边再画一份
        for (int pass = -1; pass <= 1; pass++) {
            if (pass == -1 && maxX <= W) continue;
            if (pass == 1 && minX >= 0) continue;
            float off = pass == -1 ? -W : pass == 1 ? W : 0;
            if (off != 0) {
                c.save();
                c.translate(off, 0);
            }
            for (int L = 0; L < NL; L++) {
                if (!has[L]) continue;
                Grad.placeRot(grads[L], gx0[L], gy0[L], gLen[L], gLen[L], gDeg[L]);
                p.fillShader(paths[L], grads[L], fade);
            }
            if (off != 0) c.restore();
        }
        if (active) head(hx, hy, hvx, hvy, now, fade, W);
    }

    /** 彗星头：墨色的晕 → 月白的光 → 顺着速度方向拉长的亮核，外面两道斜着绕转的细光环和一枚星芒 */
    private void head(float hx, float hy, float vx, float vy, double now, float fade, float W) {
        float t = (float) now;
        for (int pass = -1; pass <= 1; pass++) {
            float x = hx + pass * W;
            if (x < -40 || x > W + 40) continue;
            headAt(x, hy, vx, vy, t, fade);
        }
    }

    private void headAt(float x, float y, float vx, float vy, float t, float fade) {
        Canvas c = p.c;
        float ga0 = p.ga;
        p.ga = ga0 * fade;
        Grad.place(inkHalo, x, y + 6, 34, 34);
        p.circleShader(x, y + 6, 34, inkHalo, 1);
        float pulse = 1 + 0.06f * (float) Math.sin(t * 9);
        float R = 26 * pulse;
        Grad.place(headGlow, x, y, R, R);
        p.circleShader(x, y, R, headGlow, 1);
        float ang = (float) Math.atan2(vy, vx);
        c.save();
        c.translate(x, y);
        c.rotate(deg(ang));
        // 拉长的亮核（尾巴方向朝后）
        p.ovalFill(-2.5f, 0, 9, 4.6f, 0xffffffff);
        p.ovalFill(-6, 0, 12, 2.4f, 0xb3e6f4ff);
        c.restore();
        // 两道绕转的细光环
        for (int i = 0; i < 2; i++) {
            c.save();
            c.translate(x, y);
            c.rotate(deg(t * (i == 0 ? 2.6f : -2.1f)) + i * 70);
            p.ovalStroke(0, 0, 15 + i * 3, 4.8f + i, i == 0 ? 0x99d8ecff : 0x668fa8ff, 1.1f);
            c.restore();
        }
        // 星芒
        float tw = 0.75f + 0.25f * (float) Math.sin(t * 7);
        c.save();
        c.translate(x, y);
        c.rotate(deg(t * 0.8f));
        Art.sparkle(p, 0, 0, 13 * tw, 0xd9ffffff);
        c.rotate(45);
        Art.sparkle(p, 0, 0, 7 * tw, 0x99bfe0ff);
        c.restore();
        p.ga = ga0;
    }

    static boolean visible(Game g, Hero d, double now) {
        if (d.state == Hero.CLOAK) return true;
        if (g.cometN <= 0) return false;
        return now - g.cometT[g.cometNew] < FADE_T + 0.05;
    }
}
