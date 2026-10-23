package io.github.capsopasme.tuya.game;

import static io.github.capsopasme.tuya.game.C.CENTER;
import static io.github.capsopasme.tuya.game.C.HS;
import static io.github.capsopasme.tuya.game.C.PI;
import static io.github.capsopasme.tuya.game.C.TAU;
import static io.github.capsopasme.tuya.game.C.W;
import static io.github.capsopasme.tuya.game.C.clamp;
import static io.github.capsopasme.tuya.game.C.lerp;
import static io.github.capsopasme.tuya.game.C.wrapDx;
import static io.github.capsopasme.tuya.game.Entities.*;
import static io.github.capsopasme.tuya.game.Hero.*;

import java.util.ArrayList;

/**
 * 游戏逻辑：不依赖安卓，可以在普通 JVM 里直接跑（见 {@link Sim}）。
 * 物理固定按 1/120 秒一步走（由调用方保证），渲染在两步之间插值。
 */
public final class Game {
    public static final int PLAY = 0, FALLING = 1, OVER = 2;

    public final Rng rng;
    public double H = C.MIN_H;
    public int best;
    public boolean chill; // 畅快模式：要在 reset() 之前设好

    // 音效事件队列
    public final int[] events = new int[64];
    public int nEvents;

    public double t;
    public int phase;
    public double phaseT, camY, pCam, maxH;
    public int score;
    public final ArrayList<Platform> platforms = new ArrayList<>();
    public final ArrayList<Monster> monsters = new ArrayList<>();
    public final ArrayList<Hole> holes = new ArrayList<>();
    public final ArrayList<Bullet> bullets = new ArrayList<>();
    public final ArrayList<Debris> debris = new ArrayList<>();
    public final ArrayList<Spark> sparks = new ArrayList<>();
    public final ArrayList<Fx> fx = new ArrayList<>(400);
    private final ArrayList<Fx> fxPool = new ArrayList<>(400);
    public double shake, flash;
    public int flashCol = 0xffffff;
    double ghostT, ghostCd, trailT, whirT;
    public double startY, topY;
    double lastHazardY, lastX;
    public final Hero dd = new Hero();

    // 射击队列（点击屏幕时加入，下一步物理里处理）
    private final double[] shotX = new double[16], shotY = new double[16];
    private final boolean[] shotT = new boolean[16];
    private int nShots;

    public Game(Rng rng) {
        this.rng = rng;
        reset();
    }

    public void sfx(int id) {
        if (nEvents < events.length) events[nEvents++] = id;
    }

    /** 点击屏幕（逻辑坐标，y 为屏幕坐标）：射击方向由游戏自动瞄准怪物，没有怪物时朝点击处 */
    public void shootAt(double x, double y) {
        if (nShots < shotX.length) {
            shotX[nShots] = x;
            shotY[nShots] = y;
            shotT[nShots++] = true;
        }
    }

    public void shootUp() {
        if (nShots < shotX.length) {
            shotX[nShots] = 0;
            shotY[nShots] = -1;
            shotT[nShots++] = false;
        }
    }

    public void clearShots() {
        nShots = 0;
    }

    double rand(double a, double b) {
        return rng.range(a, b);
    }

    public void reset() {
        t = 0;
        phase = PLAY;
        phaseT = 0;
        camY = 0;
        maxH = 0;
        score = 0;
        platforms.clear();
        monsters.clear();
        holes.clear();
        bullets.clear();
        debris.clear();
        sparks.clear();
        for (int i = 0; i < fx.size(); i++) fxPool.add(fx.get(i));
        fx.clear();
        shake = 0;
        flash = 0;
        flashCol = 0xffffff;
        ghostT = ghostCd = trailT = whirT = 0;
        double baseY = H - 56;
        startY = baseY;
        topY = baseY;
        lastHazardY = baseY + 700;
        lastX = W / 2;
        addPlatform(P_NORMAL, W / 2, baseY);
        Hero d = dd;
        d.copyFrom(new Hero());
        d.x = W / 2;
        d.y = baseY;
        d.prevY = baseY;
        d.vy = -C.JUMP_V;
        genUntil(camY - 220);
        nEvents = 0;
        nShots = 0;
        snapshot();
    }

    // ---------- 关卡生成 ----------
    public double scoreAtY(double y) {
        return Math.max(0, (startY - y) / 2);
    }

    double diffAt(double sc) {
        return clamp(sc / 12000, 0, 1);
    }

    public Platform addPlatform(int type, double x, double y) {
        Platform p = new Platform();
        p.type = type;
        p.x = x;
        p.y = y;
        if (type == P_MOVING) {
            p.vx = (rng.next() < 0.5 ? -1 : 1) * rand(50, 85 + 45 * diffAt(scoreAtY(y)));
        }
        platforms.add(p);
        return p;
    }

    public Monster makeMonster(int kind, double x, double y) {
        Monster m = new Monster();
        m.kind = kind;
        m.x = m.x0 = x;
        m.y = m.y0 = y;
        m.t = rand(0, 6);
        if (kind == M_BLOB) {
            m.w = 52;
            m.h = 44;
            m.hp = 1;
            m.amp = rng.next() < 0.5 ? 0 : rand(18, 38);
            m.sp = rand(1.2, 2.2);
        } else if (kind == M_FLYER) {
            m.w = 54;
            m.h = 36;
            m.hp = 2;
            m.vx = (rng.next() < 0.5 ? -1 : 1) * rand(50, 90);
        } else {
            m.w = 74;
            m.h = 34;
            m.hp = 3;
            m.vx = (rng.next() < 0.5 ? -1 : 1) * rand(30, 60);
        }
        return m;
    }

    public void genUntil(double limitY) {
        while (topY > limitY) genRow();
    }

    private static final int K_HOLE = 3;
    private final int[] kinds = new int[5];

    public void genRow() {
        double prevTop = topY;
        double d = diffAt(scoreAtY(prevTop));
        double gap = rand(lerp(44, 82, d), lerp(72, 138, d));
        double y = prevTop - gap;
        topY = y;
        double sc = scoreAtY(y);
        double half = C.PLAT_W / 2;

        // 怪物 / 黑洞（两次之间至少隔一段距离，且主平台会避开它；畅快模式更晚、更稀）
        double hzFrom = chill ? C.CHILL_HAZARD_FROM : 1100;
        double hzGap = chill ? C.CHILL_HAZARD_GAP : 650;
        double hzP = chill ? C.CHILL_HAZARD_P : 0.06;
        boolean avoid = false;
        double avoidX = 0;
        if (sc > hzFrom && lastHazardY - y > hzGap && rng.next() < hzP) {
            int nk = 0;
            kinds[nk++] = M_BLOB;
            if (sc > 2200) kinds[nk++] = M_FLYER;
            if (sc > 3200) kinds[nk++] = M_UFO;
            if (sc > 3800) {
                kinds[nk++] = K_HOLE;
                kinds[nk++] = K_HOLE;
            }
            int kind = kinds[(int) Math.floor(rng.next() * nk)];
            double hx = rand(50, W - 50);
            avoid = true;
            avoidX = hx;
            lastHazardY = y;
            if (kind == K_HOLE) holes.add(new Hole(hx, y - 28, 28));
            else monsters.add(makeMonster(kind, hx, y - (kind == M_UFO ? 70 : 34)));
        }

        // 主平台（一定是安全的，保证路线可达）
        int type = P_NORMAL;
        double r = rng.next();
        double pMove = sc < 350 ? 0 : Math.min(0.38, 0.1 + (sc - 350) / 14000);
        double pWhite = sc < 800 ? 0 : Math.min(0.24, 0.07 + (sc - 800) / 20000);
        if (r < pMove) type = P_MOVING;
        else if (r < pMove + pWhite) type = P_WHITE;
        double x = W / 2;
        for (int i = 0; i < 8; i++) {
            x = rand(half + 3, W - half - 3);
            if (!avoid || Math.abs(wrapDx(x, avoidX)) > 95) break;
        }
        Platform p = addPlatform(type, x, y);

        // 道具
        if (type == P_NORMAL || type == P_MOVING) {
            double r2 = rng.next();
            double pd, pw, pj, pp, pt, ps;
            if (chill) {
                pd = sc > C.CHILL_DARK[0] ? C.CHILL_DARK[1] : 0;
                pw = sc > C.CHILL_WINGS[0] ? C.CHILL_WINGS[1] : 0;
                pj = sc > C.CHILL_JET[0] ? C.CHILL_JET[1] : 0;
                pp = sc > C.CHILL_PROP[0] ? C.CHILL_PROP[1] : 0;
                pt = sc > C.CHILL_TRAMP[0] ? C.CHILL_TRAMP[1] : 0;
                ps = sc > C.CHILL_SPRING[0] ? C.CHILL_SPRING[1] : 0;
            } else {
                pd = sc > 5000 ? 0.0025 : 0; // 紫黑飞翼：5000 分以后才会出现
                pw = sc > 3500 ? 0.0035 : 0; // 飞翼：3500 分以后才会出现
                pj = sc > 2500 ? 0.006 : 0;
                pp = sc > 1200 ? 0.012 : 0;
                pt = sc > 700 ? 0.016 : 0;
                ps = 0.06;
            }
            int it = -1;
            if (r2 < pd) it = I_DARK;
            else if (r2 < pd + pw) it = I_WINGS;
            else if (r2 < pd + pw + pj) it = I_JET;
            else if (r2 < pd + pw + pj + pp) it = I_PROP;
            else if (r2 < pd + pw + pj + pp + pt) it = I_TRAMP;
            else if (r2 < pd + pw + pj + pp + pt + ps) it = I_SPRING;
            if (it >= 0) {
                double lim = half - ITEM_SIZE[it][0] / 2 - 2;
                p.item = new Item(it, rand(-lim, lim));
            }
        }

        // 额外的易碎平台（棕色）
        double pBrown = sc < 250 ? 0 : Math.min(0.5, 0.12 + (sc - 250) / 9000);
        if (gap > 36 && rng.next() < pBrown) {
            for (int i = 0; i < 5; i++) {
                double bx = rand(half + 3, W - half - 3);
                double by = y + rand(14, gap - 14);
                if (Math.abs(wrapDx(bx, x)) < 70 && Math.abs(by - y) < 22) continue;
                if (Math.abs(wrapDx(bx, lastX)) < 70 && Math.abs(by - prevTop) < 22) continue;
                addPlatform(P_BROWN, bx, by);
                break;
            }
        }
        lastX = x;
    }

    // ---------- 主循环 ----------
    /** 渲染插值用：每个固定步长开始前记下上一步的位置 */
    public void snapshot() {
        dd.px = dd.x;
        dd.py = dd.y;
        pCam = camY;
        for (int i = 0; i < platforms.size(); i++) platforms.get(i).px = platforms.get(i).x;
        for (int i = 0; i < monsters.size(); i++) {
            Monster m = monsters.get(i);
            m.px = m.x;
            m.py = m.y;
        }
        for (int i = 0; i < bullets.size(); i++) {
            Bullet b = bullets.get(i);
            b.px = b.x;
            b.py = b.y;
        }
    }

    public void update(double dt, double axis) {
        if (phase == OVER) return;
        snapshot();
        t += dt;
        if (phase == PLAY) stepPlay(dt, axis);
        else stepFalling(dt);
        stepWorld(dt);
    }

    private static double ease(double rate, double dt) {
        return 1 - Math.exp(-rate * dt);
    }

    void stepPlay(double dt, double axisIn) {
        Hero d = dd;
        d.t += dt;
        d.stateT += dt;
        d.bounceT += dt;
        d.shootT += dt;
        d.shootCd -= dt;
        d.prevY = d.y;
        // 肚子：阻尼弹簧，被 bounce() 的速度突变踢一脚后自己晃几下；肚皮再挂一层更软的弹簧
        d.bellyV += (-C.BELLY_K * d.belly - C.BELLY_C * d.bellyV) * dt;
        d.belly = clamp(d.belly + d.bellyV * dt, -1, 1);
        d.b2V += (C.BELLY2_K * (d.belly - d.b2) - C.BELLY2_C * d.b2V) * dt;
        d.b2 = clamp(d.b2 + d.b2V * dt, -1.3, 1.3);
        d.stretch += (clamp(-d.vy / 3500, -0.12, 0.16) - d.stretch) * ease(14, dt);
        // 慢落时两条手臂向上摊开
        d.armPose += (lerp(clamp(d.vy / 350, -1, 1), -0.5, d.fallK) - d.armPose) * ease(d.fallK > 0.05 ? 6 : 16, dt);
        double lookT = d.shootT < 0.35 ? Math.signum(Math.cos(d.aim)) * 0.9 : clamp(d.vx / 140, -1, 1);
        d.look += (lookT - d.look) * ease(12, dt);
        if (d.shield > 0) d.shield = Math.max(0, d.shield - dt);
        int st = d.state;
        boolean fly = flying(st);
        double axis = clamp(axisIn, -1, 1);
        d.axis = axis;
        posture(dt, st, fly);

        // 水平方向
        if (st == NORMAL || fly) {
            double vk = st == WINGS ? C.WING_VX : st == DARKWINGS ? C.DARK_VX : 1;
            double target = axis * C.MAX_VX * vk;
            double a = C.ACCEL * vk * dt;
            d.vx += clamp(target - d.vx, -a, a);
            if (Math.abs(d.vx) > 20) d.facing = d.vx > 0 ? 1 : -1;
            d.x += d.vx * dt;
            for (int i = 0; i < nShots; i++) fire(shotT[i], shotX[i], shotY[i]);
        } else if (st == DEAD) {
            d.x += d.vx * dt;
        }
        nShots = 0;

        // 垂直方向
        if (st == NORMAL) {
            // 道具用完后的慢落：只在快到顶点、开始往下掉的时候减小重力
            if (d.glide > 0 && d.vy > -220) d.glide = Math.max(0, d.glide - dt);
            d.glideK += ((d.glide > 0 ? Math.min(1, d.glide / 0.6) : 0) - d.glideK) * ease(8, dt);
            if (d.glideK < 0.002) d.glideK = 0;
            d.fallK = d.glideK * clamp((d.vy + 320) / 320, 0, 1);
            double maxFall = lerp(C.MAX_FALL, C.GLIDE_FALL, d.glideK);
            d.vy += C.GRAVITY * lerp(1, C.GLIDE_G, d.fallK) * dt;
            if (d.vy > maxFall) d.vy = Math.max(maxFall, d.vy - 2600 * dt);
            d.y += d.vy * dt;
            if (d.vy > 0) landing();
        } else if (fly) {
            d.fallK = d.glideK = d.glide = 0;
            boolean prop = st == PROPELLER;
            boolean dark = st == DARKWINGS;
            boolean wings = winged(st);
            double T = prop ? C.PROP_T : dark ? C.DARK_T : wings ? C.WING_T : C.JET_T;
            double V = prop ? C.PROP_V : dark ? C.DARK_V : wings ? C.WING_V : C.JET_V;
            double ramp = wings ? 1 : 0.7; // 最后这段时间慢慢减速
            double left = T - d.stateT;
            double tv = -(left > ramp ? V : lerp(250, V, Math.max(0, left) / ramp));
            double acc = dark ? 4600 : wings ? 3600 : 2600;
            d.vy += clamp(tv - d.vy, -acc * dt, acc * dt);
            d.y += d.vy * dt;
            whirT -= dt;
            if (whirT <= 0) {
                whirT = dark ? 0.17 : wings ? 0.2 : 0.16;
                sfx(prop ? Snd.PROP : dark ? Snd.DARKFLAP : wings ? Snd.FLAP : Snd.JET);
            }
            if (wings) {
                ghostT = Math.max(ghostT, 0.05);
                smash();
            }
            if (left <= 0) {
                d.state = NORMAL;
                d.stateT = 0;
                if (wings) {
                    // 收翅膀前最后用力扇一下，再给一小段护盾
                    d.shield = C.SHIELD_T;
                    d.shieldDark = dark;
                    bounce(C.JUMP_V * 1.15, 1.6);
                    if (dark) darkEndFx();
                    else wingEndFx();
                    sfx(dark ? Snd.DARKFLAP : Snd.FLAP);
                }
                // 道具用完：接下来的下落是慢慢飘下来的
                d.glide = C.GLIDE_T;
                d.glideCol = dark ? GL_DARK : wings ? GL_GOLD : prop ? GL_SKY : GL_FIRE;
            }
        } else if (st == DEAD) {
            d.vy = Math.min(d.vy + C.GRAVITY * dt, 1300);
            d.y += d.vy * dt;
            d.spin += dt * 5;
        } else if (st == BLACKHOLE || st == ABDUCTED) {
            double u = clamp(d.stateT / 0.9, 0, 1);
            double e = u * u;
            d.x = lerp(d.sx, d.tx, e);
            d.y = lerp(d.sy, d.ty, e);
            d.scale = 1 - u;
            d.spin += dt * (st == BLACKHOLE ? 10 : 2);
            if (d.stateT >= 0.9) {
                d.state = GONE;
                beginFalling();
            }
        }

        // 左右循环
        if (st != BLACKHOLE && st != ABDUCTED) {
            if (d.x < 0) d.x += W;
            else if (d.x >= W) d.x -= W;
        }

        trailFx(dt);

        if (d.state == NORMAL) {
            pickups();
            if (d.state == NORMAL) hazards();
        }

        // 相机只往上走，而且是缓动跟随，冲得太快时才硬拉住
        boolean alive = d.state == NORMAL || flying(d.state);
        if (alive) {
            double target = d.y - H * C.CAM_FRAC;
            if (target < camY) camY += (target - camY) * (1 - Math.exp(-C.CAM_K * dt));
            double topLimit = d.y - H * C.CAM_TOP;
            if (topLimit < camY) camY = topLimit;
            maxH = Math.max(maxH, startY - d.y);
            score = (int) Math.floor(maxH / 2);
        }
        genUntil(camY - 220);

        // 掉出屏幕底部
        if ((d.state == NORMAL || d.state == DEAD) && d.y - camY > H + 60) {
            if (d.state == NORMAL) {
                d.state = FELL;
                sfx(Snd.FALL);
            }
            beginFalling();
        }
    }

    // 身体倾斜 + 紫黑飞翼的旋转（都是阻尼弹簧，纯视觉）
    private static final double LEAN_K = 420, LEAN_C = 17, CHARGE_T = 0.16;

    void posture(double dt, int st, boolean fly) {
        Hero d = dd;
        double target = 0;
        double airT = 1;
        if (st == NORMAL || fly) {
            double vLean = clamp(d.vx / C.MAX_VX, -1, 1) * C.LEAN;
            target = vLean;
            if (st == NORMAL && d.bounceT < CHARGE_T) {
                double k = Math.sin((d.bounceT / CHARGE_T) * PI);
                target = lerp(vLean, d.axis * C.LEAN_CHARGE, k);
                airT = 0;
            }
            if (st == DARKWINGS) target *= 0.5;
            target += Math.sin(d.t * 2.6) * 0.16 * d.fallK;
        }
        d.leanV += (LEAN_K * (target - d.lean) - LEAN_C * d.leanV) * dt;
        d.lean = clamp(d.lean + d.leanV * dt, -0.8, 0.8);
        d.leanAir += (airT - d.leanAir) * (1 - Math.exp(-(airT > d.leanAir ? 9 : 30) * dt));

        if (st == DARKWINGS) {
            double ramp = clamp(d.stateT / 0.35, 0, 1);
            d.rollV += (C.DARK_SPIN * d.spinDir * ramp - d.rollV) * (1 - Math.exp(-6 * dt));
            d.roll += d.rollV * dt;
        } else if (d.roll != 0 || d.rollV != 0) {
            double tgt = Math.round((d.roll + d.rollV * 0.2) / TAU) * TAU;
            d.rollV += (110 * (tgt - d.roll) - 15 * d.rollV) * dt;
            d.roll += d.rollV * dt;
            if (Math.abs(d.roll - tgt) < 0.003 && Math.abs(d.rollV) < 0.08) d.roll = d.rollV = 0;
        }
        if (d.roll > PI) d.roll -= TAU;
        else if (d.roll < -PI) d.roll += TAU;
    }

    void beginFalling() {
        phase = FALLING;
        phaseT = 0;
    }

    void stepFalling(double dt) {
        Hero d = dd;
        phaseT += dt;
        if (d.state == FELL || d.state == DEAD) {
            d.vy = Math.min(d.vy + C.GRAVITY * dt, 1300);
            d.y += d.vy * dt;
        }
        camY += (320 + 2600 * phaseT) * dt;
        if (phaseT >= 0.9) {
            phase = OVER;
            sfx(Snd.OVER);
        }
    }

    // ---------- 视觉特效（用 Rng.fx()，不碰关卡的种子随机数）----------
    private static double rnd() {
        return Rng.fx();
    }

    Fx newFx(int k) {
        int n = fxPool.size();
        Fx f = n > 0 ? fxPool.remove(n - 1) : new Fx();
        f.clear();
        f.k = k;
        return f;
    }

    void addFx(Fx f) {
        f.max = f.life;
        if (fx.size() < 360) fx.add(f);
        else fxPool.add(f);
    }

    void puff(double x, double y, int n, double spd) {
        for (int i = 0; i < n; i++) {
            double s = n > 1 ? ((double) i / (n - 1)) * 2 - 1 : 0;
            Fx f = newFx(Fx.PUFF);
            f.x = x + s * 11;
            f.y = y - 1;
            f.vx = s * spd * (0.7 + rnd() * 0.5);
            f.vy = -(10 + rnd() * 30);
            f.r = 3 + rnd() * 2.5;
            f.life = f.max = 0.5;
            fx.add(f);
        }
    }

    void ring(double x, double y) {
        Fx f = newFx(Fx.RING);
        f.x = x;
        f.y = y;
        f.r = 6;
        f.life = f.max = 0.36;
        fx.add(f);
    }

    /** 冲击波：扁椭圆（flat 小）或正圆（flat=1）向外扩，越扩越慢、越细 */
    void shockwave(double x, double y, double vr, double w, int col, double flat, double life, double delay) {
        Fx f = newFx(Fx.SHOCK);
        f.x = x;
        f.y = y;
        f.r = 6;
        f.vr = vr;
        f.drag = 7;
        f.w = w;
        f.flat = flat;
        f.col = col;
        f.delay = delay;
        f.life = life;
        addFx(f);
    }

    void glow(double x, double y, double r, int col, double life, double delay) {
        Fx f = newFx(Fx.GLOW);
        f.x = x;
        f.y = y;
        f.r = r;
        f.col = col;
        f.life = life;
        f.delay = delay;
        addFx(f);
    }

    void beam(double x, double y, double w, double h, int col, double life, double delay) {
        Fx f = newFx(Fx.BEAM);
        f.x = x;
        f.y = y;
        f.w = w;
        f.h = h;
        f.col = col;
        f.life = life;
        f.delay = delay;
        addFx(f);
    }

    void rays(double x, double y, int n, double a0, double a1, double R, double w, int col, double life, double delay) {
        Fx f = newFx(Fx.RAYS);
        f.x = x;
        f.y = y;
        f.n = n;
        f.a0 = a0;
        f.a1 = a1;
        f.R = R;
        f.w = w;
        f.col = col;
        f.life = life;
        f.delay = delay;
        addFx(f);
    }

    void spiral(double x, double y, int n, double R, double w, double a0, double spin, int col, double life, double delay) {
        Fx f = newFx(Fx.SPIRAL);
        f.x = x;
        f.y = y;
        f.n = n;
        f.R = R;
        f.w = w;
        f.a0 = a0;
        f.spin = spin;
        f.flat = 1;
        f.col = col;
        f.life = life;
        f.delay = delay;
        addFx(f);
    }

    /** 一把粒子（星星/圆点/火焰）朝 a0..a1 角度范围喷出去，带重力 */
    void burst(double x, double y, int n, double delay, double a0, double a1, double s0, double s1, double g, double drag,
               double r0, double r1, double l0, double l1, double spread, int[] shapes, int[] cols) {
        for (int i = 0; i < n; i++) {
            double a = a0 + (a1 - a0) * rnd();
            double sp = s0 + (s1 - s0) * rnd();
            Fx f = newFx(Fx.PART);
            f.delay = delay;
            f.shape = shapes[(int) (rnd() * shapes.length)];
            f.x = x + (rnd() - 0.5) * spread;
            f.y = y;
            f.vx = Math.cos(a) * sp;
            f.vy = Math.sin(a) * sp;
            f.g = g;
            f.drag = drag;
            f.r = r0 + (r1 - r0) * rnd();
            f.rot = rnd() * TAU;
            f.vr = (rnd() - 0.5) * 16;
            f.col = cols[(int) (rnd() * cols.length)];
            f.life = l0 + (l1 - l0) * rnd();
            addFx(f);
        }
    }

    void part(int shape, double x, double y, double vx, double vy, double g, double drag, double r, double vr, int col, double life) {
        Fx f = newFx(Fx.PART);
        f.shape = shape;
        f.x = x;
        f.y = y;
        f.vx = vx;
        f.vy = vy;
        f.g = g;
        f.drag = drag;
        f.r = r;
        f.rot = rnd() * TAU;
        f.vr = vr;
        f.col = col;
        f.life = life;
        addFx(f);
    }

    void popText(double x, double y, String txt, double size, int col, double delay, double life) {
        Fx f = newFx(Fx.TEXT);
        f.x = x;
        f.y = y;
        f.txt = txt;
        f.size = size;
        f.col = col;
        f.rot = (rnd() - 0.5) * 0.3;
        f.life = life;
        f.delay = delay;
        addFx(f);
    }

    void addGhost() {
        Fx f = newFx(Fx.GHOST);
        f.d.copyFrom(dd);
        f.x = dd.x;
        f.y = dd.y;
        f.col = dd.state == DARKWINGS ? 0xa46bff : dd.state == WINGS ? 0xffe07a : 0xb6f783;
        f.life = dd.state == DARKWINGS ? 0.26 : 0.3;
        addFx(f);
    }

    /** 一根飘落的羽毛：左右摆着慢慢往下飘 */
    void feather(double x, double y, double vx, double vy, double life, boolean dark, double delay) {
        Fx f = newFx(Fx.FEATHER);
        f.dark = dark;
        f.delay = delay;
        f.x = x;
        f.y = y;
        f.vx = vx;
        f.vy = vy;
        f.ph = rnd() * TAU;
        f.w = 4 + rnd() * 3;
        f.r = (4 + rnd() * 2.5) * (dark ? 1.1 : 1);
        f.rot = rnd() * TAU;
        f.rot0 = rnd() * TAU;
        f.life = life;
        addFx(f);
    }

    private static final int[] SH_SD = {Fx.S_STAR, Fx.S_DOT};
    private static final int[] SH_SSD = {Fx.S_STAR, Fx.S_STAR, Fx.S_DOT};
    private static final int[] SH_DS = {Fx.S_DOT, Fx.S_STAR};
    private static final int[] SH_FIRE = {Fx.S_FIRE};

    /** 飞翼撞碎怪物 / 黑洞的瞬间 */
    void smashFx(double x, double y) {
        if (dd.state == DARKWINGS) {
            shockwave(x, y, 480, 6, 0xaa5aff, 1, 0.38, 0);
            shockwave(x, y, 340, 2.5, 0x7df0ff, 1, 0.3, 0.04);
            glow(x, y, 56, 0xbe78ff, 0.22, 0);
            burst(x, y, 14, 0, 0, TAU, 140, 420, 600, 1.3, 3.5, 6.5, 0.4, 0.7, 10, SH_SD, new int[]{0xc77dff, 0xff6ef0, 0x7df9ff, 0xffffff});
            popText(x, y - 26, "POW!", 24, 0xc77dff, 0, 0.8);
            shake = Math.max(shake, 6);
            return;
        }
        shockwave(x, y, 420, 5, 0xffd65c, 1, 0.36, 0);
        glow(x, y, 50, 0xfff0aa, 0.2, 0);
        burst(x, y, 12, 0, 0, TAU, 120, 380, 700, 1.2, 3.5, 6.5, 0.4, 0.7, 10, SH_SD, new int[]{0xffd93b, 0xffffff, 0xff8a5c});
        popText(x, y - 26, "POW!", 24, 0xff8a3d, 0, 0.8);
        shake = Math.max(shake, 5);
    }

    /** 紫黑飞翼收翅膀：一圈紫色冲击波 + 霓虹细环，抖落一把黑紫色羽毛 */
    void darkEndFx() {
        double y = dd.y - CENTER;
        shockwave(dd.x, y, 360, 5, 0xaa5aff, 1, 0.4, 0);
        shockwave(dd.x, y, 260, 2, 0x7df0ff, 1, 0.34, 0.05);
        spiral(dd.x, y, 4, 70, 3, rnd() * TAU, -5, 0xc77dff, 0.5, 0);
        for (int i = 0; i < 10; i++)
            feather(dd.x + (rnd() - 0.5) * 44, y + (rnd() - 0.5) * 18, (rnd() - 0.5) * 240, -40 + rnd() * 120, 1.1 + rnd() * 0.6, true, 0);
    }

    /** 飞翼时间到，翅膀最后扇一下、抖落一把羽毛 */
    void wingEndFx() {
        double y = dd.y - CENTER;
        shockwave(dd.x, y, 300, 4, 0xffe278, 1, 0.36, 0);
        for (int i = 0; i < 8; i++)
            feather(dd.x + (rnd() - 0.5) * 40, y + (rnd() - 0.5) * 16, (rnd() - 0.5) * 220, -40 + rnd() * 120, 1 + rnd() * 0.6, false, 0);
    }

    /** 道具被用上的那一瞬间：弹簧 / 蹦床在脚下 (x, y) 炸开；螺旋桨帽 / 喷气背包 / 飞翼在身上炸开 */
    void itemFx(int type, double x, double y) {
        if (type == I_SPRING) {
            int[] cols = {0xffd93b, 0xfff6a8, 0xff7a5c, 0xffffff};
            shockwave(x, y - 2, 400, 5, 0xffec78, 0.3, 0.4, 0);
            shockwave(x, y - 2, 300, 3, 0xffffff, 0.3, 0.34, 0.05);
            beam(x, y, 26, 150, 0xffe66e, 0.3, 0);
            rays(x, y - 4, 9, -PI * 0.92, -PI * 0.08, 84, 3.6, 0xffce28, 0.34, 0);
            burst(x, y - 6, 12, 0, -PI * 0.95, -PI * 0.05, 140, 430, 900, 1.2, 4, 7, 0.5, 0.85, 16, SH_SSD, cols);
            popText(x, y - 40, "BOING!", 24, 0xffd93b, 0, 0.8);
            shake = Math.max(shake, 3.5);
            ghostT = 0.3;
        } else if (type == I_TRAMP) {
            int[] cols = {0xff5c5c, 0xffd93b, 0xffffff, 0x7be0ff, 0xff8fd0};
            int[] rc = {0xff5c5c, 0xffd93b, 0xffffff};
            for (int k = 0; k < 3; k++) shockwave(x, y - 2, 600 - k * 70, 7.5 - k * 1.8, rc[k], 0.3, 0.5 + k * 0.04, k * 0.06);
            beam(x, y, 46, 290, 0xffaaa0, 0.38, 0);
            glow(x, y - 14, 78, 0xffffff, 0.24, 0);
            rays(x, y - 6, 15, -PI * 0.99, -PI * 0.01, 140, 4.4, 0xffffff, 0.42, 0);
            rays(x, y - 6, 11, -PI * 0.95, -PI * 0.05, 100, 6, 0xff5c5c, 0.36, 0);
            burst(x, y - 8, 22, 0, -PI * 0.97, -PI * 0.03, 230, 660, 1000, 1.1, 4.5, 8, 0.6, 1.0, 24, SH_SSD, cols);
            popText(x, y - 48, "SUPER BOING!!", 28, 0xff6b6b, 0, 0.8);
            shake = Math.max(shake, 8);
            flash = Math.max(flash, 0.3);
            flashCol = 0xffffff;
            ghostT = 0.5;
        } else if (type == I_PROP) {
            int[] cols = {0x7fd0ff, 0xffd93b, 0xffffff, 0xff7a7a};
            shockwave(x, y, 320, 4.5, 0x7fd0ff, 1, 0.4, 0);
            shockwave(x, y, 230, 3, 0xffffff, 1, 0.34, 0.06);
            glow(x, y, 54, 0xbeebff, 0.22, 0);
            rays(x, y, 12, 0, (TAU * 11) / 12, 66, 3.2, 0x96deff, 0.34, 0);
            burst(x, y, 16, 0, 0, TAU, 90, 280, 140, 2.2, 3.5, 6.5, 0.5, 0.8, 8, SH_DS, cols);
            popText(x, y - 48, "WHIRR!", 22, 0x7fd0ff, 0, 0.8);
            shake = Math.max(shake, 2.5);
            flash = Math.max(flash, 0.12);
            flashCol = 0xc8ebff;
            ghostT = 0.3;
        } else if (type == I_JET) {
            int[] cols = {0xffb02e, 0xff6a3d, 0xfff29a, 0xffffff};
            shockwave(x, y + 22, 520, 7, 0xffb02e, 0.3, 0.46, 0);
            shockwave(x, y, 340, 4.5, 0xff6a3d, 1, 0.4, 0.03);
            shockwave(x, y, 250, 3, 0xffffff, 1, 0.34, 0.08);
            glow(x, y + 6, 72, 0xffc86e, 0.26, 0);
            rays(x, y, 14, 0, (TAU * 13) / 14, 82, 3.6, 0xffb02e, 0.36, 0);
            burst(x, y + 16, 12, 0, PI * 0.12, PI * 0.88, 160, 460, 300, 1.6, 5, 9, 0.35, 0.6, 18, SH_FIRE, cols);
            burst(x, y, 12, 0, 0, TAU, 120, 360, 500, 1.4, 4, 7, 0.5, 0.85, 10, SH_SD, cols);
            popText(x, y - 52, "BOOM!", 26, 0xff8a3d, 0, 0.8);
            shake = Math.max(shake, 6);
            flash = Math.max(flash, 0.26);
            flashCol = 0xffc878;
            ghostT = 0.4;
        } else if (type == I_DARK) {
            // 紫黑飞翼：先“吸”——一圈紫色星屑被吸进小海星，0.15 秒后再“炸”
            int[] cols = {0xc77dff, 0x9d4edd, 0xff6ef0, 0x7df9ff, 0xffffff};
            double D = 0.15;
            for (int i = 0; i < 20; i++) {
                double a = ((double) i / 20) * TAU + rnd() * 0.2;
                double r = 95 + rnd() * 30;
                part(i % 3 != 0 ? Fx.S_DOT : Fx.S_STAR, x + Math.cos(a) * r, y + Math.sin(a) * r, (-Math.cos(a) * r) / D,
                        (-Math.sin(a) * r) / D, 0, 0, 2.6 + rnd() * 2, 8, cols[i % 4], D);
            }
            glow(x, y, 60, 0x7828dc, D + 0.05, 0);
            shockwave(x, y, 820, 13, 0x2d0c4b, 1, 0.6, D);
            shockwave(x, y, 720, 6, 0xaa5aff, 1, 0.55, D + 0.03);
            shockwave(x, y, 560, 3.5, 0xff6eeb, 1, 0.5, D + 0.08);
            shockwave(x, y, 980, 2.5, 0x7df0ff, 1, 0.45, D + 0.05);
            shockwave(x, y + CENTER, 1000, 6, 0x9646ff, 0.3, 0.5, D);
            glow(x, y, 170, 0x9646ff, 0.42, D);
            beam(x, y + 40, 80, 620, 0x9650ff, 0.6, D);
            spiral(x, y, 5, 140, 4, rnd() * TAU, 6, 0xc77dff, 0.7, D);
            spiral(x, y, 5, 110, 2, rnd() * TAU, 6, 0xff6eeb, 0.6, D + 0.04);
            rays(x, y, 24, 0, (TAU * 23) / 24, 185, 4.5, 0xc77dff, 0.5, D);
            rays(x, y, 12, 0.13, 0.13 + (TAU * 11) / 12, 130, 2.5, 0xffffff, 0.42, D);
            burst(x, y, 26, D, 0, TAU, 200, 620, 220, 1.5, 3.5, 7.5, 0.6, 1.2, 12, SH_SSD, cols);
            for (int i = 0; i < 16; i++) {
                double a = rnd() * TAU;
                double sp = 140 + rnd() * 280;
                feather(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 1.2 + rnd() * 0.7, true, D);
            }
            popText(x, y - 58, "暗翼!!", 36, 0xc77dff, D, 0.9);
            shake = Math.max(shake, 11);
            flash = Math.max(flash, 0.42);
            flashCol = 0x461482;
            ghostT = 0.7;
        } else if (type == I_WINGS) {
            // 终极道具：金色 + 白色 + 粉色三层冲击波、巨型光柱、满天星星和羽毛、金色闪屏、狠狠一震
            int[] cols = {0xffe27a, 0xfff6c8, 0xffffff, 0xffb3e0, 0x9be8ff};
            shockwave(x, y, 760, 9, 0xffd65c, 1, 0.6, 0);
            shockwave(x, y, 600, 6, 0xffffff, 1, 0.52, 0.05);
            shockwave(x, y, 470, 4, 0xffa0dc, 1, 0.46, 0.1);
            shockwave(x, y + CENTER, 900, 7, 0xffec96, 0.3, 0.5, 0);
            glow(x, y, 150, 0xfff0aa, 0.4, 0);
            beam(x, y + 40, 76, 560, 0xffe278, 0.6, 0);
            rays(x, y, 22, 0, (TAU * 21) / 22, 170, 5, 0xffd65c, 0.5, 0);
            rays(x, y, 14, 0.11, 0.11 + (TAU * 13) / 14, 120, 3, 0xffffff, 0.42, 0);
            burst(x, y, 22, 0, 0, TAU, 180, 560, 260, 1.5, 4, 8, 0.6, 1.1, 12, SH_SSD, cols);
            for (int i = 0; i < 14; i++) {
                double a = rnd() * TAU;
                double sp = 120 + rnd() * 260;
                feather(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 1.1 + rnd() * 0.7, false, 0);
            }
            popText(x, y - 56, "飞翼!!", 34, 0xffd84a, 0, 0.8);
            shake = Math.max(shake, 9);
            flash = Math.max(flash, 0.55);
            flashCol = 0xffecaa;
            ghostT = 0.6;
        }
    }

    private static final int[][] GLIDE_COLS = {
            {0xffe27a, 0xfff6c8, 0xffffff}, // gold
            {0xc77dff, 0xff9ef5, 0xa0f6ff}, // dark
            {0x9fdcff, 0xffffff, 0xffe27a}, // sky
            {0xffd27a, 0xffffff, 0xffb37a}, // fire
    };
    private static final int[] DARK_TRAIL = {0xc77dff, 0xff6ef0, 0x7df9ff, 0xffffff, 0x9d4edd};
    private static final int[] WING_TRAIL = {0xffe27a, 0xfff6c8, 0xffffff, 0xffb3e0};

    /** 每步都跑的“持续”特效：残影、喷气背包的火焰尾迹、螺旋桨扇出来的风、慢落的光点 */
    void trailFx(double dt) {
        Hero d = dd;
        if (ghostT > 0) {
            ghostT -= dt;
            ghostCd -= dt;
            if (ghostCd <= 0) {
                ghostCd = 0.034;
                addGhost();
            }
        }
        if (fx.size() > 260) return;
        if (d.state == NORMAL && d.fallK > 0.4) {
            trailT -= dt;
            if (trailT > 0) return;
            trailT = 0.06;
            int[] cs = GLIDE_COLS[d.glideCol];
            part(rnd() < 0.3 ? Fx.S_STAR : Fx.S_DOT, d.x + (rnd() - 0.5) * 30 * HS, d.y - CENTER + (rnd() - 0.5) * 20,
                    (rnd() - 0.5) * 30, -40 - rnd() * 40, -30, 1.2, 2.2 + rnd() * 2, (rnd() - 0.5) * 6,
                    cs[(int) (rnd() * cs.length)], 0.6 + rnd() * 0.4);
            if (d.glideCol == GL_DARK && rnd() < 0.18) feather(d.x + (rnd() - 0.5) * 30, d.y - CENTER, (rnd() - 0.5) * 60, -30, 1, true, 0);
            return;
        }
        if (!flying(d.state)) return;
        trailT -= dt;
        if (trailT > 0) return;
        if (d.state == DARKWINGS) {
            trailT = 0.024;
            int s = rnd() < 0.5 ? -1 : 1;
            if (rnd() < 0.4)
                feather(d.x + s * (20 + rnd() * 24) * HS, d.y - CENTER + (rnd() - 0.5) * 10, s * (40 + rnd() * 70), 160 + rnd() * 160, 0.8 + rnd() * 0.5, true, 0);
            // 星屑螺旋：沿着身体的旋转方向甩出去
            double a = d.roll + rnd() * 0.6;
            double r = 16 * HS;
            part(rnd() < 0.55 ? Fx.S_STAR : Fx.S_DOT, d.x + Math.cos(a) * r, d.y - CENTER + Math.sin(a) * r,
                    -Math.sin(a) * 120 * d.spinDir + (rnd() - 0.5) * 30, 180 + rnd() * 160, 0, 1.6, 2 + rnd() * 2.2,
                    (rnd() - 0.5) * 14, DARK_TRAIL[(int) (rnd() * 5)], 0.45 + rnd() * 0.3);
            return;
        }
        if (d.state == JETPACK) {
            trailT = 0.022;
            // 火焰从两个油罐的喷口喷出
            for (int s = -1; s <= 1; s += 2) {
                Fx f = newFx(Fx.FIRE);
                f.x = d.x + s * 19 * HS + (rnd() - 0.5) * 4;
                f.y = d.y - 8 * HS;
                f.vx = (rnd() - 0.5) * 50;
                f.vy = 160 + rnd() * 170;
                f.r = (5 + rnd() * 3.5) * Math.sqrt(HS);
                f.life = 0.26 + rnd() * 0.2;
                addFx(f);
            }
        } else if (d.state == WINGS) {
            trailT = 0.03;
            int s = rnd() < 0.5 ? -1 : 1;
            if (rnd() < 0.45)
                feather(d.x + s * (18 + rnd() * 22) * HS, d.y - CENTER + (rnd() - 0.5) * 10, s * (30 + rnd() * 60), 140 + rnd() * 140, 0.8 + rnd() * 0.5, false, 0);
            part(rnd() < 0.5 ? Fx.S_STAR : Fx.S_DOT, d.x + (rnd() - 0.5) * 24, d.y - 4, (rnd() - 0.5) * 60, 160 + rnd() * 160, 0, 1.5,
                    2.2 + rnd() * 2, (rnd() - 0.5) * 12, WING_TRAIL[(int) (rnd() * 4)], 0.45 + rnd() * 0.3);
        } else {
            trailT = 0.06;
            int s = rnd() < 0.5 ? -1 : 1;
            Fx f = newFx(Fx.STREAK);
            f.x = d.x + s * (14 + rnd() * 14) * HS;
            f.y = d.y - 70 * HS - rnd() * 20;
            f.vx = s * 10;
            f.vy = 230 + rnd() * 120;
            f.life = 0.24;
            addFx(f);
        }
    }

    void bounce(double v, double kick) {
        Hero d = dd;
        // 脚一下子被平台挡住，身体靠惯性往前栽一下
        d.leanV += clamp(d.vx / C.MAX_VX, -1.5, 1.5) * 2.5;
        // 速度从“下落”突变成“上冲”，肚子跟不上，先被甩下去再弹回来
        d.bellyV += (v + d.vy) * C.BELLY_GAIN * kick;
        d.vy = -v;
        d.bounceT = 0;
    }

    private final ArrayList<Platform> brownHits = new ArrayList<>();

    void landing() {
        Hero d = dd;
        double prev = d.prevY;
        double cur = d.y;
        Platform bestP = null;
        Item bestIt = null;
        double bestY = 0;
        brownHits.clear();
        for (int i = 0; i < platforms.size(); i++) {
            Platform p = platforms.get(i);
            if (p.dead) continue;
            if (prev <= p.y + 0.01 && cur >= p.y && Math.abs(wrapDx(d.x, p.x)) <= p.w / 2 + C.FEET) {
                if (p.type == P_BROWN) brownHits.add(p);
                else if (bestP == null || p.y < bestY) {
                    bestP = p;
                    bestY = p.y;
                    bestIt = null;
                }
            }
            Item it = p.item;
            if (it != null && !it.used && (it.type == I_SPRING || it.type == I_TRAMP)) {
                double sy = p.y - it.h;
                // 脚踩到道具本身，或者身体中心已经在这块平台范围内都算踩中
                boolean onItem = Math.abs(wrapDx(d.x, p.x + it.ox)) <= it.w / 2 + C.FEET - 2;
                boolean overBrick = Math.abs(wrapDx(d.x, p.x)) <= (p.w / 2) * C.ITEM_REACH;
                if (prev <= sy + 0.01 && cur >= sy && (onItem || overBrick)) {
                    if (bestP == null || sy < bestY) {
                        bestP = p;
                        bestY = sy;
                        bestIt = it;
                    }
                }
            }
        }
        // 只踩碎落脚点上方（或同高）的棕色平台
        for (int i = 0; i < brownHits.size(); i++) {
            Platform b = brownHits.get(i);
            if (bestP == null || b.y <= bestY) breakPlatform(b);
        }
        if (bestP == null) return;
        Platform p = bestP;
        if (bestIt != null) {
            d.y = bestY;
            bestIt.fired = true;
            bestIt.t = 0;
            p.dipV += 200;
            puff(d.x, bestY, 6, 120);
            ring(d.x, bestY);
            itemFx(bestIt.type, d.x, bestY);
            if (bestIt.type == I_SPRING) {
                bounce(C.SPRING_V, 1.5);
                sfx(Snd.SPRING);
            } else {
                bounce(C.TRAMP_V, 2);
                sfx(Snd.TRAMP);
            }
            // 弹簧 / 蹦床也算道具：冲到顶以后慢慢飘下来
            d.glide = C.GLIDE_T * 0.8;
            d.glideCol = bestIt.type == I_SPRING ? GL_GOLD : GL_FIRE;
            return;
        }
        d.y = bestY;
        p.dipV += 150;
        puff(d.x, bestY, 4, 70);
        d.glide = 0;
        // 蓄力：倾斜手机越狠，落地时身体压得越扁
        bounce(C.JUMP_V, 1 + 0.45 * Math.abs(d.axis));
        sfx(Snd.JUMP);
        if (p.type == P_WHITE) {
            p.dead = true;
            p.fade = 0;
        }
    }

    void breakPlatform(Platform p) {
        p.dead = true;
        p.fade = 99;
        for (int s = -1; s <= 1; s += 2) {
            Debris q = new Debris();
            q.x = p.x + (s * p.w) / 4;
            q.y = p.y;
            q.w = p.w / 2;
            q.h = p.h;
            q.vx = s * 40;
            q.vy = -60;
            q.vr = s * 3;
            debris.add(q);
        }
        sfx(Snd.BREAK);
    }

    void pickups() {
        Hero d = dd;
        for (int i = 0; i < platforms.size(); i++) {
            Platform p = platforms.get(i);
            Item it = p.item;
            if (it == null || it.used || (it.type != I_PROP && it.type != I_JET && it.type != I_WINGS && it.type != I_DARK)) continue;
            double dx = wrapDx(d.x, p.x + it.ox);
            if (Math.abs(dx) < it.w / 2 + C.HALF_W - 3 && d.y > p.y - it.h - 4 && d.y - C.BODY_H < p.y) {
                it.used = true;
                d.state = it.type == I_PROP ? PROPELLER : it.type == I_JET ? JETPACK : it.type == I_DARK ? DARKWINGS : WINGS;
                d.stateT = 0;
                d.vy = Math.min(d.vy, it.type == I_DARK ? -560 : it.type == I_WINGS ? -450 : -300);
                d.glide = d.glideK = d.fallK = 0;
                if (it.type == I_DARK) d.spinDir = d.vx < -20 ? -1 : d.vx > 20 ? 1 : (d.facing != 0 ? d.facing : 1);
                whirT = 0;
                trailT = 0;
                itemFx(it.type, d.x, d.y - CENTER);
                sfx(it.type == I_DARK ? Snd.DARKWINGS : it.type == I_WINGS ? Snd.WINGS : Snd.PICKUP);
                return;
            }
        }
    }

    /** 调试 / CI 截图用：直接让主角用上某个道具（和真的捡到、踩到时走同一套效果） */
    public void debugGive(int type) {
        Hero d = dd;
        if (type == I_SPRING || type == I_TRAMP) {
            puff(d.x, d.y, 6, 120);
            ring(d.x, d.y);
            itemFx(type, d.x, d.y);
            bounce(type == I_SPRING ? C.SPRING_V : C.TRAMP_V, type == I_SPRING ? 1.5 : 2);
            sfx(type == I_SPRING ? Snd.SPRING : Snd.TRAMP);
            d.glide = C.GLIDE_T * 0.8;
            d.glideCol = type == I_SPRING ? GL_GOLD : GL_FIRE;
            return;
        }
        d.state = type == I_PROP ? PROPELLER : type == I_JET ? JETPACK : type == I_DARK ? DARKWINGS : WINGS;
        d.stateT = 0;
        d.vy = Math.min(d.vy, type == I_DARK ? -560 : type == I_WINGS ? -450 : -300);
        d.glide = d.glideK = d.fallK = 0;
        if (type == I_DARK) d.spinDir = d.vx < -20 ? -1 : 1;
        whirT = 0;
        trailT = 0;
        itemFx(type, d.x, d.y - CENTER);
        sfx(type == I_DARK ? Snd.DARKWINGS : type == I_WINGS ? Snd.WINGS : Snd.PICKUP);
    }

    void hazards() {
        Hero d = dd;
        double top = d.y - C.BODY_H;
        double bot = d.y;
        double hw = C.HALF_W;
        boolean shielded = d.shield > 0;
        for (int i = 0; i < monsters.size(); i++) {
            Monster m = monsters.get(i);
            if (m.dead) continue;
            double dx = wrapDx(d.x, m.x);
            if (!shielded && m.kind == M_UFO && Math.abs(dx) < 30 && bot > m.y + 12 && top < m.y + 150) {
                d.state = ABDUCTED;
                d.stateT = 0;
                d.sx = d.x;
                d.sy = d.y;
                d.tx = m.x;
                d.ty = m.y + 6;
                d.vx = 0;
                sfx(Snd.ABDUCT);
                return;
            }
            if (Math.abs(dx) < m.w / 2 + hw - 4 && bot > m.y - m.h / 2 && top < m.y + m.h / 2) {
                if (d.vy > 0 && d.prevY <= m.y - m.h * 0.1) {
                    killMonster(m);
                    d.glide = 0;
                    bounce(C.STOMP_V, 1);
                    sfx(Snd.STOMP);
                } else if (shielded) {
                    killMonster(m);
                    smashFx(m.x, m.y);
                    sfx(Snd.SMASH);
                } else {
                    d.state = DEAD;
                    d.stateT = 0;
                    d.vy = -300;
                    d.vx = (dx >= 0 ? 1 : -1) * 90;
                    sfx(Snd.HIT);
                    return;
                }
            }
        }
        if (shielded) return;
        for (int i = 0; i < holes.size(); i++) {
            Hole h = holes.get(i);
            double dx = wrapDx(d.x, h.x);
            double dy = d.y - CENTER - h.y;
            double rr = h.r * 0.85 + 12 * HS;
            if (dx * dx + dy * dy < rr * rr) {
                d.state = BLACKHOLE;
                d.stateT = 0;
                d.sx = d.x;
                d.sy = d.y;
                d.tx = h.x;
                d.ty = h.y + CENTER;
                d.vx = 0;
                sfx(Snd.HOLE);
                return;
            }
        }
    }

    /** 飞翼飞行中：撞到的怪物全部撞飞，黑洞直接被撞散 */
    void smash() {
        Hero d = dd;
        double cy = d.y - CENTER;
        double R = (d.state == DARKWINGS ? 34 : 30) * HS;
        for (int i = 0; i < monsters.size(); i++) {
            Monster m = monsters.get(i);
            if (m.dead) continue;
            double dx = wrapDx(d.x, m.x);
            if (Math.abs(dx) < m.w / 2 + R && Math.abs(cy - m.y) < m.h / 2 + R) {
                killMonster(m);
                m.vy = -520;
                m.vx = (dx >= 0 ? -1 : 1) * rand(120, 220);
                smashFx(m.x, m.y);
                sfx(Snd.SMASH);
            }
        }
        for (int i = holes.size() - 1; i >= 0; i--) {
            Hole h = holes.get(i);
            double dx = wrapDx(d.x, h.x);
            double dy = cy - h.y;
            double rr = h.r + R;
            if (dx * dx + dy * dy < rr * rr) {
                holes.remove(i);
                smashFx(h.x, h.y);
                sfx(Snd.SMASH);
            }
        }
    }

    private double tgX, tgY;

    /** 怪物在 t 秒后的位置（给子弹留提前量），结果写进 tgX/tgY */
    void monsterAt(Monster m, double t) {
        double tt = m.t + t;
        if (m.kind == M_BLOB) {
            tgX = m.x0 + Math.sin(tt * m.sp) * m.amp;
            tgY = m.y;
        } else if (m.kind == M_FLYER) {
            tgX = clamp(m.x + m.vx * t, 30, W - 30);
            tgY = m.y0 + Math.sin(tt * 2.2) * 14;
        } else {
            tgX = clamp(m.x + m.vx * t, 40, W - 40);
            tgY = m.y0 + Math.sin(tt * 1.5) * 8;
        }
    }

    /** 找最近的、在屏幕里、而且在射击角度范围内（往上方）的怪物 */
    boolean findTarget(double bx, double by) {
        double hi = -0.2;
        double lo = -PI + 0.2;
        boolean found = false;
        double bd = Double.POSITIVE_INFINITY;
        double fx0 = 0, fy0 = 0;
        for (int i = 0; i < monsters.size(); i++) {
            Monster m = monsters.get(i);
            if (m.dead) continue;
            double sy = m.y - camY;
            if (sy < 4 || sy > H || m.x < -10 || m.x > W + 10) continue;
            double d = Math.hypot(m.x - bx, m.y - by);
            monsterAt(m, d / C.BULLET_V);
            double a = Math.atan2(tgY - by, tgX - bx);
            if (a > hi || a < lo) continue;
            d = Math.hypot(tgX - bx, tgY - by);
            if (d < bd) {
                bd = d;
                fx0 = tgX;
                fy0 = tgY;
                found = true;
            }
        }
        tgX = fx0;
        tgY = fy0;
        return found;
    }

    public void fire(boolean hasT, double sx, double sy) {
        Hero d = dd;
        if (d.shootCd > 0) return;
        double bx = d.x;
        double by = d.y - C.MOUTH;
        double ax, ay;
        // 自动瞄准：屏幕里有能打到的怪物就直接朝它打，不管点在哪
        if (findTarget(bx, by)) {
            ax = tgX - bx;
            ay = tgY - by;
        } else if (hasT) {
            ax = wrapDx(sx, d.x) + (d.x - bx);
            ay = sy + camY - by;
        } else {
            ax = sx;
            ay = sy;
        }
        double a = Math.atan2(ay, ax);
        double hi = -0.2;
        double lo = -PI + 0.2;
        if (a > hi) a = Math.cos(a) >= 0 ? hi : lo;
        else if (a < lo) a = lo;
        double cx = Math.cos(a);
        double cy = Math.sin(a);
        Bullet b = new Bullet();
        b.x = b.px = bx + cx * 28 * HS;
        b.y = b.py = by + cy * 28 * HS;
        b.vx = cx * C.BULLET_V;
        b.vy = cy * C.BULLET_V;
        b.life = 1.2;
        bullets.add(b);
        d.aim = a;
        d.shootT = 0;
        d.shootCd = C.SHOOT_CD;
        if (cx > 0.15) d.facing = 1;
        else if (cx < -0.15) d.facing = -1;
        sfx(Snd.SHOOT);
    }

    void hitMonster(Monster m) {
        m.hp--;
        m.flash = 0.12;
        if (m.hp <= 0) killMonster(m);
        else sfx(Snd.HIT);
    }

    void killMonster(Monster m) {
        m.dead = true;
        m.vy = -260;
        m.vx = rand(-60, 60);
        sfx(Snd.MDIE);
        for (int i = 0; i < 6; i++) {
            double a = rand(0, PI * 2);
            double s = rand(60, 160);
            Spark k = new Spark();
            k.x = m.x;
            k.y = m.y;
            k.vx = Math.cos(a) * s;
            k.vy = Math.sin(a) * s - 60;
            k.life = k.max = 0.6;
            sparks.add(k);
        }
    }

    /** 平台移动、怪物、子弹、碎片等不涉及小海星的更新 */
    void stepWorld(double dt) {
        shake = shake > 0.05 ? shake * Math.exp(-13 * dt) : 0;
        flash = flash > 0.004 ? Math.max(0, flash - dt * 2.4) : 0;
        double bottom = camY + H;

        for (int i = platforms.size() - 1; i >= 0; i--) {
            Platform p = platforms.get(i);
            if (p.vx != 0) {
                p.x += p.vx * dt;
                double h = p.w / 2;
                if (p.x < h) {
                    p.x = h;
                    p.vx = -p.vx;
                } else if (p.x > W - h) {
                    p.x = W - h;
                    p.vx = -p.vx;
                }
            }
            if (p.dip != 0 || p.dipV != 0) {
                p.dipV += (-C.DIP_K * p.dip - C.DIP_C * p.dipV) * dt;
                p.dip += p.dipV * dt;
                if (Math.abs(p.dip) < 0.01 && Math.abs(p.dipV) < 0.5) p.dip = p.dipV = 0;
            }
            if (p.dead) p.fade += dt;
            if (p.item != null && p.item.fired) p.item.t += dt;
            if (p.y > bottom + 80 || (p.dead && p.fade > 0.3)) platforms.remove(i);
        }

        for (int i = monsters.size() - 1; i >= 0; i--) {
            Monster m = monsters.get(i);
            m.t += dt;
            if (m.flash > 0) m.flash -= dt;
            if (m.dead) {
                m.vy += 1300 * dt;
                m.y += m.vy * dt;
                m.x += m.vx * dt;
                m.rot += dt * 8;
            } else if (m.kind == M_BLOB) {
                m.x = m.x0 + Math.sin(m.t * m.sp) * m.amp;
            } else if (m.kind == M_FLYER) {
                m.x += m.vx * dt;
                if (m.x < 30) {
                    m.x = 30;
                    m.vx = -m.vx;
                } else if (m.x > W - 30) {
                    m.x = W - 30;
                    m.vx = -m.vx;
                }
                m.y = m.y0 + Math.sin(m.t * 2.2) * 14;
            } else {
                m.x += m.vx * dt;
                if (m.x < 40) {
                    m.x = 40;
                    m.vx = -m.vx;
                } else if (m.x > W - 40) {
                    m.x = W - 40;
                    m.vx = -m.vx;
                }
                m.y = m.y0 + Math.sin(m.t * 1.5) * 8;
            }
            if (m.y > bottom + 160) monsters.remove(i);
        }

        for (int i = holes.size() - 1; i >= 0; i--) {
            Hole h = holes.get(i);
            h.t += dt;
            if (h.y > bottom + 100) holes.remove(i);
        }

        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.life -= dt;
            boolean hit = false;
            for (int j = 0; j < monsters.size(); j++) {
                Monster m = monsters.get(j);
                if (m.dead) continue;
                if (Math.abs(b.x - m.x) < m.w / 2 + 3 && Math.abs(b.y - m.y) < m.h / 2 + 3) {
                    hitMonster(m);
                    hit = true;
                    break;
                }
            }
            if (hit || b.life <= 0 || b.x < -20 || b.x > W + 20 || b.y < camY - 60) bullets.remove(i);
        }

        for (int i = debris.size() - 1; i >= 0; i--) {
            Debris q = debris.get(i);
            q.vy += 1300 * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.rot += q.vr * dt;
            if (q.y > bottom + 100) debris.remove(i);
        }

        // 特效：原地压缩（保持顺序），过期的放回对象池
        int w = 0;
        for (int i = 0, n = fx.size(); i < n; i++) {
            Fx f = fx.get(i);
            boolean alive = true;
            if (f.delay > 0) {
                f.delay -= dt;
            } else {
                f.life -= dt;
                switch (f.k) {
                    case Fx.PUFF:
                        f.vx *= Math.exp(-3.5 * dt);
                        f.vy *= Math.exp(-2.5 * dt);
                        f.x += f.vx * dt;
                        f.y += f.vy * dt;
                        break;
                    case Fx.RING:
                        f.r += 150 * dt * Math.max(0.15, f.life / f.max);
                        break;
                    case Fx.SHOCK:
                        f.vr *= Math.exp(-f.drag * dt);
                        f.r += f.vr * dt;
                        break;
                    case Fx.PART: {
                        double dd0 = Math.exp(-f.drag * dt);
                        f.vx *= dd0;
                        f.vy = f.vy * dd0 + f.g * dt;
                        f.x += f.vx * dt;
                        f.y += f.vy * dt;
                        f.rot += f.vr * dt;
                        break;
                    }
                    case Fx.FEATHER: {
                        double dd0 = Math.exp(-2.4 * dt);
                        f.vx *= dd0;
                        f.vy = 45 + (f.vy - 45) * dd0;
                        f.ph += f.w * dt;
                        f.x += (f.vx + Math.sin(f.ph) * 26) * dt;
                        f.y += f.vy * dt;
                        f.rot = f.rot0 + Math.sin(f.ph) * 0.7;
                        break;
                    }
                    case Fx.FIRE:
                    case Fx.STREAK:
                        f.vy *= Math.exp(-1.2 * dt);
                        f.x += f.vx * dt;
                        f.y += f.vy * dt;
                        break;
                    default:
                        break;
                }
                if (f.life <= 0) alive = false;
            }
            if (alive) fx.set(w++, f);
            else fxPool.add(f);
        }
        for (int i = fx.size() - 1; i >= w; i--) fx.remove(i);

        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= dt;
            s.vy += 500 * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            if (s.life <= 0) sparks.remove(i);
        }
    }
}
