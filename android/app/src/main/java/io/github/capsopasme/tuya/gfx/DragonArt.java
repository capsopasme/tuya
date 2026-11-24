package io.github.capsopasme.tuya.gfx;

import static io.github.capsopasme.tuya.gfx.Art.deg;
import static io.github.capsopasme.tuya.gfx.Pen.INK;
import static io.github.capsopasme.tuya.gfx.Pen.clamp;
import static io.github.capsopasme.tuya.gfx.Pen.lerp;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;

import io.github.capsopasme.tuya.game.C;
import io.github.capsopasme.tuya.game.Hero;

/**
 * 变身药剂的赤焰飞龙（俯视，头朝上往上冲）：蛇一样摆动的长身子 + 一对大蝙蝠翼 + 金色犄角 + 发光的冰蓝眼睛，
 * 嘴里喷着火舌，额头上嵌着一颗金色的星星（小海星变的）。全部是手绘风矢量，坐标单位和游戏一致，原点在主角脚底。
 */
final class DragonArt {
    static final int N = 16; // 脊椎节数
    static final float SEG = 9.5f;
    static final float NECK_Y = -52;
    /** 走到屏幕边缘时另一侧也要画一份的边距（翅膀很宽） */
    static final float EDGE = 112;

    private final Pen p;
    private final float[] sx = new float[N], sy = new float[N], sr = new float[N];
    private final Path body = new Path();

    private RadialGradient aura, headGrad, eyeGlow, mouthGlow, starGlow;
    private LinearGradient membrane, hornGrad, tailGrad;

    DragonArt(Pen p) {
        this.p = p;
    }

    private static float radius(int i) {
        if (i < 3) return lerp(7.5f, 11.5f, i / 3f);
        float u = (i - 3f) / (N - 4f);
        return lerp(11.5f, 2.2f, (float) Math.pow(u, 0.85));
    }

    private static float easeOutBack(float u) {
        float c1 = 1.9f, c3 = c1 + 1;
        float v = u - 1;
        return 1 + c3 * v * v * v + c1 * v * v;
    }

    void draw(Hero d, float x, float y) {
        Canvas c = p.c;
        float t = (float) d.t;
        float st = (float) d.stateT;
        float left = (float) (C.DRAGON_T - d.stateT);
        float bank = clamp((float) d.vx / 450f, -1, 1);
        // 变身：从一团火里“蹦”出来（先放大过头再回弹）
        float k = st < 0.34f ? Math.max(0.05f, easeOutBack(st / 0.34f)) : 1;

        // 脊椎：每一节在上一节下面，左右做行波摆动，越往尾巴摆得越大；左右飞时身子甩向另一边
        float wave = left < 1 ? lerp(4.5f, 7, clamp(left, 0, 1)) : 7;
        for (int i = 0; i < N; i++) {
            float u = (float) i / (N - 1);
            sx[i] = (float) Math.sin(t * wave - i * 0.55f) * (1.5f + i * 0.95f) - bank * (float) Math.pow(u, 1.4) * 30;
            sy[i] = NECK_Y + i * SEG;
            sr[i] = radius(i);
        }

        c.save();
        c.translate(x, y);
        if (k != 1) {
            c.translate(0, -30);
            c.scale(k, k);
            c.translate(0, 30);
        }
        float ga0 = p.ga;

        // 背后的火焰光晕
        if (aura == null)
            aura = Grad.radial(0, 0, 1, new int[]{0x8cff7a2a, 0x40ff3a10, 0x00ff3a10}, new float[]{0, 0.5f, 1});
        float ar = 100 + (float) Math.sin(t * 6) * 6;
        Grad.place(aura, 0, -20, ar, ar);
        p.circleShader(0, -20, ar, aura, 1);

        // 翅膀：扇动时俯视看翼展一伸一缩、翼尖前后扫
        float flapSpeed = left < 1 ? lerp(5, 9.5f, clamp(left, 0, 1)) : 9.5f;
        float ph = t * flapSpeed;
        for (int s = -1; s <= 1; s += 2) wing(s, sx[2] + s * sr[2] * 0.6f, sy[2], ph, t);

        // 四只爪子收在身子下面
        legs(3, 1f);
        legs(9, 0.75f);

        // 身体：先一整块描墨边，再填暗红，最后沿脊椎叠几层越来越亮的条带，做出圆滚滚的立体感
        body.rewind();
        for (int i = 0; i < N - 1; i++) {
            for (int j = 0; j < 3; j++) {
                float q = j / 3f;
                body.addCircle(lerp(sx[i], sx[i + 1], q), lerp(sy[i], sy[i + 1], q), lerp(sr[i], sr[i + 1], q) + 2.4f, Path.Direction.CW);
            }
        }
        body.addCircle(sx[N - 1], sy[N - 1], sr[N - 1] + 2.4f, Path.Direction.CW);
        tail(t, true);
        p.fill(body, INK);
        tail(t, false);
        body.rewind();
        for (int i = 0; i < N - 1; i++) {
            for (int j = 0; j < 3; j++) {
                float q = j / 3f;
                body.addCircle(lerp(sx[i], sx[i + 1], q), lerp(sy[i], sy[i + 1], q), lerp(sr[i], sr[i + 1], q), Path.Direction.CW);
            }
        }
        body.addCircle(sx[N - 1], sy[N - 1], sr[N - 1], Path.Direction.CW);
        p.fill(body, 0xff7d0f1d);
        band(1.45f, 0, 0xffbf1f2c);
        band(0.95f, 0, 0xffe8382f);
        band(0.45f, -0.22f, 0xffff7048);
        band(0.16f, -0.32f, 0xccffb48a);

        // 背上的鳞片（V 字）和一排金色背刺
        Path sc = p.begin();
        for (int i = 1; i < N - 2; i++) {
            float r = sr[i];
            float cx = sx[i], cy = sy[i] + SEG * 0.5f;
            sc.moveTo(cx - r * 0.7f, cy - r * 0.2f);
            sc.lineTo(cx, cy + r * 0.32f);
            sc.lineTo(cx + r * 0.7f, cy - r * 0.2f);
        }
        p.stroke(sc, 0x8c4a0610, 1.3f);
        for (int i = 1; i < N - 3; i++) {
            float r = sr[i] * 0.62f;
            float cx = sx[i], cy = sy[i];
            Path sp = p.begin();
            sp.moveTo(cx - r * 0.5f, cy - r * 0.35f);
            sp.lineTo(cx + r * 0.5f, cy - r * 0.35f);
            sp.lineTo(lerp(cx, sx[i + 1], 0.4f), cy + r * 1.3f);
            sp.close();
            p.fillInk(sp, i % 2 == 0 ? 0xfff7d27a : 0xffffe9a8, 1.2f);
        }

        // 头
        float hx = sx[0], hy = sy[0];
        float ang = (float) Math.atan2(sx[0] - sx[1], SEG) + bank * 0.18f;
        c.save();
        c.translate(hx, hy);
        c.rotate(deg(ang));
        c.scale(1.18f, 1.18f);
        head(t);
        c.restore();

        p.ga = ga0;
        c.restore();
    }

    /** 沿脊椎画一条宽度跟着身体粗细走的条带（w = 相对半径的宽度，off = 向左偏移，模拟左上方来的光） */
    private void band(float w, float off, int col) {
        Canvas c = p.c;
        p.stroke.setColor(p.mul(col, 1));
        for (int i = 0; i < N - 1; i++) {
            float r = (sr[i] + sr[i + 1]) * 0.5f;
            p.stroke.setStrokeWidth(Math.max(0.6f, r * w));
            float o = off * r;
            c.drawLine(sx[i] + o, sy[i], sx[i + 1] + o, sy[i + 1], p.stroke);
        }
    }

    private static final float[][] TAIL = {{0, -4}, {9, 4}, {5, 12}, {0, 24}, {-5, 12}, {-9, 4}};

    /** 尾巴尖：一片火焰形状的尾鳍。ink = true 时只描墨边（在身体的墨边之前画），否则填色 */
    private void tail(float t, boolean ink) {
        Canvas c = p.c;
        float x = sx[N - 1], y = sy[N - 1];
        float a = (float) Math.atan2(sx[N - 2] - sx[N - 1], SEG);
        float fl = 1 + 0.12f * (float) Math.sin(t * 23);
        c.save();
        c.translate(x, y);
        c.rotate(deg(a));
        Path lp = p.begin();
        for (int i = 0; i < TAIL.length; i++) {
            float py = TAIL[i][1] * (i == 3 ? fl : 1);
            if (i == 0) lp.moveTo(TAIL[i][0], py);
            else lp.lineTo(TAIL[i][0], py);
        }
        lp.close();
        if (ink) {
            p.stroke(lp, INK, 4.8f);
        } else {
            if (tailGrad == null) tailGrad = Grad.linear(0, -6, 0, 24, new int[]{0xffffe36a, 0xffff8a2a, 0xffd0242e}, null);
            p.fillShader(lp, tailGrad, 1);
            lp = p.begin();
            lp.moveTo(0, 0);
            lp.lineTo(3, 8);
            lp.lineTo(0, 17 * fl);
            lp.lineTo(-3, 8);
            lp.close();
            p.fill(lp, 0xccfff6c8);
        }
        c.restore();
    }

    private void legs(int i, float k) {
        Canvas c = p.c;
        for (int s = -1; s <= 1; s += 2) {
            float x = sx[i] + s * (sr[i] + 2), y = sy[i] + 4;
            c.save();
            c.translate(x, y);
            c.rotate(deg(s * -0.55f));
            c.scale(k, k);
            p.ovalInk(0, 4, 4.2f, 7.5f, 0xffa3192a, 1.6f);
            for (int j = -1; j <= 1; j++) {
                Path cl = p.begin();
                cl.moveTo(j * 2.6f - 1.2f, 10);
                cl.lineTo(j * 2.6f + 1.2f, 10);
                cl.lineTo(j * 3.4f, 15.5f);
                cl.close();
                p.fillInk(cl, 0xfffff0cc, 1.1f);
            }
            c.restore();
        }
    }

    // 一只右翅膀（翼根在原点，向 +x 张开；左翅膀镜像）：肩 -> 肘 -> 腕，腕上四根指骨撑开翼膜
    private static final float[] EL = {30, -16}, WR = {58, -10};
    private static final float[][] TIPS = {{98, -32}, {110, 2}, {95, 34}, {64, 50}};
    private static final float[][] SCALLOP = {{90, -8}, {88, 16}, {74, 32}, {40, 32}};
    private static final float[] ROOT = {6, 42};

    private void wing(int s, float x, float y, float ph, float t) {
        Canvas c = p.c;
        float span = 0.7f + 0.3f * (float) Math.cos(ph);
        float sweep = 0.24f * (float) Math.sin(ph);
        c.save();
        c.translate(x, y);
        c.scale(s * span, 1 + 0.06f * (float) Math.sin(ph));
        c.rotate(deg(sweep));
        Path m = p.begin();
        m.moveTo(0, 0);
        m.lineTo(EL[0], EL[1]);
        m.lineTo(WR[0], WR[1]);
        m.lineTo(TIPS[0][0], TIPS[0][1]);
        for (int i = 1; i < 4; i++) m.quadTo(SCALLOP[i - 1][0], SCALLOP[i - 1][1], TIPS[i][0], TIPS[i][1]);
        m.quadTo(SCALLOP[3][0], SCALLOP[3][1], ROOT[0], ROOT[1]);
        m.close();
        if (membrane == null)
            membrane = Grad.linear(0, 0, 110, 0, new int[]{0xf2701020, 0xeec4282a, 0xe6ff6a2a, 0xd9ffb04a}, new float[]{0, 0.35f, 0.75f, 1});
        // 翅膀往下扇（迎着光）的时候亮一点
        float lit = 0.88f + 0.12f * (float) Math.cos(ph);
        p.fillShader(m, membrane, lit);
        p.stroke(m, INK, 2.2f);
        // 翼膜上淡淡的筋络
        Path v = p.begin();
        for (int i = 0; i < 4; i++) {
            v.moveTo(lerp(WR[0], TIPS[i][0], 0.3f), lerp(WR[1], TIPS[i][1], 0.3f));
            v.quadTo(lerp(WR[0], SCALLOP[i][0], 0.7f), lerp(WR[1], SCALLOP[i][1], 0.7f) + 6,
                    lerp(ROOT[0], SCALLOP[Math.min(3, i)][0], 0.6f), lerp(ROOT[1], SCALLOP[Math.min(3, i)][1], 0.6f));
        }
        p.stroke(v, 0x40ffd27a, 1.2f);
        // 骨头：肩肘腕粗、指骨细
        Path b = p.begin();
        b.moveTo(0, 0);
        b.lineTo(EL[0], EL[1]);
        b.lineTo(WR[0], WR[1]);
        p.stroke(b, INK, 5.2f);
        p.stroke(b, 0xff5a0a14, 2.6f);
        b = p.begin();
        for (int i = 0; i < 4; i++) {
            b.moveTo(WR[0], WR[1]);
            b.lineTo(TIPS[i][0], TIPS[i][1]);
        }
        p.stroke(b, INK, 3.4f);
        p.stroke(b, 0xff6e0c18, 1.5f);
        // 腕上的爪子
        Path cl = p.begin();
        cl.moveTo(WR[0] - 2, WR[1] - 1);
        cl.quadTo(WR[0] + 2, WR[1] - 10, WR[0] + 8, WR[1] - 14);
        cl.quadTo(WR[0] + 5, WR[1] - 6, WR[0] + 3, WR[1] + 1);
        cl.close();
        p.fillInk(cl, 0xfffff0cc, 1.3f);
        // 翼尖的小火苗
        for (int i = 0; i < 4; i++) {
            float fl = 0.6f + 0.4f * (float) Math.sin(t * 19 + i * 1.9f + s);
            p.circle(TIPS[i][0], TIPS[i][1], 2.2f + 1.6f * fl, Pen.rgba(0xffc23a, 0.85f));
            p.circle(TIPS[i][0], TIPS[i][1], 1.2f + 0.8f * fl, 0xfffff2b0);
        }
        c.restore();
    }

    private void head(float t) {
        Canvas c = p.c;
        // 嘴里的火舌（先画，压在头下面）
        float L = 18 + 6 * (float) Math.sin(t * 37) + 4 * (float) Math.sin(t * 23 + 1);
        if (mouthGlow == null) mouthGlow = Grad.radial(0, 0, 1, new int[]{0xccffb04a, 0x00ff6a2a}, null);
        Grad.place(mouthGlow, 0, -40, 24, 24);
        p.circleShader(0, -40, 24, mouthGlow, 1);
        Path f = p.begin();
        f.moveTo(-5.5f, -33);
        f.quadTo(-8, -34 - L * 0.55f, 0, -34 - L);
        f.quadTo(8, -34 - L * 0.55f, 5.5f, -33);
        f.close();
        p.fill(f, 0xf2ff6a2a);
        f = p.begin();
        f.moveTo(-3.5f, -33);
        f.quadTo(-5, -34 - L * 0.45f, 0, -34 - L * 0.78f);
        f.quadTo(5, -34 - L * 0.45f, 3.5f, -33);
        f.close();
        p.fill(f, 0xffffd04a);
        f = p.begin();
        f.moveTo(-1.6f, -33);
        f.quadTo(-2, -34 - L * 0.3f, 0, -34 - L * 0.5f);
        f.quadTo(2, -34 - L * 0.3f, 1.6f, -33);
        f.close();
        p.fill(f, 0xfffff8e0);

        // 胡须：从鼻子两边往后飘，像中国龙
        for (int s = -1; s <= 1; s += 2) {
            Path w = p.begin();
            w.moveTo(s * 5, -29);
            for (int j = 1; j <= 10; j++) {
                float q = j / 10f;
                float wx = s * (6 + q * 16 + (float) Math.sin(t * 6 - q * 4 + s) * 4 * q);
                float wy = -29 + q * 46;
                w.lineTo(wx, wy);
            }
            p.stroke(w, INK, 2.6f);
            p.stroke(w, 0xffffd36a, 1.3f);
        }

        // 犄角：金白色，从头顶两侧往后弯
        if (hornGrad == null) hornGrad = Grad.linear(0, -8, 0, 28, 0xfffff3d6, 0xffc9963a);
        for (int s = -1; s <= 1; s += 2) {
            Path h = p.begin();
            h.moveTo(s * 5, -9);
            h.cubicTo(s * 12, -14, s * 24, 2, s * 25, 28);
            h.cubicTo(s * 19, 8, s * 14, 0, s * 11, -2);
            h.close();
            p.fillShader(h, hornGrad, 1);
            p.stroke(h, INK, 1.6f);
            Path rg = p.begin();
            rg.moveTo(s * 10, -9);
            rg.quadTo(s * 13, -7, s * 13, -3);
            rg.moveTo(s * 15, -4);
            rg.quadTo(s * 18, -1, s * 17, 3);
            rg.moveTo(s * 19, 3);
            rg.quadTo(s * 22, 7, s * 20, 10);
            p.stroke(rg, 0x99825020, 1.1f);
            // 腮边的小尖刺
            Path fr = p.begin();
            fr.moveTo(s * 14, -14);
            fr.lineTo(s * 24, -10);
            fr.lineTo(s * 15, -8);
            fr.lineTo(s * 22, -3);
            fr.lineTo(s * 13, -4);
            fr.close();
            p.fillInk(fr, 0xff9a1626, 1.3f);
        }

        // 头骨
        Path h = p.begin();
        h.moveTo(0, 6);
        h.quadTo(-10, 7, -13, 2);
        h.cubicTo(-17, -4, -16, -14, -11, -22);
        h.cubicTo(-8, -27, -7, -31, -4, -35);
        h.quadTo(0, -38, 4, -35);
        h.cubicTo(7, -31, 8, -27, 11, -22);
        h.cubicTo(16, -14, 17, -4, 13, 2);
        h.quadTo(10, 7, 0, 6);
        h.close();
        if (headGrad == null)
            headGrad = Grad.conical(-3, -16, 2, 0, -12, 24, new int[]{0xffff7a50, 0xffd8262f, 0xff7a0e1c}, new float[]{0, 0.55f, 1});
        p.fillShader(h, headGrad, 1);
        p.stroke(h, INK, 2.4f);
        // 鼻梁上的高光和鳞
        p.line(0, -31, 0, -14, 0x66ffc8a0, 2.4f);
        Path sc = p.begin();
        for (int j = 0; j < 3; j++) {
            float yy = -24 + j * 6;
            sc.moveTo(-4, yy);
            sc.lineTo(0, yy + 2.6f);
            sc.lineTo(4, yy);
        }
        p.stroke(sc, 0x8c4a0610, 1.1f);
        // 鼻孔冒火星
        for (int s = -1; s <= 1; s += 2) {
            p.ovalFill(s * 2.6f, -31, 1.3f, 1.8f, 0xff2a0408);
            p.circle(s * 2.6f, -31.5f, 0.9f, Pen.rgba(0xffb04a, 0.6f + 0.4f * (float) Math.sin(t * 30 + s)));
        }

        // 眉骨：压低、向内斜，凶一点
        for (int s = -1; s <= 1; s += 2) {
            Path br = p.begin();
            br.moveTo(s * 13.5f, -11);
            br.quadTo(s * 9, -15, s * 4.5f, -20);
            p.stroke(br, INK, 2.6f);
        }
        // 发光的冰蓝眼睛 + 往后拖的光痕
        if (eyeGlow == null) eyeGlow = Grad.radial(0, 0, 1, new int[]{0xcc9ef8ff, 0x007df0ff}, null);
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 9);
        for (int s = -1; s <= 1; s += 2) {
            float ex = s * 8.6f, ey = -14.5f;
            Grad.place(eyeGlow, ex, ey, 11, 11);
            p.circleShader(ex, ey, 11, eyeGlow, pulse);
            Path tr = p.begin();
            tr.moveTo(ex + s * 2, ey + 1);
            tr.quadTo(ex + s * 9, ey + 8, ex + s * 12, ey + 20);
            p.stroke(tr, Pen.rgba(0x9ef8ff, 0.45f * pulse), 2.2f);
            c.save();
            c.translate(ex, ey);
            c.rotate(deg(s * -0.55f));
            p.ovalFill(0, 0, 4.6f, 2.3f, INK);
            p.ovalFill(0, 0, 3.7f, 1.6f, 0xffe6fdff);
            p.ovalFill(0, 0, 0.7f, 1.5f, 0xff0a3a48);
            c.restore();
        }
        // 额头上的金色星星：小海星的印记
        if (starGlow == null) starGlow = Grad.radial(0, 0, 1, new int[]{0xccfff0a0, 0x00ffd04a}, null);
        Grad.place(starGlow, 0, -7, 9, 9);
        p.circleShader(0, -7, 9, starGlow, 0.7f + 0.3f * (float) Math.sin(t * 5));
        Path stp = p.begin();
        Pen.star(stp, 0, -7, 4.6f);
        p.fillInk(stp, 0xffffd93b, 1.2f);
    }
}
