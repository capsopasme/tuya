package io.github.capsopasme.tuya.gfx;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** 渐变的小工具：渐变对象都在第一次用到时建好缓存，之后每帧只改局部矩阵。 */
public final class Grad {
    private Grad() {}

    public static LinearGradient linear(float x0, float y0, float x1, float y1, int[] colors, float[] stops) {
        return new LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP);
    }

    public static LinearGradient linear(float x0, float y0, float x1, float y1, int c0, int c1) {
        return new LinearGradient(x0, y0, x1, y1, c0, c1, Shader.TileMode.CLAMP);
    }

    public static RadialGradient radial(float cx, float cy, float r, int[] colors, float[] stops) {
        return new RadialGradient(cx, cy, r, colors, stops, Shader.TileMode.CLAMP);
    }

    /** 两个圆之间的锥形渐变（等价于 Canvas2D 的 createRadialGradient(x0,y0,r0,x1,y1,r1)） */
    public static RadialGradient conical(float x0, float y0, float r0, float x1, float y1, float r1, int[] colors, float[] stops) {
        long[] cl = new long[colors.length];
        for (int i = 0; i < colors.length; i++) cl[i] = Color.pack(colors[i]);
        return new RadialGradient(x0, y0, r0, x1, y1, r1, cl, stops, Shader.TileMode.CLAMP);
    }

    private static final Matrix M = new Matrix();

    /** 把单位渐变（中心在原点、半径 1）挪到 (x, y)，缩放到 sx, sy */
    public static void place(Shader s, float x, float y, float sx, float sy) {
        M.setScale(sx, sy);
        M.postTranslate(x, y);
        s.setLocalMatrix(M);
    }

    /** 同 place，再绕 (x, y) 转 deg 度 */
    public static void placeRot(Shader s, float x, float y, float sx, float sy, float deg) {
        M.setScale(sx, sy);
        M.postRotate(deg);
        M.postTranslate(x, y);
        s.setLocalMatrix(M);
    }

    /** 按颜色缓存的单位径向光晕：中心不透明，边缘透明（透明度由画笔控制） */
    private static final int[] glowKeys = new int[48];
    private static final RadialGradient[] glowVals = new RadialGradient[48];
    private static int glowN;

    public static RadialGradient glow(int rgb) {
        for (int i = 0; i < glowN; i++) if (glowKeys[i] == rgb) return glowVals[i];
        RadialGradient g = radial(0, 0, 1, new int[]{0xff000000 | rgb, rgb & 0xffffff}, null);
        if (glowN < glowKeys.length) {
            glowKeys[glowN] = rgb;
            glowVals[glowN++] = g;
        }
        return g;
    }

    /** 按颜色缓存的单位竖直渐变：y=0 不透明，y=-1 透明（光柱用） */
    private static final int[] beamKeys = new int[32];
    private static final LinearGradient[] beamVals = new LinearGradient[32];
    private static int beamN;

    public static LinearGradient beam(int rgb) {
        for (int i = 0; i < beamN; i++) if (beamKeys[i] == rgb) return beamVals[i];
        LinearGradient g = linear(0, 0, 0, -1, 0xff000000 | rgb, rgb & 0xffffff);
        if (beamN < beamKeys.length) {
            beamKeys[beamN] = rgb;
            beamVals[beamN++] = g;
        }
        return g;
    }
}
