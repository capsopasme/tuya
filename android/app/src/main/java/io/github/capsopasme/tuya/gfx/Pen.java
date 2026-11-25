package io.github.capsopasme.tuya.gfx;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * 画笔：在 android.graphics.Canvas 上提供和旧版 Canvas2D 代码差不多的手感（填充 + 墨线描边、全局透明度），
 * 所有 Paint / Path / RectF 都预先建好、反复使用，每帧不分配对象。
 */
public final class Pen {
    public static final int INK = 0xff2d2d26;

    public Canvas c;
    /** 全局透明度（相当于 Canvas2D 的 globalAlpha），乘进每次绘制的颜色里 */
    public float ga = 1f;

    public final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    public final Paint textStroke = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    public final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    public final Path path = new Path();
    public final Path path2 = new Path();
    public final RectF rect = new RectF();

    public static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    public static final Typeface NORMAL = Typeface.DEFAULT;
    public static final Typeface BLACK = Typeface.create(Typeface.DEFAULT, 900, false);

    public Pen() {
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        shade.setStyle(Paint.Style.FILL);
        text.setStyle(Paint.Style.FILL);
        text.setTypeface(BOLD);
        textStroke.setStyle(Paint.Style.STROKE);
        textStroke.setStrokeJoin(Paint.Join.ROUND);
        textStroke.setTypeface(BOLD);
    }

    /** 0xAARRGGBB 的透明度再乘上 k（和全局透明度） */
    public int mul(int argb, float k) {
        float a = ((argb >>> 24) / 255f) * k * ga;
        int ai = a <= 0 ? 0 : a >= 1 ? 255 : (int) (a * 255f + 0.5f);
        return (argb & 0x00ffffff) | (ai << 24);
    }

    /** 0xRRGGBB + 透明度（0..1） */
    public static int rgba(int rgb, float a) {
        int ai = a <= 0 ? 0 : a >= 1 ? 255 : (int) (a * 255f + 0.5f);
        return (rgb & 0x00ffffff) | (ai << 24);
    }

    // ---------- 路径 ----------
    public Path begin() {
        path.rewind();
        return path;
    }

    public void roundRect(Path p, float x, float y, float w, float h, float r) {
        rect.set(x, y, x + w, y + h);
        p.addRoundRect(rect, r, r, Path.Direction.CW);
    }

    public void oval(Path p, float cx, float cy, float rx, float ry) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        p.addOval(rect, Path.Direction.CW);
    }

    /** 十角星（五个尖） */
    public static void star(Path p, float x, float y, float r) {
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + (i * Math.PI) / 5;
            float rad = i % 2 == 0 ? r : r * 0.45f;
            float px = x + (float) Math.cos(a) * rad, py = y + (float) Math.sin(a) * rad;
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.close();
    }

    // ---------- 绘制 ----------
    public void fill(Path p, int argb) {
        fill.setColor(mul(argb, 1));
        c.drawPath(p, fill);
    }

    public void stroke(Path p, int argb, float w) {
        stroke.setColor(mul(argb, 1));
        stroke.setStrokeWidth(w);
        c.drawPath(p, stroke);
    }

    /** 填色后描一圈墨线（旧版的 fillStroke） */
    public void fillInk(Path p, int argb, float lw) {
        fill(p, argb);
        stroke(p, INK, lw);
    }

    public void fillShader(Path p, Shader s, float alpha) {
        shade.setShader(s);
        shade.setAlpha(clamp255(alpha * ga));
        c.drawPath(p, shade);
    }

    public void circle(float x, float y, float r, int argb) {
        fill.setColor(mul(argb, 1));
        c.drawCircle(x, y, r, fill);
    }

    public void circleStroke(float x, float y, float r, int argb, float w) {
        stroke.setColor(mul(argb, 1));
        stroke.setStrokeWidth(w);
        c.drawCircle(x, y, r, stroke);
    }

    public void circleInk(float x, float y, float r, int argb, float lw) {
        circle(x, y, r, argb);
        circleStroke(x, y, r, INK, lw);
    }

    public void circleShader(float x, float y, float r, Shader s, float alpha) {
        shade.setShader(s);
        shade.setAlpha(clamp255(alpha * ga));
        c.drawCircle(x, y, r, shade);
    }

    public void ovalFill(float cx, float cy, float rx, float ry, int argb) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        fill.setColor(mul(argb, 1));
        c.drawOval(rect, fill);
    }

    public void ovalStroke(float cx, float cy, float rx, float ry, int argb, float w) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        stroke.setColor(mul(argb, 1));
        stroke.setStrokeWidth(w);
        c.drawOval(rect, stroke);
    }

    public void ovalInk(float cx, float cy, float rx, float ry, int argb, float lw) {
        ovalFill(cx, cy, rx, ry, argb);
        ovalStroke(cx, cy, rx, ry, INK, lw);
    }

    public void ovalShader(float cx, float cy, float rx, float ry, Shader s, float alpha) {
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        shade.setShader(s);
        shade.setAlpha(clamp255(alpha * ga));
        c.drawOval(rect, shade);
    }

    public void rr(float x, float y, float w, float h, float r, int argb) {
        rect.set(x, y, x + w, y + h);
        fill.setColor(mul(argb, 1));
        c.drawRoundRect(rect, r, r, fill);
    }

    public void rrStroke(float x, float y, float w, float h, float r, int argb, float lw) {
        rect.set(x, y, x + w, y + h);
        stroke.setColor(mul(argb, 1));
        stroke.setStrokeWidth(lw);
        c.drawRoundRect(rect, r, r, stroke);
    }

    public void rrInk(float x, float y, float w, float h, float r, int argb, float lw) {
        rr(x, y, w, h, r, argb);
        rrStroke(x, y, w, h, r, INK, lw);
    }

    public void rrShader(float x, float y, float w, float h, float r, Shader s, float alpha) {
        rect.set(x, y, x + w, y + h);
        shade.setShader(s);
        shade.setAlpha(clamp255(alpha * ga));
        c.drawRoundRect(rect, r, r, shade);
    }

    public void line(float x0, float y0, float x1, float y1, int argb, float w) {
        stroke.setColor(mul(argb, 1));
        stroke.setStrokeWidth(w);
        c.drawLine(x0, y0, x1, y1, stroke);
    }

    public void rectFill(float x, float y, float w, float h, int argb) {
        fill.setColor(mul(argb, 1));
        c.drawRect(x, y, x + w, y + h, fill);
    }

    // ---------- 文字 ----------
    public void text(String s, float x, float y, float size, int argb, Paint.Align align, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setTextAlign(align);
        text.setColor(mul(argb, 1));
        c.drawText(s, x, y + middle(text), text);
    }

    /** char 数组版（左对齐），分数这类每帧都变的数字不用新建字符串 */
    public void text(char[] s, int off, int len, float x, float y, float size, int argb, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(mul(argb, 1));
        c.drawText(s, off, len, x, y + middle(text), text);
    }

    public float measure(char[] s, int off, int len, float size, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        return text.measureText(s, off, len);
    }

    /** 带粗墨边的字（拟声字、标题） */
    public void inkText(String s, float x, float y, float size, int argb, float lw, Typeface tf) {
        textStroke.setTypeface(tf);
        textStroke.setTextSize(size);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setStrokeWidth(lw);
        textStroke.setColor(mul(INK, 1));
        float dy = middle(textStroke);
        c.drawText(s, x, y + dy, textStroke);
        text.setTypeface(tf);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(mul(argb, 1));
        c.drawText(s, x, y + dy, text);
    }

    public float measure(String s, float size, Typeface tf) {
        text.setTypeface(tf);
        text.setTextSize(size);
        return text.measureText(s);
    }

    private final Paint.FontMetrics fm = new Paint.FontMetrics();

    /** 让 y 成为文字的垂直中线（Canvas2D 的 textBaseline = middle） */
    private float middle(Paint p) {
        p.getFontMetrics(fm);
        return -(fm.ascent + fm.descent) / 2f;
    }

    public static int clamp255(float a) {
        return a <= 0 ? 0 : a >= 1 ? 255 : (int) (a * 255f + 0.5f);
    }

    public static float clamp(float v, float a, float b) {
        return v < a ? a : v > b ? b : v;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
