var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const clamp = DJ.clamp;
  const INK = '#2d2d26';
  const UI = (DJ.ui = { screen: 'menu', t: 0, dt: 0, tIn: 1, pressed: null, buttons: [], bs: {}, meterX: 0 });

  const SENS_LABEL = ['低', '中', '高'];

  // 切换界面：tIn 从 0 开始计时，所有进场动画（淡入、弹出、按钮依次出现）都由它驱动。quiet=true 表示不要进场动画
  UI.go = function (name, quiet) {
    UI.screen = name;
    UI.tIn = quiet ? 9 : 0;
    UI.pressed = null;
  };

  // 每帧推进：按钮的按压弹簧（按下缩到 0.92，松开弹回时略有过冲）
  UI.tick = function (dt) {
    UI.dt = dt;
    UI.tIn += dt;
    const n = Math.max(1, Math.ceil(dt / 0.004));
    const h = dt / n;
    for (let i = 0; i < UI.buttons.length; i++) {
      const id = UI.buttons[i].id;
      const st = UI.bs[id] || (UI.bs[id] = { x: 1, v: 0 });
      const tg = UI.pressed === id ? 0.92 : 1;
      for (let k = 0; k < n; k++) {
        st.v += (-(st.x - tg) * 900 - st.v * 26) * h;
        st.x += st.v * h;
      }
    }
  };

  function easeOutBack(p) {
    const c1 = 1.3;
    const c3 = c1 + 1;
    const q = p - 1;
    return 1 + c3 * q * q * q + c1 * q * q;
  }
  function easeOut(p) {
    return 1 - Math.pow(1 - p, 3);
  }

  function btn(id, label, y, w, h, extra) {
    return Object.assign({ id: id, label: label, x: (W - w) / 2, y: y, w: w, h: h }, extra || {});
  }

  UI.layout = function (app) {
    const H = DJ.view.H;
    const s = UI.screen;
    const b = [];
    if (s === 'menu') {
      b.push(btn('play', '开始游戏', H * 0.34, 156, 48, { big: true }));
      b.push(btn('scores', '排行榜', H * 0.34 + 60, 156, 44));
      b.push(btn('settings', '设置', H * 0.34 + 114, 156, 44));
    } else if (s === 'pause') {
      b.push(btn('resume', '继续', H * 0.4, 156, 46));
      b.push(btn('menu', '菜单', H * 0.4 + 58, 156, 44));
    } else if (s === 'over') {
      b.push(btn('again', '再玩一次', H * 0.56, 156, 48, { big: true }));
      b.push(btn('menu', '菜单', H * 0.56 + 60, 156, 44));
    } else if (s === 'scores') {
      b.push(btn('back', '返回', H - 90, 140, 44));
    } else if (s === 'settings') {
      b.push(btn('sound', '声音：' + (app.settings.sound ? '开' : '关'), H * 0.32, 190, 46));
      b.push(btn('sens', '灵敏度：' + SENS_LABEL[app.settings.sens], H * 0.32 + 62, 190, 46));
      b.push(btn('back', '返回', H * 0.32 + 140, 140, 44));
    }
    return b;
  };

  function drawButton(ctx, b, index) {
    // 依次错开出场：每个按钮晚 50ms，用带一点过冲的缓动弹出来
    const p = clamp((UI.tIn - index * 0.05) / 0.38, 0, 1);
    if (p <= 0) return;
    const e = easeOutBack(p);
    const st = UI.bs[b.id] || { x: 1 };
    const pressed = UI.pressed === b.id;
    const sc = (0.85 + 0.15 * e) * st.x;
    const cx = b.x + b.w / 2;
    const cy = b.y + b.h / 2;
    ctx.save();
    ctx.globalAlpha = clamp(p * 1.6, 0, 1);
    ctx.translate(cx, cy + (1 - e) * 10);
    ctx.scale(sc, sc);
    ctx.translate(-cx, -cy);
    DJ.rr(ctx, b.x, b.y, b.w, b.h, 9);
    const g = ctx.createLinearGradient(0, b.y, 0, b.y + b.h);
    if (pressed) {
      g.addColorStop(0, '#8fcb4a');
      g.addColorStop(1, '#4f9a26');
    } else {
      g.addColorStop(0, '#b4ea66');
      g.addColorStop(1, '#5fb331');
    }
    ctx.fillStyle = g;
    ctx.fill();
    ctx.lineWidth = 2.4;
    ctx.strokeStyle = '#2f6b17';
    ctx.lineJoin = 'round';
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(b.x + 12, b.y + 6);
    ctx.lineTo(b.x + b.w - 12, b.y + 6);
    ctx.lineWidth = 2;
    ctx.lineCap = 'round';
    ctx.strokeStyle = 'rgba(255,255,255,0.45)';
    ctx.stroke();
    ctx.fillStyle = INK;
    ctx.font = DJ.font(b.big ? 25 : 21);
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(b.label, cx, cy + 1);
    ctx.restore();
  }

  // 手绘风标题：每个字单独描边、轻微歪斜
  function title(ctx, text, cx, cy, size, t) {
    ctx.save();
    ctx.font = DJ.font(size, '900');
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.lineJoin = 'round';
    const step = size * 1.02;
    const x0 = cx - ((text.length - 1) * step) / 2;
    for (let i = 0; i < text.length; i++) {
      ctx.save();
      ctx.translate(x0 + i * step, cy + Math.sin(t * 2.2 + i * 1.3) * 2.5);
      ctx.rotate((i % 2 ? 1 : -1) * 0.07 + Math.sin(t * 1.6 + i) * 0.02);
      ctx.lineWidth = size * 0.17;
      ctx.strokeStyle = INK;
      ctx.strokeText(text[i], 0, 0);
      const g = ctx.createLinearGradient(0, -size / 2, 0, size / 2);
      g.addColorStop(0, '#b9ee5c');
      g.addColorStop(1, '#5aae2c');
      ctx.fillStyle = g;
      ctx.fillText(text[i], 0, 0);
      ctx.restore();
    }
    ctx.restore();
  }

  function text(ctx, str, x, y, size, color, align) {
    ctx.font = DJ.font(size);
    ctx.fillStyle = color || '#3d3d34';
    ctx.textAlign = align || 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(str, x, y);
  }

  function menuScene(ctx) {
    const H = DJ.view.H;
    // 装饰平台
    DJ.drawPlatformAt(ctx, 'moving', 44, H * 0.58);
    DJ.drawPlatformAt(ctx, 'normal', 278, H * 0.5);
    DJ.drawPlatformAt(ctx, 'brown', 270, H * 0.72);
    DJ.drawPlatformAt(ctx, 'white', 50, H * 0.8);
    // 在底部平台上弹跳的小海星：用和游戏里同一条抛物线，落地时肚子弹簧衰减晃动
    const by = H - 52;
    DJ.drawPlatformAt(ctx, 'normal', W / 2, by);
    const period = 0.95;
    const u = (UI.t % period) / period;
    const h = 62;
    const y = by - h * 4 * u * (1 - u);
    const vy = (u < 0.5 ? -1 : 1) * 400 * Math.abs(1 - 2 * u);
    const facing = Math.floor(UI.t / period / 3) % 2 === 0 ? 1 : -1;
    DJ.drawDoodler(ctx, DJ.fakeDoodler(W / 2, y, facing, vy, u * period, UI.t));
  }

  // 设置页里的倾斜测试条：小球跟着手机实时左右滚，方便确认传感器有没有在工作、灵敏度合不合手
  function tiltMeter(ctx, app, y) {
    const I = DJ.input;
    const full = 17 / I.sens;
    const track = 100;
    ctx.save();
    ctx.lineCap = 'round';
    ctx.beginPath();
    ctx.moveTo(W / 2 - track, y);
    ctx.lineTo(W / 2 + track, y);
    ctx.lineWidth = 7;
    ctx.strokeStyle = 'rgba(80,110,100,0.25)';
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(W / 2, y - 7);
    ctx.lineTo(W / 2, y + 7);
    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(80,110,100,0.5)';
    ctx.stroke();
    const tx = I.raw === null ? 0 : clamp(I.raw / full, -1, 1) * track;
    UI.meterX += (tx - UI.meterX) * (1 - Math.exp(-UI.dt * 18));
    ctx.beginPath();
    ctx.arc(W / 2 + UI.meterX, y, 9, 0, Math.PI * 2);
    ctx.fillStyle = Math.abs(tx) >= track - 0.5 ? '#f0733f' : '#7cc944';
    ctx.fill();
    ctx.lineWidth = 2;
    ctx.strokeStyle = INK;
    ctx.stroke();
    ctx.restore();
    const label = I.raw === null ? '还没收到传感器数据，晃一下手机试试' : '当前倾斜 ' + (I.raw >= 0 ? '+' : '') + I.raw.toFixed(1) + '°（两端为满速）';
    text(ctx, label, W / 2, y + 26, 13, '#6b6b60');
  }

  // 绘制当前界面（play 界面由 DJ.drawScene 负责；暂停/结算时主循环会先画好场景，这里只盖一层纸色并画内容）
  UI.draw = function (ctx, app) {
    const H = DJ.view.H;
    const s = UI.screen;
    const t = UI.t;
    UI.buttons = UI.layout(app);
    const v = DJ.view;
    const fade = clamp(UI.tIn / 0.35, 0, 1);

    // ---- 背景层（不参与进场动画）----
    if (s === 'menu') {
      DJ.drawBackground(ctx, 0);
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, W, H);
      ctx.clip();
      menuScene(ctx);
      ctx.restore();
    } else if (s === 'scores' || s === 'settings') {
      DJ.drawBackground(ctx, 0);
    } else if (s === 'pause' || s === 'over') {
      ctx.fillStyle = 'rgba(247,244,225,' + ((s === 'over' ? 0.88 : 0.74) * easeOut(fade)).toFixed(3) + ')';
      ctx.fillRect(v.x0, v.y0, v.x1 - v.x0, v.y1 - v.y0);
    }

    // ---- 内容层：整体淡入 + 轻微上浮放大 ----
    const p = clamp(UI.tIn / 0.4, 0, 1);
    const e = easeOutBack(p);
    ctx.save();
    ctx.globalAlpha = clamp(UI.tIn / 0.22, 0, 1);
    ctx.translate(W / 2, H * 0.4 + (1 - e) * 12);
    ctx.scale(0.94 + 0.06 * e, 0.94 + 0.06 * e);
    ctx.translate(-W / 2, -H * 0.4);

    if (s === 'menu') {
      title(ctx, '涂鸦蹦蹦', W / 2, H * 0.15, 54, t);
    } else if (s === 'scores') {
      text(ctx, '排行榜', W / 2, 70, 38, INK);
      const list = app.scores;
      if (!list.length) text(ctx, '还没有成绩，快去玩一局吧', W / 2, 160, 18, '#6b6b60');
      for (let i = 0; i < 5; i++) {
        const y = 128 + i * 42;
        text(ctx, i + 1 + '.', 70, y, 24, '#6b6b60', 'left');
        text(ctx, list[i] !== undefined ? String(list[i]) : '—', 250, y, 26, i === 0 ? '#3c8a1a' : INK, 'right');
        ctx.beginPath();
        ctx.moveTo(60, y + 20);
        ctx.lineTo(260, y + 20);
        ctx.lineWidth = 1.4;
        ctx.strokeStyle = 'rgba(80,110,100,0.35)';
        ctx.stroke();
      }
    } else if (s === 'settings') {
      text(ctx, '设置', W / 2, H * 0.32 - 62, 38, INK);
      text(ctx, '左右倾斜手机移动，点击屏幕射击', W / 2, H * 0.32 + 218, 14, '#6b6b60');
      tiltMeter(ctx, app, H * 0.32 + 262);
    } else if (s === 'pause') {
      text(ctx, '已暂停', W / 2, H * 0.4 - 56, 40, INK);
    } else if (s === 'over') {
      text(ctx, '游戏结束!', W / 2, H * 0.17, 44, INK);
      // 得分数字从 0 滚到最终得分
      const shown = Math.round(app.game.score * easeOut(clamp((UI.tIn - 0.15) / 0.8, 0, 1)));
      text(ctx, '得分：' + shown, W / 2, H * 0.28, 28, '#3d3d34');
      text(ctx, '最高分：' + app.game.best, W / 2, H * 0.28 + 38, 22, '#6b6b60');
      if (app.newBest) {
        ctx.save();
        ctx.translate(W / 2, H * 0.28 + 80);
        const pop = easeOutBack(clamp((UI.tIn - 0.9) / 0.4, 0, 1));
        ctx.rotate(-0.06 + Math.sin(t * 5) * 0.02);
        ctx.scale(pop, pop);
        text(ctx, '新纪录!', 0, 0, 26, '#e0483c');
        ctx.restore();
      }
    }
    ctx.restore();

    for (let i = 0; i < UI.buttons.length; i++) drawButton(ctx, UI.buttons[i], i);
    if (s === 'menu' || s === 'scores' || s === 'settings') DJ.drawLetterbox(ctx);
  };

  UI.hit = function (x, y) {
    for (let i = 0; i < UI.buttons.length; i++) {
      const b = UI.buttons[i];
      if (x >= b.x && x <= b.x + b.w && y >= b.y - 4 && y <= b.y + b.h + 4) return b.id;
    }
    return null;
  };
})();
