package io.github.capsopasme.tuya.gfx;

import static io.github.capsopasme.tuya.gfx.Art.TAU;
import static io.github.capsopasme.tuya.gfx.Art.deg;
import static io.github.capsopasme.tuya.gfx.Pen.INK;
import static io.github.capsopasme.tuya.gfx.Pen.clamp;
import static io.github.capsopasme.tuya.gfx.Pen.lerp;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RadialGradient;

import io.github.capsopasme.tuya.game.C;
import io.github.capsopasme.tuya.game.Hero;
import io.github.capsopasme.tuya.game.Rng;

/**
 * 主角“小海星”：绿色的肥肥海星，五条胖手臂，正面朝前，张嘴吐泡泡球。
 * 姿态量全部来自游戏逻辑里的弹簧；这里只负责画。身体是“一整块”：五条手臂 + 中心圆盘用同一条路径描边再填色，
 * 所以手臂和身体之间没有多余的内部线条，像一块软糖。3D 模式下身体交给 {@link Hero3D}。
 */
public final class HeroArt {
    static final float HS = (float) C.HERO;
    static final float OUT_W = 2.3f, ARM_ROOT = 11.5f, ARM_TIP = 6.6f, ARM_LEN = 24, DISC = 13, FOOT_Y = -7.8f;

    /** 把当前姿态换算成各部位的位置，画角色、画残影、3D 着色器共用 */
    public static final class Geom {
        public int st;
        public float vy, L, sxB, syB, b2, bd, by, look, pose, shootK, aim;
        public final float[] top = new float[2];
        public final float[][] sides = new float[2][2];
        public final float[][] legs = new float[2][2];
    }

    final Geom geom = new Geom();
    final Geom ghostGeom = new Geom();
    final Pen p;
    Hero3D h3; // 3D 主角（不支持时为 null）
    public boolean use3d = true;

    HeroArt(Pen p) {
        this.p = p;
    }

    static void geom(Hero d, Geom g) {
        int st = d.state;
        float vy = (float) d.vy;
        float bt = (float) d.bounceT;
        float L;
        if (st == Hero.DEAD) L = 10;
        else if (bt < 0.07f) L = 5 + 3 * (bt / 0.07f);
        else if (vy < -150) L = Math.min(16, 8 + (bt - 0.07f) * 70);
        else if (vy < 150) L = 16 - 7 * clamp((vy + 150) / 300, 0, 1);
        else L = 9;
        float bd = clamp((float) d.belly, -1, 1);
        float b2 = (float) d.b2;
        float stretch = (float) d.stretch;
        float sxB = (1 + 0.5f * bd) * (1 - stretch * 0.45f);
        float syB = (1 - 0.42f * bd) * (1 + stretch * 0.6f);
        float sxA = 1 + (sxB - 1) * 0.65f;
        float v = (0.81f * (ARM_LEN - 2) + (L - 9) * 0.55f) * syB;
        float by = FOOT_Y - v;
        float look = (float) d.look;
        float pose = (float) d.armPose;
        boolean shooting = st != Hero.DEAD && d.shootT < 0.22;
        float aim = (float) d.aim;
        float shootK = st == Hero.DEAD ? 0 : d.shootT < 0.2 ? 1 : clamp(1 - ((float) d.shootT - 0.2f) / 0.14f, 0, 1);

        float sway = (float) Math.sin(d.t * 7) * 0.07f - clamp((float) d.vx / 900, -0.3f, 0.3f) - b2 * 0.1f;
        float topLen = (ARM_LEN + 1) * syB;
        g.top[0] = (float) Math.sin(sway) * topLen;
        g.top[1] = by - (float) Math.cos(sway) * topLen;

        int armSide = Math.cos(aim) >= 0 ? 1 : -1;
        for (int i = 0; i < 2; i++) {
            int s = i == 0 ? -1 : 1;
            float th = clamp(lerp(-1.05f, 0.4f, (pose + 1) / 2) + b2 * 0.45f, -1.12f, 0.7f);
            float dx = s * (float) Math.cos(th);
            float dy = (float) Math.sin(th);
            float len = ARM_LEN;
            if (shooting && s == armSide) {
                dx = lerp(dx, (float) Math.cos(aim), shootK);
                dy = lerp(dy, (float) Math.sin(aim), shootK);
                len = ARM_LEN + 3 * shootK;
            }
            float n = (float) Math.hypot(dx, dy);
            if (n == 0) n = 1;
            g.sides[i][0] = (dx / n) * len * sxA;
            g.sides[i][1] = by + (dy / n) * len * syB;
        }
        float fx = 0.59f * (ARM_LEN - 2) * sxA;
        g.legs[0][0] = -fx;
        g.legs[0][1] = FOOT_Y;
        g.legs[1][0] = fx;
        g.legs[1][1] = FOOT_Y;
        g.st = st;
        g.vy = vy;
        g.L = L;
        g.sxB = sxB;
        g.syB = syB;
        g.b2 = b2;
        g.bd = bd;
        g.by = by;
        g.look = look;
        g.pose = pose;
        g.shootK = shootK;
        g.aim = aim;
    }

    /** 整个身体的路径：五条根粗尖细的胖手臂（一串渐变半径的圆叠出来）+ 中心圆盘 */
    static void starPath(Path path, Geom g, float extra, int n) {
        path.rewind();
        for (int i = 0; i < 5; i++) {
            float tx, ty, rt;
            if (i == 0) {
                tx = g.top[0];
                ty = g.top[1];
                rt = ARM_TIP + 0.5f;
            } else if (i < 3) {
                tx = g.sides[i - 1][0];
                ty = g.sides[i - 1][1];
                rt = ARM_TIP;
            } else {
                tx = g.legs[i - 3][0];
                ty = g.legs[i - 3][1];
                rt = ARM_TIP + 1.2f;
            }
            for (int k = 1; k <= n; k++) {
                float t = (float) k / n;
                float r = lerp(ARM_ROOT, rt, t) + extra;
                path.addCircle(tx * t, g.by + (ty - g.by) * t, r, Path.Direction.CW);
            }
        }
        RECT.set(-(DISC * g.sxB + extra), g.by - (DISC * g.syB + extra), DISC * g.sxB + extra, g.by + DISC * g.syB + extra);
        path.addOval(RECT, Path.Direction.CW);
    }

    private static final android.graphics.RectF RECT = new android.graphics.RectF();

    // ---------- 身体倾斜 / 旋转 ----------
    final Matrix lean = new Matrix(), spin = new Matrix(), body = new Matrix(), leanInv = new Matrix();
    boolean hasLean, hasSpin;

    /** lean：整个角色一起朝移动方向倾斜（踩在平台上蓄力时绕前脚转，腾空后绕身体中心转）；spin：紫黑飞翼时只有身体旋转 */
    void xf(Hero d, Geom g) {
        float ln = (float) d.lean;
        float roll = (float) d.roll;
        float air = (float) d.leanAir;
        float footX = g.legs[1][0] * clamp(ln / 0.2f, -1, 1) * (1 - air);
        hasLean = Math.abs(ln) > 1e-4f;
        hasSpin = Math.abs(roll) > 1e-4f;
        lean.reset();
        if (hasLean) lean.setRotate(deg(ln), footX, lerp(FOOT_Y, g.by, air));
        spin.reset();
        if (hasSpin) spin.setRotate(deg(roll), 0, g.by);
        body.set(lean);
        body.preConcat(spin);
    }

    // ---------- 残影：一层半透明剪影 ----------
    final Path ghostPath = new Path();
    final Matrix ghostM = new Matrix();

    void ghost(Hero d, float x, float y, float alpha, int col) {
        Canvas c = p.c;
        c.save();
        c.translate(x, y);
        c.scale(HS, HS);
        Geom g = ghostGeom;
        geom(d, g);
        float ln = (float) d.lean, roll = (float) d.roll, air = (float) d.leanAir;
        ghostM.reset();
        if (Math.abs(ln) > 1e-4f) ghostM.setRotate(deg(ln), g.legs[1][0] * clamp(ln / 0.2f, -1, 1) * (1 - air), lerp(FOOT_Y, g.by, air));
        if (Math.abs(roll) > 1e-4f) ghostM.preRotate(deg(roll), 0, g.by);
        c.concat(ghostM);
        starPath(ghostPath, g, 1.5f, 4);
        p.fill(ghostPath, Pen.rgba(col, alpha));
        c.restore();
    }

    // ---------- 飞翼：两只大翅膀长在背后 ----------
    private RadialGradient wingGlowGold, wingGlowDark, shieldGold, shieldDark;
    private static final DashPathEffect DASH_RING = new DashPathEffect(new float[]{5, 7}, 0);
    private static final DashPathEffect DASH_SHIELD = new DashPathEffect(new float[]{7, 5}, 0);

    void wings(Hero d, Geom g, boolean dark) {
        Canvas c = p.c;
        float T = (float) (dark ? C.DARK_T : C.WING_T);
        float left = T - (float) d.stateT;
        float top = dark ? 19 : 15;
        float speed = left < 1 ? lerp(7, top, clamp(left, 0, 1)) : top;
        float t = (float) d.t;
        float flap = (float) Math.sin(t * speed) * (dark ? 0.5f : 0.55f);
        float open = clamp((float) d.stateT / 0.18f, 0, 1);
        float r = (dark ? 66 : 58) * (0.6f + 0.4f * open);
        RadialGradient gr;
        if (dark) {
            if (wingGlowDark == null)
                wingGlowDark = Grad.radial(0, 0, 1, new int[]{0x99be78ff, 0x526e28d2, 0x003c0a78}, new float[]{0, 0.45f, 1});
            gr = wingGlowDark;
        } else {
            if (wingGlowGold == null) wingGlowGold = Grad.radial(0, 0, 1, new int[]{0x8cffec96, 0x00ffec96}, null);
            gr = wingGlowGold;
        }
        Grad.place(gr, 0, g.by, r, r);
        p.circleShader(0, g.by, r, gr, 1);
        if (dark) {
            c.save();
            c.translate(0, g.by);
            float ga0 = p.ga;
            p.ga = ga0 * open;
            c.rotate(deg(-t * 1.6f));
            p.stroke.setPathEffect(DASH_RING);
            p.circleStroke(0, 0, 44, 0xb3c896ff, 1.6f);
            p.stroke.setPathEffect(null);
            c.rotate(deg(t * 2.8f));
            Path path = p.begin();
            for (int i = 0; i < 12; i++) {
                float a = (i * TAU) / 12;
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float r1 = i % 3 == 0 ? 56 : 52;
                path.moveTo(ca * 49, sa * 49);
                path.lineTo(ca * r1, sa * r1);
            }
            p.stroke(path, 0x8cff78eb, 1.5f);
            p.ga = ga0;
            c.restore();
        }
        for (int s = -1; s <= 1; s += 2) {
            c.save();
            c.translate(s * 6 * g.sxB, g.by - 4 * g.syB);
            float ws = (dark ? 1.36f : 1.3f) * (0.35f + 0.65f * open);
            c.scale(s * ws, ws);
            c.rotate(deg(-0.12f - flap));
            Art.wingShape(p, dark, t + s * 0.4f);
            c.restore();
        }
    }

    /** 紫黑飞翼：三颗拖着尾巴的小彗星绕着旋转的小海星转 */
    void darkFront(Hero d, Geom g) {
        Canvas c = p.c;
        float t = (float) d.t;
        float open = clamp((float) d.stateT / 0.25f, 0, 1);
        c.save();
        c.translate(0, g.by);
        c.rotate(deg(-0.35f));
        int[] cols = {0xc77dff, 0xff6eeb, 0x7df0ff};
        for (int i = 0; i < 3; i++) {
            float a0 = t * 6.5f + (i * TAU) / 3;
            float depth = (float) Math.sin(a0);
            float k = open * (0.35f + 0.65f * (0.5f + 0.5f * depth));
            Path path = p.begin();
            for (int j = 0; j <= 8; j++) {
                float a = a0 - j * 0.11f;
                float px = (float) Math.cos(a) * 40, py = (float) Math.sin(a) * 13;
                if (j == 0) path.moveTo(px, py);
                else path.lineTo(px, py);
            }
            if (k > 0.001f) p.stroke(path, Pen.rgba(cols[i], 0.6f * k), 2.6f * k);
            path = p.begin();
            Pen.star(path, (float) Math.cos(a0) * 40, (float) Math.sin(a0) * 13, 2.2f + 2.6f * k);
            p.fill(path, Pen.rgba(0xffffff, k));
        }
        c.restore();
    }

    /** 飞翼结束后的护盾：一圈闪烁的光环，快结束时闪得更快 */
    void shield(Hero d, Geom g) {
        Canvas c = p.c;
        float t = (float) d.t;
        float fade = clamp((float) d.shield / 0.4f, 0, 1);
        float blink = d.shield < 0.6 ? 0.5f + 0.5f * (float) Math.sin(t * 40) : 0.75f + 0.25f * (float) Math.sin(t * 10);
        float r = 36 + (float) Math.sin(t * 8) * 1.5f;
        boolean dark = d.shieldDark;
        float ga0 = p.ga;
        p.ga = ga0 * fade * blink;
        RadialGradient gr;
        if (dark) {
            if (shieldDark == null) shieldDark = Grad.radial(0, 0, 1, new int[]{0x00aa5aff, 0x73a050ff}, new float[]{0.55f, 1});
            gr = shieldDark;
        } else {
            if (shieldGold == null) shieldGold = Grad.radial(0, 0, 1, new int[]{0x00ffec96, 0x73ffe278}, new float[]{0.55f, 1});
            gr = shieldGold;
        }
        Grad.place(gr, 0, g.by, r, r);
        p.circleShader(0, g.by, r, gr, 1);
        // 虚线一路往前转（转画布，不用每帧新建虚线效果）
        c.save();
        c.rotate(deg(t * 30 / r), 0, g.by);
        p.stroke.setPathEffect(DASH_SHIELD);
        p.circleStroke(0, g.by, r, dark ? 0xf2be82ff : 0xe6f0be32, 2.4f);
        p.stroke.setPathEffect(null);
        c.restore();
        p.ga = ga0;
    }

    // ---------- 2D 版本的身体和脸 ----------
    final Path bodyPath = new Path();
    private RadialGradient bodyGrad;
    private static final float[] DOTS = {0.55f, 2.1f, 0.76f, 1.7f, 0.94f, 1.2f};

    void body2d(Hero d, Geom g) {
        int st = d.state;
        float sxB = g.sxB, syB = g.syB, by = g.by, b2 = g.b2, look = g.look, pose = g.pose, aim = g.aim, shootK = g.shootK;
        // 身体：先整块描黑边，再整块填上渐变的绿色
        starPath(bodyPath, g, OUT_W, 7);
        p.fill(bodyPath, INK);
        if (bodyGrad == null)
            bodyGrad = Grad.conical(-7f / 42, -12f / 42, 3f / 42, 0, 0, 1, new int[]{0xffc9f58f, 0xff82d54c, 0xff3f9d3a}, new float[]{0, 0.5f, 1});
        float R = 42 * Math.max(sxB, syB);
        Grad.place(bodyGrad, 0, by, R, R);
        starPath(bodyPath, g, 0, 7);
        p.fillShader(bodyPath, bodyGrad, 1);

        // 海星特有的小颗粒：沿着每条手臂排三个浅色小点
        Path dots = p.begin();
        for (int i = 0; i < 5; i++) {
            float tx = i == 0 ? g.top[0] : i < 3 ? g.sides[i - 1][0] : g.legs[i - 3][0];
            float ty = i == 0 ? g.top[1] : i < 3 ? g.sides[i - 1][1] : g.legs[i - 3][1];
            for (int j = 0; j < 3; j++) {
                float k = DOTS[j * 2];
                dots.addCircle(tx * k, by + (ty - by) * k, DOTS[j * 2 + 1], Path.Direction.CW);
            }
        }
        p.fill(dots, 0xe6e1ffaf);

        // 肚皮：挂在第二层弹簧上，比身体晃得更晚、更大
        p.ovalFill(0, by + 8 * syB + b2 * 6, 10.5f * sxB * (1 + b2 * 0.28f), Math.max(3, 7 * syB * (1 - b2 * 0.22f)), 0xccf0ffd2);

        // 脸：随朝向做视差
        float f = 3.4f * look;
        float re = clamp(5.8f + (sxB - 1) * 1.2f, 5, 7);
        float px = look * 1.6f, py = pose * 1.2f;
        if (shootK > 0) {
            px = lerp(px, (float) Math.cos(aim) * 2.2f, shootK);
            py = lerp(py, (float) Math.sin(aim) * 2.2f, shootK);
        }
        for (int i = 0; i < 2; i++) {
            float ex = (i == 0 ? -7 + f : 7 + f) * sxB;
            float ey = by - 5 * syB;
            if (st == Hero.DEAD) {
                Path x = p.begin();
                x.moveTo(ex - 3, ey - 3);
                x.lineTo(ex + 3, ey + 3);
                x.moveTo(ex + 3, ey - 3);
                x.lineTo(ex - 3, ey + 3);
                p.stroke(x, INK, 2);
            } else {
                p.circleInk(ex, ey, re, 0xffffffff, 1.7f);
                p.circle(ex + px, ey + py, 2.8f, INK);
            }
        }
        for (int i = 0; i < 2; i++) {
            float bx = (i == 0 ? -13 + f : 13 + f) * sxB;
            p.ovalFill(bx, by + 3.5f * syB, 3.4f, 2.3f, 0x6bff6e82);
        }
        // 嘴：平时微笑，射击时张成圆口，撞怪时歪嘴
        float mx = (f + 0.5f) * sxB, my = by + 7 * syB;
        if (st == Hero.DEAD) {
            Path m = p.begin();
            m.moveTo(mx - 4, my + 1);
            m.quadTo(mx - 1.5f, my - 2, mx, my + 1);
            m.quadTo(mx + 1.5f, my + 4, mx + 4, my);
            p.stroke(m, INK, 1.9f);
        } else if (shootK > 0.05f) {
            float ox = (float) Math.cos(aim) * 1.5f, oy = (float) Math.sin(aim) * 1.5f;
            p.ovalInk(mx + ox, my - 1 + oy, 4.4f * shootK + 1.2f, 5 * shootK + 0.8f, INK, 1.4f);
            if (shootK > 0.5f) p.ovalFill(mx + ox, my + 1 + oy, 2.6f, 2, 0xffef6f7e);
        } else {
            Path m = p.begin();
            p.rect.set(mx - 4.6f, my - 3 - 4.6f, mx + 4.6f, my - 3 + 4.6f);
            m.arcTo(p.rect, 36, 108, true);
            p.stroke(m, INK, 1.9f);
        }
    }

    // ---------- 整个角色 ----------
    void drawAt(Hero d, float x, float y, float viewScale) {
        Canvas c = p.c;
        int st = d.state;
        c.save();
        c.translate(x, y);
        c.scale(HS, HS);
        float extraScale = 1;
        if (st == Hero.BLACKHOLE || st == Hero.ABDUCTED) {
            float sc = Math.max(0.01f, (float) d.scale);
            c.translate(0, -24);
            c.rotate(deg((float) d.spin));
            c.scale(sc, sc);
            c.translate(0, 24);
            extraScale = sc;
        } else if (st == Hero.DEAD) {
            c.translate(0, -24);
            c.rotate(deg((float) Math.sin(d.spin) * 0.25f));
            c.translate(0, 24);
        }
        Geom g = geom;
        geom(d, g);
        float by = g.by, sxB = g.sxB;
        float topY = g.top[1] - ARM_TIP - OUT_W;
        xf(d, g);
        if (hasLean) c.concat(lean);

        if (Hero.winged(st)) wings(d, g, st == Hero.DARKWINGS);
        if (d.shield > 0 && st == Hero.NORMAL) shield(d, g);

        if (use3d && h3 != null && h3.ok()) {
            // 3D：着色器在“没有倾斜”的角色坐标里算（倾斜和旋转交给着色器，光照和投影方向始终不变）
            c.save();
            if (hasLean) {
                lean.invert(leanInv);
                c.concat(leanInv);
            }
            h3.draw(c, d, g, body, 1f / (viewScale * HS * extraScale));
            c.restore();
        } else if (hasSpin) {
            c.save();
            c.concat(spin);
            body2d(d, g);
            c.restore();
        } else {
            body2d(d, g);
        }
        if (st == Hero.DARKWINGS) darkFront(d, g);

        // 喷气背包：两个油罐绑在肚子两侧，火焰朝下喷
        if (st == Hero.JETPACK) {
            float fl = 14 + (float) Math.sin(d.t * 60) * 5 + (float) Rng.fx() * 3;
            for (int i = 0; i < 2; i++) {
                float jx = i == 0 ? -23.5f * sxB : 14.5f * sxB;
                float jy = by - 6;
                p.rrInk(jx, jy, 9, 24, 4, 0xff9db4c9, 2);
                p.rrInk(jx, jy, 9, 7, 3, 0xffe8483c, 1.6f);
                Path fp = p.begin();
                fp.moveTo(jx + 0.5f, jy + 24);
                fp.lineTo(jx + 4.5f, jy + 24 + fl);
                fp.lineTo(jx + 8.5f, jy + 24);
                fp.close();
                p.fillInk(fp, 0xffffb02e, 1.4f);
                fp = p.begin();
                fp.moveTo(jx + 2.5f, jy + 24);
                fp.lineTo(jx + 4.5f, jy + 24 + fl * 0.55f);
                fp.lineTo(jx + 6.5f, jy + 24);
                fp.close();
                p.fill(fp, 0xfffff29a);
            }
        }

        // 螺旋桨帽：扣在头顶那条手臂的尖上
        if (st == Hero.PROPELLER) {
            float hx = g.top[0], hy = g.top[1] - 2;
            Path hp = p.begin();
            p.rect.set(hx - 11, hy - 11, hx + 11, hy + 11);
            hp.arcTo(p.rect, 180, 180, true);
            hp.close();
            p.fillInk(hp, 0xffef5b5b, 2);
            p.rrInk(hx - 12, hy - 1, 24, 4.4f, 2, 0xff3d86d6, 1.8f);
            p.line(hx, hy - 11, hx, hy - 15, INK, 2.2f);
            float bw = 15 * Math.abs((float) Math.cos(d.t * 38));
            p.ovalInk(hx, hy - 16, Math.max(2, bw), 2.4f, 0xfff2d24a, 1.6f);
        }

        // 撞到怪物后头上转圈的星星
        if (st == Hero.DEAD) {
            for (int i = 0; i < 3; i++) {
                float a = (float) d.t * 6 + (i * TAU) / 3;
                Path sp = p.begin();
                Pen.star(sp, g.top[0] + (float) Math.cos(a) * 20, topY - 4 + (float) Math.sin(a) * 5, 6);
                p.fillInk(sp, 0xffffd93b, 1.3f);
            }
        }
        c.restore();
    }
}
