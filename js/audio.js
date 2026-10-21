var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  // 用 WebAudio 现场合成音效，不需要任何音频文件
  let ac = null;
  let master = null;
  let noiseBuf = null;
  const A = (DJ.audio = { enabled: true });

  A.unlock = function () {
    if (!ac) {
      const AC = globalThis.AudioContext || globalThis.webkitAudioContext;
      if (!AC) return;
      try {
        ac = new AC();
      } catch (e) {
        return;
      }
      master = ac.createGain();
      master.gain.value = 0.5;
      master.connect(ac.destination);
      noiseBuf = ac.createBuffer(1, ac.sampleRate, ac.sampleRate);
      const d = noiseBuf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    if (ac.state === 'suspended') {
      const r = ac.resume();
      if (r && r.catch) r.catch(function () {}); // 没有用户手势时会被拒绝，下次点击再试
    }
  };

  function tone(type, f0, f1, dur, vol, delay) {
    const t0 = ac.currentTime + (delay || 0);
    const o = ac.createOscillator();
    const g = ac.createGain();
    o.type = type;
    o.frequency.setValueAtTime(f0, t0);
    o.frequency.exponentialRampToValueAtTime(Math.max(1, f1), t0 + dur);
    g.gain.setValueAtTime(0.0001, t0);
    g.gain.exponentialRampToValueAtTime(vol, t0 + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g);
    g.connect(master);
    o.start(t0);
    o.stop(t0 + dur + 0.03);
  }

  function noise(dur, vol, cutoff, delay) {
    const t0 = ac.currentTime + (delay || 0);
    const s = ac.createBufferSource();
    s.buffer = noiseBuf;
    const f = ac.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.value = cutoff;
    const g = ac.createGain();
    g.gain.setValueAtTime(vol, t0);
    g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    s.connect(f);
    f.connect(g);
    g.connect(master);
    s.start(t0);
    s.stop(t0 + dur + 0.03);
  }

  const S = {
    jump: function () {
      tone('sine', 240, 520, 0.18, 0.3);
      tone('triangle', 480, 900, 0.1, 0.06);
    },
    spring: function () {
      tone('sine', 200, 900, 0.35, 0.3);
      tone('triangle', 400, 1300, 0.22, 0.08);
    },
    tramp: function () {
      tone('sine', 160, 700, 0.5, 0.35);
      tone('square', 320, 1400, 0.3, 0.04);
    },
    break: function () {
      noise(0.28, 0.4, 1800);
      tone('square', 200, 60, 0.2, 0.07);
    },
    shoot: function () {
      tone('square', 1100, 380, 0.09, 0.09);
    },
    hit: function () {
      tone('square', 300, 120, 0.1, 0.11);
      noise(0.08, 0.2, 3000);
    },
    mdie: function () {
      tone('sawtooth', 500, 90, 0.4, 0.13);
    },
    stomp: function () {
      tone('sine', 300, 700, 0.14, 0.25);
      tone('square', 200, 100, 0.1, 0.08);
    },
    pickup: function () {
      tone('triangle', 500, 500, 0.08, 0.25);
      tone('triangle', 800, 800, 0.12, 0.25, 0.07);
    },
    prop: function () {
      noise(0.14, 0.12, 900);
    },
    jet: function () {
      noise(0.16, 0.16, 500);
      tone('sawtooth', 90, 70, 0.16, 0.05);
    },
    // 飞翼：一串往上走的亮晶晶琶音 + 一声低沉的“呼”
    wings: function () {
      const notes = [523, 659, 784, 1047, 1319];
      for (let i = 0; i < notes.length; i++) {
        tone('triangle', notes[i], notes[i], 0.16, 0.2, i * 0.055);
        tone('sine', notes[i] * 2, notes[i] * 2, 0.12, 0.05, i * 0.055 + 0.02);
      }
      tone('sine', 110, 330, 0.5, 0.25);
      noise(0.35, 0.18, 1200);
    },
    // 紫黑飞翼：先“吸”一口气（高处往下的滑音 + 嘶嘶声），再一声低沉的爆响，接一串冷色调的和声小调琶音
    darkwings: function () {
      tone('sine', 1500, 160, 0.16, 0.12);
      noise(0.15, 0.1, 3200);
      tone('sine', 62, 38, 0.8, 0.45, 0.15);
      tone('sawtooth', 124, 62, 0.45, 0.07, 0.15);
      noise(0.45, 0.26, 800, 0.15);
      const notes = [440, 523, 659, 831, 988, 1319];
      for (let i = 0; i < notes.length; i++) {
        tone('triangle', notes[i], notes[i], 0.2, 0.17, 0.2 + i * 0.05);
        tone('sine', notes[i] * 1.5, notes[i] * 1.5, 0.16, 0.05, 0.22 + i * 0.05);
      }
    },
    darkflap: function () {
      noise(0.18, 0.2, 480);
      tone('sine', 110, 55, 0.18, 0.1);
      tone('triangle', 880, 660, 0.06, 0.02);
    },
    flap: function () {
      noise(0.16, 0.2, 700);
      tone('sine', 150, 90, 0.14, 0.08);
    },
    smash: function () {
      tone('square', 420, 90, 0.16, 0.12);
      noise(0.14, 0.3, 2500);
      tone('triangle', 900, 1400, 0.1, 0.08, 0.04);
    },
    fall: function () {
      tone('sine', 700, 90, 0.9, 0.25);
    },
    hole: function () {
      tone('sine', 900, 60, 0.8, 0.2);
    },
    abduct: function () {
      tone('sine', 300, 1200, 0.8, 0.14);
    },
    over: function () {
      tone('triangle', 392, 392, 0.18, 0.22);
      tone('triangle', 330, 330, 0.18, 0.22, 0.18);
      tone('triangle', 262, 262, 0.32, 0.22, 0.36);
    },
    click: function () {
      tone('triangle', 600, 600, 0.05, 0.12);
    },
  };

  let lastPlay = 0;
  A.play = function (name) {
    if (!A.enabled || !ac) return;
    const f = S[name];
    if (!f) return;
    lastPlay = Date.now();
    if (ac.state === 'suspended') {
      const r = ac.resume();
      if (r && r.catch) r.catch(function () {});
    }
    f();
  };

  // 省电：不在游戏中、而且 3 秒没出声，就把音频设备挂起（安卓上一个一直开着的 AudioContext 会让音频硬件常驻唤醒）
  A.idle = function (playing) {
    if (!ac || playing || ac.state !== 'running' || Date.now() - lastPlay < 3000) return;
    const r = ac.suspend();
    if (r && r.catch) r.catch(function () {});
  };
})();
