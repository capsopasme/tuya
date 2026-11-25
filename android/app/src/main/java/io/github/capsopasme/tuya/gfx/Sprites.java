package io.github.capsopasme.tuya.gfx;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import io.github.capsopasme.tuya.game.C;
import io.github.capsopasme.tuya.game.Entities;
import io.github.capsopasme.tuya.game.Entities.Item;

/**
 * 贴图缓存：不会变形的东西（平台、静止的道具、背景方格）在屏幕尺寸确定后按像素密度画进位图，
 * 再转成 GPU 常驻的硬件位图；之后每帧只是贴一个带纹理的矩形，不再细分圆角、描边和渐变。
 */
final class Sprites {
    private static final class Sp {
        Bitmap b;
        float x0, y0, w, h;
        int bw, bh;
    }

    // 粒子形状（只有透明度的贴图，画的时候用画笔颜色上色）：一份填色蒙版 + 一份墨线
    static final int SH_STAR = 0, SH_HEART = 1, SH_LEAF = 2, SH_SHARD = 3;
    /** 做贴图时的参考尺寸（Scene 里粒子的 sz 除以它就是缩放倍数） */
    static final float SH_R = 6;
    private final Sp[] shFill = new Sp[4], shInk = new Sp[4];
    private final Matrix sm = new Matrix();
    final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    // HUD 图标
    private Sp hudHeart, hudLuck;

    private final Sp[] plat = new Sp[4];
    private final Sp[] items = new Sp[4];
    private final Pen pen = new Pen();
    private final RectF dst = new RectF();
    private float k; // 每个单位多少像素

    // 背景方格
    Bitmap gridTile;
    BitmapShader gridShader;
    final Paint gridPaint = new Paint();
    final Matrix gridM = new Matrix();
    int gridN;

    void rebuild(float k) {
        if (Math.abs(k - this.k) < 1e-4f && plat[0] != null) return;
        this.k = k;
        for (int t = 0; t < 4; t++) {
            final int type = t;
            plat[t] = make(-2, -2, (float) C.PLAT_W + 4, (float) C.PLAT_H + 4, () -> Art.platShape(pen, type, (float) C.PLAT_W, (float) C.PLAT_H));
        }
        for (int t = 0; t < 4; t++) {
            final Item it = new Item(t, 0);
            float[] box = Art.ITEM_BOX[t];
            items[t] = make(box[0], box[1], box[2], box[3], () -> Art.item(pen, it, 0, 0, 0));
        }
        for (int t = 0; t < 4; t++) {
            final int kind = t;
            float m = SH_R * 2.6f;
            shFill[t] = makeAlpha(-m, -m, 2 * m, 2 * m, () -> shapePath(kind, true));
            shInk[t] = makeAlpha(-m, -m, 2 * m, 2 * m, () -> shapePath(kind, false));
        }
        hudHeart = make(-10, -11, 20, 21, () -> Art.lifeIcon(pen, 0, 0, 7.5f));
        hudLuck = make(-15, -20, 30, 34, () -> Art.luckFruit(pen, 9));
        // 方格：一个格子做成整数像素的小贴图平铺，一条线压在格子边界上（上下左右各一半）
        int n = Math.max(4, Math.round(20 * k));
        gridN = n;
        Bitmap tile = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas tc = new Canvas(tile);
        tc.drawColor(0xfff7f4e1);
        Paint lp = new Paint();
        lp.setColor(0xffc5dcd8);
        float lw = Math.max(1.3f, 0.5f * k);
        tc.drawRect(0, 0, n, lw / 2, lp);
        tc.drawRect(0, n - lw / 2, n, n, lp);
        tc.drawRect(0, 0, lw / 2, n, lp);
        tc.drawRect(n - lw / 2, 0, n, n, lp);
        gridTile = hw(tile);
        gridShader = new BitmapShader(gridTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        gridPaint.setShader(gridShader);
    }

    /** 一个格子多少个单位（整数像素取整后略有偏差，肉眼看不出） */
    float cell() {
        return gridN / k;
    }

    private static Bitmap hw(Bitmap b) {
        try {
            Bitmap h = b.copy(Bitmap.Config.HARDWARE, false);
            if (h != null) {
                b.recycle();
                return h;
            }
        } catch (RuntimeException ignored) {
        }
        b.prepareToDraw();
        return b;
    }

    private Sp make(float x0, float y0, float w, float h, Runnable draw) {
        Sp s = make0(x0, y0, w, h, draw);
        s.b = hw(s.b);
        return s;
    }

    private Sp make0(float x0, float y0, float w, float h, Runnable draw) {
        int bw = Math.max(1, (int) Math.ceil(w * k)), bh = Math.max(1, (int) Math.ceil(h * k));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(bw / w, bh / h);
        c.translate(-x0, -y0);
        pen.c = c;
        pen.ga = 1;
        draw.run();
        Sp s = new Sp();
        s.b = b;
        s.bw = bw;
        s.bh = bh;
        s.x0 = x0;
        s.y0 = y0;
        s.w = w;
        s.h = h;
        return s;
    }

    /** 粒子形状：fill=true 只画填色区域（白色），false 只画墨线 */
    private void shapePath(int kind, boolean fill) {
        android.graphics.Path path = pen.begin();
        float R = SH_R;
        float lw;
        if (kind == SH_STAR) {
            Pen.star(path, 0, 0, R * 1.5f);
            lw = 1.3f;
        } else if (kind == SH_HEART) {
            Art.heart(path, 0, 0, R * 1.25f);
            lw = 1.2f;
        } else if (kind == SH_LEAF) {
            Art.heart(path, 0, 0, R);
            path.moveTo(0, R);
            path.lineTo(0, R * 1.8f);
            lw = 1.1f;
        } else {
            path.moveTo(0, -R * 1.6f);
            path.lineTo(R * 0.8f, R * 0.4f);
            path.lineTo(-R * 0.5f, R * 1.1f);
            path.close();
            lw = 1;
        }
        if (fill) pen.fill(path, 0xffffffff);
        else pen.stroke(path, 0xffffffff, lw);
    }

    private Sp makeAlpha(float x0, float y0, float w, float h, Runnable draw) {
        Sp s = make0(x0, y0, w, h, draw);
        Bitmap a = s.b.extractAlpha();
        s.b.recycle();
        a.setHasMipMap(true); // 粒子经常缩得很小，用 mipmap 采样才不闪
        a.prepareToDraw();
        s.b = a;
        return s;
    }

    /**
     * 粒子：中心 (x, y)，scale 倍（相对 SH_R），转 rotDeg 度。fill = 填色（含透明度），ink = 墨线颜色（含透明度，0 = 不画）
     */
    void shape(Canvas c, int kind, float x, float y, float scale, float rotDeg, int fill, int ink) {
        Sp f = shFill[kind];
        sm.setScale(f.w / f.bw * scale, f.h / f.bh * scale);
        sm.postTranslate(f.x0 * scale, f.y0 * scale);
        if (rotDeg != 0) sm.postRotate(rotDeg);
        sm.postTranslate(x, y);
        if ((fill >>> 24) != 0) {
            tint.setColor(fill);
            c.drawBitmap(f.b, sm, tint);
        }
        if ((ink >>> 24) != 0) {
            tint.setColor(ink);
            c.drawBitmap(shInk[kind].b, sm, tint);
        }
    }

    /** HUD 图标：中心 (x, y)，缩放 scale，透明度由画笔决定 */
    void hudHeart(Canvas c, float x, float y, float scale, Paint p) {
        blitScaled(c, hudHeart, x, y, scale, p);
    }

    void hudLuck(Canvas c, float x, float y, float scale, Paint p) {
        blitScaled(c, hudLuck, x, y, scale, p);
    }

    private void blitScaled(Canvas c, Sp s, float x, float y, float k, Paint p) {
        dst.set(x + s.x0 * k, y + s.y0 * k, x + (s.x0 + s.w) * k, y + (s.y0 + s.h) * k);
        c.drawBitmap(s.b, null, dst, p);
    }

    private void blit(Canvas c, Sp s, float x, float y, Paint p) {
        dst.set(x + s.x0, y + s.y0, x + s.x0 + s.w, y + s.y0 + s.h);
        c.drawBitmap(s.b, null, dst, p);
    }

    /** 平台：(x, y) 是平台左上角 */
    void platform(Canvas c, int type, float x, float y, Paint p) {
        blit(c, plat[type], x, y, p);
    }

    /** 静止的道具：(x, y) 是道具锚点 */
    boolean item(Canvas c, int type, float x, float y, Paint p) {
        if (type > Entities.I_JET) return false;
        blit(c, items[type], x, y, p);
        return true;
    }
}
