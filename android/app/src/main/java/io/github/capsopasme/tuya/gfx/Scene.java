package io.github.capsopasme.tuya.gfx;

import static io.github.capsopasme.tuya.gfx.Art.TAU;
import static io.github.capsopasme.tuya.gfx.Art.deg;
import static io.github.capsopasme.tuya.gfx.Pen.INK;
import static io.github.capsopasme.tuya.gfx.Pen.clamp;
import static io.github.capsopasme.tuya.gfx.Pen.lerp;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;

import java.util.ArrayList;

import io.github.capsopasme.tuya.game.C;
import io.github.capsopasme.tuya.game.Entities;
import io.github.capsopasme.tuya.game.Entities.Bullet;
import io.github.capsopasme.tuya.game.Entities.Debris;
import io.github.capsopasme.tuya.game.Entities.Hole;
import io.github.capsopasme.tuya.game.Entities.Item;
import io.github.capsopasme.tuya.game.Entities.Monster;
import io.github.capsopasme.tuya.game.Entities.Platform;
import io.github.capsopasme.tuya.game.Entities.Spark;
import io.github.capsopasme.tuya.game.Fx;
import io.github.capsopasme.tuya.game.Game;
import io.github.capsopasme.tuya.game.Hero;

/** 整个游戏场景的绘制。每帧在 UI 线程录制一次显示列表，真正的光栅化在 RenderThread 上由 GPU 完成。 */
public final class Scene {
    public final Pen pen = new Pen();
    public final View2 v = new View2();
    final Sprites sprites = new Sprites();
    public final HeroArt hero = new HeroArt(pen);
    final Hero tmpHero = new Hero();
    /** 动画时间（秒） */
    public double clock;

    public Scene() {
        hero.h3 = Hero3D.create();
        Art.prewarm();
    }

    private int warmLeft = 2;

    /**
     * 着色器预热：菜单的头两帧，在画面中央用几乎看不见的透明度（1/255）把所有道具、飞翼、飞龙、护盾、火球、粒子贴图各画一遍，
     * 让 GPU 提前编译好这些渐变 / 着色器组合，游戏里第一次出现时就不会卡一下。
     */
    public void warm(Canvas c) {
        if (warmLeft <= 0 || v.pw == 0) return;
        warmLeft--;
        Pen p = pen;
        p.c = c;
        float ga0 = p.ga;
        p.ga = 1.2f / 255;
        c.save();
        enterLogical(c);
        float x = v.W / 2, y = v.H / 2, t = 1.3f;
        Art.wingItem(p, false, x, y, t);
        Art.wingItem(p, true, x, y, t);
        Art.lifeItem(p, x, y, t);
        Art.luckItem(p, x, y, t);
        Art.potionItem(p, x, y, t);
        Art.hole(p, x, y, 28, t);
        fireball(x, y, 0, -500);
        RadialGradient g = Grad.glow(0xffffff);
        Grad.place(g, x, y, 30, 30);
        p.circleShader(x, y, 30, g, 1);
        sprites.shape(c, Sprites.SH_STAR, x, y, 1, 30, p.mul(0xffffffff, 1), p.mul(INK, 1));
        p.bmp.setAlpha(1);
        sprites.hudLuck(c, x, y, 0.8f, p.bmp);
        Hero h = tmpHero;
        boolean u3 = hero.use3d;
        hero.use3d = false; // 3D 着色器自己的画笔不吃全局透明度，菜单里本来就一直在画，不用预热
        int[] states = {Hero.WINGS, Hero.DARKWINGS, Hero.DRAGON, Hero.JETPACK, Hero.PROPELLER, Hero.NORMAL};
        for (int st : states) {
            h.fake(x, y, 1, -400, 1, t);
            h.state = st;
            h.stateT = 1;
            h.shield = st == Hero.NORMAL ? 1 : 0;
            heroAt(h, x, y);
        }
        hero.use3d = u3;
        c.restore();
        p.ga = ga0;
    }

    public boolean has3d() {
        return hero.h3 != null && hero.h3.ok();
    }

    public void resize(int pw, int ph) {
        v.set(pw, ph);
        sprites.rebuild(v.s);
        layoutHud();
    }

    /** 进入逻辑坐标（单位）。调用方负责 save/restore */
    public void enterLogical(Canvas c) {
        c.translate(v.ox, v.oy);
        c.scale(v.s, v.s);
    }

    static float ix(double prev, double cur, float a) {
        if (Math.abs(cur - prev) > 100) return (float) cur;
        return (float) (prev + (cur - prev) * a);
    }

    // ---------- 背景：方格笔记本纸 ----------
    /** 在像素坐标下调用（还没进入逻辑坐标）：平铺方格，每条线都对齐整像素 */
    public void background(Canvas c, float camY, float shx, float shy) {
        float cell = sprites.cell();
        float offY = ((camY % cell) + cell) % cell;
        float tx = Math.round(v.ox + shx * v.s);
        float ty = Math.round(v.oy + (shy - offY) * v.s);
        sprites.gridM.setTranslate(tx, ty);
        sprites.gridShader.setLocalMatrix(sprites.gridM);
        sprites.gridPaint.setShader(sprites.gridShader);
        c.drawRect(0, 0, v.pw, v.ph, sprites.gridPaint);
    }

    /** 屏幕比例特殊时，游戏区域以外的部分压暗并画边线 */
    public void letterbox(Canvas c) {
        Pen p = pen;
        if (v.x0 < -0.5f) {
            p.rectFill(v.x0, v.y0, -v.x0, v.y1 - v.y0, 0x123c3c28);
            p.rectFill(v.W, v.y0, v.x1 - v.W, v.y1 - v.y0, 0x123c3c28);
        }
        if (v.y0 < -0.5f) {
            p.rectFill(0, v.y0, v.W, -v.y0, 0x123c3c28);
            p.rectFill(0, v.H, v.W, v.y1 - v.H, 0x123c3c28);
        }
        if (v.x0 < -0.5f || v.y0 < -0.5f) {
            p.stroke.setStrokeCap(Paint.Cap.BUTT);
            p.rect.set(0, 0, v.W, v.H);
            p.stroke.setColor(0x593c3c28);
            p.stroke.setStrokeWidth(1.5f / v.s);
            c.drawRect(p.rect, p.stroke);
            p.stroke.setStrokeCap(Paint.Cap.ROUND);
        }
    }

    // ---------- 平台与道具 ----------
    void platform(Canvas c, Platform pl, float x, double clockT) {
        Pen p = pen;
        float y = (float) (pl.y + pl.dip);
        float a = pl.dead ? Math.max(0, 1 - (float) pl.fade / 0.22f) : 1;
        if (a <= 0) return;
        p.bmp.setAlpha(Pen.clamp255(a * p.ga));
        sprites.platform(c, pl.type, x - (float) pl.w / 2, y, p.bmp);
        Item it = pl.item;
        if (it != null && !it.used) item(c, it, x + (float) it.ox, y, x, clockT);
    }

    void item(Canvas c, Item it, float x, float y, float platX, double clockT) {
        float t = (float) clockT;
        if (it.type == Entities.I_WINGS || it.type == Entities.I_DARK) {
            Art.wingItem(pen, it.type == Entities.I_DARK, x, y, t);
            return;
        }
        if (it.type == Entities.I_LIFE) {
            Art.lifeItem(pen, x, y, t);
            return;
        }
        if (it.type == Entities.I_LUCK) {
            Art.luckItem(pen, x, y, t);
            return;
        }
        if (it.type == Entities.I_DRAGON) {
            Art.potionItem(pen, x, y, t);
            return;
        }
        if (!it.fired) {
            float bob = it.type == Entities.I_PROP ? (float) Math.sin(platX * 0.05f + t * 3) * 1.2f : 0;
            pen.bmp.setAlpha(Pen.clamp255(pen.ga));
            if (sprites.item(c, it.type, x, y + bob, pen.bmp)) return;
        }
        Art.item(pen, it, x, y, 0);
    }

    /** 菜单里的装饰平台：(x, y) 是平台中心顶边 */
    public void platformAt(Canvas c, int type, float x, float y) {
        pen.bmp.setAlpha(Pen.clamp255(pen.ga));
        sprites.platform(c, type, x - (float) C.PLAT_W / 2, y, pen.bmp);
    }

    // ---------- 主角 ----------
    public void heroAt(Hero d, float x, float y) {
        hero.drawAt(d, x, y, v.s);
    }

    void doodler(Hero d, float a) {
        if (d.state == Hero.GONE) return;
        float x = ix(d.px, d.x, a), y = ix(d.py, d.y, a);
        heroAt(d, x, y);
        if (d.state != Hero.BLACKHOLE && d.state != Hero.ABDUCTED) {
            // 走到屏幕边缘时另一侧也画一份（飞翼张开时翅膀很宽，边距跟着放大）
            float edge = d.state == Hero.DRAGON ? DragonArt.EDGE : (Hero.winged(d.state) ? 84 : 52) * HeroArt.HS;
            if (x < edge) heroAt(d, x + v.W, y);
            else if (x > v.W - edge) heroAt(d, x - v.W, y);
        }
    }

    // ---------- 特效 ----------
    private static final DashPathEffect DASH_BEST = new DashPathEffect(new float[]{8, 6}, 0);

    void effects(Canvas c, ArrayList<Fx> list, boolean front) {
        Pen p = pen;
        for (int i = 0, n = list.size(); i < n; i++) {
            Fx f = list.get(i);
            if (Fx.front(f.k) != front) continue;
            if (f.delay > 0) continue;
            float al = clamp((float) (f.life / f.max), 0, 1);
            float u = 1 - al;
            float fx = (float) f.x, fy = (float) f.y;
            switch (f.k) {
                case Fx.PUFF: {
                    float r = (float) f.r * (1 + (1 - al) * 1.3f);
                    p.ga = al * 0.7f;
                    p.circle(fx, fy - r * 0.4f, r, 0xfffffdf3);
                    p.circleStroke(fx, fy - r * 0.4f, r, 0x8c5f6e64, 1.3f);
                    break;
                }
                case Fx.RING:
                    p.ga = al * 0.6f;
                    p.ovalStroke(fx, fy - 2, (float) f.r * 1.6f, (float) f.r * 0.45f, 0xcc467864, 2.2f);
                    break;
                case Fx.SHOCK:
                    p.ga = Math.min(1, al * 1.4f);
                    p.ovalStroke(fx, fy - 2, (float) f.r, (float) (f.r * f.flat), 0xff000000 | f.col, (float) f.w * (0.25f + 0.75f * al));
                    break;
                case Fx.GLOW: {
                    float r = (float) f.r * (0.6f + 0.8f * u);
                    p.ga = 1;
                    RadialGradient g = Grad.glow(f.col);
                    Grad.place(g, fx, fy, r, r);
                    p.circleShader(fx, fy, r, g, al * 0.95f);
                    break;
                }
                case Fx.BEAM: {
                    float w = (float) f.w * (0.3f + 0.7f * al);
                    float h = (float) f.h * (0.55f + 0.45f * Math.min(1, u * 3));
                    p.ga = 1;
                    LinearGradient g = Grad.beam(f.col);
                    Grad.place(g, 0, fy, 1, h);
                    Path path = p.begin();
                    path.moveTo(fx - w / 2, fy);
                    path.lineTo(fx + w / 2, fy);
                    path.lineTo(fx + w * 0.18f, fy - h);
                    path.lineTo(fx - w * 0.18f, fy - h);
                    path.close();
                    p.fillShader(path, g, al * 0.9f);
                    LinearGradient gw = Grad.beam(0xffffff);
                    Grad.place(gw, 0, fy, 1, h * 0.7f);
                    path = p.begin();
                    path.moveTo(fx - w * 0.2f, fy);
                    path.lineTo(fx + w * 0.2f, fy);
                    path.lineTo(fx + w * 0.05f, fy - h * 0.7f);
                    path.lineTo(fx - w * 0.05f, fy - h * 0.7f);
                    path.close();
                    p.fillShader(path, gw, al * 0.9f);
                    break;
                }
                case Fx.GHOST:
                    p.ga = 1;
                    if (f.d.state == Hero.NORMAL || Hero.flying(f.d.state)) hero.ghost(f.d, fx, fy, al * 0.42f, f.col);
                    break;
                case Fx.FEATHER: {
                    p.ga = Math.min(1, al * 2);
                    c.save();
                    c.translate(fx, fy);
                    c.rotate(deg((float) f.rot));
                    float r = (float) f.r;
                    if (f.dark) {
                        p.ovalFill(0, 0, r * 1.9f, r * 0.75f, 0xff2a1242);
                        p.ovalStroke(0, 0, r * 1.9f, r * 0.75f, 0xd9c896ff, 1.2f);
                    } else {
                        p.ovalInk(0, 0, r * 1.9f, r * 0.75f, 0xfffffdf2, 1.2f);
                    }
                    p.line(-r * 2.4f, 0, r * 1.5f, 0, f.dark ? 0xcc82f0ff : 0xe6c8a03c, 1);
                    c.restore();
                    break;
                }
                case Fx.SPIRAL: {
                    float e = 1 - (float) Math.pow(1 - Math.min(1, u * 1.5f), 3);
                    float R = (float) f.R * (0.2f + 0.8f * e);
                    p.ga = Math.min(1, al * 1.3f);
                    Path path = p.begin();
                    for (int j = 0; j < f.n; j++) {
                        for (int k = 2; k <= 14; k++) {
                            float q = k / 14f;
                            float a = (float) (f.a0 + (j * TAU) / f.n + u * f.spin + q * 2.4f);
                            float r = R * q;
                            float px = fx + (float) Math.cos(a) * r, py = fy + (float) (Math.sin(a) * r * f.flat);
                            if (k == 2) path.moveTo(px, py);
                            else path.lineTo(px, py);
                        }
                    }
                    p.stroke(path, 0xff000000 | f.col, (float) f.w * (0.3f + 0.7f * al));
                    break;
                }
                case Fx.RAYS: {
                    float e = 1 - (float) Math.pow(1 - Math.min(1, u * 1.7f), 2);
                    p.ga = 1;
                    Path path = p.begin();
                    for (int j = 0; j < f.n; j++) {
                        float t = f.n > 1 ? (float) j / (f.n - 1) : 0.5f;
                        float a = (float) (f.a0 + (f.a1 - f.a0) * t + Math.sin(j * 12.9) * 0.07);
                        float lenK = 0.72f + 0.28f * (float) Math.sin(j * 7.3);
                        float rOut = (float) f.R * e * lenK + 8;
                        float rIn = 8 + (rOut - 8) * u * u * 0.92f;
                        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                        path.moveTo(fx + ca * rIn, fy + sa * rIn);
                        path.lineTo(fx + ca * rOut, fy + sa * rOut);
                    }
                    p.stroke(path, Pen.rgba(f.col, Math.min(1, al * 1.2f)), (float) f.w * (0.4f + 0.6f * al));
                    break;
                }
                case Fx.PART: {
                    float sz = (float) f.r * (0.55f + 0.45f * al);
                    p.ga = Math.min(1, al * 2.2f);
                    if (f.shape == Fx.S_HEART || f.shape == Fx.S_LEAF || f.shape == Fx.S_SHARD || f.shape == Fx.S_STAR) {
                        // 贴图粒子：一份蒙版上色 + 一份墨线，不再每个粒子细分两条路径
                        int kind = f.shape == Fx.S_STAR ? Sprites.SH_STAR : f.shape == Fx.S_HEART ? Sprites.SH_HEART
                                : f.shape == Fx.S_LEAF ? Sprites.SH_LEAF : Sprites.SH_SHARD;
                        boolean shard = f.shape == Fx.S_SHARD;
                        sprites.shape(c, kind, fx, fy, sz / Sprites.SH_R, deg((float) f.rot),
                                p.mul(shard ? Pen.rgba(f.col, 0.8f) : 0xff000000 | f.col, 1), p.mul(shard ? 0xcc2d2d26 : INK, 1));
                    } else if (f.shape == Fx.S_FIRE) {
                        p.circle(fx, fy, sz * 1.1f, al > 0.66f ? 0xffffdf4d : al > 0.33f ? 0xffff9f2e : 0xffff6a3d);
                    } else {
                        p.circleInk(fx, fy, sz, 0xff000000 | f.col, 1.2f);
                    }
                    break;
                }
                case Fx.FIRE:
                    p.ga = Math.min(1, al * 1.8f);
                    p.circle(fx, fy, (float) f.r * (0.35f + 0.65f * al), al > 0.66f ? 0xffffdf4d : al > 0.33f ? 0xffff9f2e : 0xffff6a3d);
                    break;
                case Fx.STREAK:
                    p.ga = 1;
                    p.line(fx, fy, fx - (float) f.vx * 0.06f, fy - (float) f.vy * 0.06f, Pen.rgba(0xffffff, al * 0.85f), 2);
                    break;
                case Fx.TEXT: {
                    // 拟声字：弹性“蹦”出来（先放大过头再回弹），向上飘一点，最后淡出
                    float sc = u < 0.16f ? lerp(0.3f, 1.3f, u / 0.16f) : u < 0.3f ? lerp(1.3f, 1, (u - 0.16f) / 0.14f) : 1;
                    p.ga = al < 0.25f ? al / 0.25f : 1;
                    float size = (float) f.size;
                    float tw = p.measure(f.txt, size, Pen.BOLD) * sc / 2;
                    c.save();
                    c.translate(clamp(fx, tw + 6, v.W - tw - 6), fy - 26 * (1 - (float) Math.pow(1 - u, 3)));
                    c.rotate(deg((float) f.rot));
                    c.scale(sc, sc);
                    p.inkText(f.txt, 0, 0, size, 0xff000000 | f.col, 5.5f, Pen.BOLD);
                    c.restore();
                    break;
                }
                default:
                    break;
            }
        }
        p.ga = 1;
    }

    void speedLines(Hero d, float x, float y) {
        float k = clamp(((float) -d.vy - 650) / 450, 0, 1);
        if (k <= 0 || d.state == Hero.DEAD) return;
        boolean dragon = d.state == Hero.DRAGON;
        boolean dark = d.state == Hero.DARKWINGS;
        boolean wings = dark || d.state == Hero.WINGS;
        double t = clock * (dragon ? 2600 : dark ? 2100 : wings ? 1600 : 1100);
        int n = dragon ? 19 : dark ? 13 : wings ? 11 : 7;
        float span = dragon ? 15 : wings ? 12 : 13 * HeroArt.HS;
        int col = dragon ? 0xff5a1e : dark ? 0x8c46e6 : wings ? 0xc89628 : 0x466e6e;
        if (dragon) y += 20;
        for (int i = 0; i < n; i++) {
            float lx = x + (i - (n - 1) / 2f) * span + (float) Math.sin(i * 12.9) * 4;
            float len = (dragon ? 70 : dark ? 52 : wings ? 40 : 26) + ((i * 37) % 22);
            float off = (float) ((t + i * 53) % 70);
            pen.line(lx, y + 4 + off, lx, y + 4 + off + len, Pen.rgba(col, 0.32f * k * (1 - off / 70)), 2);
        }
    }

    // ---------- HUD ----------
    // 顶栏：左边分数 → 生命 → 幸运果实，右边暂停键（和“畅快”标签）。
    // 挖孔 / 刘海所在的水平区间、屏幕圆角都会避开；左边一排放不下的元素挪到顶栏下面的第二行。
    static final float BAR_H = 34;
    private static final String[] LIVES_S = new String[C.MAX_LIVES + 1];
    private static final float[] LIVES_W = new float[C.MAX_LIVES + 1];

    static {
        for (int i = 0; i <= C.MAX_LIVES; i++) LIVES_S[i] = "× " + i;
    }

    // 屏幕挖孔（像素矩形，每 4 个一组）和左上 / 右上圆角（像素：圆心 x, 圆心 y, 半径），由 GameView 从 WindowInsets 里取
    private float[] cutPx = new float[0];
    private final float[] cornerPx = new float[6];
    // 换算成逻辑单位后：顶栏里被挡住的水平区间、左右边距
    private final float[] cutA = new float[8], cutB = new float[8];
    private int nCut;
    public float padL = 12, padR = 12;
    /** 暂停键中心（逻辑 x），点击判定用 */
    public float pauseCx = (float) C.W - 24;

    public void setInsets(float[] cutoutsPx, float[] cornersPx) {
        cutPx = cutoutsPx;
        System.arraycopy(cornersPx, 0, cornerPx, 0, 6);
        layoutHud();
    }

    private void layoutHud() {
        nCut = 0;
        for (int i = 0; i + 3 < cutPx.length && nCut < cutA.length; i += 4) {
            float l = v.toX(cutPx[i]), t = v.toY(cutPx[i + 1]), r = v.toX(cutPx[i + 2]), b = v.toY(cutPx[i + 3]);
            if (t >= BAR_H || b <= 0 || r <= 0 || l >= v.W) continue;
            cutA[nCut] = l - 5;
            cutB[nCut++] = r + 5;
        }
        // 圆角：内容最高到 y≈5，在这个高度上圆角边界往里收多少
        padL = 12;
        padR = 12;
        float yt = 5;
        if (cornerPx[2] > 0) {
            float cx = v.toX(cornerPx[0]), cy = v.toY(cornerPx[1]), r = cornerPx[2] / v.s;
            if (yt < cy) padL = Math.max(padL, cx - (float) Math.sqrt(Math.max(0, r * r - (cy - yt) * (cy - yt))) + 4);
        }
        if (cornerPx[5] > 0) {
            float cx = v.toX(cornerPx[3]), cy = v.toY(cornerPx[4]), r = cornerPx[5] / v.s;
            if (yt < cy) padR = Math.max(padR, v.W - (cx + (float) Math.sqrt(Math.max(0, r * r - (cy - yt) * (cy - yt)))) + 4);
        }
        padL = Math.min(padL, 40);
        padR = Math.min(padR, 40);
    }

    /** 从 x 往右找第一个放得下宽 w、不压到挖孔的位置 */
    private float fitRight(float x, float w) {
        for (int pass = 0; pass < 4; pass++) {
            boolean moved = false;
            for (int i = 0; i < nCut; i++) {
                if (x < cutB[i] && x + w > cutA[i]) {
                    x = cutB[i];
                    moved = true;
                }
            }
            if (!moved) break;
        }
        return x;
    }

    /** 右端在 xr、宽 w：往左找第一个不压到挖孔的位置，返回左端 */
    private float fitLeft(float xr, float w) {
        float x = xr - w;
        for (int pass = 0; pass < 4; pass++) {
            boolean moved = false;
            for (int i = 0; i < nCut; i++) {
                if (x < cutB[i] && x + w > cutA[i]) {
                    x = cutA[i] - w;
                    moved = true;
                }
            }
            if (!moved) break;
        }
        return x;
    }

    // 分数：直接写进 char 数组，不再每帧新建字符串
    private final char[] scoreBuf = new char[12];
    private int scoreLen = 1, hudScore = -1;
    private float scoreW;
    private int bestShown = -1;
    private String bestS = "";

    private void scoreChars(int score) {
        if (score == hudScore) return;
        hudScore = score;
        int n = Math.max(0, score), len = 0;
        do {
            scoreBuf[scoreBuf.length - 1 - len++] = (char) ('0' + n % 10);
            n /= 10;
        } while (n > 0 && len < scoreBuf.length);
        System.arraycopy(scoreBuf, scoreBuf.length - len, scoreBuf, 0, len);
        scoreLen = len;
        scoreW = pen.measure(scoreBuf, 0, len, 24, Pen.BOLD);
    }

    public void hud(Canvas c, int score, Game g) {
        Pen p = pen;
        float bh = BAR_H, cy = bh / 2;
        p.rectFill(v.x0, v.y0, v.x1 - v.x0, bh - v.y0, 0xa8bee1e8);
        p.rectFill(v.x0, bh - 1.2f, v.x1 - v.x0, 1.2f, 0xd95f96a5);

        // 右边：暂停键，左边挨着“畅快”标签
        float px = fitLeft(v.W - padR, 18) + 9;
        pauseCx = px;
        p.rr(px - 8, cy - 9, 5.5f, 18, 2, 0xff3d3d34);
        p.rr(px + 2.5f, cy - 9, 5.5f, 18, 2, 0xff3d3d34);
        float limit = px - 9 - 10;
        if (g.chill) {
            float tw = p.measure("畅快", 13, Pen.BOLD) + 12;
            float tx = fitLeft(limit, tw);
            p.rr(tx, cy - 10, tw, 20, 10, 0xe6ffc446);
            p.rrStroke(tx, cy - 10, tw, 20, 10, 0xcc965f14, 1.4f);
            p.text("畅快", tx + tw / 2, cy + 1, 13, 0xff6a3f0c, Paint.Align.CENTER, Pen.BOLD);
            limit = tx - 10;
        }

        // 左边：分数
        scoreChars(score);
        float x = fitRight(padL, scoreW);
        p.text(scoreBuf, 0, scoreLen, x, cy + 1, 24, 0xff3d3d34, Pen.BOLD);
        x += scoreW + 12;

        // 生命、幸运果实：顶栏放不下就放到第二行
        float lw = livesW(g), kw = luckW(g);
        boolean row2 = false;
        float x2 = padL + 4;
        if (lw > 0) {
            float lx = fitRight(x, lw);
            if (lx + lw <= limit) {
                drawLives(c, g, lx, cy);
                x = lx + lw + 10;
            } else {
                row2 = true;
            }
        }
        float kx = kw > 0 ? fitRight(x, kw) : 0;
        boolean luck2 = kw > 0 && (row2 || kx + kw > limit);
        if (row2 || luck2) {
            float w2 = (row2 ? lw : 0) + (luck2 ? kw : 0) + (row2 && luck2 ? 10 : 0);
            p.rr(padL - 2, bh + 3, w2 + 12, 26, 13, 0x8cbee1e8);
            float cy2 = bh + 16;
            if (row2) {
                drawLives(c, g, x2, cy2);
                x2 += lw + 10;
            }
            if (luck2) drawLuck(c, g, x2, cy2);
        }
        if (kw > 0 && !luck2) drawLuck(c, g, kx, cy);
    }

    private float livesW(Game g) {
        int n = g.lives;
        if (n <= 0) return g.reviveT < 0.6 ? 20 : 0;
        if (LIVES_W[n] == 0) LIVES_W[n] = pen.measure(LIVES_S[n], 16, Pen.BOLD);
        return 19 + LIVES_W[n];
    }

    /** 生命：小心形 + “× N”；拿到 / 用掉时心形蹦一下，用掉最后一次时心形碎掉淡出 */
    private void drawLives(Canvas c, Game g, float x, float cy) {
        Pen p = pen;
        int n = g.lives;
        if (n <= 0) {
            float u = (float) g.reviveT / 0.6f;
            p.bmp.setAlpha(Pen.clamp255(1 - u));
            sprites.hudHeart(c, x + 9, cy + 1 - u * 12, 1 + u, p.bmp);
            return;
        }
        float pop = (float) Math.min(g.lifeT, g.reviveT);
        float k = pop < 0.35f ? 1 + 0.5f * (float) Math.sin(pop / 0.35f * Math.PI) : 1;
        p.bmp.setAlpha(255);
        sprites.hudHeart(c, x + 8, cy + 1, k, p.bmp);
        p.text(LIVES_S[n], x + 19, cy + 1, 16, 0xffd8385e, Paint.Align.LEFT, Pen.BOLD);
    }

    private static final float LUCK_HUD_K = 0.8f;

    private float luckW(Game g) {
        if (g.luck) return 24;
        return g.luckT < 0.45 ? 24 : 0;
    }

    /** 幸运果实：果子随剩余时间慢慢变淡，最后 3 秒越闪越快；时间到了放大一下消失 */
    private void drawLuck(Canvas c, Game g, float x, float cy) {
        Pen p = pen;
        float cx = x + 12, y = cy + 2.5f;
        float a, k = 1;
        if (g.luck) {
            float left = (float) g.luckLeftT, T = (float) C.LUCK_T;
            if (left > 3) {
                a = 0.45f + 0.55f * clamp((left - 3) / (T - 3), 0, 1);
            } else {
                // 闪烁频率从 2Hz 加快到 6Hz：相位是频率对时间的积分
                float L = Math.max(0, left);
                float ph = 6 * (3 - L) - (2f / 3) * (9 - L * L);
                float b = 0.5f + 0.5f * (float) Math.cos(ph * TAU);
                a = 0.06f + 0.6f * b;
                k = 1 + 0.08f * b;
            }
            float pop = (float) g.luckT;
            if (pop < 0.4f) k *= 1 + 0.55f * (float) Math.sin(pop / 0.4f * Math.PI);
        } else {
            float u = (float) g.luckT / 0.45f;
            a = 0.5f * (1 - u);
            k = 1 + 0.7f * u;
        }
        p.bmp.setAlpha(Pen.clamp255(a));
        sprites.hudLuck(c, cx, y, LUCK_HUD_K * k, p.bmp);
    }

    /** 龙息火球：外焰 + 内焰 + 白芯，拖着一道火痕 */
    void fireball(float x, float y, float vx, float vy) {
        Pen p = pen;
        float v = (float) Math.hypot(vx, vy);
        float dx = v > 0 ? -vx / v : 0, dy = v > 0 ? -vy / v : 1;
        p.line(x, y, x + dx * 26, y + dy * 26, 0x99ff6a2a, 9);
        p.line(x, y, x + dx * 16, y + dy * 16, 0xccffc23a, 5);
        RadialGradient gl = Grad.glow(0xff7a2a);
        Grad.place(gl, x, y, 16, 16);
        p.circleShader(x, y, 16, gl, 0.8f);
        p.circle(x, y, 7, 0xffff6a2a);
        p.circle(x, y, 4.6f, 0xffffd04a);
        p.circle(x, y, 2.4f, 0xfffff8e0);
    }

    private LinearGradient heatL, heatR, heatB;

    /** 飞龙时屏幕两侧和底部烧起来的热浪边 */
    void heat(Canvas c, float st, float left) {
        Pen p = pen;
        float k = Math.min(1, st / 0.3f) * Math.min(1, Math.max(0, left) / 0.6f);
        if (k <= 0) return;
        float a = k * (0.75f + 0.25f * (float) Math.sin(clock * 9));
        float w = 34;
        if (heatL == null) {
            heatL = Grad.linear(0, 0, 1, 0, 0x99ff4a1e, 0x00ff4a1e);
            heatR = Grad.linear(0, 0, -1, 0, 0x99ff4a1e, 0x00ff4a1e);
            heatB = Grad.linear(0, 0, 0, -1, 0x80ff6a1e, 0x00ff6a1e);
        }
        Grad.place(heatL, v.x0, 0, w, 1);
        p.shade.setShader(heatL);
        p.shade.setAlpha(Pen.clamp255(a));
        c.drawRect(v.x0, v.y0, v.x0 + w, v.y1, p.shade);
        Grad.place(heatR, v.x1, 0, w, 1);
        p.shade.setShader(heatR);
        c.drawRect(v.x1 - w, v.y0, v.x1, v.y1, p.shade);
        Grad.place(heatB, 0, v.y1, 1, 60);
        p.shade.setShader(heatB);
        c.drawRect(v.x0, v.y1 - 60, v.x1, v.y1, p.shade);
    }

    // ---------- 整个场景 ----------
    /**
     * alpha：上一个物理步到当前物理步之间的插值比例；frozen：暂停 / 结算界面上画的静止场景（不抖屏、不闪光）。
     * 调用时画布在像素坐标下（View 的原始画布）。
     */
    public void draw(Canvas c, Game g, float alpha, boolean frozen, int shownScore, String perfText) {
        Pen p = pen;
        p.c = c;
        p.ga = 1;
        float a = clamp(alpha, 0, 1);
        float cam = ix(g.pCam, g.camY, a);
        float sh = frozen ? 0 : (float) g.shake;
        float shx = 0, shy = 0;
        if (sh > 0) {
            shx = (float) Math.sin(clock * 91) * sh;
            shy = (float) Math.cos(clock * 73) * sh * 0.8f;
        }
        background(c, cam, shx, shy);
        c.save();
        enterLogical(c);
        c.save();
        c.translate(shx, shy);
        c.clipRect(0, 0, v.W, v.H);
        c.translate(0, -cam);
        float top = cam - 90, bot = cam + v.H + 90;

        // 最高分记录线
        if (g.best > 0) {
            float ry = (float) (g.startY - g.best * 2.0);
            if (ry > top && ry < bot) {
                p.stroke.setPathEffect(DASH_BEST);
                p.stroke.setStrokeCap(Paint.Cap.BUTT);
                p.line(0, ry, v.W, ry, 0xbfd64848, 2);
                p.stroke.setPathEffect(null);
                p.stroke.setStrokeCap(Paint.Cap.ROUND);
                if (bestShown != g.best) {
                    bestShown = g.best;
                    bestS = "最高分 " + g.best;
                }
                p.text(bestS, v.W - 6, ry - 3 - 7, 13, 0xe6d64848, Paint.Align.RIGHT, Pen.BOLD);
            }
        }

        for (int i = 0, n = g.platforms.size(); i < n; i++) {
            Platform pl = g.platforms.get(i);
            if (pl.y > top && pl.y < bot) {
                float x = pl.vx != 0 ? ix(pl.px, pl.x, a) : (float) pl.x;
                platform(c, pl, x, clock);
            }
        }
        for (int i = 0, n = g.holes.size(); i < n; i++) {
            Hole h = g.holes.get(i);
            Art.hole(p, (float) h.x, (float) h.y, (float) h.r, (float) h.t);
        }
        effects(c, g.fx, false);
        Hero d = g.dd;
        float heroLookX = (float) d.x, heroLookY = (float) (d.y - 24 * C.HERO);
        for (int i = 0, n = g.monsters.size(); i < n; i++) {
            Monster m = g.monsters.get(i);
            if (m.y > top - 40 && m.y < bot + 40) Art.monster(p, m, ix(m.px, m.x, a), ix(m.py, m.y, a), heroLookX, heroLookY);
        }
        for (int i = 0, n = g.debris.size(); i < n; i++) {
            Debris q = g.debris.get(i);
            Art.debris(p, (float) q.x, (float) q.y, (float) q.w, (float) q.h, (float) q.rot);
        }
        for (int i = 0, n = g.bullets.size(); i < n; i++) {
            Bullet b = g.bullets.get(i);
            float bx = ix(b.px, b.x, a), by = ix(b.py, b.y, a);
            if (b.fire) fireball(bx, by, (float) b.vx, (float) b.vy);
            else p.circleInk(bx, by, 4.6f, 0xfffffdf0, 1.7f);
        }
        for (int i = 0, n = g.sparks.size(); i < n; i++) {
            Spark s = g.sparks.get(i);
            p.ga = clamp((float) (s.life / s.max), 0, 1);
            sprites.shape(c, Sprites.SH_STAR, (float) s.x, (float) s.y, 4 / Sprites.SH_R, 0, p.mul(0xffffd93b, 1), p.mul(INK, 1));
        }
        p.ga = 1;
        speedLines(d, ix(d.px, d.x, a), ix(d.py, d.y, a));
        doodler(d, a);
        effects(c, g.fx, true);
        c.restore();
        if (d.state == Hero.DRAGON) heat(c, (float) d.stateT, (float) (C.DRAGON_T - d.stateT));
        if (!frozen && g.flash > 0.004) p.rectFill(v.x0, v.y0, v.x1 - v.x0, v.y1 - v.y0, Pen.rgba(g.flashCol, clamp((float) g.flash, 0, 1)));
        letterbox(c);
        hud(c, shownScore, g);
        if (!frozen && perfText != null) perf(c, perfText);
        c.restore();
    }

    private final String[] perfLines = new String[4];

    void perf(Canvas c, String text) {
        Pen p = pen;
        int n = 0;
        int st = 0;
        for (int i = 0; i <= text.length() && n < perfLines.length; i++) {
            if (i == text.length() || text.charAt(i) == '\n') {
                perfLines[n++] = text.substring(st, i);
                st = i + 1;
            }
        }
        float tw = 0;
        for (int i = 0; i < n; i++) tw = Math.max(tw, p.measure(perfLines[i], 11, Pen.NORMAL));
        // 放在顶栏第二行（生命 / 幸运果实挤下来时用）下面
        p.rr(6, 70, tw + 10, 4 + 14 * n, 5, 0x9e28281e);
        for (int i = 0; i < n; i++) p.text(perfLines[i], 11, 73 + 14 * i + 7, 11, 0xffffffff, Paint.Align.LEFT, Pen.NORMAL);
    }
}
