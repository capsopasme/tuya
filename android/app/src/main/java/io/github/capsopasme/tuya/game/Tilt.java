package io.github.capsopasme.tuya.game;

/** 重力感应 -> 左右速度比例（-1..1）。读数来自加速度计（app 层写入），这里只做滤波和响应曲线。 */
public final class Tilt {
    private static final double DEAD = 1.2; // 死区（度）
    public static final double FULL = 17; // 灵敏度 1 时，歪到多少度就是满速

    public double raw; // 最新读数（度，向右歪为正）
    public boolean has; // 收到过读数没有
    public double tilt; // 经过时间滤波后的倾斜角
    public double sens = 1;

    public void setRaw(double deg) {
        if (Double.isNaN(deg)) return;
        if (!has) tilt = deg;
        raw = deg;
        has = true;
    }

    /** 由重力加速度的 X 分量换算倾斜角（安卓上手机向右歪时 x 为负） */
    public static double accelToDeg(double ax) {
        return Math.toDegrees(Math.asin(C.clamp(-ax / 9.80665, -1, 1)));
    }

    /** 切回应用 / 开局 / 继续游戏时：滤波值立刻对齐当前读数，不留旧姿势的“尾巴” */
    public void snap() {
        if (has) tilt = raw;
    }

    /** 倾斜角 -> 速度比例：死区之外先缓后快，两端平滑 */
    public double response(double deg) {
        double full = FULL / sens;
        double n = C.clamp((Math.abs(deg) - DEAD) / (full - DEAD), 0, 1);
        double e = n * n * (3 - 2 * n);
        return Math.signum(deg) * (0.55 * n + 0.45 * e);
    }

    /** 每帧调用一次：时间常数 40ms 的一阶滤波，滤掉手抖又不拖手 */
    public double axis(double dt) {
        if (!has) return 0;
        tilt += (raw - tilt) * (1 - Math.exp(-dt / 0.04));
        return response(tilt);
    }
}
