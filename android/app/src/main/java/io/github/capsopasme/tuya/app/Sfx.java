package io.github.capsopasme.tuya.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

import io.github.capsopasme.tuya.game.Snd;

/** 音效：启动时在后台线程离线合成成 WAV，交给 SoundPool（原生混音、低延迟）。 */
final class Sfx {
    private static final int VERSION = 1; // 合成配方改了就加一，让缓存的 WAV 重新生成
    private final SoundPool pool;
    private final int[] ids = new int[Snd.COUNT];
    // 按 SoundPool 的 sampleId 记录加载完成（回调可能比 load() 返回还早，所以不按音效编号记）
    private final boolean[] done = new boolean[512];
    volatile boolean enabled = true;

    Sfx(Context ctx) {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(aa).build();
        pool.setOnLoadCompleteListener((p, sampleId, status) -> {
            if (status == 0 && sampleId > 0 && sampleId < done.length) done[sampleId] = true;
        });
        final File dir = new File(ctx.getCacheDir(), "sfx" + VERSION);
        Thread t = new Thread(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) return;
                Synth s = null;
                for (int i = 0; i < Snd.COUNT; i++) {
                    File f = new File(dir, i + ".wav");
                    if (!f.isFile() || f.length() < 64) {
                        if (s == null) s = new Synth();
                        File tmp = new File(dir, i + ".tmp");
                        try (FileOutputStream o = new FileOutputStream(tmp)) {
                            o.write(Synth.wav(s.render(i)));
                        }
                        if (!tmp.renameTo(f)) continue;
                    }
                    ids[i] = pool.load(f.getAbsolutePath(), 1);
                }
            } catch (Exception e) {
                Log.w("tuya", "音效生成失败", e);
            }
        }, "sfx-synth");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    void play(int id) {
        if (!enabled || id < 0 || id >= ids.length) return;
        int sid = ids[id];
        if (sid <= 0 || sid >= done.length || !done[sid]) return;
        pool.play(sid, 1f, 1f, id == Snd.CLICK ? 0 : 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
