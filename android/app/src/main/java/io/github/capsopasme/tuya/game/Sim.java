package io.github.capsopasme.tuya.game;

import static io.github.capsopasme.tuya.game.Entities.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 无头模拟测试（普通 JVM 直接跑，CI 里在打包前执行）：
 *  1) 关卡永远可达（相邻平台的高度差不超过起跳高度）
 *  2) 没有 NaN / 数组无限增长
 *  3) 机器人在没有怪物干扰的情况下能一路爬到很高
 *  4) 各个道具、护盾、慢落、蓄力等机制的小测试
 * 用法：java ...Sim            跑全部测试
 *      java ...Sim trace S N  打印逐步轨迹（和旧版 JS 对拍用）
 */
public final class Sim {
    static final double DT = 1.0 / 120;
    static final String[] STATES = {"normal", "propeller", "jetpack", "wings", "darkwings", "dead", "blackhole", "abducted", "fell", "gone"};
    static final String[] SND = {"jump", "spring", "tramp", "break", "shoot", "hit", "mdie", "stomp", "pickup", "prop", "jet", "wings",
            "darkwings", "darkflap", "flap", "smash", "fall", "hole", "abduct", "over", "click"};

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("trace")) {
            trace(Integer.parseInt(args[1]), Integer.parseInt(args[2]), args.length > 3 && args[3].equals("1"));
            return;
        }
        List<String> fails = new ArrayList<>();
        tests(fails);
        if (!fails.isEmpty()) {
            System.err.println("\n失败：\n - " + String.join("\n - ", fails));
            System.exit(1);
        }
        System.out.println("sim 通过");
    }

    static String f(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    static void trace(int seed, int steps, boolean chill) {
        Rng rng = new Rng(seed);
        Game g = new Game(rng);
        g.H = 560;
        g.chill = chill;
        g.reset();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps && g.phase != Game.OVER; i++) {
            double ax = Bot.axis(g);
            Bot.shoot(g);
            g.update(DT, ax);
            StringBuilder ev = new StringBuilder();
            for (int k = 0; k < g.nEvents; k++) {
                if (k > 0) ev.append(',');
                ev.append(SND[g.events[k]]);
            }
            g.nEvents = 0;
            Hero d = g.dd;
            sb.append(i).append(' ').append(f(d.x)).append(' ').append(f(d.y)).append(' ').append(f(d.vy)).append(' ')
                    .append(f(g.camY)).append(' ').append(g.score).append(' ').append(g.platforms.size()).append(' ')
                    .append(g.monsters.size()).append(' ').append(g.holes.size()).append(' ').append(g.bullets.size()).append(' ')
                    .append(STATES[d.state]).append(' ').append(f(d.belly)).append(' ').append(f(d.lean)).append(' ')
                    .append(f(d.roll)).append(' ').append(ev).append('\n');
        }
        System.out.print(sb);
    }

    // ---------- 单局 ----------
    static final class Run {
        int seed, score, maxPlat;
        boolean nan;
        double minRel = Double.POSITIVE_INFINITY, H;
    }

    static Run run(int seed, boolean noHazards, boolean chill, double seconds) {
        Game g = new Game(new Rng(seed));
        g.H = 560;
        g.chill = chill;
        g.reset();
        Run r = new Run();
        r.seed = seed;
        r.H = g.H;
        int steps = 0;
        double maxSteps = seconds / DT;
        while (g.phase != Game.OVER && steps < maxSteps) {
            if (noHazards) {
                g.monsters.clear();
                g.holes.clear();
            }
            double ax = Bot.axis(g);
            Bot.shoot(g);
            g.update(DT, ax);
            g.nEvents = 0;
            steps++;
            r.maxPlat = Math.max(r.maxPlat, g.platforms.size());
            Hero d = g.dd;
            if (!Double.isFinite(d.x) || !Double.isFinite(d.y) || !Double.isFinite(d.vy) || !Double.isFinite(g.camY)) {
                r.nan = true;
                break;
            }
            if (d.state == Hero.NORMAL || Hero.flying(d.state)) r.minRel = Math.min(r.minRel, d.y - g.camY);
        }
        r.score = g.score;
        return r;
    }

    static double worstGap(Game g) {
        ArrayList<Platform> mains = new ArrayList<>();
        for (Platform p : g.platforms) if (p.type != P_BROWN) mains.add(p);
        mains.sort((a, b) -> Double.compare(b.y, a.y));
        double worst = 0;
        for (int i = 1; i < mains.size(); i++) worst = Math.max(worst, mains.get(i - 1).y - mains.get(i).y);
        return worst;
    }

    static Game bare(int seed) {
        Game g = new Game(new Rng(seed));
        g.H = 560;
        g.reset();
        g.platforms.clear();
        g.monsters.clear();
        g.holes.clear();
        g.camY = 0;
        return g;
    }

    static final class Drop {
        List<Integer> names = new ArrayList<>();
        double[] belly = new double[360];
        int hit = -1;
    }

    static Drop dropOnto(double x, Item item) {
        Game g = bare(1);
        Platform p = g.addPlatform(P_NORMAL, 160, 400);
        p.vx = 0;
        p.item = item;
        g.dd.x = x;
        g.dd.y = 300;
        g.dd.prevY = 300;
        g.dd.vy = 200;
        Drop r = new Drop();
        for (int i = 0; i < 360; i++) {
            g.update(DT, 0);
            if (g.nEvents > 0 && r.hit < 0) r.hit = i;
            for (int k = 0; k < g.nEvents; k++) r.names.add(g.events[k]);
            g.nEvents = 0;
            r.belly[i] = g.dd.belly;
        }
        return r;
    }

    static double median(int[] a) {
        int[] s = a.clone();
        Arrays.sort(s);
        return s[s.length / 2];
    }

    static final class Fly {
        int st = -1;
        double h;
        Game g;
    }

    static Fly flyHeight(int type) {
        Game g = bare(7);
        Platform p = g.addPlatform(P_NORMAL, 160, 450);
        p.item = new Item(type, 0);
        p.item.h = 30;
        g.dd.x = 160;
        g.dd.y = 440;
        g.dd.prevY = 440;
        g.dd.vy = 50;
        double y0 = g.dd.y;
        Fly f = new Fly();
        double top = y0;
        for (int i = 0; i < 120 * 8; i++) {
            g.update(DT, 0);
            g.nEvents = 0;
            if (f.st < 0 && g.dd.state != Hero.NORMAL) f.st = g.dd.state;
            top = Math.min(top, g.dd.y);
            if (f.st >= 0 && g.dd.state == Hero.NORMAL && g.dd.vy > 0) break;
        }
        f.h = y0 - top;
        f.g = g;
        return f;
    }

    static void tests(List<String> fails) {
        double jumpH = (C.JUMP_V * C.JUMP_V) / (2 * C.GRAVITY);
        double worst = 0;
        for (int seed = 1; seed <= 40; seed++) {
            Game g = new Game(new Rng(seed * 7919));
            g.H = 560;
            g.reset();
            while (g.scoreAtY(g.topY) < 30000) g.genRow();
            worst = Math.max(worst, worstGap(g));
        }
        System.out.printf(Locale.ROOT, "起跳高度 %.1f px，生成关卡里最大的相邻平台间距 %.1f px%n", jumpH, worst);
        if (worst > jumpH * 0.8) fails.add("平台间距过大，离起跳高度太近");

        int[] clean = new int[16];
        for (int s = 1; s <= 16; s++) {
            Run r = run(s, true, false, 400);
            clean[s - 1] = r.score;
            if (r.nan) fails.add("seed " + s + " 出现 NaN");
            if (r.minRel < r.H * 0.1) fails.add("seed " + s + " 角色跑出了屏幕上方");
            if (r.score < 4000) fails.add("seed " + s + " 无怪物下只到 " + r.score);
        }
        System.out.printf("无怪物 16 局得分中位数 %.0f%n", median(clean));

        int[] full = new int[16];
        for (int s = 100; s < 116; s++) {
            Run r = run(s, false, false, 400);
            full[s - 100] = r.score;
            if (r.nan) fails.add("seed " + s + " 出现 NaN");
            if (r.maxPlat > 120) fails.add("seed " + s + " 平台数组过大 " + r.maxPlat);
        }
        System.out.printf("有怪物 16 局得分中位数 %.0f%n", median(full));
        if (median(full) < 1500) fails.add("有怪物时中位数过低");

        int[] ch = new int[12];
        for (int s = 200; s < 212; s++) {
            Run r = run(s, false, true, 400);
            ch[s - 200] = r.score;
            if (r.nan) fails.add("畅快 seed " + s + " 出现 NaN");
        }
        System.out.printf("畅快模式 12 局得分中位数 %.0f%n", median(ch));
        if (!(median(ch) > median(full))) fails.add("畅快模式得分中位数没有比普通模式高");

        // 弹簧：身体中心在平台内、离弹簧较远时也触发；身体中心出了平台只算普通落地
        Drop r = dropOnto(186, new Item(I_SPRING, -24));
        if (!r.names.contains(Snd.SPRING) || r.names.contains(Snd.JUMP)) fails.add("身体中心在平台内时应当触发弹簧");
        r = dropOnto(196, new Item(I_SPRING, -24));
        if (r.names.contains(Snd.SPRING) || !r.names.contains(Snd.JUMP)) fails.add("身体中心在平台外时不应触发弹簧");
        r = dropOnto(136, new Item(I_SPRING, -24));
        if (!r.names.contains(Snd.SPRING)) fails.add("正踩弹簧没有触发");
        r = dropOnto(186, new Item(I_TRAMP, -24));
        if (!r.names.contains(Snd.TRAMP)) fails.add("蹦床：身体中心在平台内应当触发");

        // 肚子晃动
        r = dropOnto(160, null);
        double peak = 0, tail = 0;
        int cross = 0;
        for (int i = 0; i < 100; i++) {
            double v = r.belly[r.hit + i];
            if (i < 60) peak = Math.max(peak, Math.abs(v));
            if (i > 0 && Math.signum(v) != Math.signum(r.belly[r.hit + i - 1])) cross++;
        }
        for (int i = 95; i < 110; i++) tail = Math.max(tail, Math.abs(r.belly[r.hit + i]));
        System.out.printf(Locale.ROOT, "肚子晃动：峰值 %.2f，过零 %d 次，末段残余 %.3f%n", peak, cross, tail);
        if (!(peak > 0.4 && peak <= 1)) fails.add("肚子晃动幅度不合理：" + peak);
        if (cross < 2) fails.add("肚子只晃了不到一个来回");
        if (tail > 0.12) fails.add("肚子没有收住：" + tail);

        // 自动瞄准
        Game g = bare(2);
        g.dd.x = 100;
        g.dd.y = 450;
        Monster m = g.makeMonster(M_BLOB, 260, 200);
        m.amp = 0;
        g.monsters.add(m);
        g.fire(true, 20, 450);
        Bullet b = g.bullets.get(0);
        if (Math.abs(Math.atan2(b.vy, b.vx) - Math.atan2(200 - (450 - 32), 260 - 100)) > 0.05) fails.add("自动瞄准没有指向怪物");
        g = bare(3);
        g.dd.x = 100;
        g.dd.y = 450;
        g.fire(true, 250, 100);
        b = g.bullets.get(0);
        if (Math.abs(Math.atan2(b.vy, b.vx) - Math.atan2(100 - (450 - 32), 250 - 100)) > 0.05) fails.add("没有怪物时应朝点击方向");
        g = bare(5);
        g.dd.x = 60;
        g.dd.y = 480;
        Monster fl = g.makeMonster(M_FLYER, 200, 150);
        fl.vx = 80;
        g.monsters.add(fl);
        g.fire(true, 0, 0);
        for (int i = 0; i < 360 && !fl.dead && fl.hp > 0; i++) g.update(DT, 0);
        if (fl.hp == 2) fails.add("自动瞄准的子弹没有命中移动中的怪物");

        // 棕色平台和真平台在同一步里都被穿过
        g = bare(6);
        g.addPlatform(P_NORMAL, 160, 400);
        Platform brn = g.addPlatform(P_BROWN, 160, 396);
        g.dd.x = 160;
        g.dd.y = 390;
        g.dd.prevY = 390;
        g.dd.vy = 1100;
        boolean jumped = false;
        for (int i = 0; i < 3; i++) {
            g.update(DT, 0);
            for (int k = 0; k < g.nEvents; k++) if (g.events[k] == Snd.JUMP) jumped = true;
            g.nEvents = 0;
        }
        if (!brn.dead) fails.add("同一步里被穿过的棕色平台没有碎");
        if (!jumped || g.dd.vy >= 0) fails.add("棕色平台挡住了同一步里的真平台");

        Fly jet = flyHeight(I_JET);
        Fly wing = flyHeight(I_WINGS);
        Fly dark = flyHeight(I_DARK);
        System.out.printf(Locale.ROOT, "飞行高度：喷气背包 %.0f，飞翼 %.0f，紫黑飞翼 %.0f px%n", jet.h, wing.h, dark.h);
        if (wing.st != Hero.WINGS) fails.add("拿到飞翼后没有进入 wings 状态");
        if (!(wing.h > jet.h * 1.8)) fails.add("飞翼应该比喷气背包飞得高得多");
        if (dark.st != Hero.DARKWINGS) fails.add("拿到紫黑飞翼后没有进入 darkwings 状态");
        if (!(dark.h > wing.h * 1.2)) fails.add("紫黑飞翼应该比白色飞翼飞得更高");
        if (!dark.g.dd.shieldDark) fails.add("紫黑飞翼结束后的护盾不是紫色的");
        if (!(jet.g.dd.glide > 0 || jet.g.dd.glideK > 0)) fails.add("喷气背包用完后没有进入慢落");

        g = bare(8);
        g.dd.x = 160;
        g.dd.y = 400;
        g.dd.state = Hero.WINGS;
        g.dd.vy = -1000;
        Monster mon = g.makeMonster(M_BLOB, 160, 300);
        mon.amp = 0;
        g.monsters.add(mon);
        g.holes.add(new Hole(160, 120, 28));
        for (int i = 0; i < 60; i++) g.update(DT, 0);
        if (!mon.dead) fails.add("飞翼没有撞碎路上的怪物");
        if (!g.holes.isEmpty()) fails.add("飞翼没有撞散路上的黑洞");
        if (g.dd.state != Hero.WINGS) fails.add("飞翼飞行中被打断了");

        g = bare(10);
        g.dd.x = 160;
        g.dd.y = 400;
        g.dd.state = Hero.DARKWINGS;
        g.dd.vy = -900;
        double turned = 0, prevR = 0;
        for (int i = 0; i < 240; i++) {
            g.update(DT, 0);
            double dr = g.dd.roll - prevR;
            if (dr > Math.PI) dr -= C.TAU;
            else if (dr < -Math.PI) dr += C.TAU;
            turned += dr;
            prevR = g.dd.roll;
        }
        if (Math.abs(turned) < C.TAU * 3) fails.add("紫黑飞翼飞行中小海星没有转起来");
        g.dd.stateT = C.DARK_T;
        for (int i = 0; i < 180; i++) g.update(DT, 0);
        if (g.dd.state != Hero.NORMAL || Math.abs(g.dd.roll) > 0.02) fails.add("紫黑飞翼结束后没有回正");

        // 慢落
        double vN = fall(false), vG = fall(true);
        System.out.printf(Locale.ROOT, "下落 0.8 秒后的速度：普通 %.0f，慢落 %.0f%n", vN, vG);
        if (!(vG < vN * 0.6)) fails.add("慢落没有明显变慢");
        g = bare(12);
        Platform sp = g.addPlatform(P_NORMAL, 160, 450);
        sp.item = new Item(I_SPRING, 0);
        g.dd.x = 160;
        g.dd.y = g.dd.prevY = 430;
        g.dd.vy = 200;
        double topY = 999;
        for (int i = 0; i < 180; i++) {
            g.update(DT, 0);
            topY = Math.min(topY, g.dd.y);
        }
        double rise = 436 - topY, ideal = C.SPRING_V * C.SPRING_V / (2 * C.GRAVITY);
        if (Math.abs(rise - ideal) > ideal * 0.08) fails.add("弹簧起跳高度被慢落改变了");

        // 蓄力
        g = bare(13);
        g.addPlatform(P_NORMAL, 160, 400);
        g.dd.x = 160;
        g.dd.y = g.dd.prevY = 396;
        g.dd.vy = 300;
        double maxLean = 0;
        for (int i = 0; i < 18; i++) {
            g.update(DT, 1);
            maxLean = Math.max(maxLean, g.dd.lean);
        }
        if (!(maxLean > 0.2)) fails.add("落地蓄力时身体没有朝倾斜方向压下去");

        // 护盾
        g = bare(9);
        g.dd.shield = 1;
        g.dd.x = 160;
        g.dd.y = 300;
        g.dd.vy = -200;
        Monster m2 = g.makeMonster(M_BLOB, 160, 290);
        m2.amp = 0;
        g.monsters.add(m2);
        g.holes.add(new Hole(160, 250, 28));
        for (int i = 0; i < 10; i++) g.update(DT, 0);
        if (g.dd.state != Hero.NORMAL) fails.add("护盾期间仍然被怪物/黑洞干掉了");
        if (!m2.dead) fails.add("护盾期间撞到的怪物应当被撞飞");
        g.dd.shield = 0;
        Monster m3 = g.makeMonster(M_BLOB, g.dd.x, g.dd.y - 10);
        m3.amp = 0;
        g.monsters.add(m3);
        g.holes.clear();
        for (int i = 0; i < 5; i++) g.update(DT, 0);
        if (g.dd.state != Hero.DEAD) fails.add("护盾结束后撞到怪物应该会死");

        Run a = run(5, true, false, 60), a2 = run(5, true, false, 60);
        if (a.score != a2.score) fails.add("同一种子两次结果不同");
    }

    static double fall(boolean glide) {
        Game g = bare(11);
        g.dd.x = 160;
        g.dd.y = 300;
        g.dd.vy = 0;
        g.dd.glide = glide ? C.GLIDE_T : 0;
        g.dd.glideK = glide ? 1 : 0;
        for (int i = 0; i < 96; i++) g.update(DT, 0);
        return g.dd.vy;
    }
}
