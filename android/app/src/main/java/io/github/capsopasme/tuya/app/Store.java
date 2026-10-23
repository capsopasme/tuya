package io.github.capsopasme.tuya.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;

/** 设置、最高分、排行榜（前 5 名），存在 SharedPreferences 里。 */
final class Store {
    static final double[] SENS = {0.75, 1, 1.35}; // 灵敏度：低 / 中 / 高

    boolean sound = true;
    int sens = 1;
    boolean hero3d = true;
    boolean chill;
    int fps = 120;
    boolean perf;
    int best;
    int[] scores = new int[0];

    private final SharedPreferences sp;

    Store(Context c) {
        sp = c.getSharedPreferences("tuya", Context.MODE_PRIVATE);
        sound = sp.getBoolean("sound", true);
        sens = sp.getInt("sens", 1);
        if (sens < 0 || sens > 2) sens = 1;
        hero3d = sp.getBoolean("hero3d", true);
        chill = sp.getBoolean("chill", false);
        fps = sp.getInt("fps", 120);
        if (fps != 60 && fps != 120) fps = 120;
        perf = sp.getBoolean("perf", false);
        best = Math.max(0, sp.getInt("best", 0));
        scores = parse(sp.getString("scores", ""));
    }

    boolean migrated() {
        return sp.getBoolean("migrated", false);
    }

    void setMigrated() {
        sp.edit().putBoolean("migrated", true).apply();
    }

    void saveSettings() {
        sp.edit().putBoolean("sound", sound).putInt("sens", sens).putBoolean("hero3d", hero3d).putBoolean("chill", chill)
                .putInt("fps", fps).putBoolean("perf", perf).apply();
    }

    void saveScores() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) b.append(',');
            b.append(scores[i]);
        }
        sp.edit().putInt("best", best).putString("scores", b.toString()).apply();
    }

    /** 新的一局成绩：返回是否破纪录 */
    boolean addScore(int score) {
        boolean nb = score > best && score > 0;
        if (nb) best = score;
        if (score > 0) {
            int[] s = Arrays.copyOf(scores, scores.length + 1);
            s[scores.length] = score;
            Arrays.sort(s);
            int n = Math.min(5, s.length);
            int[] top = new int[n];
            for (int i = 0; i < n; i++) top[i] = s[s.length - 1 - i];
            scores = top;
        }
        saveScores();
        return nb;
    }

    /** 合并旧版（WebView）里存的成绩 */
    void mergeOld(int oldBest, int[] oldScores) {
        best = Math.max(best, oldBest);
        int[] s = Arrays.copyOf(scores, scores.length + oldScores.length);
        System.arraycopy(oldScores, 0, s, scores.length, oldScores.length);
        Arrays.sort(s);
        int n = Math.min(5, s.length);
        int[] top = new int[n];
        for (int i = 0; i < n; i++) top[i] = s[s.length - 1 - i];
        scores = top;
        saveScores();
    }

    private static int[] parse(String s) {
        if (s == null || s.isEmpty()) return new int[0];
        String[] parts = s.split(",");
        int[] out = new int[parts.length];
        int n = 0;
        for (String p : parts) {
            try {
                int v = Integer.parseInt(p.trim());
                if (v > 0) out[n++] = v;
            } catch (NumberFormatException ignored) {
            }
        }
        return Arrays.copyOf(out, n);
    }
}
