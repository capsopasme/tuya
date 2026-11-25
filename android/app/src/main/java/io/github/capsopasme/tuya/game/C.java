package io.github.capsopasme.tuya.game;

/** 常量和物理参数（单位：逻辑像素 / 秒）。逻辑画面宽度固定 320，高度随屏幕比例在 480..780 之间。 */
public final class C {
    private C() {}

    public static final double W = 320;
    public static final double MIN_H = 480;
    public static final double MAX_H = 780;

    public static final double GRAVITY = 1400;
    public static final double JUMP_V = 730; // 普通平台起跳速度，跳高约 190px
    public static final double SPRING_V = 1100;
    public static final double TRAMP_V = 1350;
    public static final double STOMP_V = 800;
    public static final double MAX_FALL = 1100;
    public static final double MAX_VX = 300;
    public static final double ACCEL = 3200;
    public static final double PLAT_W = 58;
    public static final double PLAT_H = 14;
    public static final double HERO = 0.74; // 主角整体缩放（画面和碰撞框一起缩）
    public static final double FEET = 11; // 脚的半宽，用于平台碰撞
    public static final double PROP_V = 600;
    public static final double PROP_T = 2.7;
    public static final double JET_V = 820;
    public static final double JET_T = 3.6;
    public static final double WING_V = 1350; // 飞翼：比喷气背包快六成、飞得更久，一路撞碎怪物，无视黑洞和 UFO
    public static final double WING_T = 5.2;
    public static final double WING_VX = 1.25;
    public static final double SHIELD_T = 1.6; // 飞翼结束后的护盾时间
    public static final double DARK_V = 1900; // 紫黑飞翼：比白色飞翼再快四成，飞行中小海星整个旋转起来
    public static final double DARK_T = 5.0;
    public static final double DARK_VX = 1.35;
    public static final double DARK_SPIN = 15; // 旋转角速度（弧度/秒）
    public static final double DRAGON_V = 2350; // 变身飞龙：新的终极道具，比紫黑飞翼再快两成多、飞得久得多（约 8000 分）
    public static final double DRAGON_T = 7.7;
    public static final double DRAGON_HOVER = 0.28; // 变身后先悬停一下（巨龙从火里钻出来），再冲出去
    public static final double DRAGON_VX = 1.5;
    public static final double DRAGON_SHIELD_T = 2.4; // 变回小海星后的火焰护盾
    public static final double DRAGON_BREATH_CD = 0.11; // 龙息火球的间隔
    public static final double DRAGON_CAM_FRAC = 0.42; // 飞龙时镜头把它放得更靠下，前方看得更远
    public static final double DRAGON_CAM_K = 40;
    public static final double REVIVE_V = 1500; // 生命果实复活：从屏幕底下被弹回来的速度
    public static final double REVIVE_SHIELD_T = 2.6;
    public static final int MAX_LIVES = 9;
    public static final double LUCK_T = 25; // 幸运果实：接下来这么多秒内爆率改变（正常爬升约 2500~3000 分）
    public static final double GLIDE_T = 2.4; // 道具用完后的“慢落”：下落阶段最多持续这么久
    public static final double GLIDE_G = 0.4; // 慢落时的重力倍数
    public static final double GLIDE_FALL = 470; // 慢落时的最大下落速度
    public static final double LEAN = 0.2; // 空中身体朝移动方向倾斜的最大角度（弧度）
    public static final double LEAN_CHARGE = 0.42; // 落地蓄力时朝倾斜方向压下去的最大角度
    public static final double BULLET_V = 780;
    public static final double SHOOT_CD = 0.16;
    public static final double ITEM_REACH = 1.0; // 弹簧/蹦床触发范围：身体中心在平台范围内就算踩到
    public static final double BELLY_K = 380; // 肚子果冻弹簧：刚度
    public static final double BELLY_C = 5.2; // 阻尼
    public static final double BELLY_GAIN = 0.0105; // 弹跳瞬间速度突变 -> 肚子被甩出去的力度
    public static final double BELLY2_K = 650; // 肚皮：挂在肚子弹簧上的第二层弹簧
    public static final double BELLY2_C = 9;
    public static final double DIP_K = 650; // 落地时平台被压下去的弹簧
    public static final double DIP_C = 21;
    public static final double CAM_K = 15; // 镜头往上跟随的缓动速度
    public static final double CAM_TOP = 0.12; // 角色最高只能到屏幕上方 12% 处
    public static final double CAM_FRAC = 0.45; // 小海星最高只会到屏幕 45% 的位置，再往上就卷动

    // 畅快模式：每种道具 {出现所需分数, 每块可放道具的平台上出现的概率}
    public static final double[] CHILL_DRAGON = {3500, 0.006};
    public static final double[] CHILL_LIFE = {600, 0.007};
    public static final double[] CHILL_LUCK = {400, 0.008};
    public static final double[] CHILL_DARK = {2500, 0.008};
    public static final double[] CHILL_WINGS = {1500, 0.012};
    public static final double[] CHILL_JET = {600, 0.026};
    public static final double[] CHILL_PROP = {250, 0.04};
    public static final double[] CHILL_TRAMP = {0, 0.05};
    public static final double[] CHILL_SPRING = {0, 0.13};
    public static final double CHILL_HAZARD_FROM = 2000;
    public static final double CHILL_HAZARD_GAP = 1700;
    public static final double CHILL_HAZARD_P = 0.03;

    // 主角缩放后的身体尺寸。脚底是原点，身体中心在脚底上方 CENTER 处
    public static final double HS = HERO;
    public static final double BODY_H = 46 * HS;
    public static final double HALF_W = 15 * HS;
    public static final double CENTER = 24 * HS;
    public static final double MOUTH = 32 * HS;

    public static final double PI = Math.PI;
    public static final double TAU = Math.PI * 2;

    public static double clamp(double v, double a, double b) {
        return v < a ? a : v > b ? b : v;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 左右循环屏幕下 a 相对 b 的水平偏移（取最短路径） */
    public static double wrapDx(double a, double b) {
        double d = a - b;
        if (d > W / 2) d -= W;
        else if (d < -W / 2) d += W;
        return d;
    }
}
