package io.github.capsopasme.tuya.game;

/** 主角“小海星”的全部状态。姿态量（肚子、手臂、倾斜……）都是弹簧或指数平滑，渲染只读。 */
public final class Hero {
    // 状态
    public static final int NORMAL = 0, PROPELLER = 1, JETPACK = 2, WINGS = 3, DARKWINGS = 4,
            DEAD = 5, BLACKHOLE = 6, ABDUCTED = 7, FELL = 8, GONE = 9, DRAGON = 10, CLOAK = 11;
    // 慢落的颜色
    public static final int GL_GOLD = 0, GL_DARK = 1, GL_SKY = 2, GL_FIRE = 3, GL_LIFE = 4, GL_MOON = 5;
    // 护盾的颜色
    public static final int SHIELD_GOLD = 0, SHIELD_DARK = 1, SHIELD_LIFE = 2, SHIELD_FIRE = 3, SHIELD_MOON = 4;

    public static boolean flying(int st) {
        return (st >= PROPELLER && st <= DARKWINGS) || st == DRAGON || st == CLOAK;
    }

    public static boolean winged(int st) {
        return st == WINGS || st == DARKWINGS;
    }

    public double x, y, prevY, vx, vy, px, py;
    public int facing = 1;
    public int state = NORMAL;
    public double stateT, bounceT, shootT = 99, shootCd, aim = -Math.PI / 2;
    public double belly, bellyV, b2, b2V; // 肚子 / 肚皮两层弹簧
    public double look, armPose, stretch, spin, scale = 1;
    public double shield;
    public int shieldKind;
    public double lean, leanV, leanAir = 1, roll, rollV;
    public int spinDir = 1;
    public double axis, glide, glideK, fallK;
    public int glideCol = GL_GOLD;
    public double sx, sy, tx, ty, t;

    public void copyFrom(Hero o) {
        x = o.x; y = o.y; prevY = o.prevY; vx = o.vx; vy = o.vy; px = o.px; py = o.py;
        facing = o.facing; state = o.state; stateT = o.stateT; bounceT = o.bounceT; shootT = o.shootT;
        shootCd = o.shootCd; aim = o.aim; belly = o.belly; bellyV = o.bellyV; b2 = o.b2; b2V = o.b2V;
        look = o.look; armPose = o.armPose; stretch = o.stretch; spin = o.spin; scale = o.scale;
        shield = o.shield; shieldKind = o.shieldKind; lean = o.lean; leanV = o.leanV; leanAir = o.leanAir;
        roll = o.roll; rollV = o.rollV; spinDir = o.spinDir; axis = o.axis; glide = o.glide;
        glideK = o.glideK; fallK = o.fallK; glideCol = o.glideCol; sx = o.sx; sy = o.sy; tx = o.tx; ty = o.ty; t = o.t;
    }

    /** 菜单里用的假小海星：没有物理，用落地后的衰减余弦模拟肚子的 duang duang */
    public void fake(double x, double y, int facing, double vy, double bounceT, double t) {
        this.x = this.px = x;
        this.y = this.py = y;
        this.facing = facing;
        this.vy = vy;
        this.vx = 0;
        this.bounceT = bounceT;
        this.t = t;
        belly = 0.75 * Math.exp(-bounceT * 3.2) * Math.cos(bounceT * 19.5);
        b2 = 0.9 * Math.exp(-bounceT * 3.4) * Math.cos(bounceT * 19.5 - 0.9);
        look = facing * 0.7;
        armPose = Math.max(-1, Math.min(1, vy / 350));
        stretch = Math.max(-0.12, Math.min(0.16, -vy / 3500));
        state = NORMAL;
        shootT = 99;
        aim = -Math.PI / 2;
        spin = 0;
        scale = 1;
        lean = 0;
        roll = 0;
        leanAir = 1;
        shield = 0;
    }
}
