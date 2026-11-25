package io.github.capsopasme.tuya.app;

import io.github.capsopasme.tuya.game.Snd;

/**
 * 离线合成全部音效（和旧版 WebAudio 的振荡器 + 包络 + 低通噪声一模一样的配方），启动时算成 PCM 交给 SoundPool。
 * 没有任何音频文件。纯 Java，不依赖安卓。
 */
final class Synth {
    static final int SR = 44100;
    private static final double MASTER = 0.5;
    private static final int SINE = 0, TRIANGLE = 1, SQUARE = 2, SAW = 3;

    private float[] buf;
    private final float[] noise;

    Synth() {
        noise = new float[SR];
        long x = 0x2545F4914F6CDD1DL;
        for (int i = 0; i < noise.length; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            noise[i] = (float) (((x >>> 11) * 0x1.0p-53) * 2 - 1);
        }
    }

    private void ensure(double end) {
        int n = (int) Math.ceil(end * SR) + 2;
        if (buf.length < n) {
            float[] b = new float[n];
            System.arraycopy(buf, 0, b, 0, buf.length);
            buf = b;
        }
    }

    private static double expRamp(double v0, double v1, double u) {
        return v0 * Math.pow(v1 / v0, u);
    }

    // PolyBLEP：方波 / 锯齿波的带限修正，避免高音刺耳的混叠
    private static double blep(double t, double dt) {
        if (t < dt) {
            t /= dt;
            return t + t - t * t - 1;
        } else if (t > 1 - dt) {
            t = (t - 1) / dt;
            return t * t + t + t + 1;
        }
        return 0;
    }

    /** 振荡器：频率从 f0 指数滑到 f1；音量 12ms 起音到 vol，再指数衰减到 dur 结束 */
    private void tone(int type, double f0, double f1, double dur, double vol, double delay) {
        double t0 = delay;
        double end = t0 + dur + 0.03;
        ensure(end);
        int i0 = (int) Math.round(t0 * SR), i1 = (int) Math.ceil(end * SR);
        double phase = 0;
        for (int i = i0; i < i1; i++) {
            double t = (double) i / SR - t0;
            double f = t < dur ? expRamp(f0, Math.max(1, f1), t / dur) : Math.max(1, f1);
            double g;
            if (t < 0.012) g = expRamp(0.0001, vol, t / 0.012);
            else if (t < dur) g = expRamp(vol, 0.0001, (t - 0.012) / (dur - 0.012));
            else g = 0.0001;
            double dt = f / SR;
            double s;
            switch (type) {
                case SINE:
                    s = Math.sin(phase * Math.PI * 2);
                    break;
                case TRIANGLE:
                    s = 1 - 4 * Math.abs(phase - 0.5);
                    s = -s;
                    break;
                case SQUARE: {
                    s = phase < 0.5 ? 1 : -1;
                    s += blep(phase, dt);
                    double p2 = phase + 0.5;
                    if (p2 >= 1) p2 -= 1;
                    s -= blep(p2, dt);
                    break;
                }
                default: {
                    s = 2 * phase - 1;
                    s -= blep(phase, dt);
                    break;
                }
            }
            buf[i] += (float) (s * g);
            phase += dt;
            if (phase >= 1) phase -= 1;
        }
    }

    /** 白噪声经过低通滤波（RBJ biquad，Q 和 WebAudio 默认的 1dB 一致），音量指数衰减 */
    private void noise(double dur, double vol, double cutoff, double delay) {
        double t0 = delay;
        double end = t0 + dur + 0.03;
        ensure(end);
        double w0 = 2 * Math.PI * cutoff / SR;
        double q = Math.pow(10, 1 / 20.0);
        double alpha = Math.sin(w0) / (2 * q);
        double cw = Math.cos(w0);
        double b0 = (1 - cw) / 2, b1 = 1 - cw, b2 = (1 - cw) / 2, a0 = 1 + alpha, a1 = -2 * cw, a2 = 1 - alpha;
        b0 /= a0;
        b1 /= a0;
        b2 /= a0;
        a1 /= a0;
        a2 /= a0;
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        int i0 = (int) Math.round(t0 * SR), i1 = (int) Math.ceil(end * SR);
        for (int i = i0; i < i1; i++) {
            double t = (double) i / SR - t0;
            double x = noise[(i - i0) % noise.length];
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            double g = t < dur ? expRamp(vol, 0.0001, t / dur) : 0.0001;
            buf[i] += (float) (y * g);
        }
    }

    /** 合成一个音效，返回 16 位 PCM */
    short[] render(int id) {
        buf = new float[SR / 2];
        switch (id) {
            case Snd.JUMP:
                tone(SINE, 240, 520, 0.18, 0.3, 0);
                tone(TRIANGLE, 480, 900, 0.1, 0.06, 0);
                break;
            case Snd.SPRING:
                tone(SINE, 200, 900, 0.35, 0.3, 0);
                tone(TRIANGLE, 400, 1300, 0.22, 0.08, 0);
                break;
            case Snd.TRAMP:
                tone(SINE, 160, 700, 0.5, 0.35, 0);
                tone(SQUARE, 320, 1400, 0.3, 0.04, 0);
                break;
            case Snd.BREAK:
                noise(0.28, 0.4, 1800, 0);
                tone(SQUARE, 200, 60, 0.2, 0.07, 0);
                break;
            case Snd.SHOOT:
                tone(SQUARE, 1100, 380, 0.09, 0.09, 0);
                break;
            case Snd.HIT:
                tone(SQUARE, 300, 120, 0.1, 0.11, 0);
                noise(0.08, 0.2, 3000, 0);
                break;
            case Snd.MDIE:
                tone(SAW, 500, 90, 0.4, 0.13, 0);
                break;
            case Snd.STOMP:
                tone(SINE, 300, 700, 0.14, 0.25, 0);
                tone(SQUARE, 200, 100, 0.1, 0.08, 0);
                break;
            case Snd.PICKUP:
                tone(TRIANGLE, 500, 500, 0.08, 0.25, 0);
                tone(TRIANGLE, 800, 800, 0.12, 0.25, 0.07);
                break;
            case Snd.PROP:
                noise(0.14, 0.12, 900, 0);
                break;
            case Snd.JET:
                noise(0.16, 0.16, 500, 0);
                tone(SAW, 90, 70, 0.16, 0.05, 0);
                break;
            case Snd.WINGS: {
                // 一串往上走的亮晶晶琶音 + 一声低沉的“呼”
                double[] notes = {523, 659, 784, 1047, 1319};
                for (int i = 0; i < notes.length; i++) {
                    tone(TRIANGLE, notes[i], notes[i], 0.16, 0.2, i * 0.055);
                    tone(SINE, notes[i] * 2, notes[i] * 2, 0.12, 0.05, i * 0.055 + 0.02);
                }
                tone(SINE, 110, 330, 0.5, 0.25, 0);
                noise(0.35, 0.18, 1200, 0);
                break;
            }
            case Snd.DARKWINGS: {
                // 先“吸”一口气，再一声低沉的爆响，接一串冷色调的和声小调琶音
                tone(SINE, 1500, 160, 0.16, 0.12, 0);
                noise(0.15, 0.1, 3200, 0);
                tone(SINE, 62, 38, 0.8, 0.45, 0.15);
                tone(SAW, 124, 62, 0.45, 0.07, 0.15);
                noise(0.45, 0.26, 800, 0.15);
                double[] notes = {440, 523, 659, 831, 988, 1319};
                for (int i = 0; i < notes.length; i++) {
                    tone(TRIANGLE, notes[i], notes[i], 0.2, 0.17, 0.2 + i * 0.05);
                    tone(SINE, notes[i] * 1.5, notes[i] * 1.5, 0.16, 0.05, 0.22 + i * 0.05);
                }
                break;
            }
            case Snd.CLOAK: {
                // 一声低沉的钟 + 一阵往上卷的风，接五声音阶的拨弦（像古琴泛音）往上走
                tone(SINE, 98, 92, 1.1, 0.32, 0);
                tone(SINE, 196, 190, 0.8, 0.12, 0);
                noise(0.55, 0.16, 900, 0);
                tone(SINE, 300, 1400, 0.45, 0.06, 0.05);
                double[] notes = {587, 659, 784, 880, 1175, 1319};
                for (int i = 0; i < notes.length; i++) {
                    tone(TRIANGLE, notes[i], notes[i] * 0.995, 0.32, 0.16, 0.12 + i * 0.07);
                    tone(SINE, notes[i] * 2, notes[i] * 2, 0.25, 0.05, 0.13 + i * 0.07);
                }
                break;
            }
            case Snd.WHOOSH:
                // 飞遁时轻轻的风声
                noise(0.3, 0.09, 520, 0);
                tone(SINE, 140, 90, 0.25, 0.04, 0);
                break;
            case Snd.DARKFLAP:
                noise(0.18, 0.2, 480, 0);
                tone(SINE, 110, 55, 0.18, 0.1, 0);
                tone(TRIANGLE, 880, 660, 0.06, 0.02, 0);
                break;
            case Snd.FLAP:
                noise(0.16, 0.2, 700, 0);
                tone(SINE, 150, 90, 0.14, 0.08, 0);
                break;
            case Snd.SMASH:
                tone(SQUARE, 420, 90, 0.16, 0.12, 0);
                noise(0.14, 0.3, 2500, 0);
                tone(TRIANGLE, 900, 1400, 0.1, 0.08, 0.04);
                break;
            case Snd.FALL:
                tone(SINE, 700, 90, 0.9, 0.25, 0);
                break;
            case Snd.HOLE:
                tone(SINE, 900, 60, 0.8, 0.2, 0);
                break;
            case Snd.ABDUCT:
                tone(SINE, 300, 1200, 0.8, 0.14, 0);
                break;
            case Snd.OVER:
                tone(TRIANGLE, 392, 392, 0.18, 0.22, 0);
                tone(TRIANGLE, 330, 330, 0.18, 0.22, 0.18);
                tone(TRIANGLE, 262, 262, 0.32, 0.22, 0.36);
                break;
            case Snd.LIFE: {
                // 叮叮叮往上跳的大三和弦 + 一声软软的“啵”
                double[] notes = {784, 988, 1175, 1568};
                for (int i = 0; i < notes.length; i++) {
                    tone(TRIANGLE, notes[i], notes[i], 0.18, 0.2, i * 0.06);
                    tone(SINE, notes[i] * 2, notes[i] * 2, 0.12, 0.05, i * 0.06 + 0.01);
                }
                tone(SINE, 300, 600, 0.12, 0.2, 0);
                break;
            }
            case Snd.REVIVE: {
                // 往上扫的光 + 一个明亮的长和弦，带一点闪闪的噪声
                tone(SINE, 180, 900, 0.6, 0.3, 0);
                tone(TRIANGLE, 360, 1800, 0.45, 0.06, 0);
                noise(0.5, 0.12, 6000, 0.05);
                double[] chord = {523, 659, 784, 1047};
                for (int i = 0; i < chord.length; i++) {
                    tone(TRIANGLE, chord[i], chord[i], 0.7, 0.14, 0.18 + i * 0.03);
                    tone(SINE, chord[i] * 2, chord[i] * 2, 0.5, 0.04, 0.2 + i * 0.03);
                }
                break;
            }
            case Snd.LUCK: {
                // 五声音阶的一串亮晶晶快速琶音
                double[] notes = {1047, 1175, 1319, 1568, 1760, 2093, 2349};
                for (int i = 0; i < notes.length; i++) {
                    tone(TRIANGLE, notes[i], notes[i], 0.14, 0.15, i * 0.04);
                    tone(SINE, notes[i] * 1.5, notes[i] * 1.5, 0.1, 0.04, i * 0.04 + 0.02);
                }
                noise(0.3, 0.06, 8000, 0);
                break;
            }
            case Snd.DRAGON: {
                // 玻璃碎 -> 一口吸气 -> 低沉的龙吼（锯齿 + 方波 + 噪声 + 超低音）-> 一段铜管味的号角
                noise(0.12, 0.25, 7000, 0);
                tone(TRIANGLE, 2400, 1800, 0.08, 0.06, 0);
                tone(SINE, 1600, 140, 0.2, 0.12, 0.02);
                tone(SAW, 190, 58, 1.0, 0.16, 0.2);
                tone(SAW, 196, 61, 0.9, 0.08, 0.21);
                tone(SQUARE, 95, 42, 0.85, 0.07, 0.2);
                tone(SINE, 60, 32, 1.1, 0.5, 0.2);
                noise(0.9, 0.38, 650, 0.2);
                noise(0.5, 0.18, 2200, 0.22);
                double[] horn = {262, 392, 523, 784};
                for (int i = 0; i < horn.length; i++) {
                    double dl = 0.5 + i * 0.12;
                    tone(SAW, horn[i], horn[i], 0.32, 0.055, dl);
                    tone(TRIANGLE, horn[i], horn[i], 0.32, 0.12, dl);
                    tone(SQUARE, horn[i] * 0.5, horn[i] * 0.5, 0.28, 0.025, dl);
                }
                break;
            }
            case Snd.DRAGONFLAP:
                // 巨大翅膀扇一下：很低的“呼——”
                noise(0.24, 0.26, 360, 0);
                tone(SINE, 85, 42, 0.24, 0.16, 0);
                break;
            case Snd.FIREBALL:
                noise(0.16, 0.12, 2400, 0);
                tone(SAW, 320, 110, 0.14, 0.04, 0);
                break;
            case Snd.BOOM:
                tone(SINE, 130, 34, 0.55, 0.42, 0);
                noise(0.45, 0.34, 1300, 0);
                tone(SQUARE, 170, 48, 0.25, 0.06, 0);
                break;
            case Snd.CLICK:
                tone(TRIANGLE, 600, 600, 0.05, 0.12, 0);
                break;
            default:
                break;
        }
        // 去掉尾部的静音
        int n = buf.length;
        while (n > 1 && Math.abs(buf[n - 1]) < 1e-5f) n--;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double v = buf[i] * MASTER;
            v = v > 1 ? 1 : v < -1 ? -1 : v;
            out[i] = (short) Math.round(v * 32767);
        }
        return out;
    }

    /** 16 位单声道 WAV */
    static byte[] wav(short[] pcm) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataLen).put(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '})
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(SR).putInt(SR * 2).putShort((short) 2)
                .putShort((short) 16).put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataLen);
        for (short s : pcm) bb.putShort(s);
        return b;
    }
}
