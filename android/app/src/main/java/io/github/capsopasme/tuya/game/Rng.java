package io.github.capsopasme.tuya.game;

/**
 * 关卡生成用的可设种子随机数（mulberry32，和旧版 JS 实现逐位一致，便于测试复现）。
 * 纯视觉特效另用 {@link #fx()}，不消耗这个序列。
 */
public final class Rng {
    private int a;

    public Rng(int seed) {
        a = seed;
    }

    public void seed(int s) {
        a = s;
    }

    public double next() {
        a = a + 0x6d2b79f5;
        int t = (a ^ (a >>> 15)) * (1 | a);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
    }

    public double range(double lo, double hi) {
        return lo + next() * (hi - lo);
    }

    // ---- 特效用的快速随机数（xorshift，不影响关卡） ----
    private static long fxState = 0x9E3779B97F4A7C15L ^ System.nanoTime();

    public static double fx() {
        long x = fxState;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        fxState = x;
        return (x >>> 11) * 0x1.0p-53;
    }
}
