package io.github.capsopasme.tuya.game;

import io.github.capsopasme.tuya.game.Entities.Monster;
import io.github.capsopasme.tuya.game.Entities.Platform;

/**
 * 自动驾驶：按抛物线预测落到每个平台高度的时间，选“够得着的最高平台”，再左右（含环绕）靠过去。
 * 用于模拟测试和 CI 里的自动演示，正常游戏不会用到。
 */
public final class Bot {
    private Bot() {}

    public static double axis(Game g) {
        Hero d = g.dd;
        Platform best = null;
        double bestScore = 0, bestT = 0;
        for (int i = 0; i < g.platforms.size(); i++) {
            Platform p = g.platforms.get(i);
            if (p.dead || p.type == Entities.P_BROWN) continue;
            double a = C.GRAVITY / 2;
            double b = d.vy;
            double c = d.y - p.y;
            double disc = b * b - 4 * a * c;
            if (disc < 0) continue;
            double t = (-b + Math.sqrt(disc)) / (2 * a);
            if (t < 0.02) continue;
            double dx = C.wrapDx(p.x, d.x);
            double reach = C.MAX_VX * t * 0.9 + C.PLAT_W / 2;
            if (Math.abs(dx) > reach) continue;
            double score = -p.y * 10 - Math.abs(dx) * 0.01;
            if (best == null || score > bestScore) {
                best = p;
                bestScore = score;
                bestT = t;
            }
        }
        if (best == null) return 0;
        double px = best.x + best.vx * bestT;
        double dx = C.wrapDx(px, d.x);
        return C.clamp(dx / 18, -1, 1);
    }

    /** 朝屏幕上半部分、在头顶上方的第一只怪物开枪 */
    public static void shoot(Game g) {
        Hero d = g.dd;
        for (int i = 0; i < g.monsters.size(); i++) {
            Monster m = g.monsters.get(i);
            if (m.dead) continue;
            double sy = m.y - g.camY;
            if (sy > 0 && sy < g.H * 0.55 && m.y < d.y - 20) {
                g.shootAt(m.x, sy);
                break;
            }
        }
    }
}
