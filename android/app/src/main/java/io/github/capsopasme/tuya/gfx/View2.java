package io.github.capsopasme.tuya.gfx;

import io.github.capsopasme.tuya.game.C;

/**
 * 逻辑画面到屏幕像素的映射：宽度固定 320 个单位，高度随屏幕比例（长屏铺满，超出范围才留边）。
 * s = 每个单位多少像素；ox, oy = 游戏区域左上角的像素位置；x0..x1 / y0..y1 = 整个屏幕覆盖的单位范围。
 */
public final class View2 {
    public float W = (float) C.W, H = (float) C.MIN_H, s = 1, ox, oy, x0, x1, y0, y1;
    public int pw, ph;

    public void set(int pw, int ph) {
        this.pw = pw;
        this.ph = ph;
        float aspect = (float) ph / Math.max(1, pw);
        H = aspect >= 1.5f ? Math.min((float) C.MAX_H, Math.round(W * aspect)) : (float) C.MIN_H;
        s = Math.min(pw / W, ph / H);
        ox = (pw - W * s) / 2;
        oy = (ph - H * s) / 2;
        x0 = -ox / s;
        x1 = W + ox / s;
        y0 = -oy / s;
        y1 = H + oy / s;
    }

    public float toX(float px) {
        return (px - ox) / s;
    }

    public float toY(float py) {
        return (py - oy) / s;
    }
}
