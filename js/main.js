(function () {
  'use strict';
  const W = DJ.W;
  const canvas = document.getElementById('c');
  const ctx = canvas.getContext('2d');
  const I = DJ.input;
  const UI = DJ.ui;

  const game = new DJ.Game();
  const app = {
    game: game,
    settings: Object.assign({ sound: true, control: 'auto' }, DJ.store.get('settings', {})),
    scores: DJ.store.get('scores', []),
    newBest: false,
  };
  DJ.app = app; // 方便测试脚本读取状态
  game.best = DJ.store.get('best', 0);
  DJ.audio.enabled = app.settings.sound;
  I.mode = app.settings.control;
  I.coarse = !!(window.matchMedia && matchMedia('(pointer: coarse)').matches);

  // ---------- 画布尺寸 ----------
  function resize() {
    const vv = window.visualViewport;
    const vw = Math.max(1, Math.round(vv ? vv.width : window.innerWidth));
    const vh = Math.max(1, Math.round(vv ? vv.height : window.innerHeight));
    const aspect = vh / vw;
    const H = aspect >= 1.5 ? Math.min(DJ.MAX_H, Math.round(W * aspect)) : DJ.MIN_H;
    const scale = Math.min(vw / W, vh / H);
    const dpr = Math.min(window.devicePixelRatio || 1, 3);
    canvas.width = Math.round(vw * dpr);
    canvas.height = Math.round(vh * dpr);
    canvas.style.width = vw + 'px';
    canvas.style.height = vh + 'px';
    const ox = (vw - W * scale) / 2;
    const oy = (vh - H * scale) / 2;
    DJ.view = {
      W: W,
      H: H,
      scale: scale,
      dpr: dpr,
      ox: ox,
      oy: oy,
      px: 1 / (scale * dpr),
      x0: -ox / scale,
      x1: W + ox / scale,
      y0: -oy / scale,
      y1: H + oy / scale,
    };
    game.H = H;
  }
  window.addEventListener('resize', resize);
  if (window.visualViewport) window.visualViewport.addEventListener('resize', resize);
  resize();

  function toLogical(e) {
    const v = DJ.view;
    return { x: (e.clientX - v.ox) / v.scale, y: (e.clientY - v.oy) / v.scale };
  }

  // ---------- 状态切换 ----------
  function save() {
    DJ.store.set('settings', app.settings);
  }

  function startGame() {
    game.H = DJ.view.H;
    game.reset();
    app.newBest = false;
    I.reset();
    UI.screen = 'play';
    requestTilt();
  }

  function pause() {
    if (UI.screen !== 'play' || game.phase !== 'play') return;
    I.reset();
    UI.screen = 'pause';
  }

  function resume() {
    UI.screen = 'play';
    last = performance.now();
  }

  function toMenu() {
    I.reset();
    UI.screen = 'menu';
  }

  function finishGame() {
    const score = game.score;
    app.newBest = score > game.best && score > 0;
    if (app.newBest) {
      game.best = score;
      DJ.store.set('best', score);
    }
    if (score > 0) {
      app.scores.push(score);
      app.scores.sort(function (a, b) {
        return b - a;
      });
      app.scores.length = Math.min(app.scores.length, 5);
      DJ.store.set('scores', app.scores);
    }
    I.reset();
    UI.screen = 'over';
  }

  const CONTROLS = ['auto', 'tilt', 'touch'];
  function act(id) {
    DJ.audio.unlock();
    DJ.audio.play('click');
    switch (id) {
      case 'play':
      case 'again':
        startGame();
        break;
      case 'scores':
        UI.screen = 'scores';
        break;
      case 'settings':
        UI.screen = 'settings';
        break;
      case 'back':
        UI.screen = 'menu';
        break;
      case 'menu':
        toMenu();
        break;
      case 'resume':
        resume();
        break;
      case 'sound':
        app.settings.sound = !app.settings.sound;
        DJ.audio.enabled = app.settings.sound;
        save();
        break;
      case 'control':
        app.settings.control = CONTROLS[(CONTROLS.indexOf(app.settings.control) + 1) % CONTROLS.length];
        I.mode = app.settings.control;
        save();
        requestTilt();
        break;
    }
  }

  // 安卓壳按返回键时调用：返回 true 表示游戏内部已处理，false 表示可以退出
  DJ.onBack = function () {
    if (UI.screen === 'play') {
      pause();
      return true;
    }
    if (UI.screen === 'pause') {
      resume();
      return true;
    }
    if (UI.screen === 'scores' || UI.screen === 'settings') {
      UI.screen = 'menu';
      return true;
    }
    if (UI.screen === 'over') {
      toMenu();
      return true;
    }
    return false;
  };

  // ---------- 重力感应 ----------
  // 来源优先级：安卓壳里的原生传感器 > 浏览器 devicemotion（重力加速度）> deviceorientation（兜底）
  const IOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
  let motionSeen = false;
  function onMotion(e) {
    const a = e.accelerationIncludingGravity;
    if (!a || a.x === null || a.x === undefined) return;
    motionSeen = true;
    I.setAccel(a.x, IOS ? -1 : 1);
  }
  function onOrientation(e) {
    if (motionSeen || e.gamma === null || e.gamma === undefined) return;
    let g = e.gamma;
    const ang = (screen.orientation && screen.orientation.angle) || window.orientation || 0;
    if (ang === 90 || ang === -270) g = e.beta;
    else if (ang === -90 || ang === 270) g = -e.beta;
    else if (ang === 180) g = -g;
    I.setTilt(g);
  }
  let sensorsOn = false;
  function listenSensors() {
    if (sensorsOn) return;
    sensorsOn = true;
    window.addEventListener('devicemotion', onMotion);
    window.addEventListener('deviceorientation', onOrientation);
  }
  let tiltAsked = false;
  function requestTilt() {
    if (tiltAsked) return;
    const DME = window.DeviceMotionEvent;
    if (DME && typeof DME.requestPermission === 'function') {
      // iOS 需要在用户手势里申请权限
      tiltAsked = true;
      DME.requestPermission()
        .then(function (r) {
          if (r === 'granted') listenSensors();
        })
        .catch(function () {
          tiltAsked = false;
        });
    } else {
      listenSensors();
    }
  }
  if (!(window.DeviceMotionEvent && typeof window.DeviceMotionEvent.requestPermission === 'function')) listenSensors();
  // 安卓壳通过 window.NativeTilt.get() 提供经过滤波的倾斜角（度）
  DJ.pollNativeTilt = function () {
    const n = window.NativeTilt;
    if (n && typeof n.get === 'function') {
      try {
        I.setTilt(n.get());
        motionSeen = true;
      } catch (e) {}
    }
  };

  // ---------- 指针 / 触屏 ----------
  let pausePress = null;
  function inPauseBtn(p) {
    return p.x > W - 52 && p.y < 40;
  }

  canvas.addEventListener('pointerdown', function (e) {
    e.preventDefault();
    DJ.audio.unlock();
    try {
      canvas.setPointerCapture(e.pointerId);
    } catch (err) {}
    const p = toLogical(e);
    if (UI.screen === 'play') {
      if (inPauseBtn(p)) {
        pausePress = e.pointerId;
        return;
      }
      I.down(e.pointerId, e.pointerType, p.x, p.y, performance.now());
    } else {
      UI.pressed = UI.hit(p.x, p.y);
    }
  });

  canvas.addEventListener('pointermove', function (e) {
    const p = toLogical(e);
    if (UI.screen === 'play') I.move(e.pointerId, p.x, p.y);
    else if (UI.pressed) UI.pressed = UI.hit(p.x, p.y) === UI.pressed ? UI.pressed : null;
  });

  function pointerEnd(e) {
    const p = toLogical(e);
    if (UI.screen === 'play') {
      if (pausePress === e.pointerId) {
        pausePress = null;
        if (inPauseBtn(p)) pause();
        return;
      }
      I.up(e.pointerId, p.x, p.y, performance.now());
    } else {
      const id = UI.pressed;
      UI.pressed = null;
      if (id && e.type === 'pointerup' && UI.hit(p.x, p.y) === id) act(id);
    }
  }
  canvas.addEventListener('pointerup', pointerEnd);
  canvas.addEventListener('pointercancel', function (e) {
    pausePress = null;
    UI.pressed = null;
    I.touches.delete(e.pointerId);
  });
  canvas.addEventListener('contextmenu', function (e) {
    e.preventDefault();
  });
  document.addEventListener('touchmove', function (e) {
    e.preventDefault();
  }, { passive: false });

  // ---------- 键盘 ----------
  window.addEventListener('keydown', function (e) {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    DJ.audio.unlock();
    const c = e.code;
    if (c === 'ArrowLeft' || c === 'KeyA') I.left = true;
    else if (c === 'ArrowRight' || c === 'KeyD') I.right = true;
    else if (c === 'Space' || c === 'ArrowUp' || c === 'KeyW') {
      if (UI.screen === 'play') I.shootUp();
      else if (!e.repeat && c === 'Space') {
        if (UI.screen === 'menu') act('play');
        else if (UI.screen === 'over') act('again');
        else if (UI.screen === 'pause') act('resume');
      }
    } else if (c === 'Enter') {
      if (UI.screen === 'menu') act('play');
      else if (UI.screen === 'over') act('again');
      else if (UI.screen === 'pause') act('resume');
    } else if (c === 'KeyP' || c === 'Escape') {
      if (UI.screen === 'play') pause();
      else if (UI.screen === 'pause') act('resume');
      else if (UI.screen === 'scores' || UI.screen === 'settings') UI.screen = 'menu';
    } else return;
    e.preventDefault();
  });
  window.addEventListener('keyup', function (e) {
    const c = e.code;
    if (c === 'ArrowLeft' || c === 'KeyA') I.left = false;
    else if (c === 'ArrowRight' || c === 'KeyD') I.right = false;
  });

  document.addEventListener('visibilitychange', function () {
    if (document.hidden) pause();
  });
  window.addEventListener('blur', function () {
    I.left = I.right = false;
    pause();
  });

  // ---------- 主循环 ----------
  let last = performance.now();
  const STEP = 1 / 120;

  function frame(now) {
    const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
    last = now;
    DJ.clock = now / 1000;
    DJ.pollNativeTilt();
    UI.t += dt;

    if (UI.screen === 'play') {
      const input = { axis: I.axis(now), shots: I.shots };
      const n = Math.max(1, Math.ceil(dt / STEP));
      for (let i = 0; i < n; i++) game.update(dt / n, input);
      const ev = game.events;
      for (let i = 0; i < ev.length; i++) DJ.audio.play(ev[i]);
      ev.length = 0;
      if (game.phase === 'over') finishGame();
    }

    ctx.setTransform(1, 0, 0, 1, 0, 0);
    const v = DJ.view;
    ctx.setTransform(v.dpr * v.scale, 0, 0, v.dpr * v.scale, v.ox * v.dpr, v.oy * v.dpr);
    if (UI.screen === 'play' || UI.screen === 'pause') {
      DJ.drawScene(ctx, game);
      if (UI.screen === 'pause') UI.draw(ctx, app);
    } else {
      UI.draw(ctx, app);
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  if ('serviceWorker' in navigator && !window.NativeTilt && (location.protocol === 'https:' || location.hostname === 'localhost')) {
    navigator.serviceWorker.register('sw.js').catch(function () {});
  }
})();
