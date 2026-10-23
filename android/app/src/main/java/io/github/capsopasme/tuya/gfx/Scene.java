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
    }

    public boolean has3d() {
        return hero.h3 != null && hero.h3.ok();
    }

    public void resize(int pw, int ph) {
        v.set(pw, ph);
        sprites.rebuild(v.s);
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
            float edge = (Hero.winged(d.state) ? 84 : 52) * HeroArt.HS;
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
                    if (f.shape == Fx.S_STAR) {
                        c.save();
                        c.translate(fx, fy);
                        c.rotate(deg((float) f.rot));
                        Path path = p.begin();
                        Pen.star(path, 0, 0, sz * 1.5f);
                        p.fillInk(path, 0xff000000 | f.col, 1.3f);
                        c.restore();
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
        boolean dark = d.state == Hero.DARKWINGS;
        boolean wings = dark || d.state == Hero.WINGS;
        double t = clock * (dark ? 2100 : wings ? 1600 : 1100);
        int n = dark ? 13 : wings ? 11 : 7;
        float span = wings ? 12 : 13 * HeroArt.HS;
        int col = dark ? 0x8c46e6 : wings ? 0xc89628 : 0x466e6e;
        for (int i = 0; i < n; i++) {
            float lx = x + (i - (n - 1) / 2f) * span + (float) Math.sin(i * 12.9) * 4;
            float len = (dark ? 52 : wings ? 40 : 26) + ((i * 37) % 22);
            float off = (float) ((t + i * 53) % 70);
            pen.line(lx, y + 4 + off, lx, y + 4 + off + len, Pen.rgba(col, 0.32f * k * (1 - off / 70)), 2);
        }
    }

    // ---------- HUD ----------
    private int hudScore = -1;
    private String hudScoreS = "0";
    private int bestShown = -1;
    private String bestS = "";

    public void hud(Canvas c, int score, boolean chill) {
        Pen p = pen;
        float bh = 34;
        p.rectFill(0, 0, v.W, bh, 0xa8bee1e8);
        p.rectFill(0, bh - 1.2f, v.W, 1.2f, 0xd95f96a5);
        if (score != hudScore) {
            hudScore = score;
            hudScoreS = Integer.toString(score);
        }
        p.text(hudScoreS, 12, bh / 2 + 1, 24, 0xff3d3d34, Paint.Align.LEFT, Pen.BOLD);
        if (chill) {
            float tw = p.measure("畅快", 13, Pen.BOLD) + 12;
            float tx = v.W - 48 - tw;
            p.rr(tx, bh / 2 - 10, tw, 20, 10, 0xe6ffc446);
            p.rrStroke(tx, bh / 2 - 10, tw, 20, 10, 0xcc965f14, 1.4f);
            p.text("畅快", tx + tw / 2, bh / 2 + 1, 13, 0xff6a3f0c, Paint.Align.CENTER, Pen.BOLD);
        }
        float px = v.W - 24, py = bh / 2;
        p.rr(px - 8, py - 9, 5.5f, 18, 2, 0xff3d3d34);
        p.rr(px + 2.5f, py - 9, 5.5f, 18, 2, 0xff3d3d34);
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
            p.circleInk(ix(b.px, b.x, a), ix(b.py, b.y, a), 4.6f, 0xfffffdf0, 1.7f);
        }
        for (int i = 0, n = g.sparks.size(); i < n; i++) {
            Spark s = g.sparks.get(i);
            p.ga = clamp((float) (s.life / s.max), 0, 1);
            Path path = p.begin();
            Pen.star(path, (float) s.x, (float) s.y, 6);
            p.fillInk(path, 0xffffd93b, 1.2f);
        }
        p.ga = 1;
        speedLines(d, ix(d.px, d.x, a), ix(d.py, d.y, a));
        doodler(d, a);
        effects(c, g.fx, true);
        c.restore();
        if (!frozen && g.flash > 0.004) p.rectFill(v.x0, v.y0, v.x1 - v.x0, v.y1 - v.y0, Pen.rgba(g.flashCol, clamp((float) g.flash, 0, 1)));
        letterbox(c);
        hud(c, shownScore, g.chill);
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
        p.rr(6, 38, tw + 10, 4 + 14 * n, 5, 0x9e28281e);
        for (int i = 0; i < n; i++) p.text(perfLines[i], 11, 41 + 14 * i + 7, 11, 0xffffffff, Paint.Align.LEFT, Pen.NORMAL);
    }
}
