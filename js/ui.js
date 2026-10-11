var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const INK = '#2d2d26';
  const UI = (DJ.ui = { screen: 'menu', t: 0, pressed: null, buttons: [] });

  const CONTROL_LABEL = { auto: '自动', tilt: '倾斜', touch: '触屏' };

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
      b.push(btn('control', '操控：' + CONTROL_LABEL[app.settings.control], H * 0.32 + 62, 190, 46));
      b.push(btn('back', '返回', H * 0.32 + 140, 140, 44));
    }
    return b;
  };

  function drawButton(ctx, b, pressed) {
    const dy = pressed ? 2 : 0;
    ctx.save();
    ctx.translate(0, dy);
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
    ctx.fillText(b.label, b.x + b.w / 2, b.y + b.h / 2 + 1);
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
      ctx.rotate(((i % 2 ? 1 : -1) * 0.07) + Math.sin(t * 1.6 + i) * 0.02);
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
    // 在底部平台上弹跳的小橘
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

  // 绘制当前界面（play 界面由 DJ.drawScene 负责）
  UI.draw = function (ctx, app) {
    const H = DJ.view.H;
    const s = UI.screen;
    UI.buttons = UI.layout(app);
    const t = UI.t;

    if (s === 'menu') {
      DJ.drawBackground(ctx, 0);
      ctx.save();
      ctx.beginPath();
      ctx.rect(0, 0, W, H);
      ctx.clip();
      menuScene(ctx);
      title(ctx, '涂鸦蹦蹦', W / 2, H * 0.15, 54, t);
      ctx.restore();
      DJ.drawLetterbox(ctx);
    } else if (s === 'scores') {
      DJ.drawBackground(ctx, 0);
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
      DJ.drawLetterbox(ctx);
    } else if (s === 'settings') {
      DJ.drawBackground(ctx, 0);
      text(ctx, '设置', W / 2, H * 0.32 - 62, 38, INK);
      const hint =
        app.settings.control === 'tilt'
          ? '倾斜手机移动，点击屏幕射击'
          : app.settings.control === 'touch'
          ? '按住屏幕左/右侧移动，轻点射击'
          : '手机自动用倾斜，其他设备用触屏/键盘';
      text(ctx, hint, W / 2, H * 0.32 + 112, 14, '#6b6b60');
      DJ.drawLetterbox(ctx);
    } else if (s === 'pause') {
      ctx.fillStyle = 'rgba(247,244,225,0.72)';
      ctx.fillRect(DJ.view.x0, DJ.view.y0, DJ.view.x1 - DJ.view.x0, DJ.view.y1 - DJ.view.y0);
      text(ctx, '已暂停', W / 2, H * 0.4 - 56, 40, INK);
    } else if (s === 'over') {
      DJ.drawBackground(ctx, 0);
      text(ctx, '游戏结束!', W / 2, H * 0.17, 44, INK);
      text(ctx, '得分：' + app.game.score, W / 2, H * 0.28, 28, '#3d3d34');
      text(ctx, '最高分：' + app.game.best, W / 2, H * 0.28 + 38, 22, '#6b6b60');
      if (app.newBest) {
        ctx.save();
        ctx.translate(W / 2, H * 0.28 + 80);
        ctx.rotate(-0.06 + Math.sin(t * 5) * 0.02);
        text(ctx, '新纪录!', 0, 0, 26, '#e0483c');
        ctx.restore();
      }
      DJ.drawLetterbox(ctx);
    }

    for (let i = 0; i < UI.buttons.length; i++) {
      const b = UI.buttons[i];
      drawButton(ctx, b, UI.pressed === b.id);
    }
  };

  UI.hit = function (x, y) {
    for (let i = 0; i < UI.buttons.length; i++) {
      const b = UI.buttons[i];
      if (x >= b.x && x <= b.x + b.w && y >= b.y - 4 && y <= b.y + b.h + 4) return b.id;
    }
    return null;
  };
})();
