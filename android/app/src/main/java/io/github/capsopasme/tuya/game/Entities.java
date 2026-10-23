package io.github.capsopasme.tuya.game;

/** 游戏里的各种实体。全部是可变的普通对象（public 字段），渲染直接读取。 */
public final class Entities {
    private Entities() {}

    // 平台类型
    public static final int P_NORMAL = 0, P_MOVING = 1, P_BROWN = 2, P_WHITE = 3;
    // 道具类型
    public static final int I_SPRING = 0, I_TRAMP = 1, I_PROP = 2, I_JET = 3, I_WINGS = 4, I_DARK = 5;
    // 怪物
    public static final int M_BLOB = 0, M_FLYER = 1, M_UFO = 2;

    static final double[][] ITEM_SIZE = {
            {18, 14}, // spring
            {42, 10}, // tramp
            {24, 24}, // prop
            {26, 32}, // jet
            {36, 30}, // wings
            {38, 32}, // dark
    };

    public static final class Item {
        public int type;
        public double ox, w, h, t;
        public boolean used, fired;

        public Item(int type, double ox) {
            this.type = type;
            this.ox = ox;
            this.w = ITEM_SIZE[type][0];
            this.h = ITEM_SIZE[type][1];
        }
    }

    public static final class Platform {
        public int type;
        public double x, y, w = C.PLAT_W, h = C.PLAT_H, vx, fade, dip, dipV, px;
        public boolean dead;
        public Item item;
    }

    public static final class Monster {
        public int kind;
        public double x, y, x0, y0, t, flash, vx, vy, rot, w, h, amp, sp, px, py;
        public int hp;
        public boolean dead;
    }

    public static final class Hole {
        public double x, y, r, t;

        public Hole(double x, double y, double r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }
    }

    public static final class Bullet {
        public double x, y, vx, vy, life, px, py;
    }

    public static final class Debris {
        public double x, y, w, h, vx, vy, rot, vr;
    }

    public static final class Spark {
        public double x, y, vx, vy, life, max;
    }
}
