var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const clamp = DJ.clamp;
  const lerp = DJ.lerp;
  const INK = '#2d2d26';
  const TAU = Math.PI * 2;

  DJ.FONT =
    '"Chalkboard SE","Marker Felt","Bradley Hand","Segoe Print","Comic Sans MS","Kaiti SC","STKaiti","KaiTi","Noto Sans CJK SC","Microsoft YaHei",cursive,sans-serif';
  DJ.font = function (px, weight) {
    return (weight || 'bold') + ' ' + px + 'px ' + DJ.FONT;
  };

  // DJ.view 由 main.js（或测试脚本）设置：{ W, H, scale, dpr, px, x0, x1, y0, y1 }
  DJ.view = DJ.view || { W: W, H: 480, scale: 1, dpr: 1, px: 1, x0: 0, x1: W, y0: 0, y1: 480 };

  function rr(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.arcTo(x + w, y, x + w, y + r, r);
    ctx.lineTo(x + w, y + h - r);
    ctx.arcTo(x + w, y + h, x + w - r, y + h, r);
    ctx.lineTo(x + r, y + h);
    ctx.arcTo(x, y + h, x, y + h - r, r);
    ctx.lineTo(x, y + r);
    ctx.arcTo(x, y, x + r, y, r);
    ctx.closePath();
  }
  DJ.rr = rr;

  function star(ctx, x, y, r) {
    ctx.beginPath();
    for (let i = 0; i < 10; i++) {
      const a = -Math.PI / 2 + (i * Math.PI) / 5;
      const rad = i % 2 === 0 ? r : r * 0.45;
      ctx.lineTo(x + Math.cos(a) * rad, y + Math.sin(a) * rad);
    }
    ctx.closePath();
  }

  function fillStroke(ctx, fill, lw) {
    ctx.fillStyle = fill;
    ctx.fill();
    ctx.lineWidth = lw || 2;
    ctx.strokeStyle = INK;
    ctx.lineJoin = 'round';
    ctx.stroke();
  }

  // ---------- 背景：方格笔记本纸 ----------
  function background(ctx, camY) {
    const v = DJ.view;
    const cell = 20;
    ctx.fillStyle = '#f7f4e1';
    ctx.fillRect(v.x0 - 2, v.y0 - 2, v.x1 - v.x0 + 4, v.y1 - v.y0 + 4);
    ctx.beginPath();
    for (let x = Math.floor(v.x0 / cell) * cell; x <= v.x1; x += cell) {
      ctx.moveTo(x, v.y0);
      ctx.lineTo(x, v.y1);
    }
    const n0 = Math.floor((camY + v.y0) / cell);
    const n1 = Math.ceil((camY + v.y1) / cell);
    for (let n = n0; n <= n1; n++) {
      const y = n * cell - camY;
      ctx.moveTo(v.x0, y);
      ctx.lineTo(v.x1, y);
    }
    ctx.lineWidth = Math.max(v.px * 1.3, 0.5);
    ctx.strokeStyle = '#c5dcd8';
    ctx.stroke();
  }
  DJ.drawBackground = background;

  // 屏幕比例特殊时，游戏区域以外的部分压暗并画边线
  function letterbox(ctx) {
    const v = DJ.view;
    ctx.fillStyle = 'rgba(60,60,40,0.07)';
    if (v.x0 < -0.5) {
      ctx.fillRect(v.x0, v.y0, -v.x0, v.y1 - v.y0);
      ctx.fillRect(W, v.y0, v.x1 - W, v.y1 - v.y0);
    }
    if (v.y0 < -0.5) {
      ctx.fillRect(0, v.y0, W, -v.y0);
      ctx.fillRect(0, v.H, W, v.y1 - v.H);
    }
    if (v.x0 < -0.5 || v.y0 < -0.5) {
      ctx.strokeStyle = 'rgba(60,60,40,0.35)';
      ctx.lineWidth = v.px * 1.5;
      ctx.strokeRect(0, 0, W, v.H);
    }
  }
  DJ.drawLetterbox = letterbox;

  // ---------- 平台 ----------
  const PAL = {
    normal: { top: '#a9e25c', bot: '#5db02e', line: '#3b7a1c', hi: 'rgba(255,255,255,0.45)' },
    moving: { top: '#8fd8f7', bot: '#3c9ddb', line: '#1d6aa6', hi: 'rgba(255,255,255,0.5)' },
    brown: { top: '#c48d55', bot: '#8d5c2e', line: '#5a3817', hi: 'rgba(255,255,255,0.25)' },
    white: { top: '#ffffff', bot: '#d9d9d2', line: '#8e8e86', hi: 'rgba(255,255,255,0.8)' },
  };

  function platform(ctx, p) {
    const x = p.x - p.w / 2;
    const y = p.y;
    const pal = PAL[p.type];
    ctx.save();
    if (p.dead) ctx.globalAlpha = Math.max(0, 1 - p.fade / 0.22);
    const g = ctx.createLinearGradient(0, y, 0, y + p.h);
    g.addColorStop(0, pal.top);
    g.addColorStop(1, pal.bot);
    rr(ctx, x, y, p.w, p.h, 5.5);
    ctx.fillStyle = g;
    ctx.fill();
    ctx.lineWidth = 1.8;
    ctx.strokeStyle = pal.line;
    ctx.stroke();
    // 高光
    ctx.beginPath();
    ctx.moveTo(x + 7, y + 3.4);
    ctx.lineTo(x + p.w - 7, y + 3.4);
    ctx.lineWidth = 1.6;
    ctx.strokeStyle = pal.hi;
    ctx.lineCap = 'round';
    ctx.stroke();
    if (p.type === 'brown') {
      ctx.beginPath();
      ctx.moveTo(x + 18, y + 1);
      ctx.lineTo(x + 24, y + 6);
      ctx.lineTo(x + 20, y + 9);
      ctx.lineTo(x + 27, y + p.h - 1);
      ctx.moveTo(x + 40, y + 1);
      ctx.lineTo(x + 35, y + 7);
      ctx.lineTo(x + 41, y + p.h - 1);
      ctx.lineWidth = 1.4;
      ctx.strokeStyle = pal.line;
      ctx.stroke();
    }
    ctx.restore();
    if (p.item && !p.item.used) item(ctx, p, p.item);
  }

  // ---------- 平台上的道具 ----------
  function item(ctx, p, it) {
    const x = p.x + it.ox;
    const y = p.y;
    ctx.save();
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    if (it.type === 'spring') {
      const ext = it.fired ? Math.sin(clamp(it.t / 0.3, 0, 1) * Math.PI) * 14 : 0;
      const h = 12 + ext;
      ctx.translate(x, y);
      // 弹簧圈
      ctx.beginPath();
      ctx.moveTo(-6, 0);
      const n = 4;
      for (let i = 0; i < n; i++) {
        const yy = -(h * (i + 0.5)) / n;
        ctx.lineTo(i % 2 === 0 ? 7 : -7, yy);
      }
      ctx.lineTo(0, -h);
      ctx.lineWidth = 3.4;
      ctx.strokeStyle = INK;
      ctx.stroke();
      ctx.lineWidth = 1.6;
      ctx.strokeStyle = '#c6ccd2';
      ctx.stroke();
      rr(ctx, -8, -h - 3, 16, 4.5, 2);
      fillStroke(ctx, '#e8483c', 1.6);
      rr(ctx, -8, -1.5, 16, 3, 1.5);
      fillStroke(ctx, '#9aa3ab', 1.4);
    } else if (it.type === 'tramp') {
      const ext = it.fired ? Math.sin(clamp(it.t / 0.35, 0, 1) * Math.PI) * 16 : 0;
      ctx.translate(x, y);
      const h = 8 + ext;
      for (const sx of [-14, 14]) {
        ctx.beginPath();
        ctx.moveTo(sx - 3, 0);
        ctx.lineTo(sx + 3, -h * 0.33);
        ctx.lineTo(sx - 3, -h * 0.66);
        ctx.lineTo(sx, -h);
        ctx.lineWidth = 2.6;
        ctx.strokeStyle = INK;
        ctx.stroke();
      }
      rr(ctx, -21, -h - 4, 42, 6, 3);
      fillStroke(ctx, '#ef4e4e', 1.8);
      ctx.beginPath();
      ctx.moveTo(-16, -h - 2.2);
      ctx.lineTo(16, -h - 2.2);
      ctx.lineWidth = 1.2;
      ctx.strokeStyle = 'rgba(255,255,255,0.6)';
      ctx.stroke();
    } else if (it.type === 'prop') {
      const bob = Math.sin(p.x * 0.05 + performance_now() * 3) * 1.2;
      ctx.translate(x, y - 2 + bob);
      // 螺旋桨帽
      ctx.beginPath();
      ctx.arc(0, -4, 9, Math.PI, TAU);
      ctx.closePath();
      fillStroke(ctx, '#ef5b5b', 1.8);
      rr(ctx, -10, -5, 20, 4, 2);
      fillStroke(ctx, '#3d86d6', 1.6);
      ctx.beginPath();
      ctx.moveTo(0, -13);
      ctx.lineTo(0, -17);
      ctx.lineWidth = 2;
      ctx.strokeStyle = INK;
      ctx.stroke();
      ctx.beginPath();
      ctx.ellipse(0, -18, 13, 2.4, 0, 0, TAU);
      fillStroke(ctx, '#f2d24a', 1.6);
    } else if (it.type === 'jet') {
      ctx.translate(x, y - 1);
      rr(ctx, -11, -28, 9, 26, 4);
      fillStroke(ctx, '#9db4c9', 1.8);
      rr(ctx, 2, -28, 9, 26, 4);
      fillStroke(ctx, '#9db4c9', 1.8);
      rr(ctx, -11, -28, 9, 7, 3);
      fillStroke(ctx, '#e8483c', 1.6);
      rr(ctx, 2, -28, 9, 7, 3);
      fillStroke(ctx, '#e8483c', 1.6);
      ctx.beginPath();
      ctx.moveTo(-2, -16);
      ctx.lineTo(2, -16);
      ctx.lineWidth = 2.4;
      ctx.strokeStyle = INK;
      ctx.stroke();
    }
    ctx.restore();
  }

  // 动画时间：浏览器用 performance.now，测试里也可用
  function performance_now() {
    return (DJ.clock !== undefined ? DJ.clock : Date.now() / 1000) || 0;
  }

  // ---------- 主角：小橘（原创角色：圆滚滚的橘色小家伙，头顶一株小芽，正面朝前，张嘴吐墨水球）----------
  function leaf(ctx, s, len, wid) {
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.quadraticCurveTo(s * len * 0.35, -wid, s * len, -wid * 0.35);
    ctx.quadraticCurveTo(s * len * 0.55, wid * 0.7, 0, 0);
    ctx.closePath();
    fillStroke(ctx, '#86d24a', 1.7);
    ctx.beginPath();
    ctx.moveTo(s * 2, -1);
    ctx.lineTo(s * len * 0.7, -wid * 0.3);
    ctx.lineWidth = 1;
    ctx.strokeStyle = 'rgba(40,100,20,0.6)';
    ctx.stroke();
  }

  function doodlerAt(ctx, dd, x) {
    const st = dd.state;
    ctx.save();
    ctx.translate(x, dd.y);
    if (st === 'blackhole' || st === 'abducted') {
      ctx.translate(0, -24);
      ctx.rotate(dd.spin);
      ctx.scale(Math.max(0.01, dd.scale), Math.max(0.01, dd.scale));
      ctx.translate(0, 24);
    } else if (st === 'dead') {
      ctx.translate(0, -24);
      ctx.rotate(Math.sin(dd.spin) * 0.25);
      ctx.translate(0, 24);
    }
    ctx.scale(dd.facing, 1);
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';

    // 腿的长度：落地压缩 -> 起跳伸长 -> 顶点收起
    const vy = dd.vy;
    let L;
    if (st === 'dead') L = 10;
    else if (dd.bounceT < 0.07) L = 5 + 3 * (dd.bounceT / 0.07);
    else if (vy < -150) L = Math.min(16, 8 + (dd.bounceT - 0.07) * 70);
    else if (vy < 150) L = 16 - 7 * clamp((vy + 150) / 300, 0, 1);
    else L = 9;
    const cy = -(L + 21);
    const shooting = st !== 'dead' && dd.shootT < 0.22;
    const la = dd.facing > 0 ? dd.aim : Math.PI - dd.aim; // 本地坐标下的瞄准角

    // 喷气背包（在身体两侧偏后）
    if (st === 'jetpack') {
      const fl = 14 + Math.sin(dd.t * 60) * 5 + Math.random() * 3;
      for (const jx of [-27, 16]) {
        rr(ctx, jx, cy - 10, 11, 26, 4);
        fillStroke(ctx, '#9db4c9', 2);
        rr(ctx, jx, cy - 10, 11, 7, 3);
        fillStroke(ctx, '#e8483c', 1.6);
        ctx.beginPath();
        ctx.moveTo(jx + 1, cy + 16);
        ctx.lineTo(jx + 5.5, cy + 16 + fl);
        ctx.lineTo(jx + 10, cy + 16);
        ctx.closePath();
        fillStroke(ctx, '#ffb02e', 1.4);
        ctx.beginPath();
        ctx.moveTo(jx + 3, cy + 16);
        ctx.lineTo(jx + 5.5, cy + 16 + fl * 0.55);
        ctx.lineTo(jx + 8, cy + 16);
        ctx.closePath();
        ctx.fillStyle = '#fff29a';
        ctx.fill();
      }
    }

    // 腿
    for (const lx of [-8, 8]) {
      rr(ctx, lx - 3.5, -L - 4, 7, L + 2, 3);
      fillStroke(ctx, '#e3683f', 2);
      ctx.beginPath();
      ctx.ellipse(lx + (lx > 0 ? 1.5 : -1.5), -2.4, 7.5, 3.3, 0, 0, TAU);
      fillStroke(ctx, '#c9502f', 2);
    }

    // 小手：上升时举高，下落时放低，射击时朝瞄准方向
    let ax = 0.75;
    let ay = vy < -100 ? -0.75 : vy > 150 ? 0.55 : -0.1;
    if (st === 'dead') ay = -0.9;
    for (const s of [-1, 1]) {
      let dx = s * ax;
      let dy = ay;
      if (shooting && s === 1) {
        dx = Math.cos(la);
        dy = Math.sin(la);
      }
      const n = Math.hypot(dx, dy);
      const sx0 = s * 16;
      const sy0 = cy + 4;
      ctx.beginPath();
      ctx.moveTo(sx0, sy0);
      ctx.lineTo(sx0 + (dx / n) * 10, sy0 + (dy / n) * 10);
      ctx.lineWidth = 8.6;
      ctx.strokeStyle = INK;
      ctx.stroke();
      ctx.lineWidth = 4.8;
      ctx.strokeStyle = '#f0733f';
      ctx.stroke();
    }

    // 头顶的小芽（螺旋桨帽出现时收起来）
    if (st !== 'propeller') {
      ctx.save();
      ctx.translate(0, cy - 19);
      const sway = Math.sin(dd.t * 7) * 0.1 + clamp(vy / 1600, -0.5, 0.5);
      ctx.rotate(sway);
      ctx.beginPath();
      ctx.moveTo(0, 2);
      ctx.quadraticCurveTo(1.5, -5, 0, -9);
      ctx.lineWidth = 2.4;
      ctx.strokeStyle = INK;
      ctx.stroke();
      ctx.translate(0, -8);
      ctx.save();
      ctx.rotate(-0.25 - clamp(vy / 1800, -0.4, 0.4));
      leaf(ctx, -1, 13, 8);
      ctx.restore();
      ctx.save();
      ctx.rotate(0.25 + clamp(vy / 1800, -0.4, 0.4));
      leaf(ctx, 1, 13, 8);
      ctx.restore();
      ctx.restore();
    }

    // 身体
    const bg = ctx.createRadialGradient(-6, cy - 9, 3, 0, cy, 28);
    bg.addColorStop(0, '#ffc98a');
    bg.addColorStop(1, '#f06b3d');
    ctx.beginPath();
    ctx.ellipse(0, cy, 21, 20.5, 0, 0, TAU);
    fillStroke(ctx, bg, 2.3);
    // 肚皮
    ctx.beginPath();
    ctx.ellipse(0, cy + 8, 12.5, 9.5, 0, 0, TAU);
    ctx.fillStyle = 'rgba(255,238,200,0.8)';
    ctx.fill();

    // 脸（略微偏向朝向的一侧）
    const f = 3;
    const ex = [-7 + f, 7 + f];
    const ey = cy - 5;
    let px = 1.4;
    let py = vy < -100 ? -1.4 : vy > 150 ? 1 : 0;
    if (shooting) {
      px = Math.cos(la) * 2.2;
      py = Math.sin(la) * 2.2;
    }
    for (const e of ex) {
      if (st === 'dead') {
        ctx.beginPath();
        ctx.moveTo(e - 3, ey - 3);
        ctx.lineTo(e + 3, ey + 3);
        ctx.moveTo(e + 3, ey - 3);
        ctx.lineTo(e - 3, ey + 3);
        ctx.lineWidth = 2;
        ctx.strokeStyle = INK;
        ctx.stroke();
      } else {
        ctx.beginPath();
        ctx.arc(e, ey, 5.8, 0, TAU);
        fillStroke(ctx, '#fff', 1.7);
        ctx.beginPath();
        ctx.arc(e + px, ey + py, 2.8, 0, TAU);
        ctx.fillStyle = INK;
        ctx.fill();
      }
    }
    ctx.fillStyle = 'rgba(255,90,90,0.38)';
    for (const bx of [-13 + f, 13 + f]) {
      ctx.beginPath();
      ctx.ellipse(bx, cy + 3.5, 3.4, 2.3, 0, 0, TAU);
      ctx.fill();
    }
    // 嘴：平时微笑，射击时张成圆口，撞怪时歪嘴
    const mx = f + 0.5;
    const my = cy + 7;
    if (st === 'dead') {
      ctx.beginPath();
      ctx.moveTo(mx - 4, my + 1);
      ctx.quadraticCurveTo(mx - 1.5, my - 2, mx, my + 1);
      ctx.quadraticCurveTo(mx + 1.5, my + 4, mx + 4, my);
      ctx.lineWidth = 1.9;
      ctx.strokeStyle = INK;
      ctx.stroke();
    } else if (shooting) {
      ctx.beginPath();
      ctx.ellipse(mx + Math.cos(la) * 1.5, my - 1 + Math.sin(la) * 1.5, 4.4, 5, 0, 0, TAU);
      fillStroke(ctx, INK, 1.4);
      ctx.beginPath();
      ctx.ellipse(mx + Math.cos(la) * 1.5, my + 1 + Math.sin(la) * 1.5, 2.6, 2, 0, 0, TAU);
      ctx.fillStyle = '#ef6f7e';
      ctx.fill();
    } else {
      ctx.beginPath();
      ctx.arc(mx, my - 3, 4.6, 0.2 * Math.PI, 0.8 * Math.PI);
      ctx.lineWidth = 1.9;
      ctx.strokeStyle = INK;
      ctx.stroke();
    }

    // 螺旋桨帽
    if (st === 'propeller') {
      const hy = cy - 19;
      ctx.beginPath();
      ctx.arc(0, hy, 11, Math.PI, TAU);
      ctx.closePath();
      fillStroke(ctx, '#ef5b5b', 2);
      rr(ctx, -12, hy - 1, 24, 4.4, 2);
      fillStroke(ctx, '#3d86d6', 1.8);
      ctx.beginPath();
      ctx.moveTo(0, hy - 11);
      ctx.lineTo(0, hy - 15);
      ctx.lineWidth = 2.2;
      ctx.strokeStyle = INK;
      ctx.stroke();
      const bw = 15 * Math.abs(Math.cos(dd.t * 38));
      ctx.beginPath();
      ctx.ellipse(0, hy - 16, Math.max(2, bw), 2.4, 0, 0, TAU);
      fillStroke(ctx, '#f2d24a', 1.6);
    }

    // 撞到怪物后头上转圈的星星
    if (st === 'dead') {
      for (let i = 0; i < 3; i++) {
        const a = dd.t * 6 + (i * TAU) / 3;
        star(ctx, Math.cos(a) * 17, cy - 26 + Math.sin(a) * 5, 6);
        fillStroke(ctx, '#ffd93b', 1.3);
      }
    }
    ctx.restore();
  }

  function doodler(ctx, dd) {
    if (dd.state === 'gone') return;
    doodlerAt(ctx, dd, dd.x);
    if (dd.state !== 'blackhole' && dd.state !== 'abducted') {
      if (dd.x < 36) doodlerAt(ctx, dd, dd.x + W);
      else if (dd.x > W - 36) doodlerAt(ctx, dd, dd.x - W);
    }
  }
  DJ.drawDoodler = doodler;

  // ---------- 怪物 ----------
  function eyeAt(ctx, x, y, r, look) {
    ctx.beginPath();
    ctx.arc(x, y, r, 0, TAU);
    fillStroke(ctx, '#fff', 1.8);
    ctx.beginPath();
    ctx.arc(x + look.x * r * 0.4, y + look.y * r * 0.4, r * 0.48, 0, TAU);
    ctx.fillStyle = INK;
    ctx.fill();
  }

  function monster(ctx, m, dd) {
    ctx.save();
    ctx.translate(m.x, m.y);
    if (m.dead) ctx.rotate(m.rot);
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    let lx = dd.x - m.x;
    let ly = dd.y - 24 - m.y;
    const ll = Math.hypot(lx, ly) || 1;
    const look = m.dead ? { x: 0, y: 0 } : { x: lx / ll, y: ly / ll };
    const flash = m.flash > 0;

    if (m.kind === 'blob') {
      const sq = 1 + 0.07 * Math.sin(m.t * 5);
      ctx.scale(sq, 1 / sq);
      // 触角
      for (const s of [-1, 1]) {
        ctx.beginPath();
        ctx.moveTo(s * 9, -17);
        ctx.quadraticCurveTo(s * 15, -28, s * 12, -33);
        ctx.lineWidth = 2.2;
        ctx.strokeStyle = INK;
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(s * 12, -34, 3.2, 0, TAU);
        fillStroke(ctx, '#f06a8d', 1.6);
      }
      // 脚
      for (const s of [-1, 1]) {
        ctx.beginPath();
        ctx.ellipse(s * 13, 21, 8, 4, 0, 0, TAU);
        fillStroke(ctx, '#6f35a8', 1.8);
      }
      const g = ctx.createRadialGradient(-8, -10, 4, 0, 0, 32);
      g.addColorStop(0, '#c58df0');
      g.addColorStop(1, '#8445bf');
      ctx.beginPath();
      ctx.ellipse(0, 0, 26, 22, 0, 0, TAU);
      fillStroke(ctx, flash ? '#ffffff' : g, 2.4);
      eyeAt(ctx, -10, -6, 8, look);
      eyeAt(ctx, 9, -6, 8, look);
      // 锯齿嘴
      ctx.beginPath();
      ctx.moveTo(-13, 9);
      for (let i = 0; i < 6; i++) ctx.lineTo(-13 + (i + 1) * (26 / 6), i % 2 === 0 ? 14 : 8);
      ctx.lineWidth = 2;
      ctx.strokeStyle = INK;
      ctx.stroke();
    } else if (m.kind === 'flyer') {
      const fl = Math.sin(m.t * 14);
      for (const s of [-1, 1]) {
        ctx.save();
        ctx.translate(s * 12, -4);
        ctx.rotate(s * (-0.35 + fl * 0.5));
        ctx.beginPath();
        ctx.moveTo(0, 0);
        ctx.quadraticCurveTo(s * 18, -22, s * 30, -8);
        ctx.quadraticCurveTo(s * 20, 0, s * 26, 8);
        ctx.quadraticCurveTo(s * 12, 6, 0, 6);
        ctx.closePath();
        fillStroke(ctx, flash ? '#fff' : '#a8dcf7', 2);
        ctx.restore();
      }
      const g = ctx.createRadialGradient(-5, -6, 3, 0, 0, 22);
      g.addColorStop(0, '#79c9f5');
      g.addColorStop(1, '#3a97d8');
      ctx.beginPath();
      ctx.ellipse(0, 0, 17, 16, 0, 0, TAU);
      fillStroke(ctx, flash ? '#fff' : g, 2.3);
      eyeAt(ctx, 0, -2, 9, look);
      ctx.beginPath();
      ctx.moveTo(-6, 9);
      ctx.lineTo(0, 12);
      ctx.lineTo(6, 9);
      ctx.lineWidth = 2;
      ctx.strokeStyle = INK;
      ctx.stroke();
      for (const s of [-1, 1]) {
        ctx.beginPath();
        ctx.moveTo(s * 7, 15);
        ctx.lineTo(s * 8, 21);
        ctx.lineTo(s * 12, 21);
        ctx.stroke();
      }
    } else {
      // UFO：光束先画
      if (!m.dead) {
        const bg = ctx.createLinearGradient(0, 10, 0, 150);
        bg.addColorStop(0, 'rgba(255,244,130,0.7)');
        bg.addColorStop(1, 'rgba(255,244,130,0)');
        ctx.beginPath();
        ctx.moveTo(-14, 10);
        ctx.lineTo(14, 10);
        ctx.lineTo(32, 150);
        ctx.lineTo(-32, 150);
        ctx.closePath();
        ctx.fillStyle = bg;
        ctx.fill();
      }
      // 圆顶 + 外星人
      ctx.beginPath();
      ctx.arc(0, -3, 16, Math.PI, TAU);
      ctx.closePath();
      fillStroke(ctx, 'rgba(150,235,225,0.9)', 2);
      ctx.beginPath();
      ctx.ellipse(0, -9, 7, 8, 0, 0, TAU);
      fillStroke(ctx, '#7fd957', 1.6);
      ctx.fillStyle = INK;
      ctx.beginPath();
      ctx.ellipse(-2.6, -10, 1.6, 2.6, 0.3, 0, TAU);
      ctx.ellipse(2.6, -10, 1.6, 2.6, -0.3, 0, TAU);
      ctx.fill();
      // 碟身
      const g = ctx.createLinearGradient(0, -2, 0, 16);
      g.addColorStop(0, '#d2d9e0');
      g.addColorStop(1, '#8e98a3');
      ctx.beginPath();
      ctx.ellipse(0, 5, 37, 12, 0, 0, TAU);
      fillStroke(ctx, flash ? '#fff' : g, 2.3);
      for (let i = 0; i < 5; i++) {
        const lxx = -24 + i * 12;
        ctx.beginPath();
        ctx.arc(lxx, 6 + Math.abs(i - 2) * 0.6, 2.5, 0, TAU);
        ctx.fillStyle = Math.floor(m.t * 4 + i) % 2 === 0 ? '#ffe45c' : '#ff6a5c';
        ctx.fill();
      }
    }
    ctx.restore();
  }

  function hole(ctx, h) {
    ctx.save();
    ctx.translate(h.x, h.y);
    const g = ctx.createRadialGradient(0, 0, 2, 0, 0, h.r);
    g.addColorStop(0, '#000');
    g.addColorStop(0.75, '#111');
    g.addColorStop(1, '#2a2a28');
    ctx.beginPath();
    ctx.arc(0, 0, h.r, 0, TAU);
    ctx.fillStyle = g;
    ctx.fill();
    ctx.rotate(h.t * 3);
    ctx.lineCap = 'round';
    for (let i = 0; i < 3; i++) {
      ctx.rotate(TAU / 3);
      ctx.beginPath();
      for (let a = 0; a < 3.2; a += 0.2) {
        const r = 4 + a * 7;
        ctx.lineTo(Math.cos(a) * r, Math.sin(a) * r);
      }
      ctx.lineWidth = 2;
      ctx.strokeStyle = 'rgba(255,255,255,0.28)';
      ctx.stroke();
    }
    ctx.restore();
  }

  function bullet(ctx, b) {
    ctx.beginPath();
    ctx.arc(b.x, b.y, 4.6, 0, TAU);
    fillStroke(ctx, '#fffdf0', 1.7);
  }

  function debris(ctx, q) {
    ctx.save();
    ctx.translate(q.x, q.y + q.h / 2);
    ctx.rotate(q.rot);
    const pal = PAL.brown;
    rr(ctx, -q.w / 2, -q.h / 2, q.w, q.h, 4);
    const g = ctx.createLinearGradient(0, -q.h / 2, 0, q.h / 2);
    g.addColorStop(0, pal.top);
    g.addColorStop(1, pal.bot);
    ctx.fillStyle = g;
    ctx.fill();
    ctx.lineWidth = 1.8;
    ctx.strokeStyle = pal.line;
    ctx.stroke();
    ctx.restore();
  }

  // ---------- HUD ----------
  function hud(ctx, score) {
    const bh = 34;
    ctx.fillStyle = 'rgba(190,225,232,0.66)';
    ctx.fillRect(0, 0, W, bh);
    ctx.fillStyle = 'rgba(95,150,165,0.85)';
    ctx.fillRect(0, bh - 1.2, W, 1.2);
    ctx.fillStyle = '#3d3d34';
    ctx.font = DJ.font(24);
    ctx.textBaseline = 'middle';
    ctx.textAlign = 'left';
    ctx.fillText(String(score), 12, bh / 2 + 1);
    // 暂停按钮
    const px = W - 24;
    const py = bh / 2;
    rr(ctx, px - 8, py - 9, 5.5, 18, 2);
    ctx.fillStyle = '#3d3d34';
    ctx.fill();
    rr(ctx, px + 2.5, py - 9, 5.5, 18, 2);
    ctx.fill();
  }
  DJ.drawHud = hud;

  // ---------- 整个场景 ----------
  DJ.drawScene = function (ctx, g) {
    const v = DJ.view;
    background(ctx, g.camY);
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 0, W, v.H);
    ctx.clip();
    ctx.translate(0, -g.camY);
    const top = g.camY - 90;
    const bot = g.camY + v.H + 90;

    // 最高分记录线
    if (g.best > 0) {
      const ry = g.startY - g.best * 2;
      if (ry > top && ry < bot) {
        ctx.beginPath();
        ctx.setLineDash([8, 6]);
        ctx.moveTo(0, ry);
        ctx.lineTo(W, ry);
        ctx.lineWidth = 2;
        ctx.strokeStyle = 'rgba(214,72,72,0.75)';
        ctx.stroke();
        ctx.setLineDash([]);
        ctx.font = DJ.font(13);
        ctx.fillStyle = 'rgba(214,72,72,0.9)';
        ctx.textAlign = 'right';
        ctx.textBaseline = 'bottom';
        ctx.fillText('最高分 ' + g.best, W - 6, ry - 3);
      }
    }

    for (let i = 0; i < g.platforms.length; i++) {
      const p = g.platforms[i];
      if (p.y > top && p.y < bot) platform(ctx, p);
    }
    for (let i = 0; i < g.holes.length; i++) hole(ctx, g.holes[i]);
    for (let i = 0; i < g.monsters.length; i++) {
      const m = g.monsters[i];
      if (m.y > top - 40 && m.y < bot + 40) monster(ctx, m, g.dd);
    }
    for (let i = 0; i < g.debris.length; i++) debris(ctx, g.debris[i]);
    for (let i = 0; i < g.bullets.length; i++) bullet(ctx, g.bullets[i]);
    for (let i = 0; i < g.sparks.length; i++) {
      const s = g.sparks[i];
      ctx.globalAlpha = clamp(s.life / s.max, 0, 1);
      star(ctx, s.x, s.y, 6);
      fillStroke(ctx, '#ffd93b', 1.2);
      ctx.globalAlpha = 1;
    }
    doodler(ctx, g.dd);
    ctx.restore();
    letterbox(ctx);
    hud(ctx, g.score);
  };

  // 菜单里用的假小橘
  DJ.fakeDoodler = function (x, y, facing, vy, bounceT, t) {
    return { x: x, y: y, facing: facing, vy: vy, bounceT: bounceT, t: t, state: 'normal', shootT: 99, aim: -Math.PI / 2, spin: 0, scale: 1 };
  };
  DJ.drawPlatformAt = function (ctx, type, x, y) {
    platform(ctx, { type: type, x: x, y: y, w: DJ.C.PLAT_W, h: DJ.C.PLAT_H, dead: false, fade: 0, item: null });
  };
})();
