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
    }

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
        int bw = Math.max(1, (int) Math.ceil(w * k)), bh = Math.max(1, (int) Math.ceil(h * k));
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(bw / w, bh / h);
        c.translate(-x0, -y0);
        pen.c = c;
        pen.ga = 1;
        draw.run();
        Sp s = new Sp();
        s.b = hw(b);
        s.x0 = x0;
        s.y0 = y0;
        s.w = w;
        s.h = h;
        return s;
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
