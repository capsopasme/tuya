package io.github.capsopasme.tuya.gfx;

import static io.github.capsopasme.tuya.gfx.Pen.INK;
import static io.github.capsopasme.tuya.gfx.Pen.clamp;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import io.github.capsopasme.tuya.game.Entities;
import io.github.capsopasme.tuya.game.Entities.Item;
import io.github.capsopasme.tuya.game.Entities.Monster;

/** 世界里的手绘美术：平台、道具、翅膀、怪物、黑洞。坐标单位和游戏逻辑一致（逻辑像素）。 */
final class Art {
    private Art() {}

    static final float TAU = (float) (Math.PI * 2);
    static final float PI = (float) Math.PI;

    // ---------- 平台 ----------
    static final int[][] PAL = {
            // top, bot, line, hi
            {0xffa9e25c, 0xff5db02e, 0xff3b7a1c, 0x73ffffff}, // normal
            {0xff8fd8f7, 0xff3c9ddb, 0xff1d6aa6, 0x80ffffff}, // moving
            {0xffc48d55, 0xff8d5c2e, 0xff5a3817, 0x40ffffff}, // brown
            {0xffffffff, 0xffd9d9d2, 0xff8e8e86, 0xccffffff}, // white
    };

    /** 平台的样子（左上角为原点）。只在做贴图时调用 */
    static void platShape(Pen p, int type, float w, float h) {
        int[] pal = PAL[type];
        Path path = p.begin();
        p.roundRect(path, 0, 0, w, h, 5.5f);
        p.fillShader(path, Grad.linear(0, 0, 0, h, pal[0], pal[1]), 1);
        p.stroke(path, pal[2], 1.8f);
        p.line(7, 3.4f, w - 7, 3.4f, pal[3], 1.6f);
        if (type == Entities.P_BROWN) {
            path = p.begin();
            path.moveTo(18, 1);
            path.lineTo(24, 6);
            path.lineTo(20, 9);
            path.lineTo(27, h - 1);
            path.moveTo(40, 1);
            path.lineTo(35, 7);
            path.lineTo(41, h - 1);
            p.stroke(path, pal[2], 1.4f);
        }
    }

    private static LinearGradient debrisGrad;

    static void debris(Pen p, float x, float y, float w, float h, float rot) {
        Canvas c = p.c;
        c.save();
        c.translate(x, y + h / 2);
        c.rotate((float) Math.toDegrees(rot));
        if (debrisGrad == null) debrisGrad = Grad.linear(0, -h / 2, 0, h / 2, PAL[2][0], PAL[2][1]);
        p.rrShader(-w / 2, -h / 2, w, h, 4, debrisGrad, 1);
        p.rrStroke(-w / 2, -h / 2, w, h, 4, PAL[2][2], 1.8f);
        c.restore();
    }

    // ---------- 平台上的道具 ----------
    /** 贴图的范围 {x0, y0, w, h}（相对道具锚点） */
    static final float[][] ITEM_BOX = {
            {-12, -20, 24, 24}, // spring
            {-24, -15, 48, 18}, // tramp
            {-16, -27, 32, 28}, // prop
            {-14, -31, 28, 34}, // jet
    };

    /** 弹簧 / 蹦床（可弹出）、螺旋桨帽、喷气背包；(x, y) 是道具在平台表面的锚点 */
    static void item(Pen p, Item it, float x, float y, float bob) {
        Canvas c = p.c;
        c.save();
        if (it.type == Entities.I_SPRING) {
            float ext = it.fired ? (float) Math.sin(clamp((float) it.t / 0.3f, 0, 1) * Math.PI) * 22 : 0;
            float h = 12 + ext;
            c.translate(x, y);
            Path path = p.begin();
            path.moveTo(-6, 0);
            int n = 4;
            for (int i = 0; i < n; i++) path.lineTo(i % 2 == 0 ? 7 : -7, -(h * (i + 0.5f)) / n);
            path.lineTo(0, -h);
            p.stroke(path, INK, 3.4f);
            p.stroke(path, 0xffc6ccd2, 1.6f);
            p.rrInk(-8, -h - 3, 16, 4.5f, 2, 0xffe8483c, 1.6f);
            p.rrInk(-8, -1.5f, 16, 3, 1.5f, 0xff9aa3ab, 1.4f);
        } else if (it.type == Entities.I_TRAMP) {
            float ext = it.fired ? (float) Math.sin(clamp((float) it.t / 0.35f, 0, 1) * Math.PI) * 26 : 0;
            c.translate(x, y);
            float h = 8 + ext;
            for (int s = -1; s <= 1; s += 2) {
                float sx = s * 14;
                Path path = p.begin();
                path.moveTo(sx - 3, 0);
                path.lineTo(sx + 3, -h * 0.33f);
                path.lineTo(sx - 3, -h * 0.66f);
                path.lineTo(sx, -h);
                p.stroke(path, INK, 2.6f);
            }
            p.rrInk(-21, -h - 4, 42, 6, 3, 0xffef4e4e, 1.8f);
            p.line(-16, -h - 2.2f, 16, -h - 2.2f, 0x99ffffff, 1.2f);
        } else if (it.type == Entities.I_PROP) {
            c.translate(x, y - 2 + bob);
            Path path = p.begin();
            p.rect.set(-9, -13, 9, 5);
            path.arcTo(p.rect, 180, 180, true);
            path.close();
            p.fillInk(path, 0xffef5b5b, 1.8f);
            p.rrInk(-10, -5, 20, 4, 2, 0xff3d86d6, 1.6f);
            p.line(0, -13, 0, -17, INK, 2);
            p.ovalInk(0, -18, 13, 2.4f, 0xfff2d24a, 1.6f);
        } else if (it.type == Entities.I_JET) {
            c.translate(x, y - 1);
            p.rrInk(-11, -28, 9, 26, 4, 0xff9db4c9, 1.8f);
            p.rrInk(2, -28, 9, 26, 4, 0xff9db4c9, 1.8f);
            p.rrInk(-11, -28, 9, 7, 3, 0xffe8483c, 1.6f);
            p.rrInk(2, -28, 9, 7, 3, 0xffe8483c, 1.6f);
            p.line(-2, -16, 2, -16, INK, 2.4f);
        }
        c.restore();
    }

    private static RadialGradient wingItemGlow, darkItemGlow;
    private static final DashPathEffect DASH_RUNE = new DashPathEffect(new float[]{2.5f, 4.5f}, 0);

    /** 悬在平台上方的飞翼 / 紫黑飞翼（一直在动，矢量画） */
    static void wingItem(Pen p, boolean dark, float x, float y, float t) {
        Canvas c = p.c;
        c.save();
        if (!dark) {
            float bob = (float) Math.sin(t * 3 + x * 0.05f) * 2;
            c.translate(x, y - 14 + bob);
            float pulse = 0.75f + 0.25f * (float) Math.sin(t * 5);
            if (wingItemGlow == null) wingItemGlow = Grad.radial(0, 0, 30, new int[]{0xd9ffec96, 0x00ffec96}, new float[]{2f / 30, 1});
            p.circleShader(0, 0, 30, wingItemGlow, pulse);
            float flap = (float) Math.sin(t * 6) * 0.3f;
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(s * 3, 2);
                c.scale(s * 0.4f, 0.4f);
                c.rotate(deg(-0.25f - flap));
                wingShape(p, false, t);
                c.restore();
            }
            Path path = p.begin();
            path.moveTo(0, -5);
            path.lineTo(4.5f, 1);
            path.lineTo(0, 7);
            path.lineTo(-4.5f, 1);
            path.close();
            p.fillInk(path, 0xffff7ac8, 1.6f);
            for (int i = 0; i < 3; i++) {
                float a = t * 1.7f + (i * TAU) / 3;
                float k = 0.5f + 0.5f * (float) Math.sin(t * 7 + i * 2);
                path = p.begin();
                Pen.star(path, (float) Math.cos(a) * 24, (float) Math.sin(a) * 12 - 2, 2 + 2.2f * k);
                p.fill(path, Pen.rgba(0xfff0a0, 0.5f + 0.5f * k));
            }
        } else {
            float bob = (float) Math.sin(t * 2.6f + x * 0.05f) * 2.4f;
            c.translate(x, y - 16 + bob);
            float pulse = 0.7f + 0.3f * (float) Math.sin(t * 4);
            if (darkItemGlow == null)
                darkItemGlow = Grad.radial(0, 0, 34, new int[]{0xccaa5aff, 0x596e28c8, 0x005014a0}, new float[]{2f / 34, 0.55f, 1});
            p.circleShader(0, 0, 34, darkItemGlow, pulse);
            c.save();
            c.rotate(deg(t * 0.9f));
            p.stroke.setPathEffect(DASH_RUNE);
            p.circleStroke(0, 0, 24, 0xd9d7b4ff, 1.4f);
            p.stroke.setPathEffect(null);
            c.restore();
            float flap = (float) Math.sin(t * 7) * 0.32f;
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(s * 3, 2);
                c.scale(s * 0.42f, 0.42f);
                c.rotate(deg(-0.25f - flap));
                wingShape(p, true, t);
                c.restore();
            }
            Path path = p.begin();
            path.moveTo(0, -6);
            path.lineTo(5, 1);
            path.lineTo(0, 8);
            path.lineTo(-5, 1);
            path.close();
            p.fill(path, 0xff2a0f45);
            p.stroke(path, 0xffe2c6ff, 1.7f);
            p.line(-1.5f, -2.5f, 1.5f, 0, 0xe68cf0ff, 1.3f);
            for (int i = 0; i < 4; i++) {
                float a = -t * 2.1f + (i * TAU) / 4;
                float k = 0.5f + 0.5f * (float) Math.sin(t * 8 + i * 1.7f);
                path = p.begin();
                Pen.star(path, (float) Math.cos(a) * 27, (float) Math.sin(a) * 13 - 2, 1.8f + 2 * k);
                p.fill(path, i % 2 != 0 ? Pen.rgba(0xff82f0, 0.55f + 0.45f * k) : Pen.rgba(0xe1c8ff, 0.55f + 0.45f * k));
            }
        }
        c.restore();
    }

    static float deg(float rad) {
        return rad * (180f / PI);
    }

    // ---------- 翅膀 ----------
    // 一只右翅膀（根部在原点，向 +x 张开），五根长飞羽扇形排开 + 根部一片覆羽。左翅膀由调用方镜像
    static final float[][] FEATHERS = {{-0.78f, 52}, {-0.5f, 50}, {-0.22f, 45}, {0.05f, 38}, {0.3f, 29}};
    private static final LinearGradient[] whiteFeather = new LinearGradient[5];
    private static final int SHEEN_N = 40;
    private static final LinearGradient[][] darkFeather = new LinearGradient[5][SHEEN_N];

    private static Shader featherGrad(boolean dark, int i, float L, float t) {
        if (!dark) {
            if (whiteFeather[i] == null)
                whiteFeather[i] = Grad.linear(0, 0, L, 0, new int[]{0xffffffff, 0xfffffbe8, 0xffffd96a}, new float[]{0, 0.6f, 1});
            return whiteFeather[i];
        }
        // 紫黑飞翼：一道虹彩光泽在羽毛上来回流动（位置量化成 40 档，渐变对象全部缓存）
        float sh = 0.25f + 0.5f * (0.5f + 0.5f * (float) Math.sin(t * 3.2f - i * 0.8f));
        int q = Math.round((sh - 0.25f) / 0.5f * (SHEEN_N - 1));
        q = Math.max(0, Math.min(SHEEN_N - 1, q));
        LinearGradient g = darkFeather[i][q];
        if (g == null) {
            float s = 0.25f + 0.5f * q / (SHEEN_N - 1);
            g = Grad.linear(0, 0, L, 0, new int[]{0xff160a24, 0xff2e1350, 0xffb98aff, 0xff4a1f86, 0xff8f4dff},
                    new float[]{0, Math.max(0.01f, s - 0.18f), s, Math.min(0.99f, s + 0.18f), 1});
            darkFeather[i][q] = g;
        }
        return g;
    }

    /** dark=true 是紫黑飞翼：飞羽更长更尖、黑紫色，内侧一圈霓虹紫描边，还有流动的虹彩光泽 */
    static void wingShape(Pen p, boolean dark, float t) {
        Canvas c = p.c;
        for (int i = 0; i < FEATHERS.length; i++) {
            float a = FEATHERS[i][0];
            float L = FEATHERS[i][1] * (dark ? 1.1f : 1);
            float rw = dark ? 5.9f : 6.8f;
            c.save();
            c.rotate(deg(a));
            p.ovalShader(L * 0.52f, 0, L * 0.52f, rw, featherGrad(dark, i, L, t), 1);
            p.ovalStroke(L * 0.52f, 0, L * 0.52f, rw, dark ? 0xff09040f : INK, 1.8f);
            if (dark) p.ovalStroke(L * 0.54f, 0, L * 0.44f, rw * 0.55f, 0xbfcea0ff, 0.9f);
            c.restore();
        }
        c.save();
        c.translate(13, -6);
        c.rotate(deg(-0.35f));
        p.ovalInk(0, 0, 17, 11, dark ? 0xff1f0c33 : 0xffffffff, 1.8f);
        if (dark) {
            p.ovalStroke(0, 0, 17, 11, 0xff09040f, 1.8f);
            // 覆羽上的一颗小宝石反光（覆羽坐标系里的 (3,-2)，转到旋转后的坐标系）
            p.ovalFill(3.5f, -0.85f, 3.4f, 2, 0xd996f0ff);
        }
        c.restore();
        Path path = p.begin();
        path.moveTo(5, -4);
        path.quadTo(14, -12, 25, -11);
        p.stroke(path, dark ? 0xf2c88cff : 0xcce6b43c, 1.4f);
    }

    // ---------- 怪物 ----------
    private static RadialGradient blobGrad, flyerGrad;
    private static LinearGradient ufoBeam, ufoBody;

    static void monster(Pen p, Monster m, float x, float y, float heroX, float heroY) {
        Canvas c = p.c;
        c.save();
        c.translate(x, y);
        if (m.dead) c.rotate(deg((float) m.rot));
        float lx = heroX - x, ly = heroY - y;
        float ll = (float) Math.hypot(lx, ly);
        if (ll == 0) ll = 1;
        float lookX = m.dead ? 0 : lx / ll, lookY = m.dead ? 0 : ly / ll;
        boolean flash = m.flash > 0;
        float mt = (float) m.t;

        if (m.kind == Entities.M_BLOB) {
            float sq = 1 + 0.07f * (float) Math.sin(mt * 5);
            c.scale(sq, 1 / sq);
            for (int s = -1; s <= 1; s += 2) {
                Path path = p.begin();
                path.moveTo(s * 9, -17);
                path.quadTo(s * 15, -28, s * 12, -33);
                p.stroke(path, INK, 2.2f);
                p.circleInk(s * 12, -34, 3.2f, 0xfff06a8d, 1.6f);
            }
            for (int s = -1; s <= 1; s += 2) p.ovalInk(s * 13, 21, 8, 4, 0xff6f35a8, 1.8f);
            if (blobGrad == null) blobGrad = Grad.conical(-8, -10, 4, 0, 0, 32, new int[]{0xffc58df0, 0xff8445bf}, null);
            if (flash) p.ovalFill(0, 0, 26, 22, 0xffffffff);
            else p.ovalShader(0, 0, 26, 22, blobGrad, 1);
            p.ovalStroke(0, 0, 26, 22, INK, 2.4f);
            eye(p, -10, -6, 8, lookX, lookY);
            eye(p, 9, -6, 8, lookX, lookY);
            Path path = p.begin();
            path.moveTo(-13, 9);
            for (int i = 0; i < 6; i++) path.lineTo(-13 + (i + 1) * (26f / 6), i % 2 == 0 ? 14 : 8);
            p.stroke(path, INK, 2);
        } else if (m.kind == Entities.M_FLYER) {
            float fl = (float) Math.sin(mt * 14);
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(s * 12, -4);
                c.rotate(deg(s * (-0.35f + fl * 0.5f)));
                Path path = p.begin();
                path.moveTo(0, 0);
                path.quadTo(s * 18, -22, s * 30, -8);
                path.quadTo(s * 20, 0, s * 26, 8);
                path.quadTo(s * 12, 6, 0, 6);
                path.close();
                p.fillInk(path, flash ? 0xffffffff : 0xffa8dcf7, 2);
                c.restore();
            }
            if (flyerGrad == null) flyerGrad = Grad.conical(-5, -6, 3, 0, 0, 22, new int[]{0xff79c9f5, 0xff3a97d8}, null);
            if (flash) p.ovalFill(0, 0, 17, 16, 0xffffffff);
            else p.ovalShader(0, 0, 17, 16, flyerGrad, 1);
            p.ovalStroke(0, 0, 17, 16, INK, 2.3f);
            eye(p, 0, -2, 9, lookX, lookY);
            Path path = p.begin();
            path.moveTo(-6, 9);
            path.lineTo(0, 12);
            path.lineTo(6, 9);
            for (int s = -1; s <= 1; s += 2) {
                path.moveTo(s * 7, 15);
                path.lineTo(s * 8, 21);
                path.lineTo(s * 12, 21);
            }
            p.stroke(path, INK, 2);
        } else {
            // UFO：光束先画
            if (!m.dead) {
                if (ufoBeam == null) ufoBeam = Grad.linear(0, 10, 0, 150, 0xb3fff482, 0x00fff482);
                Path path = p.begin();
                path.moveTo(-14, 10);
                path.lineTo(14, 10);
                path.lineTo(32, 150);
                path.lineTo(-32, 150);
                path.close();
                p.fillShader(path, ufoBeam, 1);
            }
            Path path = p.begin();
            p.rect.set(-16, -19, 16, 13);
            path.arcTo(p.rect, 180, 180, true);
            path.close();
            p.fillInk(path, 0xe696ebe1, 2);
            p.ovalInk(0, -9, 7, 8, 0xff7fd957, 1.6f);
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(s * 2.6f, -10);
                c.rotate(deg(s * 0.3f));
                p.ovalFill(0, 0, 1.6f, 2.6f, INK);
                c.restore();
            }
            if (ufoBody == null) ufoBody = Grad.linear(0, -2, 0, 16, 0xffd2d9e0, 0xff8e98a3);
            if (flash) p.ovalFill(0, 5, 37, 12, 0xffffffff);
            else p.ovalShader(0, 5, 37, 12, ufoBody, 1);
            p.ovalStroke(0, 5, 37, 12, INK, 2.3f);
            for (int i = 0; i < 5; i++) {
                float lxx = -24 + i * 12;
                p.circle(lxx, 6 + Math.abs(i - 2) * 0.6f, 2.5f, ((int) Math.floor(mt * 4 + i)) % 2 == 0 ? 0xffffe45c : 0xffff6a5c);
            }
        }
        c.restore();
    }

    static void eye(Pen p, float x, float y, float r, float lx, float ly) {
        p.circleInk(x, y, r, 0xffffffff, 1.8f);
        p.circle(x + lx * r * 0.4f, y + ly * r * 0.4f, r * 0.48f, INK);
    }

    private static RadialGradient holeGrad;

    static void hole(Pen p, float x, float y, float r, float t) {
        Canvas c = p.c;
        c.save();
        c.translate(x, y);
        if (holeGrad == null) holeGrad = Grad.conical(0, 0, 2, 0, 0, 28, new int[]{0xff000000, 0xff111111, 0xff2a2a28}, new float[]{0, 0.75f, 1});
        float k = r / 28f;
        c.scale(k, k);
        p.circleShader(0, 0, 28, holeGrad, 1);
        c.scale(1 / k, 1 / k);
        c.rotate(deg(t * 3));
        Path path = p.begin();
        for (int i = 0; i < 3; i++) {
            float base = (i + 1) * TAU / 3;
            boolean first = true;
            for (float a = 0; a < 3.2f; a += 0.2f) {
                float rr = 4 + a * 7;
                float px = (float) Math.cos(a + base) * rr, py = (float) Math.sin(a + base) * rr;
                if (first) path.moveTo(px, py);
                else path.lineTo(px, py);
                first = false;
            }
        }
        p.stroke(path, 0x47ffffff, 2);
        c.restore();
    }
}
