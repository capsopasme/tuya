package io.github.capsopasme.tuya.game;

/** 纯视觉特效（尘土、冲击波、粒子、残影、拟声字……）。对象池复用，游戏过程中不再分配。 */
public final class Fx {
    public static final int PUFF = 0, RING = 1, SHOCK = 2, PART = 3, GLOW = 4, BEAM = 5, RAYS = 6, TEXT = 7,
            GHOST = 8, FEATHER = 9, SPIRAL = 10, FIRE = 11, STREAK = 12;
    // 粒子形状
    public static final int S_DOT = 0, S_STAR = 1, S_FIRE = 2, S_HEART = 3, S_LEAF = 4, S_SHARD = 5;

    public int k;
    public double x, y, vx, vy, r, life, max, delay;
    public double vr, drag, w, h, flat, g, rot, rot0, ph, a0, a1, R, spin, size;
    public int n, shape;
    public int col; // 0xRRGGBB
    public boolean dark;
    public String txt;
    public final Hero d = new Hero(); // 残影用的姿态快照

    void clear() {
        x = y = vx = vy = r = life = max = delay = 0;
        vr = drag = w = h = g = rot = rot0 = ph = a0 = a1 = R = spin = size = 0;
        flat = 0.3;
        n = shape = col = 0;
        dark = false;
        txt = null;
    }

    public static boolean front(int k) {
        return k == RAYS || k == PART || k == FIRE || k == STREAK || k == TEXT;
    }
}
