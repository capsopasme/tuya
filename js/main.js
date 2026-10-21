(function () {
  'use strict';
  const W = DJ.W;
  const canvas = document.getElementById('c');
  const ctx = canvas.getContext('2d', { alpha: false });
  const I = DJ.input;
  const UI = DJ.ui;

  const game = new DJ.Game();
  const SENS = [0.75, 1, 1.35]; // 灵敏度：低 / 中 / 高
  const app = {
    game: game,
    // 老版本存过的 control（触屏/倾斜）设置已经没有意义，直接忽略
    settings: Object.assign({ sound: true, sens: 1, hero3d: true, chill: false, fps: 120, perf: false }, DJ.store.get('settings', {})),
    scores: DJ.store.get('scores', []),
    newBest: false,
  };
  // 存档可能是旧版本写的、或者被改坏了：逐项校验，不合法就用默认值
  if ([0, 1, 2].indexOf(app.settings.sens) < 0) app.settings.sens = 1;
  app.settings.sound = app.settings.sound !== false;
  app.settings.hero3d = app.settings.hero3d !== false;
  app.settings.chill = app.settings.chill === true;
  if (app.settings.fps !== 60 && app.settings.fps !== 120) app.settings.fps = 120;
  app.settings.perf = app.settings.perf === true;
  if (!Array.isArray(app.scores)) app.scores = [];
  app.scores = app.scores.filter(function (s) {
    return typeof s === 'number' && isFinite(s) && s > 0;
  });
  DJ.app = app; // 方便测试脚本读取状态
  const best = DJ.store.get('best', 0);
  game.best = typeof best === 'number' && isFinite(best) && best > 0 ? Math.floor(best) : 0;
  DJ.hero3d.on = app.settings.hero3d;
  DJ.audio.enabled = app.settings.sound;
  I.sens = SENS[app.settings.sens];
  DJ.shownScore = 0;

  // ---------- 帧率 ----------
  // 120：跟着高刷屏走；60：省电（安卓壳里会让屏幕本身也切到 60Hz，浏览器里则每隔一帧才画一次）。
  // 物理永远是 1/120 秒一步，两种帧率下手感完全一样
  function applyFps() {
    try {
      if (window.NativeApp && window.NativeApp.setFps) window.NativeApp.setFps(app.settings.fps);
    } catch (e) {}
  }
  applyFps();

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

  // ---------- 固定步长 + 渲染插值 ----------
  // 物理永远按 1/120 秒一步走，和屏幕刷新率无关；画面在两步之间按 alpha 插值。
  // 这样 60/90/120Hz 的屏幕、以及偶尔掉一帧时，运动都是匀速平滑的，不会一顿一顿。
  const STEP = 1 / 120;
  let acc = 0;
  let alpha = 1;
  let last = performance.now();

  // ---------- 状态切换 ----------
  function save() {
    DJ.store.set('settings', app.settings);
  }

  function syncClock() {
    last = performance.now();
    acc = 0;
  }

  function startGame() {
    game.H = DJ.view.H;
    game.chill = app.settings.chill;
    game.reset();
    app.newBest = false;
    I.reset();
    I.snapTilt();
    DJ.shownScore = 0;
    syncClock();
    alpha = 1;
    UI.go('play');
  }

  function pause() {
    if (UI.screen !== 'play' || game.phase !== 'play') return;
    I.reset();
    UI.go('pause');
  }

  function resume() {
    I.reset();
    I.snapTilt(); // 继续时立刻对齐当前的握持姿势
    syncClock();
    UI.go('play', true);
  }

  function toMenu() {
    I.reset();
    UI.go('menu');
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
    UI.go('over');
  }

  function act(id) {
    DJ.audio.unlock();
    DJ.audio.play('click');
    switch (id) {
      case 'play':
      case 'again':
        startGame();
        break;
      case 'scores':
        UI.go('scores');
        break;
      case 'settings':
        UI.go('settings');
        break;
      case 'back':
        UI.go('menu');
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
      case 'hero':
        app.settings.hero3d = !app.settings.hero3d;
        DJ.hero3d.on = app.settings.hero3d;
        save();
        break;
      case 'chill':
        app.settings.chill = !app.settings.chill; // 下一局开始生效
        save();
        break;
      case 'fps':
        app.settings.fps = app.settings.fps === 120 ? 60 : 120;
        applyFps();
        save();
        break;
      case 'perf':
        app.settings.perf = !app.settings.perf;
        save();
        break;
      case 'sens':
        app.settings.sens = (app.settings.sens + 1) % SENS.length;
        I.sens = SENS[app.settings.sens];
        save();
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
      UI.go('menu');
      return true;
    }
    if (UI.screen === 'over') {
      toMenu();
      return true;
    }
    return false;
  };

  // ---------- 重力感应 ----------
  // 传感器来源只能有一个，否则两路读数交替写入会互相打架：
  //   安卓壳里：原生加速度计（每帧通过 window.NativeTilt.get() 读取）
  //   浏览器里：devicemotion 的重力加速度；没有的话再退到 deviceorientation
  const NATIVE = !!window.NativeTilt;
  const IOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
  let motionSeen = false;
  function onMotion(e) {
    if (NATIVE) return;
    const a = e.accelerationIncludingGravity;
    if (!a || a.x === null || a.x === undefined) return;
    motionSeen = true;
    I.setAccel(a.x, IOS ? -1 : 1);
  }
  function onOrientation(e) {
    if (NATIVE || motionSeen || e.gamma === null || e.gamma === undefined) return;
    I.setRaw(e.gamma);
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
  DJ.pollNativeTilt = function () {
    if (!NATIVE) return;
    try {
      I.setRaw(window.NativeTilt.get());
    } catch (e) {}
  };
  // 切回应用 / 窗口重新获得焦点：对齐传感器、丢掉旧时间，避免第一帧一个大 dt 或沿用旧姿势
  DJ.onResume = function () {
    DJ.pollNativeTilt();
    I.snapTilt();
    syncClock();
  };

  // ---------- 指针 / 触屏 ----------
  let pausePress = null;
  function inPauseBtn(p) {
    return p.x > W - 52 && p.y < 40;
  }

  // 暂停 / 排行榜这类静止界面进场动画放完以后就不再重画（省电），有触摸、按键、尺寸变化时再画一会儿
  let dirtyUntil = 0;
  function poke() {
    dirtyUntil = performance.now() + 900;
  }
  window.addEventListener('resize', poke);
  window.addEventListener('keydown', poke);
  window.addEventListener('pointermove', poke);
  window.addEventListener('pointerup', poke);

  canvas.addEventListener('pointerdown', function (e) {
    e.preventDefault();
    poke();
    DJ.audio.unlock();
    requestTilt();
    const p = toLogical(e);
    if (UI.screen === 'play') {
      if (inPauseBtn(p)) pausePress = e.pointerId;
      else I.shootAt(p.x, p.y); // 点击只负责射击，移动完全靠倾斜
    } else {
      try {
        canvas.setPointerCapture(e.pointerId);
      } catch (err) {}
      UI.pressed = UI.hit(p.x, p.y);
    }
  });

  canvas.addEventListener('pointermove', function (e) {
    if (UI.screen !== 'play' && UI.pressed) {
      const p = toLogical(e);
      if (UI.hit(p.x, p.y) !== UI.pressed) UI.pressed = null;
    }
  });

  canvas.addEventListener('pointerup', function (e) {
    const p = toLogical(e);
    if (UI.screen === 'play') {
      if (pausePress === e.pointerId) {
        pausePress = null;
        if (inPauseBtn(p)) pause();
      }
    } else {
      const id = UI.pressed;
      UI.pressed = null;
      if (id && UI.hit(p.x, p.y) === id) act(id);
    }
  });
  canvas.addEventListener('pointercancel', function () {
    pausePress = null;
    UI.pressed = null;
  });
  canvas.addEventListener('contextmenu', function (e) {
    e.preventDefault();
  });
  document.addEventListener(
    'touchmove',
    function (e) {
      e.preventDefault();
    },
    { passive: false }
  );

  // ---------- 键盘（电脑上调试用）----------
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
      else if (UI.screen === 'scores' || UI.screen === 'settings') UI.go('menu');
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
    else DJ.onResume();
  });
  window.addEventListener('blur', function () {
    I.left = I.right = false;
    pause();
  });
  window.addEventListener('focus', DJ.onResume);

  // ---------- 性能信息（设置里打开后显示在分数栏下面）----------
  // rAF：浏览器每秒回调多少次 = WebView 实际跟着屏幕刷新的频率；画：每秒真正画了多少帧；
  // 卡顿：最近 2 秒里两帧间隔超过正常间隔 1.5 倍的次数；JS：每帧脚本耗时（不含 GPU）
  const perf = (DJ.perf = { text: '', raf: [], draws: [], gaps: [], js: 0, lastRaf: 0, t: 0 });
  function perfTick(now, drew, jsMs) {
    if (!app.settings.perf) return;
    perf.raf.push(now);
    while (perf.raf.length && perf.raf[0] < now - 1000) perf.raf.shift();
    if (drew) {
      if (perf.draws.length) perf.gaps.push([now, now - perf.draws[perf.draws.length - 1]]);
      perf.draws.push(now);
      perf.js += (jsMs - perf.js) * 0.1;
    }
    while (perf.draws.length && perf.draws[0] < now - 1000) perf.draws.shift();
    while (perf.gaps.length && perf.gaps[0][0] < now - 2000) perf.gaps.shift();
    if (now - perf.t < 250) return;
    perf.t = now;
    let hz = 0;
    try {
      if (window.NativeApp && window.NativeApp.getHz) hz = Math.round(window.NativeApp.getHz());
    } catch (e) {}
    const rafN = perf.raf.length;
    const expect = 1000 / Math.min(app.settings.fps, Math.max(30, rafN));
    let jank = 0;
    for (let i = 0; i < perf.gaps.length; i++) if (perf.gaps[i][1] > expect * 1.5) jank++;
    perf.text = (hz ? '屏幕 ' + hz + 'Hz · ' : '') + 'rAF ' + rafN + ' · 画 ' + perf.draws.length + ' 帧 · 卡顿 ' + jank + ' · JS ' + perf.js.toFixed(1) + 'ms';
  }

  // ---------- 主循环 ----------
  let lastDraw = -1e9;
  let lastScreen = '';
  function frame(now) {
    requestAnimationFrame(frame);
    const s0 = UI.screen;
    if (s0 !== lastScreen) {
      lastScreen = s0;
      poke();
    }
    // 帧率上限：所有界面都按设置（60 / 120）
    const cap = app.settings.fps;
    if (now - lastDraw < 1000 / cap - 2.5) {
      perfTick(now, false, 0);
      return;
    }
    // 静止界面：进场动画放完、也没人碰屏幕，就什么都不画（画面保持上一帧）
    const still = (s0 === 'pause' || s0 === 'scores' || (s0 === 'over' && !app.newBest)) && UI.tIn > 1.4 && now > dirtyUntil && !UI.pressed;
    DJ.audio.idle(s0 === 'play');
    if (still) {
      last = now;
      perfTick(now, false, 0);
      return;
    }
    lastDraw = now;
    const t0 = performance.now();
    const dt = Math.min(0.1, Math.max(0, (now - last) / 1000));
    last = now;
    DJ.clock = now / 1000;
    UI.t += dt;
    UI.tick(dt);
    DJ.pollNativeTilt();

    if (UI.screen === 'play') {
      const input = { axis: I.axis(dt), shots: I.shots };
      acc += dt;
      let n = 0;
      while (acc >= STEP && n < 20) {
        game.update(STEP, input);
        acc -= STEP;
        n++;
        if (game.phase === 'over') break;
      }
      if (n >= 20) acc = 0; // 卡得太厉害就放弃追赶，别越追越卡
      alpha = game.phase === 'over' ? 1 : acc / STEP;
      const ev = game.events;
      for (let i = 0; i < ev.length; i++) DJ.audio.play(ev[i]);
      ev.length = 0;
      // 分数数字缓动追上真实得分，而不是一下跳变
      const d = game.score - DJ.shownScore;
      DJ.shownScore = Math.abs(d) < 0.5 ? game.score : DJ.shownScore + d * (1 - Math.exp(-dt * 16));
      if (game.phase === 'over') finishGame();
    }

    const v = DJ.view;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.setTransform(v.dpr * v.scale, 0, 0, v.dpr * v.scale, v.ox * v.dpr, v.oy * v.dpr);
    const s = UI.screen;
    if (s === 'play') {
      DJ.drawScene(ctx, game, alpha);
      // 开局时场景从纸色里淡入
      if (UI.tIn < 0.35) {
        ctx.fillStyle = 'rgba(247,244,225,' + (1 - UI.tIn / 0.35).toFixed(3) + ')';
        ctx.fillRect(v.x0, v.y0, v.x1 - v.x0, v.y1 - v.y0);
      }
    } else if (s === 'pause' || s === 'over') {
      DJ.drawScene(ctx, game, s === 'over' ? 1 : alpha, true);
      UI.draw(ctx, app);
    } else {
      UI.draw(ctx, app);
    }
    perfTick(now, true, performance.now() - t0);
  }
  requestAnimationFrame(frame);

  if ('serviceWorker' in navigator && !NATIVE && (location.protocol === 'https:' || location.hostname === 'localhost')) {
    navigator.serviceWorker.register('sw.js').catch(function () {});
  }
})();
