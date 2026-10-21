var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const clamp = DJ.clamp;
  const lerp = DJ.lerp;
  const INK = '#2d2d26';
  const TAU = Math.PI * 2;
  const HS = DJ.C.HERO; // 主角缩放：画主角时先缩放，内部仍按原来的“单位”画

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
  // 省电：方格只在屏幕尺寸变化时画一次，做成一小块贴图（一个格子），每帧用一次 fillRect 平铺上去，
  // 不用每帧描一百多条整屏长的线
  let bgTile = null;
  let bgPat = null;
  let bgK = 0;
  let bgCtx = null;
  function bgPattern(ctx, v) {
    const k = v.scale * v.dpr; // 一个单位等于多少个设备像素
    if (bgPat && bgK === k && bgCtx === ctx) return bgPat;
    bgPat = null;
    if (typeof document === 'undefined' || typeof DOMMatrix === 'undefined') return null;
    // 贴图边长取整数个设备像素，平铺时一个贴图像素正好对一个屏幕像素（不缩放、不插值，最快也最清晰）；
    // 格子因此可能是 19.9 或 20.1 个单位，肉眼看不出来
    const n = Math.max(4, Math.round(20 * k));
    bgTile = bgTile || document.createElement('canvas');
    bgTile.width = bgTile.height = n;
    const t = bgTile.getContext('2d');
    t.fillStyle = '#f7f4e1';
    t.fillRect(0, 0, n, n);
    // 和原来一样：线宽 max(1.3 设备像素, 0.5 单位)，线居中压在格子边界上（贴图的上边/左边各一半、下边/右边各一半）
    const lw = Math.max(1.3, 0.5 * k);
    t.fillStyle = '#c5dcd8';
    t.fillRect(0, 0, n, lw / 2);
    t.fillRect(0, n - lw / 2, n, lw / 2);
    t.fillRect(0, 0, lw / 2, n);
    t.fillRect(n - lw / 2, 0, lw / 2, n);
    const pat = ctx.createPattern(bgTile, 'repeat');
    if (!pat || !pat.setTransform) return null;
    bgPat = pat;
    bgK = k;
    bgCtx = ctx;
    bgPat.n = n;
    bgPat.cell = n / k;
    return bgPat;
  }
  function background(ctx, camY) {
    const v = DJ.view;
    const cell = 20;
    const m = 14; // 多画一圈边距：屏幕震动时画面会偏移，不能露出底下的旧画面
    const pat = bgPattern(ctx, v);
    if (pat) {
      const k = v.scale * v.dpr;
      const c = pat.cell;
      // 卷动偏移和画布本身的平移都对齐到整数设备像素
      const tr = ctx.getTransform();
      const offY = ((camY % c) + c) % c;
      const ex = (Math.round(tr.e) - tr.e) / k;
      const ey = (Math.round(tr.f - offY * k) - (tr.f - offY * k)) / k - offY;
      pat.setTransform(new DOMMatrix([1 / k, 0, 0, 1 / k, ex, ey]));
      ctx.fillStyle = pat;
      ctx.fillRect(v.x0 - m, v.y0 - m, v.x1 - v.x0 + 2 * m, v.y1 - v.y0 + 2 * m);
      return;
    }
    ctx.fillStyle = '#f7f4e1';
    ctx.fillRect(v.x0 - m, v.y0 - m, v.x1 - v.x0 + 2 * m, v.y1 - v.y0 + 2 * m);
    ctx.beginPath();
    for (let x = Math.floor((v.x0 - m) / cell) * cell; x <= v.x1 + m; x += cell) {
      ctx.moveTo(x, v.y0 - m);
      ctx.lineTo(x, v.y1 + m);
    }
    const n0 = Math.floor((camY + v.y0 - m) / cell);
    const n1 = Math.ceil((camY + v.y1 + m) / cell);
    for (let n = n0; n <= n1; n++) {
      const y = n * cell - camY;
      ctx.moveTo(v.x0 - m, y);
      ctx.lineTo(v.x1 + m, y);
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

  function platGradient(ctx, type, h) {
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, PAL[type].top);
    g.addColorStop(1, PAL[type].bot);
    return g;
  }

  // ---------- 贴图缓存 ----------
  // 不会变形的东西（平台、静止的道具）只在第一次用到时按屏幕像素密度画进一张小画布，之后每帧一次 drawImage 贴上去。
  // 矢量路径（圆角、描边、渐变）每帧都要重新细分三角形、光栅化，贴图只是一个带纹理的矩形，GPU 上便宜得多
  const sprites = {};
  let spriteK = 0;
  function sprite(key, x0, y0, w, h, draw) {
    const v = DJ.view;
    const k = v.scale * v.dpr;
    if (Math.abs(k - spriteK) > 1e-6) {
      for (const kk in sprites) delete sprites[kk];
      spriteK = k;
    }
    let sp = sprites[key];
    if (sp === undefined) {
      sp = null;
      if (typeof document !== 'undefined') {
        const c = document.createElement('canvas');
        c.width = Math.max(1, Math.ceil(w * k));
        c.height = Math.max(1, Math.ceil(h * k));
        const g = c.getContext('2d');
        g.scale(c.width / w, c.height / h);
        g.translate(-x0, -y0);
        g.lineJoin = 'round';
        g.lineCap = 'round';
        draw(g);
        sp = { c: c, x0: x0, y0: y0, w: w, h: h };
      }
      sprites[key] = sp;
    }
    return sp;
  }
  function blit(ctx, sp) {
    ctx.drawImage(sp.c, sp.x0, sp.y0, sp.w, sp.h);
  }

  function platformBody(ctx, p) {
    ctx.save();
    if (p.dead) ctx.globalAlpha = Math.max(0, 1 - p.fade / 0.22);
    ctx.translate(p.x - p.w / 2, p.y);
    const sp = sprite('plat-' + p.type + '-' + p.w + 'x' + p.h, -2, -2, p.w + 4, p.h + 4, function (g) {
      platShape(g, p.type, p.w, p.h);
    });
    if (sp) blit(ctx, sp);
    else platShape(ctx, p.type, p.w, p.h);
    ctx.restore();
    if (p.item && !p.item.used) item(ctx, p, p.item);
  }

  function platShape(ctx, type, w, h) {
    const pal = PAL[type];
    const p = { w: w, h: h, type: type };
    const x = 0;
    const y = 0;
    rr(ctx, x, y, p.w, p.h, 5.5);
    ctx.fillStyle = platGradient(ctx, p.type, p.h);
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
  }

  // 被踩到时整块平台（连同上面的道具）跟着 dip 弹簧往下沉一点再弹回来
  function platform(ctx, p) {
    if (p.dip) {
      ctx.save();
      ctx.translate(0, p.dip);
      platformBody(ctx, p);
      ctx.restore();
    } else platformBody(ctx, p);
  }

  // ---------- 平台上的道具 ----------
  // 静止的弹簧 / 蹦床、喷气背包、螺旋桨帽（只是上下浮动）走贴图；弹出动画和飞翼这类一直在动的照旧矢量画
  const ITEM_BOX = { spring: [-12, -20, 24, 24], tramp: [-24, -15, 48, 18], jet: [-14, -31, 28, 34], prop: [-16, -27, 32, 28] };
  function item(ctx, p, it) {
    const box = ITEM_BOX[it.type];
    if (box && !it.fired) {
      const sp = sprite('item-' + it.type, box[0], box[1], box[2], box[3], function (g) {
        itemRaw(g, { x: 0, y: 0 }, { type: it.type, ox: 0, fired: false, t: 0 }, true);
      });
      if (sp) {
        const bob = it.type === 'prop' ? Math.sin(p.x * 0.05 + performance_now() * 3) * 1.2 : 0;
        ctx.save();
        ctx.translate(p.x + it.ox, p.y + bob);
        blit(ctx, sp);
        ctx.restore();
        return;
      }
    }
    itemRaw(ctx, p, it, false);
  }
  function itemRaw(ctx, p, it, still) {
    const x = p.x + it.ox;
    const y = p.y;
    ctx.save();
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    if (it.type === 'spring') {
      const ext = it.fired ? Math.sin(clamp(it.t / 0.3, 0, 1) * Math.PI) * 22 : 0;
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
      const ext = it.fired ? Math.sin(clamp(it.t / 0.35, 0, 1) * Math.PI) * 26 : 0;
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
      const bob = still ? 0 : Math.sin(p.x * 0.05 + performance_now() * 3) * 1.2;
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
    } else if (it.type === 'wings') {
      // 飞翼：一对小翅膀悬在平台上方慢慢扇，周围一圈呼吸的金光和闪烁的小星星
      const t = performance_now();
      const bob = Math.sin(t * 3 + p.x * 0.05) * 2;
      ctx.translate(x, y - 14 + bob);
      const pulse = 0.75 + 0.25 * Math.sin(t * 5);
      const gr = ctx.createRadialGradient(0, 0, 2, 0, 0, 30);
      gr.addColorStop(0, 'rgba(255,236,150,' + (0.85 * pulse).toFixed(3) + ')');
      gr.addColorStop(1, 'rgba(255,236,150,0)');
      ctx.fillStyle = gr;
      ctx.beginPath();
      ctx.arc(0, 0, 30, 0, TAU);
      ctx.fill();
      const flap = Math.sin(t * 6) * 0.3;
      for (const s of [-1, 1]) {
        ctx.save();
        ctx.translate(s * 3, 2);
        ctx.scale(s * 0.4, 0.4);
        ctx.rotate(-0.25 - flap);
        wingShape(ctx, false, t);
        ctx.restore();
      }
      // 中间一颗小宝石把两只翅膀连起来
      ctx.beginPath();
      ctx.moveTo(0, -5);
      ctx.lineTo(4.5, 1);
      ctx.lineTo(0, 7);
      ctx.lineTo(-4.5, 1);
      ctx.closePath();
      fillStroke(ctx, '#ff7ac8', 1.6);
      for (let i = 0; i < 3; i++) {
        const a = t * 1.7 + (i * TAU) / 3;
        const k = 0.5 + 0.5 * Math.sin(t * 7 + i * 2);
        star(ctx, Math.cos(a) * 24, Math.sin(a) * 12 - 2, 2 + 2.2 * k);
        ctx.fillStyle = 'rgba(255,240,160,' + (0.5 + 0.5 * k).toFixed(3) + ')';
        ctx.fill();
      }
    } else if (it.type === 'dark') {
      // 紫黑飞翼：悬得更高，暗紫色光晕里有一圈慢慢转的符文虚线环，几颗紫色小火星绕着它转
      const t = performance_now();
      const bob = Math.sin(t * 2.6 + p.x * 0.05) * 2.4;
      ctx.translate(x, y - 16 + bob);
      const pulse = 0.7 + 0.3 * Math.sin(t * 4);
      const gr = ctx.createRadialGradient(0, 0, 2, 0, 0, 34);
      gr.addColorStop(0, 'rgba(170,90,255,' + (0.8 * pulse).toFixed(3) + ')');
      gr.addColorStop(0.55, 'rgba(110,40,200,' + (0.35 * pulse).toFixed(3) + ')');
      gr.addColorStop(1, 'rgba(80,20,160,0)');
      ctx.fillStyle = gr;
      ctx.beginPath();
      ctx.arc(0, 0, 34, 0, TAU);
      ctx.fill();
      ctx.save();
      ctx.rotate(t * 0.9);
      ctx.beginPath();
      ctx.arc(0, 0, 24, 0, TAU);
      ctx.setLineDash([2.5, 4.5]);
      ctx.lineWidth = 1.4;
      ctx.strokeStyle = 'rgba(215,180,255,0.85)';
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.restore();
      const flap = Math.sin(t * 7) * 0.32;
      for (const s of [-1, 1]) {
        ctx.save();
        ctx.translate(s * 3, 2);
        ctx.scale(s * 0.42, 0.42);
        ctx.rotate(-0.25 - flap);
        wingShape(ctx, true, t);
        ctx.restore();
      }
      // 中间一颗黑紫色的宝石，边上一圈霓虹描边
      ctx.beginPath();
      ctx.moveTo(0, -6);
      ctx.lineTo(5, 1);
      ctx.lineTo(0, 8);
      ctx.lineTo(-5, 1);
      ctx.closePath();
      ctx.fillStyle = '#2a0f45';
      ctx.fill();
      ctx.lineWidth = 1.7;
      ctx.strokeStyle = '#e2c6ff';
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(-1.5, -2.5);
      ctx.lineTo(1.5, 0);
      ctx.lineWidth = 1.3;
      ctx.strokeStyle = 'rgba(140,240,255,0.9)';
      ctx.stroke();
      for (let i = 0; i < 4; i++) {
        const a = -t * 2.1 + (i * TAU) / 4;
        const k = 0.5 + 0.5 * Math.sin(t * 8 + i * 1.7);
        star(ctx, Math.cos(a) * 27, Math.sin(a) * 13 - 2, 1.8 + 2 * k);
        ctx.fillStyle = i % 2 ? 'rgba(255,130,240,' + (0.55 + 0.45 * k).toFixed(3) + ')' : 'rgba(225,200,255,' + (0.55 + 0.45 * k).toFixed(3) + ')';
        ctx.fill();
      }
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

  // 一只右翅膀（根部在原点，向 +x 张开），五根长飞羽扇形排开 + 根部一片覆羽。左翅膀由调用方镜像
  const FEATHERS = [
    [-0.78, 52],
    [-0.5, 50],
    [-0.22, 45],
    [0.05, 38],
    [0.3, 29],
  ];
  // dark=true 是紫黑飞翼：飞羽更长更尖、黑紫色，每根羽毛内侧有一圈霓虹紫的描边，
  // 还有一道随时间在羽毛上来回流动的虹彩光泽
  function wingShape(ctx, dark, t) {
    ctx.lineJoin = 'round';
    for (let i = 0; i < FEATHERS.length; i++) {
      const a = FEATHERS[i][0];
      const L = FEATHERS[i][1] * (dark ? 1.1 : 1);
      const ca = Math.cos(a);
      const sa = Math.sin(a);
      const g = ctx.createLinearGradient(0, 0, ca * L, sa * L);
      const rw = dark ? 5.9 : 6.8;
      if (dark) {
        const sh = 0.25 + 0.5 * (0.5 + 0.5 * Math.sin((t || 0) * 3.2 - i * 0.8));
        g.addColorStop(0, '#160a24');
        g.addColorStop(Math.max(0.01, sh - 0.18), '#2e1350');
        g.addColorStop(sh, '#b98aff');
        g.addColorStop(Math.min(0.99, sh + 0.18), '#4a1f86');
        g.addColorStop(1, '#8f4dff');
      } else {
        g.addColorStop(0, '#ffffff');
        g.addColorStop(0.6, '#fffbe8');
        g.addColorStop(1, '#ffd96a');
      }
      ctx.beginPath();
      ctx.ellipse(ca * L * 0.52, sa * L * 0.52, L * 0.52, rw, a, 0, TAU);
      ctx.fillStyle = g;
      ctx.fill();
      ctx.lineWidth = 1.8;
      ctx.strokeStyle = dark ? '#09040f' : INK;
      ctx.stroke();
      if (dark) {
        ctx.beginPath();
        ctx.ellipse(ca * L * 0.54, sa * L * 0.54, L * 0.44, rw * 0.55, a, 0, TAU);
        ctx.lineWidth = 0.9;
        ctx.strokeStyle = 'rgba(206,160,255,0.75)';
        ctx.stroke();
      }
    }
    ctx.beginPath();
    ctx.ellipse(13, -6, 17, 11, -0.35, 0, TAU);
    fillStroke(ctx, dark ? '#1f0c33' : '#ffffff', 1.8);
    if (dark) {
      ctx.strokeStyle = '#09040f';
      ctx.stroke();
      // 覆羽上的一颗小宝石反光
      ctx.beginPath();
      ctx.ellipse(16, -8, 3.4, 2, -0.35, 0, TAU);
      ctx.fillStyle = 'rgba(150,240,255,0.85)';
      ctx.fill();
    }
    ctx.beginPath();
    ctx.moveTo(5, -4);
    ctx.quadraticCurveTo(14, -12, 25, -11);
    ctx.lineWidth = 1.4;
    ctx.strokeStyle = dark ? 'rgba(200,140,255,0.95)' : 'rgba(230,180,60,0.8)';
    ctx.stroke();
  }

  // ---------- 主角：小海星（原创角色：绿色的肥肥海星，五条胖手臂，正面朝前，张嘴吐泡泡球）----------
  // 全部姿态量（肚子、肚皮、手臂、拉伸、脸朝向）都由 world.js 里的弹簧/指数平滑产生，这里只负责画。
  // 身体是“一整块”：五条胖手臂 + 中心圆盘用同一条路径、同样的线宽描一遍外轮廓再描一遍填色，
  // 所以手臂和身体之间没有多余的内部线条，像一块软糖。下面两条手臂当腿，两侧的手臂当手。
  const OUT_W = 2.3; // 外轮廓粗细
  const ARM_ROOT = 11.5; // 手臂根部的半径（越大越肥）
  const ARM_TIP = 6.6; // 手臂尖端的半径（比根部细，才有海星尖尖的手臂）
  const ARM_LEN = 24; // 手臂从中心伸出去的长度
  const DISC = 13; // 中心圆盘的半径，脸画在这上面
  const FOOT_Y = -7.8; // 脚尖圆心的高度，让描边底部刚好落在平台表面

  // 把当前姿态换算成各个部位的位置（身体、手、脚、头顶那条手臂），画角色和画残影共用
  function starGeom(dd) {
    const st = dd.state;
    const vy = dd.vy;
    // 腿的长度：落地压缩 -> 起跳伸长 -> 顶点收起（用缓动曲线，不是分段直线）
    let L;
    if (st === 'dead') L = 10;
    else if (dd.bounceT < 0.07) L = 5 + 3 * (dd.bounceT / 0.07);
    else if (vy < -150) L = Math.min(16, 8 + (dd.bounceT - 0.07) * 70);
    else if (vy < 150) L = 16 - 7 * clamp((vy + 150) / 300, 0, 1);
    else L = 9;

    // 果冻身体：肚子弹簧 belly（>0 又宽又扁，<0 又窄又高）+ 空中拉伸 stretch
    const bd = clamp(dd.belly || 0, -1, 1);
    const b2 = dd.b2 === undefined ? bd : dd.b2;
    const stretch = dd.stretch || 0;
    const sxB = (1 + 0.5 * bd) * (1 - stretch * 0.45);
    const syB = (1 - 0.42 * bd) * (1 + stretch * 0.6);
    const sxA = 1 + (sxB - 1) * 0.65; // 手臂左右伸缩得比圆盘克制一点，不然压扁时会张得太开
    const v = (0.81 * (ARM_LEN - 2) + (L - 9) * 0.55) * syB; // 脚尖到身体中心的垂直距离：脚固定踩在地上，身体被腿撑起来
    const by = FOOT_Y - v;
    const look = dd.look === undefined ? 0.5 : dd.look;
    const pose = dd.armPose === undefined ? clamp(vy / 350, -1, 1) : dd.armPose;
    const shooting = st !== 'dead' && dd.shootT < 0.22;
    const aim = dd.aim === undefined ? -Math.PI / 2 : dd.aim;
    const shootK = st === 'dead' ? 0 : dd.shootT < 0.2 ? 1 : clamp(1 - (dd.shootT - 0.2) / 0.14, 0, 1);

    // 头顶那条手臂：跟着水平速度往后甩，随肚子晃
    const sway = Math.sin((dd.t || 0) * 7) * 0.07 - clamp((dd.vx || 0) / 900, -0.3, 0.3) - b2 * 0.1;
    const topLen = (ARM_LEN + 1) * syB;
    const top = [Math.sin(sway) * topLen, by - Math.cos(sway) * topLen];

    // 两侧的手：姿态由 armPose 平滑决定（高举 -> 斜上 -> 微垂），随肚皮晃动甩一甩；射击时那一侧的手指向瞄准方向
    const armSide = Math.cos(aim) >= 0 ? 1 : -1;
    const sides = [];
    for (const s of [-1, 1]) {
      // 肚皮被压扁（b2>0）时手臂往外摊开，像拍扁的海星；拉长（b2<0）时往上收。
      // 角度限制在 -1.12 以内：再往上就和头顶那条手臂叠成一团了
      const th = clamp(lerp(-1.05, 0.4, (pose + 1) / 2) + b2 * 0.45, -1.12, 0.7);
      let dx = s * Math.cos(th);
      let dy = Math.sin(th);
      let len = ARM_LEN;
      if (shooting && s === armSide) {
        dx = lerp(dx, Math.cos(aim), shootK);
        dy = lerp(dy, Math.sin(aim), shootK);
        len = ARM_LEN + 3 * shootK;
      }
      const n = Math.hypot(dx, dy) || 1;
      sides.push([(dx / n) * len * sxA, by + (dy / n) * len * syB]);
    }
    // 下面两条手臂当腿：脚间距固定，腿的伸缩体现在身体被撑高
    const fx = 0.59 * (ARM_LEN - 2) * sxA;
    const legs = [
      [-fx, FOOT_Y],
      [fx, FOOT_Y],
    ];
    return { st: st, vy: vy, L: L, sxB: sxB, syB: syB, b2: b2, bd: bd, by: by, look: look, pose: pose, shootK: shootK, aim: aim, top: top, sides: sides, legs: legs };
  }
  DJ.starGeom = starGeom;
  DJ.HERO_DIM = { OUT_W: OUT_W, ARM_ROOT: ARM_ROOT, ARM_TIP: ARM_TIP, ARM_LEN: ARM_LEN, DISC: DISC, FOOT_Y: FOOT_Y };

  // 整个身体的路径：五条根粗尖细的胖手臂（一串渐变半径的圆叠出来）+ 中心圆盘，全在同一条路径里，
  // extra 是外扩的像素数（画外轮廓时用），整条路径一次 fill 出来就是一整块没有内部线条的剪影
  function starPath(ctx, g, extra, n) {
    ctx.beginPath();
    const limbs = [
      [g.top, ARM_TIP + 0.5],
      [g.sides[0], ARM_TIP],
      [g.sides[1], ARM_TIP],
      [g.legs[0], ARM_TIP + 1.2],
      [g.legs[1], ARM_TIP + 1.2],
    ];
    const N = n || 7; // 残影只是一层半透明剪影，用 4 个圆叠一条手臂就够了，路径少一半
    for (let i = 0; i < limbs.length; i++) {
      const tx = limbs[i][0][0];
      const ty = limbs[i][0][1];
      const rt = limbs[i][1];
      for (let k = 1; k <= N; k++) {
        const t = k / N;
        const r = lerp(ARM_ROOT, rt, t) + extra;
        const cx = tx * t;
        const cy = g.by + (ty - g.by) * t;
        ctx.moveTo(cx + r, cy);
        ctx.arc(cx, cy, r, 0, TAU);
      }
    }
    ctx.moveTo(DISC * g.sxB + extra, g.by);
    ctx.ellipse(0, g.by, DISC * g.sxB + extra, DISC * g.syB + extra, 0, 0, TAU);
  }

  // ---------- 身体倾斜 / 旋转 ----------
  // canvas 变换矩阵 [a, b, c, d, e, f]：x' = a x + c y + e，y' = b x + d y + f
  const ID = [1, 0, 0, 1, 0, 0];
  function rotAbout(a, px, py) {
    const c = Math.cos(a);
    const s = Math.sin(a);
    return [c, s, -s, c, px - c * px + s * py, py - s * px - c * py];
  }
  function mul(m, n) {
    return [
      m[0] * n[0] + m[2] * n[1],
      m[1] * n[0] + m[3] * n[1],
      m[0] * n[2] + m[2] * n[3],
      m[1] * n[2] + m[3] * n[3],
      m[0] * n[4] + m[2] * n[5] + m[4],
      m[1] * n[4] + m[3] * n[5] + m[5],
    ];
  }
  // lean：整个角色（连同翅膀、喷气背包）一起朝移动方向倾斜。踩在平台上蓄力时绕脚底转，腾空后绕身体中心转（leanAir 平滑过渡）
  // spin：紫黑飞翼时只有身体绕中心旋转，翅膀保持张开朝上
  function heroXf(dd, g) {
    const lean = dd.lean || 0;
    const roll = dd.roll || 0;
    const air = dd.leanAir === undefined ? 1 : dd.leanAir;
    // 踩在平台上时转轴是“前脚”（往哪边歪就以哪只脚为支点，另一只脚抬起来），不会把脚压进平台里
    const footX = g.legs[1][0] * clamp(lean / 0.2, -1, 1) * (1 - air);
    const lm = Math.abs(lean) > 1e-4 ? rotAbout(lean, footX, lerp(FOOT_Y, g.by, air)) : ID;
    const sm = Math.abs(roll) > 1e-4 ? rotAbout(roll, 0, g.by) : ID;
    return { lean: lm, spin: sm, body: sm === ID ? lm : mul(lm, sm) };
  }
  DJ.heroXf = heroXf;

  // 残影：只画一层半透明的绿色剪影（一次 fill，不会因为部位重叠而叠深）
  function starGhost(ctx, d, x, y, alpha, col) {
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(HS, HS);
    const g = starGeom(d);
    const xf = heroXf(d, g);
    if (xf.body !== ID) ctx.transform.apply(ctx, xf.body);
    starPath(ctx, g, 1.5, 4);
    ctx.globalAlpha = alpha;
    ctx.fillStyle = col || '#b6f783';
    ctx.fill();
    ctx.restore();
  }
  DJ.drawStarGhost = starGhost;

  // 飞翼：两只大翅膀长在背后（画在身体后面），飞行时快速扇动，快结束时越扇越慢
  function heroWings(ctx, dd, g, dark) {
    const T = dark ? DJ.C.DARK_T : DJ.C.WING_T;
    const left = T - (dd.stateT || 0);
    const top = dark ? 19 : 15;
    const speed = left < 1 ? lerp(7, top, clamp(left, 0, 1)) : top;
    const flap = Math.sin((dd.t || 0) * speed) * (dark ? 0.5 : 0.55);
    const open = clamp((dd.stateT || 0) / 0.18, 0, 1); // 拿到的一瞬间翅膀“唰”地张开
    const t = dd.t || 0;
    // 背后的光晕：白色飞翼是金色的；紫黑飞翼是深紫色，外面还有两圈反向转动的符文环
    const r = (dark ? 66 : 58) * (0.6 + 0.4 * open);
    const gr = ctx.createRadialGradient(0, g.by, 4, 0, g.by, r);
    if (dark) {
      gr.addColorStop(0, 'rgba(190,120,255,0.6)');
      gr.addColorStop(0.45, 'rgba(110,40,210,0.32)');
      gr.addColorStop(1, 'rgba(60,10,120,0)');
    } else {
      gr.addColorStop(0, 'rgba(255,236,150,0.55)');
      gr.addColorStop(1, 'rgba(255,236,150,0)');
    }
    ctx.fillStyle = gr;
    ctx.beginPath();
    ctx.arc(0, g.by, r, 0, TAU);
    ctx.fill();
    if (dark) {
      ctx.save();
      ctx.translate(0, g.by);
      ctx.globalAlpha = open;
      ctx.lineCap = 'round';
      ctx.rotate(-t * 1.6);
      ctx.beginPath();
      ctx.arc(0, 0, 44, 0, TAU);
      ctx.setLineDash([5, 7]);
      ctx.lineWidth = 1.6;
      ctx.strokeStyle = 'rgba(200,150,255,0.7)';
      ctx.stroke();
      ctx.setLineDash([]);
      // 外圈：12 道刻度
      ctx.rotate(t * 2.8);
      ctx.beginPath();
      for (let i = 0; i < 12; i++) {
        const a = (i * TAU) / 12;
        ctx.moveTo(Math.cos(a) * 49, Math.sin(a) * 49);
        ctx.lineTo(Math.cos(a) * (i % 3 === 0 ? 56 : 52), Math.sin(a) * (i % 3 === 0 ? 56 : 52));
      }
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = 'rgba(255,120,235,0.55)';
      ctx.stroke();
      ctx.restore();
    }
    for (const s of [-1, 1]) {
      ctx.save();
      ctx.translate(s * 6 * g.sxB, g.by - 4 * g.syB);
      const ws = (dark ? 1.36 : 1.3) * (0.35 + 0.65 * open); // 翅膀比身体还大，一眼就看得出是终极道具
      ctx.scale(s * ws, ws);
      ctx.rotate(-0.12 - flap);
      wingShape(ctx, dark, t + s * 0.4);
      ctx.restore();
    }
  }

  // 紫黑飞翼：三颗拖着尾巴的小彗星绕着旋转的小海星转（画在身体前面，椭圆轨道后半圈压暗，像真的绕到了背后）
  function heroDarkFront(ctx, dd, g) {
    const t = dd.t || 0;
    const open = clamp((dd.stateT || 0) / 0.25, 0, 1);
    ctx.save();
    ctx.translate(0, g.by);
    ctx.rotate(-0.35);
    ctx.lineCap = 'round';
    const cols = ['199,125,255', '255,110,235', '125,240,255'];
    for (let i = 0; i < 3; i++) {
      const a0 = t * 6.5 + (i * TAU) / 3;
      const depth = Math.sin(a0); // >0 在前面
      const k = open * (0.35 + 0.65 * (0.5 + 0.5 * depth));
      ctx.beginPath();
      for (let j = 0; j <= 8; j++) {
        const a = a0 - j * 0.11;
        const px = Math.cos(a) * 40;
        const py = Math.sin(a) * 13;
        if (j === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.lineWidth = 2.6 * k;
      ctx.strokeStyle = 'rgba(' + cols[i] + ',' + (0.6 * k).toFixed(3) + ')';
      ctx.stroke();
      star(ctx, Math.cos(a0) * 40, Math.sin(a0) * 13, 2.2 + 2.6 * k);
      ctx.fillStyle = 'rgba(255,255,255,' + k.toFixed(3) + ')';
      ctx.fill();
    }
    ctx.restore();
  }

  // 飞翼结束后的护盾：一圈闪烁的金色光环，快结束时闪得更快
  function heroShield(ctx, dd, g) {
    const t = dd.t || 0;
    const fade = clamp(dd.shield / 0.4, 0, 1);
    const blink = dd.shield < 0.6 ? 0.5 + 0.5 * Math.sin(t * 40) : 0.75 + 0.25 * Math.sin(t * 10);
    const r = 36 + Math.sin(t * 8) * 1.5;
    ctx.save();
    ctx.globalAlpha = fade * blink;
    const dark = dd.shieldDark;
    const gr = ctx.createRadialGradient(0, g.by, r * 0.55, 0, g.by, r);
    gr.addColorStop(0, dark ? 'rgba(170,90,255,0)' : 'rgba(255,236,150,0)');
    gr.addColorStop(1, dark ? 'rgba(160,80,255,0.45)' : 'rgba(255,226,120,0.45)');
    ctx.fillStyle = gr;
    ctx.beginPath();
    ctx.arc(0, g.by, r, 0, TAU);
    ctx.fill();
    ctx.lineWidth = 2.4;
    ctx.strokeStyle = dark ? 'rgba(190,130,255,0.95)' : 'rgba(240,190,50,0.9)';
    ctx.setLineDash([7, 5]);
    ctx.lineDashOffset = -t * 30;
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
  }

  // 2D 版本的身体和脸（手绘描边）
  function body2d(ctx, dd, g) {
    const st = dd.state;
    const sxB = g.sxB;
    const syB = g.syB;
    const by = g.by;
    const b2 = g.b2;
    const look = g.look;
    const pose = g.pose;
    const aim = g.aim;
    const shootK = g.shootK;
    // 身体：先整块描黑边，再整块填上渐变的绿色
    starPath(ctx, g, OUT_W);
    ctx.fillStyle = INK;
    ctx.fill();
    const bg = ctx.createRadialGradient(-7 * sxB, by - 12 * syB, 3, 0, by, 42 * Math.max(sxB, syB));
    bg.addColorStop(0, '#c9f58f');
    bg.addColorStop(0.5, '#82d54c');
    bg.addColorStop(1, '#3f9d3a');
    starPath(ctx, g, 0);
    ctx.fillStyle = bg;
    ctx.fill();

    // 海星特有的小颗粒：沿着每条手臂排两三个浅色小点
    ctx.fillStyle = 'rgba(225,255,175,0.9)';
    const tips = [g.top, g.sides[0], g.sides[1], g.legs[0], g.legs[1]];
    for (let i = 0; i < tips.length; i++) {
      const tx = tips[i][0];
      const ty = tips[i][1];
      const dots = [
        [0.55, 2.1],
        [0.76, 1.7],
        [0.94, 1.2],
      ];
      for (let j = 0; j < dots.length; j++) {
        ctx.beginPath();
        ctx.arc(tx * dots[j][0], by + (ty - by) * dots[j][0], dots[j][1], 0, TAU);
        ctx.fill();
      }
    }

    // 肚皮：挂在第二层弹簧上，比身体晃得更晚、更大，叠出波浪感
    ctx.beginPath();
    ctx.ellipse(0, by + 8 * syB + b2 * 6, 10.5 * sxB * (1 + b2 * 0.28), Math.max(3, 7 * syB * (1 - b2 * 0.22)), 0, 0, TAU);
    ctx.fillStyle = 'rgba(240,255,210,0.8)';
    ctx.fill();

    // 脸：随朝向做视差（脸比身体偏得更多）
    const f = 3.4 * look;
    const P = (u, v) => [u * sxB, by + v * syB];
    const re = clamp(5.8 + (sxB - 1) * 1.2, 5, 7);
    const eyes = [P(-7 + f, -5), P(7 + f, -5)];
    let px = look * 1.6;
    let py = pose * 1.2;
    if (shootK > 0) {
      px = lerp(px, Math.cos(aim) * 2.2, shootK);
      py = lerp(py, Math.sin(aim) * 2.2, shootK);
    }
    for (const e of eyes) {
      if (st === 'dead') {
        ctx.beginPath();
        ctx.moveTo(e[0] - 3, e[1] - 3);
        ctx.lineTo(e[0] + 3, e[1] + 3);
        ctx.moveTo(e[0] + 3, e[1] - 3);
        ctx.lineTo(e[0] - 3, e[1] + 3);
        ctx.lineWidth = 2;
        ctx.strokeStyle = INK;
        ctx.stroke();
      } else {
        ctx.beginPath();
        ctx.arc(e[0], e[1], re, 0, TAU);
        fillStroke(ctx, '#fff', 1.7);
        ctx.beginPath();
        ctx.arc(e[0] + px, e[1] + py, 2.8, 0, TAU);
        ctx.fillStyle = INK;
        ctx.fill();
      }
    }
    ctx.fillStyle = 'rgba(255,110,130,0.42)';
    for (const bx of [-13 + f, 13 + f]) {
      const q = P(bx, 3.5);
      ctx.beginPath();
      ctx.ellipse(q[0], q[1], 3.4, 2.3, 0, 0, TAU);
      ctx.fill();
    }
    // 嘴：平时微笑，射击时张成圆口，撞怪时歪嘴
    const mq = P(f + 0.5, 7);
    if (st === 'dead') {
      ctx.beginPath();
      ctx.moveTo(mq[0] - 4, mq[1] + 1);
      ctx.quadraticCurveTo(mq[0] - 1.5, mq[1] - 2, mq[0], mq[1] + 1);
      ctx.quadraticCurveTo(mq[0] + 1.5, mq[1] + 4, mq[0] + 4, mq[1]);
      ctx.lineWidth = 1.9;
      ctx.strokeStyle = INK;
      ctx.stroke();
    } else if (shootK > 0.05) {
      const ox = Math.cos(aim) * 1.5;
      const oy = Math.sin(aim) * 1.5;
      ctx.beginPath();
      ctx.ellipse(mq[0] + ox, mq[1] - 1 + oy, 4.4 * shootK + 1.2, 5 * shootK + 0.8, 0, 0, TAU);
      fillStroke(ctx, INK, 1.4);
      if (shootK > 0.5) {
        ctx.beginPath();
        ctx.ellipse(mq[0] + ox, mq[1] + 1 + oy, 2.6, 2, 0, 0, TAU);
        ctx.fillStyle = '#ef6f7e';
        ctx.fill();
      }
    } else {
      ctx.beginPath();
      ctx.arc(mq[0], mq[1] - 3, 4.6, 0.2 * Math.PI, 0.8 * Math.PI);
      ctx.lineWidth = 1.9;
      ctx.strokeStyle = INK;
      ctx.stroke();
    }
  }

  function doodlerAt(ctx, dd, x, y) {
    const st = dd.state;
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(HS, HS); // 以下都按原来的“单位”画，整体缩小
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
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';

    const g = starGeom(dd);
    const sxB = g.sxB;
    const by = g.by;
    const topY = g.top[1] - ARM_TIP - OUT_W; // 头顶最高点（含描边）

    const xf = heroXf(dd, g);
    if (xf.lean !== ID) ctx.transform.apply(ctx, xf.lean);

    if (st === 'wings' || st === 'darkwings') heroWings(ctx, dd, g, st === 'darkwings');
    if (dd.shield > 0 && st === 'normal') heroShield(ctx, dd, g);

    // 3D 模式：身体和脸由 WebGL 光线步进渲染（真的有体积、光照和晃动的大肚子），道具配件仍用 2D 画在上面
    if (DJ.hero3d && DJ.hero3d.active()) {
      const t3 = DJ.perf && DJ.perf.on ? performance.now() : 0;
      DJ.hero3d.draw(ctx, dd, g, xf.lean, xf.body);
      if (t3) DJ.perf.h3 += performance.now() - t3;
    } else if (xf.spin !== ID) {
      ctx.save();
      ctx.transform.apply(ctx, xf.spin);
      body2d(ctx, dd, g);
      ctx.restore();
    } else {
      body2d(ctx, dd, g);
    }
    if (st === 'darkwings') heroDarkFront(ctx, dd, g);

    // 喷气背包：两个油罐绑在肚子两侧（画在身体前面，不然会被胖手臂挡住），火焰朝下喷
    if (st === 'jetpack') {
      const fl = 14 + Math.sin(dd.t * 60) * 5 + Math.random() * 3;
      for (const jx of [-23.5 * sxB, 14.5 * sxB]) {
        const jy = by - 6;
        rr(ctx, jx, jy, 9, 24, 4);
        fillStroke(ctx, '#9db4c9', 2);
        rr(ctx, jx, jy, 9, 7, 3);
        fillStroke(ctx, '#e8483c', 1.6);
        ctx.beginPath();
        ctx.moveTo(jx + 0.5, jy + 24);
        ctx.lineTo(jx + 4.5, jy + 24 + fl);
        ctx.lineTo(jx + 8.5, jy + 24);
        ctx.closePath();
        fillStroke(ctx, '#ffb02e', 1.4);
        ctx.beginPath();
        ctx.moveTo(jx + 2.5, jy + 24);
        ctx.lineTo(jx + 4.5, jy + 24 + fl * 0.55);
        ctx.lineTo(jx + 6.5, jy + 24);
        ctx.closePath();
        ctx.fillStyle = '#fff29a';
        ctx.fill();
      }
    }

    // 螺旋桨帽：扣在头顶那条手臂的尖上
    if (st === 'propeller') {
      const hx = g.top[0];
      const hy = g.top[1] - 2;
      ctx.beginPath();
      ctx.arc(hx, hy, 11, Math.PI, TAU);
      ctx.closePath();
      fillStroke(ctx, '#ef5b5b', 2);
      rr(ctx, hx - 12, hy - 1, 24, 4.4, 2);
      fillStroke(ctx, '#3d86d6', 1.8);
      ctx.beginPath();
      ctx.moveTo(hx, hy - 11);
      ctx.lineTo(hx, hy - 15);
      ctx.lineWidth = 2.2;
      ctx.strokeStyle = INK;
      ctx.stroke();
      const bw = 15 * Math.abs(Math.cos(dd.t * 38));
      ctx.beginPath();
      ctx.ellipse(hx, hy - 16, Math.max(2, bw), 2.4, 0, 0, TAU);
      fillStroke(ctx, '#f2d24a', 1.6);
    }

    // 撞到怪物后头上转圈的星星
    if (st === 'dead') {
      for (let i = 0; i < 3; i++) {
        const a = dd.t * 6 + (i * TAU) / 3;
        star(ctx, g.top[0] + Math.cos(a) * 20, topY - 4 + Math.sin(a) * 5, 6);
        fillStroke(ctx, '#ffd93b', 1.3);
      }
    }
    ctx.restore();
  }

  // 两个固定步长之间的渲染插值：prev -> cur 按 a（0..1）混合；跨屏环绕等大跳变不插值
  function ix(prev, cur, a) {
    if (prev === undefined || Math.abs(cur - prev) > 100) return cur;
    return prev + (cur - prev) * a;
  }
  DJ.ix = ix;

  function doodler(ctx, dd, a) {
    if (dd.state === 'gone') return;
    const x = ix(dd.px, dd.x, a);
    const y = ix(dd.py, dd.y, a);
    doodlerAt(ctx, dd, x, y);
    if (dd.state !== 'blackhole' && dd.state !== 'abducted') {
      // 走到屏幕边缘时另一侧也画一份（飞翼张开时翅膀很宽，边距跟着放大）
      const edge = (dd.state === 'wings' || dd.state === 'darkwings' ? 84 : 52) * HS;
      if (x < edge) doodlerAt(ctx, dd, x + W, y);
      else if (x > W - edge) doodlerAt(ctx, dd, x - W, y);
    }
  }
  DJ.drawDoodler = function (ctx, dd) {
    doodler(ctx, dd, 1);
  };

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
    let ly = dd.y - 24 * HS - m.y;
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
  function hud(ctx, score, chill) {
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
    // 畅快模式小标签（暂停键左边）
    if (chill) {
      ctx.font = DJ.font(13);
      const tw = ctx.measureText('畅快').width + 12;
      const tx = W - 48 - tw;
      rr(ctx, tx, bh / 2 - 10, tw, 20, 10);
      ctx.fillStyle = 'rgba(255,196,70,0.9)';
      ctx.fill();
      ctx.lineWidth = 1.4;
      ctx.strokeStyle = 'rgba(150,95,20,0.8)';
      ctx.stroke();
      ctx.fillStyle = '#6a3f0c';
      ctx.textAlign = 'center';
      ctx.fillText('畅快', tx + tw / 2, bh / 2 + 1);
    }
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

  // ---------- 特效：落地尘土、冲击波、放射线、光柱、粒子、拟声字、残影 ----------
  // 分两层画：后层（残影、光柱、冲击波、光晕、尘土）垫在怪物和主角后面；前层（放射线、粒子、火焰、拟声字）盖在最上面
  const FRONT = { rays: 1, part: 1, fire: 1, streak: 1, text: 1 };
  function effects(ctx, list, front) {
    for (let i = 0; i < list.length; i++) {
      const f = list[i];
      if ((FRONT[f.k] === 1) !== front) continue;
      if (f.delay > 0) continue;
      const al = clamp(f.life / f.max, 0, 1);
      const u = 1 - al;
      if (f.k === 'puff') {
        const r = f.r * (1 + (1 - al) * 1.3);
        ctx.globalAlpha = al * 0.7;
        ctx.beginPath();
        ctx.arc(f.x, f.y - r * 0.4, r, 0, TAU);
        ctx.fillStyle = '#fffdf3';
        ctx.fill();
        ctx.lineWidth = 1.3;
        ctx.strokeStyle = 'rgba(95,110,100,0.55)';
        ctx.stroke();
      } else if (f.k === 'ring') {
        ctx.globalAlpha = al * 0.6;
        ctx.beginPath();
        ctx.ellipse(f.x, f.y - 2, f.r * 1.6, f.r * 0.45, 0, 0, TAU);
        ctx.lineWidth = 2.2;
        ctx.strokeStyle = 'rgba(70,120,100,0.8)';
        ctx.stroke();
      } else if (f.k === 'shock') {
        ctx.globalAlpha = Math.min(1, al * 1.4);
        ctx.beginPath();
        ctx.ellipse(f.x, f.y - 2, f.r, f.r * f.flat, 0, 0, TAU);
        ctx.lineWidth = f.w * (0.25 + 0.75 * al);
        ctx.strokeStyle = 'rgb(' + f.col + ')';
        ctx.stroke();
      } else if (f.k === 'glow') {
        const r = f.r * (0.6 + 0.8 * u);
        const gr = ctx.createRadialGradient(f.x, f.y, 0, f.x, f.y, r);
        gr.addColorStop(0, 'rgba(' + f.col + ',' + (al * 0.95).toFixed(3) + ')');
        gr.addColorStop(1, 'rgba(' + f.col + ',0)');
        ctx.globalAlpha = 1;
        ctx.fillStyle = gr;
        ctx.beginPath();
        ctx.arc(f.x, f.y, r, 0, TAU);
        ctx.fill();
      } else if (f.k === 'beam') {
        // 光柱：从脚下往上射出去，越往上越细越淡，随时间收窄
        const w = f.w * (0.3 + 0.7 * al);
        const h = f.h * (0.55 + 0.45 * Math.min(1, u * 3));
        const gr = ctx.createLinearGradient(0, f.y, 0, f.y - h);
        gr.addColorStop(0, 'rgba(' + f.col + ',' + (al * 0.9).toFixed(3) + ')');
        gr.addColorStop(1, 'rgba(' + f.col + ',0)');
        ctx.globalAlpha = 1;
        ctx.fillStyle = gr;
        ctx.beginPath();
        ctx.moveTo(f.x - w / 2, f.y);
        ctx.lineTo(f.x + w / 2, f.y);
        ctx.lineTo(f.x + w * 0.18, f.y - h);
        ctx.lineTo(f.x - w * 0.18, f.y - h);
        ctx.closePath();
        ctx.fill();
        const gc = ctx.createLinearGradient(0, f.y, 0, f.y - h * 0.7);
        gc.addColorStop(0, 'rgba(255,255,255,' + (al * 0.9).toFixed(3) + ')');
        gc.addColorStop(1, 'rgba(255,255,255,0)');
        ctx.fillStyle = gc;
        ctx.beginPath();
        ctx.moveTo(f.x - w * 0.2, f.y);
        ctx.lineTo(f.x + w * 0.2, f.y);
        ctx.lineTo(f.x + w * 0.05, f.y - h * 0.7);
        ctx.lineTo(f.x - w * 0.05, f.y - h * 0.7);
        ctx.closePath();
        ctx.fill();
      } else if (f.k === 'ghost') {
        ctx.globalAlpha = 1;
        if (f.d.state === 'normal' || DJ.FLYING[f.d.state] === 1) DJ.drawStarGhost(ctx, f.d, f.x, f.y, al * 0.42, f.col);
      } else if (f.k === 'feather') {
        // 羽毛：白色细长叶片 + 一根羽轴，摆动着往下飘，最后淡出
        ctx.globalAlpha = Math.min(1, al * 2);
        ctx.save();
        ctx.translate(f.x, f.y);
        ctx.rotate(f.rot);
        ctx.beginPath();
        ctx.ellipse(0, 0, f.r * 1.9, f.r * 0.75, 0, 0, TAU);
        if (f.dark) {
          // 黑紫色羽毛，边缘一圈淡淡的霓虹紫
          ctx.fillStyle = '#2a1242';
          ctx.fill();
          ctx.lineWidth = 1.2;
          ctx.strokeStyle = 'rgba(200,150,255,0.85)';
          ctx.stroke();
        } else fillStroke(ctx, '#fffdf2', 1.2);
        ctx.beginPath();
        ctx.moveTo(-f.r * 2.4, 0);
        ctx.lineTo(f.r * 1.5, 0);
        ctx.lineWidth = 1;
        ctx.strokeStyle = f.dark ? 'rgba(130,240,255,0.8)' : 'rgba(200,160,60,0.9)';
        ctx.stroke();
        ctx.restore();
      } else if (f.k === 'spiral') {
        // 漩涡：几条螺旋臂一边转一边往外甩开，越来越细、越来越淡
        const e = 1 - Math.pow(1 - Math.min(1, u * 1.5), 3);
        const R = f.R * (0.2 + 0.8 * e);
        ctx.globalAlpha = Math.min(1, al * 1.3);
        ctx.lineCap = 'round';
        ctx.lineWidth = f.w * (0.3 + 0.7 * al);
        ctx.strokeStyle = 'rgb(' + f.col + ')';
        for (let j = 0; j < f.n; j++) {
          ctx.beginPath();
          for (let k = 2; k <= 14; k++) {
            const q = k / 14;
            const a = f.a0 + (j * TAU) / f.n + u * f.spin + q * 2.4;
            const r = R * q;
            if (k === 2) ctx.moveTo(f.x + Math.cos(a) * r, f.y + Math.sin(a) * r * f.flat);
            else ctx.lineTo(f.x + Math.cos(a) * r, f.y + Math.sin(a) * r * f.flat);
          }
          ctx.stroke();
        }
      } else if (f.k === 'rays') {
        // 放射线：从中心往外“嗖”地射出去，射线先变长、后脱离中心
        const e = 1 - Math.pow(1 - Math.min(1, u * 1.7), 2);
        ctx.globalAlpha = 1;
        ctx.lineCap = 'round';
        ctx.lineWidth = f.w * (0.4 + 0.6 * al);
        ctx.strokeStyle = 'rgba(' + f.col + ',' + Math.min(1, al * 1.2).toFixed(3) + ')';
        ctx.beginPath();
        for (let j = 0; j < f.n; j++) {
          const t = f.n > 1 ? j / (f.n - 1) : 0.5;
          const a = f.a0 + (f.a1 - f.a0) * t + Math.sin(j * 12.9) * 0.07;
          const lenK = 0.72 + 0.28 * Math.sin(j * 7.3);
          const rOut = f.R * e * lenK + 8;
          const rIn = 8 + (rOut - 8) * u * u * 0.92;
          ctx.moveTo(f.x + Math.cos(a) * rIn, f.y + Math.sin(a) * rIn);
          ctx.lineTo(f.x + Math.cos(a) * rOut, f.y + Math.sin(a) * rOut);
        }
        ctx.stroke();
      } else if (f.k === 'part') {
        const sz = f.r * (0.55 + 0.45 * al);
        ctx.globalAlpha = Math.min(1, al * 2.2);
        if (f.shape === 'star') {
          ctx.save();
          ctx.translate(f.x, f.y);
          ctx.rotate(f.rot);
          star(ctx, 0, 0, sz * 1.5);
          fillStroke(ctx, f.col, 1.3);
          ctx.restore();
        } else if (f.shape === 'fire') {
          ctx.fillStyle = al > 0.66 ? '#ffdf4d' : al > 0.33 ? '#ff9f2e' : '#ff6a3d';
          ctx.beginPath();
          ctx.arc(f.x, f.y, sz * 1.1, 0, TAU);
          ctx.fill();
        } else {
          ctx.beginPath();
          ctx.arc(f.x, f.y, sz, 0, TAU);
          fillStroke(ctx, f.col, 1.2);
        }
      } else if (f.k === 'fire') {
        ctx.globalAlpha = Math.min(1, al * 1.8);
        ctx.fillStyle = al > 0.66 ? '#ffdf4d' : al > 0.33 ? '#ff9f2e' : '#ff6a3d';
        ctx.beginPath();
        ctx.arc(f.x, f.y, f.r * (0.35 + 0.65 * al), 0, TAU);
        ctx.fill();
      } else if (f.k === 'streak') {
        ctx.globalAlpha = 1;
        ctx.lineCap = 'round';
        ctx.lineWidth = 2;
        ctx.strokeStyle = 'rgba(255,255,255,' + (al * 0.85).toFixed(3) + ')';
        ctx.beginPath();
        ctx.moveTo(f.x, f.y);
        ctx.lineTo(f.x - f.vx * 0.06, f.y - f.vy * 0.06);
        ctx.stroke();
      } else if (f.k === 'text') {
        // 拟声字：弹性“蹦”出来（先放大过头再回弹），向上飘一点，最后淡出
        const sc = u < 0.16 ? lerp(0.3, 1.3, u / 0.16) : u < 0.3 ? lerp(1.3, 1, (u - 0.16) / 0.14) : 1;
        ctx.save();
        ctx.globalAlpha = al < 0.25 ? al / 0.25 : 1;
        ctx.font = DJ.font(f.size);
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const tw = (ctx.measureText(f.txt).width * sc) / 2;
        ctx.translate(clamp(f.x, tw + 6, W - tw - 6), f.y - 26 * (1 - Math.pow(1 - u, 3)));
        ctx.rotate(f.rot);
        ctx.scale(sc, sc);
        ctx.lineJoin = 'round';
        ctx.lineWidth = 5.5;
        ctx.strokeStyle = INK;
        ctx.strokeText(f.txt, 0, 0);
        ctx.fillStyle = f.col;
        ctx.fillText(f.txt, 0, 0);
        ctx.restore();
      }
    }
    ctx.globalAlpha = 1;
  }

  function speedLines(ctx, dd, x, y) {
    const k = clamp((-dd.vy - 650) / 450, 0, 1);
    if (k <= 0 || dd.state === 'dead') return;
    const dark = dd.state === 'darkwings';
    const wings = dark || dd.state === 'wings'; // 飞翼时速度线更多、更宽、更长，还带一点金色（紫黑飞翼是紫色）
    const t = (DJ.clock || 0) * (dark ? 2100 : wings ? 1600 : 1100);
    const n = dark ? 13 : wings ? 11 : 7;
    const span = wings ? 12 : 13 * HS;
    const col = dark ? '140,70,230,' : wings ? '200,150,40,' : '70,110,110,';
    ctx.lineCap = 'round';
    ctx.lineWidth = 2;
    for (let i = 0; i < n; i++) {
      const lx = x + (i - (n - 1) / 2) * span + Math.sin(i * 12.9) * 4;
      const len = (dark ? 52 : wings ? 40 : 26) + ((i * 37) % 22);
      const off = (t + i * 53) % 70;
      ctx.beginPath();
      ctx.moveTo(lx, y + 4 + off);
      ctx.lineTo(lx, y + 4 + off + len);
      ctx.strokeStyle = 'rgba(' + col + (0.32 * k * (1 - off / 70)).toFixed(3) + ')';
      ctx.stroke();
    }
  }

  // ---------- 整个场景 ----------
  // alpha：上一个物理步到当前物理步之间的插值比例（0..1），让画面在任何刷新率下都顺
  // frozen：暂停 / 结算界面上画的静止场景，不再抖屏、不再闪光（否则停下来的那一刻的震动会一直抖下去）
  DJ.drawScene = function (ctx, g, alpha, frozen) {
    const v = DJ.view;
    const a = alpha === undefined ? 1 : clamp(alpha, 0, 1);
    const cam = ix(g.pCam, g.camY, a);
    // 屏幕震动：整个场景（背景和内容）一起抖，HUD 和留边不抖
    const sh = frozen ? 0 : g.shake || 0;
    ctx.save();
    if (sh > 0) {
      const tt = performance_now();
      ctx.translate(Math.sin(tt * 91) * sh, Math.cos(tt * 73) * sh * 0.8);
    }
    background(ctx, cam);
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 0, W, v.H);
    ctx.clip();
    ctx.translate(0, -cam);
    const top = cam - 90;
    const bot = cam + v.H + 90;

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
      if (p.y > top && p.y < bot) {
        const ox = p.x;
        if (p.vx) p.x = ix(p.px, p.x, a);
        platform(ctx, p);
        p.x = ox;
      }
    }
    for (let i = 0; i < g.holes.length; i++) hole(ctx, g.holes[i]);
    effects(ctx, g.fx, false);
    for (let i = 0; i < g.monsters.length; i++) {
      const m = g.monsters[i];
      if (m.y > top - 40 && m.y < bot + 40) {
        const ox = m.x;
        const oy = m.y;
        m.x = ix(m.px, m.x, a);
        m.y = ix(m.py, m.y, a);
        monster(ctx, m, g.dd);
        m.x = ox;
        m.y = oy;
      }
    }
    for (let i = 0; i < g.debris.length; i++) debris(ctx, g.debris[i]);
    for (let i = 0; i < g.bullets.length; i++) {
      const b = g.bullets[i];
      const ox = b.x;
      const oy = b.y;
      b.x = ix(b.px, b.x, a);
      b.y = ix(b.py, b.y, a);
      bullet(ctx, b);
      b.x = ox;
      b.y = oy;
    }
    for (let i = 0; i < g.sparks.length; i++) {
      const s = g.sparks[i];
      ctx.globalAlpha = clamp(s.life / s.max, 0, 1);
      star(ctx, s.x, s.y, 6);
      fillStroke(ctx, '#ffd93b', 1.2);
      ctx.globalAlpha = 1;
    }
    speedLines(ctx, g.dd, ix(g.dd.px, g.dd.x, a), ix(g.dd.py, g.dd.y, a));
    doodler(ctx, g.dd, a);
    effects(ctx, g.fx, true);
    ctx.restore();
    ctx.restore();
    if (!frozen && g.flash > 0.004) {
      ctx.fillStyle = 'rgba(' + (g.flashCol || '255,255,255') + ',' + clamp(g.flash, 0, 1).toFixed(3) + ')';
      ctx.fillRect(v.x0, v.y0, v.x1 - v.x0, v.y1 - v.y0);
    }
    letterbox(ctx);
    hud(ctx, Math.round(DJ.shownScore === undefined ? g.score : DJ.shownScore), g.chill);
    if (!frozen && DJ.app && DJ.app.settings.perf && DJ.perf && DJ.perf.text) {
      ctx.font = DJ.font(11, 'normal');
      ctx.textAlign = 'left';
      ctx.textBaseline = 'top';
      const lines = DJ.perf.text.split('\n');
      let tw = 0;
      for (let i = 0; i < lines.length; i++) tw = Math.max(tw, ctx.measureText(lines[i]).width);
      ctx.fillStyle = 'rgba(40,40,30,0.62)';
      rr(ctx, 6, 38, tw + 10, 4 + 14 * lines.length, 5);
      ctx.fill();
      ctx.fillStyle = '#fff';
      for (let i = 0; i < lines.length; i++) ctx.fillText(lines[i], 11, 41 + 14 * i);
    }
  };

  // 菜单里用的假小海星
  DJ.fakeDoodler = function (x, y, facing, vy, bounceT, t) {
    // 菜单里没有物理，用落地后的衰减余弦模拟肚子的 duang duang
    const belly = 0.75 * Math.exp(-bounceT * 3.2) * Math.cos(bounceT * 19.5);
    const b2 = 0.9 * Math.exp(-bounceT * 3.4) * Math.cos(bounceT * 19.5 - 0.9);
    return {
      x: x, y: y, facing: facing, vy: vy, bounceT: bounceT, t: t,
      belly: belly, b2: b2, look: facing * 0.7, armPose: Math.max(-1, Math.min(1, vy / 350)), stretch: Math.max(-0.12, Math.min(0.16, -vy / 3500)),
      state: 'normal', shootT: 99, aim: -Math.PI / 2, spin: 0, scale: 1,
    };
  };
  DJ.drawPlatformAt = function (ctx, type, x, y) {
    platform(ctx, { type: type, x: x, y: y, w: DJ.C.PLAT_W, h: DJ.C.PLAT_H, dead: false, fade: 0, item: null });
  };
})();
