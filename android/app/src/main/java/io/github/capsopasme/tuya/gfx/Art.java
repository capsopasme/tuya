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

    // ---------- 生命果实 / 幸运果实 / 变身药剂（悬在平台上方，一直在动） ----------
    private static RadialGradient lifeGlow, lifeFruit, luckGlow, luckFruitGrad, potionAura;
    private static LinearGradient potionLiquid;
    private static final DashPathEffect DASH_POTION = new DashPathEffect(new float[]{3, 5}, 0);

    /** 心形（尖朝下），中心在 (x, y)，r 是半宽 */
    static void heart(Path path, float x, float y, float r) {
        path.moveTo(x, y + r * 0.95f);
        path.cubicTo(x - r * 0.25f, y + r * 0.7f, x - r * 1.05f, y + r * 0.25f, x - r, y - r * 0.25f);
        path.cubicTo(x - r * 0.95f, y - r * 0.85f, x - r * 0.2f, y - r * 1.05f, x, y - r * 0.5f);
        path.cubicTo(x + r * 0.2f, y - r * 1.05f, x + r * 0.95f, y - r * 0.85f, x + r, y - r * 0.25f);
        path.cubicTo(x + r * 1.05f, y + r * 0.25f, x + r * 0.25f, y + r * 0.7f, x, y + r * 0.95f);
        path.close();
    }

    /** 一片四叶草的叶子（心形，尖朝原点），朝角度 a */
    static void cloverLeaf(Pen p, float a, float r, int col) {
        Canvas c = p.c;
        c.save();
        c.rotate(deg(a + PI / 2));
        Path path = p.begin();
        heart(path, 0, -r * 0.95f, r);
        p.fillInk(path, col, 1.3f);
        c.restore();
    }

    static void lifeIcon(Pen p, float x, float y, float r) {
        Path path = p.begin();
        heart(path, x, y, r);
        if (lifeFruit == null)
            lifeFruit = Grad.conical(-0.35f, -0.4f, 0.05f, 0, 0, 1.2f, new int[]{0xffffd0dc, 0xffff5c8a, 0xffc8143c}, new float[]{0, 0.45f, 1});
        Grad.place(lifeFruit, x, y, r, r);
        p.fillShader(path, lifeFruit, 1);
        p.stroke(path, INK, Math.max(1.2f, r * 0.16f));
        p.ovalFill(x - r * 0.42f, y - r * 0.32f, r * 0.2f, r * 0.13f, 0xccffffff);
        // 小叶子
        Path lf = p.begin();
        lf.moveTo(x + r * 0.05f, y - r * 0.55f);
        lf.quadTo(x + r * 0.35f, y - r * 1.25f, x + r * 0.9f, y - r * 1.05f);
        lf.quadTo(x + r * 0.55f, y - r * 0.55f, x + r * 0.05f, y - r * 0.55f);
        lf.close();
        p.fillInk(lf, 0xff7fd34a, Math.max(1, r * 0.12f));
    }

    static void lifeItem(Pen p, float x, float y, float t) {
        Canvas c = p.c;
        c.save();
        float bob = (float) Math.sin(t * 3.2f + x * 0.05f) * 2.2f;
        c.translate(x, y - 16 + bob);
        float beat = (float) Math.max(0, Math.sin(t * 6.5f)) ;
        float pulse = 1 + 0.1f * beat * beat;
        if (lifeGlow == null) lifeGlow = Grad.radial(0, 0, 30, new int[]{0xccff8fb0, 0x00ff8fb0}, new float[]{0.05f, 1});
        p.circleShader(0, 0, 30, lifeGlow, 0.7f + 0.3f * beat);
        // 一圈往外扩的心跳环
        float u = (t * 1.2f) % 1;
        p.circleStroke(0, 0, 12 + u * 18, Pen.rgba(0xff6e96, 0.6f * (1 - u)), 2);
        c.scale(pulse, pulse);
        lifeIcon(p, 0, 0, 11);
        c.restore();
        // 绕着转的小加号
        for (int i = 0; i < 3; i++) {
            float a = t * 1.8f + (i * TAU) / 3;
            float k = 0.5f + 0.5f * (float) Math.sin(t * 6 + i * 2);
            float px = x + (float) Math.cos(a) * 20, py = y - 16 + bob + (float) Math.sin(a) * 9;
            float r = 2 + 1.6f * k;
            p.line(px - r, py, px + r, py, Pen.rgba(0xffffff, 0.5f + 0.5f * k), 1.8f);
            p.line(px, py - r, px, py + r, Pen.rgba(0xffffff, 0.5f + 0.5f * k), 1.8f);
        }
    }

    // 幸运果实：一颗金灿灿的苹果形果子，顶上一片叶子 + 一棵小四叶草，脸上有腮红和高光。
    // 形状固定（半径 LR），路径只建一次，之后每帧复用同一个 Path（GPU 那边能直接复用细分结果），调用方用画布缩放改大小。
    static final float LR = 11;
    private static Path luckBody, luckLeaf, luckVein, luckStem, sparkle4, luckRays;
    private static RadialGradient luckRayGrad;
    private static LinearGradient luckShine, luckLeafGrad;
    private static final int[] LUCK_SPARK_COLS = {0xfffff3a8, 0xffffffff, 0xffc8ff9a, 0xffffe27a};
    private static final float[][] LUCK_SPARKS = {{-19, -14, 0}, {18, -20, 0.27f}, {21, 7, 0.55f}, {-17, 11, 0.8f}};

    private static void luckPaths() {
        if (luckBody != null) return;
        float r = LR;
        Path b = new Path();
        b.moveTo(0, -0.6f * r);
        b.cubicTo(-0.3f * r, -0.98f * r, -1.02f * r, -0.98f * r, -1.03f * r, -0.18f * r);
        b.cubicTo(-1.04f * r, 0.5f * r, -0.56f * r, 1.02f * r, -0.2f * r, 0.98f * r);
        b.quadTo(0, 0.94f * r, 0.2f * r, 0.98f * r);
        b.cubicTo(0.56f * r, 1.02f * r, 1.04f * r, 0.5f * r, 1.03f * r, -0.18f * r);
        b.cubicTo(1.02f * r, -0.98f * r, 0.3f * r, -0.98f * r, 0, -0.6f * r);
        b.close();
        luckBody = b;
        Path s = new Path();
        s.moveTo(0, -0.58f * r);
        s.quadTo(-0.02f * r, -0.95f * r, 0.16f * r, -1.22f * r);
        luckStem = s;
        Path l = new Path();
        l.moveTo(-0.02f * r, -0.86f * r);
        l.cubicTo(-0.3f * r, -1.38f * r, -0.86f * r, -1.42f * r, -1.12f * r, -1.18f * r);
        l.cubicTo(-0.86f * r, -0.8f * r, -0.36f * r, -0.68f * r, -0.02f * r, -0.86f * r);
        l.close();
        luckLeaf = l;
        Path v = new Path();
        v.moveTo(-0.08f * r, -0.88f * r);
        v.quadTo(-0.55f * r, -1.12f * r, -0.98f * r, -1.16f * r);
        luckVein = v;
        // 四角闪光（单位大小，画的时候缩放）
        Path k = new Path();
        k.moveTo(0, -1);
        k.quadTo(0.14f, -0.14f, 1, 0);
        k.quadTo(0.14f, 0.14f, 0, 1);
        k.quadTo(-0.14f, 0.14f, -1, 0);
        k.quadTo(-0.14f, -0.14f, 0, -1);
        k.close();
        sparkle4 = k;
        // 身后的一圈光芒：10 根细长的楔形
        Path ry = new Path();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            double w = i % 2 == 0 ? 0.13 : 0.08;
            float R = i % 2 == 0 ? 34 : 26;
            ry.moveTo((float) Math.cos(a - w) * 7, (float) Math.sin(a - w) * 7);
            ry.lineTo((float) Math.cos(a) * R, (float) Math.sin(a) * R);
            ry.lineTo((float) Math.cos(a + w) * 7, (float) Math.sin(a + w) * 7);
            ry.close();
        }
        luckRays = ry;
        luckRayGrad = Grad.radial(0, 0, 34, new int[]{0xccfff2a0, 0x66ffe27a, 0x00ffe27a}, new float[]{0.15f, 0.55f, 1});
        luckFruitGrad = Grad.conical(-0.42f * r, -0.5f * r, 0.6f, 0, 0.05f * r, 1.12f * r,
                new int[]{0xfffffbe6, 0xffffe066, 0xfff5a623, 0xffc8661a}, new float[]{0, 0.3f, 0.78f, 1});
        luckLeafGrad = Grad.linear(-1.1f * r, -1.2f * r, 0, -0.8f * r, 0xff9be86a, 0xff3fa83a);
        // 扫光：一道斜着的白色窄带（单位宽度，挪动局部矩阵扫过果子）
        luckShine = Grad.linear(-1, 0, 1, 0, new int[]{0x00ffffff, 0xbfffffff, 0x00ffffff}, new float[]{0, 0.5f, 1});
    }

    /** 小四叶草：中心在原点，叶子半径 r */
    static void clover(Pen p, float r) {
        for (int i = 0; i < 4; i++) cloverLeaf(p, i * PI / 2 + PI / 4, r, i == 0 ? 0xff9be86a : 0xff5fc43a);
        p.circle(0, 0, r * 0.32f, 0xffffe27a);
    }

    /**
     * 幸运果实本体（中心在原点，半径 LR）。shine：扫光的位置（-1..1 扫过果子，范围外不画）；用作 HUD 贴图时传 9。
     */
    static void luckFruit(Pen p, float shine) {
        luckPaths();
        Canvas c = p.c;
        float r = LR;
        // 果柄、叶子
        p.stroke(luckStem, 0xff7a4a22, 2.2f);
        p.fillShader(luckLeaf, luckLeafGrad, 1);
        p.stroke(luckLeaf, INK, 1.2f);
        p.stroke(luckVein, 0x993a7a26, 0.9f);
        // 果子
        p.fillShader(luckBody, luckFruitGrad, 1);
        p.ovalFill(0.42f * r, 0.36f * r, 0.26f * r, 0.17f * r, 0x59ff7a4a); // 腮红
        p.ovalFill(-0.42f * r, 0.36f * r, 0.2f * r, 0.13f * r, 0x40ff7a4a);
        if (shine > -1.2f && shine < 1.2f) {
            Grad.placeRot(luckShine, shine * 1.4f * r, 0, 0.42f * r, 1, 24);
            p.fillShader(luckBody, luckShine, 1);
        }
        p.stroke(luckBody, INK, 1.7f);
        // 高光
        c.save();
        c.rotate(-35, -0.48f * r, -0.3f * r);
        p.ovalFill(-0.48f * r, -0.3f * r, 0.26f * r, 0.13f * r, 0xe6ffffff);
        c.restore();
        p.circle(-0.18f * r, -0.5f * r, 0.07f * r, 0xccffffff);
        // 果柄旁边的小四叶草
        c.save();
        c.translate(0.52f * r, -1.08f * r);
        c.rotate(18);
        clover(p, 0.27f * r);
        c.restore();
    }

    /** 四角闪光，中心 (x, y)，半径 r */
    static void sparkle(Pen p, float x, float y, float r, int argb) {
        luckPaths();
        Canvas c = p.c;
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        p.fill(sparkle4, argb);
        c.restore();
    }

    static void luckItem(Pen p, float x, float y, float t) {
        luckPaths();
        Canvas c = p.c;
        float bob = (float) Math.sin(t * 2.8f + x * 0.05f) * 2.2f;
        float cy = y - 17 + bob;
        c.save();
        c.translate(x, cy);
        if (luckGlow == null) luckGlow = Grad.radial(0, 0, 32, new int[]{0xccfff09a, 0x55b6f783, 0x00b6f783}, new float[]{0.05f, 0.5f, 1});
        p.circleShader(0, 0, 32, luckGlow, 0.7f + 0.3f * (float) Math.sin(t * 4));
        // 慢慢转的光芒
        c.save();
        c.rotate(deg(t * 0.6f));
        p.fillShader(luckRays, luckRayGrad, 0.55f + 0.25f * (float) Math.sin(t * 3));
        c.restore();
        // 果子轻轻摇摆，每 2.6 秒扫过一道光
        float u = (t * 0.385f + x * 0.003f) % 1;
        c.rotate((float) Math.sin(t * 2.2f) * 6, 0, LR);
        luckFruit(p, u < 0.4f ? -1.2f + u / 0.4f * 2.4f : 9);
        c.restore();
        // 一闪一闪的四角星
        for (int i = 0; i < 4; i++) {
            float[] s = LUCK_SPARKS[i];
            float ph = (t * 0.9f + s[2]) % 1;
            float k = (float) Math.sin(ph * PI);
            k = k * k;
            if (k < 0.04f) continue;
            sparkle(p, x + s[0], cy + s[1], 1.5f + 3.6f * k, Pen.rgba(LUCK_SPARK_COLS[i], 0.35f + 0.65f * k));
        }
    }

    /** 变身药剂：圆肚烧瓶里翻滚着赤红的岩浆药水，里面一只金色的竖瞳龙眼，外面一圈金色符文环和火苗 */
    static void potionItem(Pen p, float x, float y, float t) {
        Canvas c = p.c;
        c.save();
        float bob = (float) Math.sin(t * 2.4f + x * 0.05f) * 2.6f;
        c.translate(x, y - 21 + bob);
        float pulse = 0.72f + 0.28f * (float) Math.sin(t * 4.5f);
        if (potionAura == null)
            potionAura = Grad.radial(0, 0, 40, new int[]{0xd9ff7a2a, 0x66ff3a10, 0x00ff3a10}, new float[]{0.05f, 0.5f, 1});
        p.circleShader(0, 2, 40, potionAura, pulse);
        // 金色符文环
        c.save();
        c.rotate(deg(t * 1.1f));
        p.stroke.setPathEffect(DASH_POTION);
        p.circleStroke(0, 2, 27, 0xe6ffd36a, 1.5f);
        p.stroke.setPathEffect(null);
        Path rn = p.begin();
        for (int i = 0; i < 6; i++) {
            float a = i * TAU / 6;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            rn.moveTo(ca * 30, 2 + sa * 30);
            rn.lineTo(ca * 34, 2 + sa * 34);
        }
        p.stroke(rn, 0xccffb04a, 1.6f);
        c.restore();
        // 绕着转的火苗
        for (int i = 0; i < 5; i++) {
            float a = -t * 2.3f + (i * TAU) / 5;
            float px = (float) Math.cos(a) * 24, py = 2 + (float) Math.sin(a) * 11;
            float k = 0.5f + 0.5f * (float) Math.sin(t * 9 + i);
            Path f = p.begin();
            f.moveTo(px - 2.6f, py + 2);
            f.quadTo(px - 2.6f, py - 3, px, py - 5 - 3 * k);
            f.quadTo(px + 2.6f, py - 3, px + 2.6f, py + 2);
            f.close();
            p.fill(f, Pen.rgba(0xff8a2a, 0.65f + 0.35f * k));
            p.circle(px, py, 1.3f, 0xfffff2b0);
        }
        // 瓶子
        float R = 11.5f;
        p.rrInk(-4.2f, -19, 8.4f, 10, 2, 0x66e8fbff, 1.6f); // 瓶颈
        p.rrInk(-5.6f, -24, 11.2f, 6, 2.4f, 0xffb07a44, 1.6f); // 木塞
        p.line(-3.5f, -22.5f, 3.5f, -22.5f, 0x80ffffff, 1);
        // 液体（波浪液面）
        Canvas cc = p.c;
        cc.save();
        Path clip = p.path2;
        clip.rewind();
        clip.addCircle(0, 2, R - 0.6f, Path.Direction.CW);
        cc.clipPath(clip);
        p.circle(0, 2, R, 0x55e8fbff);
        Path lq = p.begin();
        float lvl = -1.5f;
        lq.moveTo(-R, lvl);
        for (int i = 0; i <= 8; i++) {
            float q = i / 8f;
            lq.lineTo(-R + q * 2 * R, lvl + (float) Math.sin(t * 5 + q * 7) * 1.4f);
        }
        lq.lineTo(R, R + 4);
        lq.lineTo(-R, R + 4);
        lq.close();
        if (potionLiquid == null)
            potionLiquid = Grad.linear(0, -2, 0, 14, new int[]{0xffff8a2a, 0xffe8283a, 0xff7a0a24}, null);
        p.fillShader(lq, potionLiquid, 1);
        // 龙眼
        float blink = (t % 3.2f) < 0.12f ? 0.15f : 1;
        p.ovalFill(0, 5, 5.6f, 3.4f * blink, 0xffffd04a);
        p.ovalFill(0, 5, 1.2f, 3f * blink, 0xff2a0408);
        p.circle(-1.8f, 4, 0.8f, 0xccffffff);
        // 气泡往上冒
        for (int i = 0; i < 4; i++) {
            float u = ((t * 0.9f + i * 0.27f) % 1);
            float bx = (float) Math.sin(i * 5.3f + t * 2) * 5;
            p.circleStroke(bx, 10 - u * 14, 0.9f + i * 0.25f, Pen.rgba(0xfff2b0, 1 - u), 0.9f);
        }
        cc.restore();
        p.circleStroke(0, 2, R, INK, 1.9f);
        // 玻璃反光
        Path gl = p.begin();
        p.rect.set(-R + 2.5f, -R + 4.5f, R - 2.5f, R - 0.5f);
        gl.arcTo(p.rect, 195, 60, true);
        p.stroke(gl, 0xd9ffffff, 1.8f);
        c.restore();
        // 瓶口冒出来的火星
        for (int i = 0; i < 3; i++) {
            float u = ((t * 0.8f + i * 0.33f) % 1);
            float ex = x + (float) Math.sin(t * 3 + i * 2.1f) * (3 + u * 6);
            float ey = y - 21 + bob - 25 - u * 18;
            p.circle(ex, ey, 1.6f * (1 - u) + 0.4f, Pen.rgba(i == 1 ? 0xfff2b0 : 0xffa03a, 1 - u));
        }
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
        if (g == null) g = darkFeather[i][q] = darkSheen(L, q);
        return g;
    }

    private static LinearGradient darkSheen(float L, int q) {
        float s = 0.25f + 0.5f * q / (SHEEN_N - 1);
        return Grad.linear(0, 0, L, 0, new int[]{0xff160a24, 0xff2e1350, 0xffb98aff, 0xff4a1f86, 0xff8f4dff},
                new float[]{0, Math.max(0.01f, s - 0.18f), s, Math.min(0.99f, s + 0.18f), 1});
    }

    /** 启动时把紫黑飞翼的 200 个光泽渐变、幸运果实的路径都先建好，第一次飞的时候不用临时分配 */
    static void prewarm() {
        for (int i = 0; i < FEATHERS.length; i++) {
            float L = FEATHERS[i][1] * 1.1f;
            for (int q = 0; q < SHEEN_N; q++) if (darkFeather[i][q] == null) darkFeather[i][q] = darkSheen(L, q);
        }
        luckPaths();
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
