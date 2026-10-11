var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const C = DJ.C;
  const clamp = DJ.clamp;
  const lerp = DJ.lerp;
  const rand = DJ.rand;
  const wrapDx = DJ.wrapDx;

  const ITEM = {
    spring: { w: 18, h: 14 },
    tramp: { w: 42, h: 10 },
    prop: { w: 24, h: 24 },
    jet: { w: 26, h: 32 },
  };

  // 游戏逻辑：不依赖 DOM，可在 Node 中直接跑（见 tools/sim.js）
  function Game() {
    this.H = DJ.MIN_H;
    this.best = 0;
    this.events = [];
    this.reset();
  }
  DJ.Game = Game;
  const P = Game.prototype;

  P.sfx = function (name) {
    this.events.push(name);
  };

  P.reset = function () {
    const H = this.H;
    this.t = 0;
    this.phase = 'play'; // play -> falling -> over
    this.phaseT = 0;
    this.camY = 0;
    this.maxH = 0;
    this.score = 0;
    this.platforms = [];
    this.monsters = [];
    this.holes = [];
    this.bullets = [];
    this.debris = [];
    this.sparks = [];
    this.fx = []; // 纯视觉特效：尘土、冲击环
    this.whirT = 0;
    const baseY = H - 56;
    this.startY = baseY;
    this.topY = baseY;
    this.lastHazardY = baseY + 700;
    this.lastX = W / 2;
    this.addPlatform('normal', W / 2, baseY);
    this.dd = {
      x: W / 2,
      y: baseY, // 脚底位置
      prevY: baseY,
      vx: 0,
      vy: -C.JUMP_V,
      facing: 1,
      state: 'normal', // normal propeller jetpack dead blackhole abducted fell gone
      stateT: 0,
      bounceT: 0,
      shootT: 99,
      shootCd: 0,
      aim: -Math.PI / 2,
      belly: 0, // 肚子挤压量（>0 往下坠、变宽变扁；<0 往上缩、变窄变高）
      bellyV: 0,
      b2: 0, // 肚皮：跟着 belly 晃的第二层弹簧
      b2V: 0,
      look: 0, // 脸朝向（-1..1，平滑过渡）
      armPose: 0, // 手臂姿态（-1 高举 .. 1 下垂，平滑过渡）
      stretch: 0, // 空中拉伸（跟速度走，平滑过渡）
      spin: 0,
      scale: 1,
      sx: 0,
      sy: 0,
      tx: 0,
      ty: 0,
      t: 0,
    };
    this.genUntil(this.camY - 220);
    this.events.length = 0;
    this.snapshot();
  };

  // ---------- 关卡生成 ----------
  P.scoreAtY = function (y) {
    return Math.max(0, (this.startY - y) / 2);
  };
  P.diffAt = function (sc) {
    return clamp(sc / 12000, 0, 1);
  };

  P.addPlatform = function (type, x, y) {
    const p = { type: type, x: x, y: y, w: C.PLAT_W, h: C.PLAT_H, vx: 0, dead: false, fade: 0, item: null, dip: 0, dipV: 0 };
    if (type === 'moving') {
      p.vx = (DJ.random() < 0.5 ? -1 : 1) * rand(50, 85 + 45 * this.diffAt(this.scoreAtY(y)));
    }
    this.platforms.push(p);
    return p;
  };

  P.makeMonster = function (kind, x, y) {
    const m = { kind: kind, x: x, y: y, x0: x, y0: y, t: rand(0, 6), dead: false, flash: 0, vx: 0, vy: 0, rot: 0 };
    if (kind === 'blob') {
      m.w = 52;
      m.h = 44;
      m.hp = 1;
      m.amp = DJ.random() < 0.5 ? 0 : rand(18, 38);
      m.sp = rand(1.2, 2.2);
    } else if (kind === 'flyer') {
      m.w = 54;
      m.h = 36;
      m.hp = 2;
      m.vx = (DJ.random() < 0.5 ? -1 : 1) * rand(50, 90);
    } else {
      m.w = 74;
      m.h = 34;
      m.hp = 3;
      m.vx = (DJ.random() < 0.5 ? -1 : 1) * rand(30, 60);
    }
    return m;
  };

  P.genUntil = function (limitY) {
    while (this.topY > limitY) this.genRow();
  };

  P.genRow = function () {
    const prevTop = this.topY;
    const d = this.diffAt(this.scoreAtY(prevTop));
    const gap = rand(lerp(44, 82, d), lerp(72, 138, d));
    const y = prevTop - gap;
    this.topY = y;
    const sc = this.scoreAtY(y);
    const half = C.PLAT_W / 2;

    // 怪物 / 黑洞（两次之间至少隔 650px，且主平台会避开它）
    let avoidX = null;
    if (sc > 1100 && this.lastHazardY - y > 650 && DJ.random() < 0.06) {
      const kinds = ['blob'];
      if (sc > 2200) kinds.push('flyer');
      if (sc > 3200) kinds.push('ufo');
      if (sc > 3800) kinds.push('hole', 'hole');
      const kind = kinds[Math.floor(DJ.random() * kinds.length)];
      const hx = rand(50, W - 50);
      avoidX = hx;
      this.lastHazardY = y;
      if (kind === 'hole') this.holes.push({ x: hx, y: y - 28, r: 28, t: 0 });
      else this.monsters.push(this.makeMonster(kind, hx, y - (kind === 'ufo' ? 70 : 34)));
    }

    // 主平台（一定是安全的，保证路线可达）
    let type = 'normal';
    const r = DJ.random();
    const pMove = sc < 350 ? 0 : Math.min(0.38, 0.1 + (sc - 350) / 14000);
    const pWhite = sc < 800 ? 0 : Math.min(0.24, 0.07 + (sc - 800) / 20000);
    if (r < pMove) type = 'moving';
    else if (r < pMove + pWhite) type = 'white';
    let x = W / 2;
    for (let i = 0; i < 8; i++) {
      x = rand(half + 3, W - half - 3);
      if (avoidX === null || Math.abs(wrapDx(x, avoidX)) > 95) break;
    }
    const p = this.addPlatform(type, x, y);

    // 道具
    if (type === 'normal' || type === 'moving') {
      const r2 = DJ.random();
      const pj = sc > 2500 ? 0.006 : 0;
      const pp = sc > 1200 ? 0.012 : 0;
      const pt = sc > 700 ? 0.016 : 0;
      const ps = 0.06;
      let it = null;
      if (r2 < pj) it = 'jet';
      else if (r2 < pj + pp) it = 'prop';
      else if (r2 < pj + pp + pt) it = 'tramp';
      else if (r2 < pj + pp + pt + ps) it = 'spring';
      if (it) {
        const lim = half - ITEM[it].w / 2 - 2;
        p.item = { type: it, ox: rand(-lim, lim), w: ITEM[it].w, h: ITEM[it].h, used: false, fired: false, t: 0 };
      }
    }

    // 额外的易碎平台（棕色）
    const pBrown = sc < 250 ? 0 : Math.min(0.5, 0.12 + (sc - 250) / 9000);
    if (gap > 36 && DJ.random() < pBrown) {
      for (let i = 0; i < 5; i++) {
        const bx = rand(half + 3, W - half - 3);
        const by = y + rand(14, gap - 14);
        if (Math.abs(wrapDx(bx, x)) < 70 && Math.abs(by - y) < 22) continue;
        if (Math.abs(wrapDx(bx, this.lastX)) < 70 && Math.abs(by - prevTop) < 22) continue;
        this.addPlatform('brown', bx, by);
        break;
      }
    }
    this.lastX = x;
  };

  // ---------- 主循环 ----------
  // input: { axis: -1..1, shots: [{tx,ty} | {dx,dy}] }
  // 渲染插值用：每个固定步长开始前记下上一步的位置（画面在两步之间按比例插值，60/90/120Hz 屏幕都一样顺）
  P.snapshot = function () {
    const dd = this.dd;
    dd.px = dd.x;
    dd.py = dd.y;
    this.pCam = this.camY;
    for (let i = 0; i < this.platforms.length; i++) this.platforms[i].px = this.platforms[i].x;
    for (let i = 0; i < this.monsters.length; i++) {
      const m = this.monsters[i];
      m.px = m.x;
      m.py = m.y;
    }
    for (let i = 0; i < this.bullets.length; i++) {
      const b = this.bullets[i];
      b.px = b.x;
      b.py = b.y;
    }
  };

  P.update = function (dt, input) {
    if (this.phase === 'over') return;
    this.snapshot();
    this.t += dt;
    if (this.phase === 'play') this.stepPlay(dt, input || {});
    else this.stepFalling(dt);
    this.stepWorld(dt);
  };

  P.stepPlay = function (dt, input) {
    const dd = this.dd;
    dd.t += dt;
    dd.stateT += dt;
    dd.bounceT += dt;
    dd.shootT += dt;
    dd.shootCd -= dt;
    dd.prevY = dd.y;
    // 肚子：阻尼弹簧，被 bounce() 的速度突变踢一脚后自己晃几下；肚皮再挂一层更软的弹簧，晃得更晚更大
    dd.bellyV += (-C.BELLY_K * dd.belly - C.BELLY_C * dd.bellyV) * dt;
    dd.belly = clamp(dd.belly + dd.bellyV * dt, -1, 1);
    dd.b2V += (C.BELLY2_K * (dd.belly - dd.b2) - C.BELLY2_C * dd.b2V) * dt;
    dd.b2 = clamp(dd.b2 + dd.b2V * dt, -1.3, 1.3);
    // 其余姿态量都做指数平滑，没有任何一帧是“瞬间切换”的
    const ease = (rate) => 1 - Math.exp(-rate * dt);
    dd.stretch += (clamp(-dd.vy / 3500, -0.12, 0.16) - dd.stretch) * ease(14);
    dd.armPose += (clamp(dd.vy / 350, -1, 1) - dd.armPose) * ease(16);
    const lookT = dd.shootT < 0.35 ? Math.sign(Math.cos(dd.aim)) * 0.9 : clamp(dd.vx / 140, -1, 1);
    dd.look += (lookT - dd.look) * ease(12);
    const st = dd.state;
    const flying = st === 'propeller' || st === 'jetpack';

    // 水平方向
    if (st === 'normal' || flying) {
      const target = clamp(input.axis || 0, -1, 1) * C.MAX_VX;
      const a = C.ACCEL * dt;
      dd.vx += clamp(target - dd.vx, -a, a);
      if (Math.abs(dd.vx) > 20) dd.facing = dd.vx > 0 ? 1 : -1;
      dd.x += dd.vx * dt;
      if (input.shots) for (let i = 0; i < input.shots.length; i++) this.fire(input.shots[i]);
    } else if (st === 'dead') {
      dd.x += dd.vx * dt;
    }
    if (input.shots) input.shots.length = 0;

    // 垂直方向
    if (st === 'normal') {
      dd.vy = Math.min(dd.vy + C.GRAVITY * dt, C.MAX_FALL);
      dd.y += dd.vy * dt;
      if (dd.vy > 0) this.landing();
    } else if (flying) {
      const prop = st === 'propeller';
      const T = prop ? C.PROP_T : C.JET_T;
      const V = prop ? C.PROP_V : C.JET_V;
      const left = T - dd.stateT;
      const tv = -(left > 0.7 ? V : lerp(250, V, Math.max(0, left) / 0.7));
      dd.vy += clamp(tv - dd.vy, -2600 * dt, 2600 * dt);
      dd.y += dd.vy * dt;
      this.whirT -= dt;
      if (this.whirT <= 0) {
        this.whirT = 0.16;
        this.sfx(prop ? 'prop' : 'jet');
      }
      if (left <= 0) {
        dd.state = 'normal';
        dd.stateT = 0;
      }
    } else if (st === 'dead') {
      dd.vy = Math.min(dd.vy + C.GRAVITY * dt, 1300);
      dd.y += dd.vy * dt;
      dd.spin += dt * 5;
    } else if (st === 'blackhole' || st === 'abducted') {
      const u = clamp(dd.stateT / 0.9, 0, 1);
      const e = u * u;
      dd.x = lerp(dd.sx, dd.tx, e);
      dd.y = lerp(dd.sy, dd.ty, e);
      dd.scale = 1 - u;
      dd.spin += dt * (st === 'blackhole' ? 10 : 2);
      if (dd.stateT >= 0.9) {
        dd.state = 'gone';
        this.beginFalling();
      }
    }

    // 左右循环
    if (st !== 'blackhole' && st !== 'abducted') {
      if (dd.x < 0) dd.x += W;
      else if (dd.x >= W) dd.x -= W;
    }

    if (dd.state === 'normal') {
      this.pickups();
      if (dd.state === 'normal') this.hazards();
    }

    // 相机只往上走，而且是缓动跟随（不是硬贴着角色），冲得太快时才硬拉住别让角色出屏幕
    if (dd.state === 'normal' || dd.state === 'propeller' || dd.state === 'jetpack') {
      const target = dd.y - this.H * C.CAM_FRAC;
      if (target < this.camY) this.camY += (target - this.camY) * (1 - Math.exp(-C.CAM_K * dt));
      const topLimit = dd.y - this.H * C.CAM_TOP;
      if (topLimit < this.camY) this.camY = topLimit;
    }
    if (dd.state === 'normal' || dd.state === 'propeller' || dd.state === 'jetpack') {
      this.maxH = Math.max(this.maxH, this.startY - dd.y);
      this.score = Math.floor(this.maxH / 2);
    }
    this.genUntil(this.camY - 220);

    // 掉出屏幕底部
    if ((dd.state === 'normal' || dd.state === 'dead') && dd.y - this.camY > this.H + 60) {
      if (dd.state === 'normal') {
        dd.state = 'fell';
        this.sfx('fall');
      }
      this.beginFalling();
    }
  };

  P.beginFalling = function () {
    this.phase = 'falling';
    this.phaseT = 0;
  };

  P.stepFalling = function (dt) {
    const dd = this.dd;
    this.phaseT += dt;
    if (dd.state === 'fell' || dd.state === 'dead') {
      dd.vy = Math.min(dd.vy + C.GRAVITY * dt, 1300);
      dd.y += dd.vy * dt;
    }
    this.camY += (320 + 2600 * this.phaseT) * dt;
    if (this.phaseT >= 0.9) {
      this.phase = 'over';
      this.sfx('over');
    }
  };

  // 视觉特效（用 Math.random，不碰会影响关卡生成的种子随机数）
  P.puff = function (x, y, n, spd) {
    for (let i = 0; i < n; i++) {
      const s = n > 1 ? (i / (n - 1)) * 2 - 1 : 0;
      this.fx.push({ k: 'puff', x: x + s * 11, y: y - 1, vx: s * spd * (0.7 + Math.random() * 0.5), vy: -(10 + Math.random() * 30), r: 3 + Math.random() * 2.5, life: 0.5, max: 0.5 });
    }
  };
  P.ring = function (x, y) {
    this.fx.push({ k: 'ring', x: x, y: y, r: 6, vx: 0, vy: 0, life: 0.36, max: 0.36 });
  };

  P.bounce = function (v) {
    const dd = this.dd;
    // 速度从“下落”突变成“上冲”，肚子跟不上，先被甩下去再弹回来
    dd.bellyV += (v + dd.vy) * C.BELLY_GAIN;
    dd.vy = -v;
    dd.bounceT = 0;
  };

  P.landing = function () {
    const dd = this.dd;
    const prev = dd.prevY;
    const cur = dd.y;
    let best = null;
    for (let i = 0; i < this.platforms.length; i++) {
      const p = this.platforms[i];
      if (p.dead) continue;
      if (prev <= p.y + 0.01 && cur >= p.y && Math.abs(wrapDx(dd.x, p.x)) <= p.w / 2 + C.FEET) {
        if (!best || p.y < best.sy) best = { sy: p.y, p: p, it: null };
      }
      const it = p.item;
      if (it && !it.used && (it.type === 'spring' || it.type === 'tramp')) {
        const sy = p.y - it.h;
        // 脚踩到道具本身，或者身体中心已经在这块平台范围内（半个身子压在砖上）都算踩中
        const onItem = Math.abs(wrapDx(dd.x, p.x + it.ox)) <= it.w / 2 + C.FEET - 2;
        const overBrick = Math.abs(wrapDx(dd.x, p.x)) <= (p.w / 2) * C.ITEM_REACH;
        if (prev <= sy + 0.01 && cur >= sy && (onItem || overBrick)) {
          if (!best || sy < best.sy) best = { sy: sy, p: p, it: it };
        }
      }
    }
    if (!best) return;
    const p = best.p;
    if (best.it) {
      dd.y = best.sy;
      best.it.fired = true;
      best.it.t = 0;
      p.dipV += 200;
      this.puff(dd.x, best.sy, 6, 120);
      this.ring(dd.x, best.sy);
      if (best.it.type === 'spring') {
        this.bounce(C.SPRING_V);
        this.sfx('spring');
      } else {
        this.bounce(C.TRAMP_V);
        this.sfx('tramp');
      }
      return;
    }
    if (p.type === 'brown') {
      this.breakPlatform(p);
      return;
    }
    dd.y = best.sy;
    p.dipV += 150; // 平台被踩得往下沉一点再弹回来
    this.puff(dd.x, best.sy, 4, 70);
    this.bounce(C.JUMP_V);
    this.sfx('jump');
    if (p.type === 'white') {
      p.dead = true;
      p.fade = 0;
    }
  };

  P.breakPlatform = function (p) {
    p.dead = true;
    p.fade = 99;
    for (let s = -1; s <= 1; s += 2) {
      this.debris.push({ x: p.x + (s * p.w) / 4, y: p.y, w: p.w / 2, h: p.h, vx: s * 40, vy: -60, rot: 0, vr: s * 3 });
    }
    this.sfx('break');
  };

  P.pickups = function () {
    const dd = this.dd;
    for (let i = 0; i < this.platforms.length; i++) {
      const p = this.platforms[i];
      const it = p.item;
      if (!it || it.used || (it.type !== 'prop' && it.type !== 'jet')) continue;
      const dx = wrapDx(dd.x, p.x + it.ox);
      if (Math.abs(dx) < it.w / 2 + 12 && dd.y > p.y - it.h - 4 && dd.y - 46 < p.y) {
        it.used = true;
        dd.state = it.type === 'prop' ? 'propeller' : 'jetpack';
        dd.stateT = 0;
        dd.vy = Math.min(dd.vy, -300);
        this.whirT = 0;
        this.sfx('pickup');
        return;
      }
    }
  };

  P.hazards = function () {
    const dd = this.dd;
    const top = dd.y - 46;
    const bot = dd.y;
    const hw = 15;
    for (let i = 0; i < this.monsters.length; i++) {
      const m = this.monsters[i];
      if (m.dead) continue;
      const dx = wrapDx(dd.x, m.x);
      if (m.kind === 'ufo' && Math.abs(dx) < 30 && bot > m.y + 12 && top < m.y + 150) {
        dd.state = 'abducted';
        dd.stateT = 0;
        dd.sx = dd.x;
        dd.sy = dd.y;
        dd.tx = m.x;
        dd.ty = m.y + 6;
        dd.vx = 0;
        this.sfx('abduct');
        return;
      }
      if (Math.abs(dx) < m.w / 2 + hw - 4 && bot > m.y - m.h / 2 && top < m.y + m.h / 2) {
        if (dd.vy > 0 && dd.prevY <= m.y - m.h * 0.1) {
          this.killMonster(m);
          this.bounce(C.STOMP_V);
          this.sfx('stomp');
        } else {
          dd.state = 'dead';
          dd.stateT = 0;
          dd.vy = -300;
          dd.vx = (dx >= 0 ? 1 : -1) * 90;
          this.sfx('hit');
          return;
        }
      }
    }
    for (let i = 0; i < this.holes.length; i++) {
      const h = this.holes[i];
      const dx = wrapDx(dd.x, h.x);
      const dy = dd.y - 24 - h.y;
      const rr = h.r * 0.85 + 12;
      if (dx * dx + dy * dy < rr * rr) {
        dd.state = 'blackhole';
        dd.stateT = 0;
        dd.sx = dd.x;
        dd.sy = dd.y;
        dd.tx = h.x;
        dd.ty = h.y + 24;
        dd.vx = 0;
        this.sfx('hole');
        return;
      }
    }
  };

  // 怪物在 t 秒后的位置（给子弹留提前量）
  P.monsterAt = function (m, t) {
    const tt = m.t + t;
    if (m.kind === 'blob') return { x: m.x0 + Math.sin(tt * m.sp) * m.amp, y: m.y };
    if (m.kind === 'flyer') return { x: clamp(m.x + m.vx * t, 30, W - 30), y: m.y0 + Math.sin(tt * 2.2) * 14 };
    return { x: clamp(m.x + m.vx * t, 40, W - 40), y: m.y0 + Math.sin(tt * 1.5) * 8 };
  };

  // 找最近的、在屏幕里、而且在射击角度范围内（往上方）的怪物
  P.findTarget = function (bx, by) {
    const hi = -0.2;
    const lo = -Math.PI + 0.2;
    let best = null;
    let bd = Infinity;
    for (let i = 0; i < this.monsters.length; i++) {
      const m = this.monsters[i];
      if (m.dead) continue;
      const sy = m.y - this.camY;
      if (sy < 4 || sy > this.H || m.x < -10 || m.x > W + 10) continue;
      let d = Math.hypot(m.x - bx, m.y - by);
      const q = this.monsterAt(m, d / C.BULLET_V);
      const a = Math.atan2(q.y - by, q.x - bx);
      if (a > hi || a < lo) continue;
      d = Math.hypot(q.x - bx, q.y - by);
      if (d < bd) {
        bd = d;
        best = q;
      }
    }
    return best;
  };

  P.fire = function (shot) {
    const dd = this.dd;
    if (dd.shootCd > 0) return;
    const bx = dd.x;
    const by = dd.y - 32;
    let ax;
    let ay;
    // 自动瞄准：屏幕里有能打到的怪物就直接朝它打，不管点在哪
    const tg = this.findTarget(bx, by);
    if (tg) {
      ax = tg.x - bx;
      ay = tg.y - by;
    } else if (shot.tx !== undefined) {
      ax = wrapDx(shot.tx, dd.x) + (dd.x - bx);
      ay = shot.ty + this.camY - by;
    } else {
      ax = shot.dx;
      ay = shot.dy;
    }
    let a = Math.atan2(ay, ax);
    const hi = -0.2;
    const lo = -Math.PI + 0.2;
    if (a > hi) a = Math.cos(a) >= 0 ? hi : lo;
    else if (a < lo) a = lo;
    const cx = Math.cos(a);
    const cy = Math.sin(a);
    this.bullets.push({ x: bx + cx * 28, y: by + cy * 28, vx: cx * C.BULLET_V, vy: cy * C.BULLET_V, life: 1.2 });
    dd.aim = a;
    dd.shootT = 0;
    dd.shootCd = C.SHOOT_CD;
    if (cx > 0.15) dd.facing = 1;
    else if (cx < -0.15) dd.facing = -1;
    this.sfx('shoot');
  };

  P.hitMonster = function (m) {
    m.hp--;
    m.flash = 0.12;
    if (m.hp <= 0) this.killMonster(m);
    else this.sfx('hit');
  };

  P.killMonster = function (m) {
    m.dead = true;
    m.vy = -260;
    m.vx = rand(-60, 60);
    this.sfx('mdie');
    for (let i = 0; i < 6; i++) {
      const a = rand(0, Math.PI * 2);
      const s = rand(60, 160);
      this.sparks.push({ x: m.x, y: m.y, vx: Math.cos(a) * s, vy: Math.sin(a) * s - 60, life: 0.6, max: 0.6 });
    }
  };

  // 平台移动、怪物、子弹、碎片等不涉及小橘的更新
  P.stepWorld = function (dt) {
    const camY = this.camY;
    const H = this.H;
    const bottom = camY + H;

    for (let i = this.platforms.length - 1; i >= 0; i--) {
      const p = this.platforms[i];
      if (p.vx) {
        p.x += p.vx * dt;
        const h = p.w / 2;
        if (p.x < h) {
          p.x = h;
          p.vx = -p.vx;
        } else if (p.x > W - h) {
          p.x = W - h;
          p.vx = -p.vx;
        }
      }
      if (p.dip || p.dipV) {
        p.dipV += (-C.DIP_K * p.dip - C.DIP_C * p.dipV) * dt;
        p.dip += p.dipV * dt;
        if (Math.abs(p.dip) < 0.01 && Math.abs(p.dipV) < 0.5) p.dip = p.dipV = 0;
      }
      if (p.dead) p.fade += dt;
      if (p.item && p.item.fired) p.item.t += dt;
      if (p.y > bottom + 80 || (p.dead && p.fade > 0.3)) this.platforms.splice(i, 1);
    }

    for (let i = this.monsters.length - 1; i >= 0; i--) {
      const m = this.monsters[i];
      m.t += dt;
      if (m.flash > 0) m.flash -= dt;
      if (m.dead) {
        m.vy += 1300 * dt;
        m.y += m.vy * dt;
        m.x += m.vx * dt;
        m.rot += dt * 8;
      } else if (m.kind === 'blob') {
        m.x = m.x0 + Math.sin(m.t * m.sp) * m.amp;
      } else if (m.kind === 'flyer') {
        m.x += m.vx * dt;
        if (m.x < 30) {
          m.x = 30;
          m.vx = -m.vx;
        } else if (m.x > W - 30) {
          m.x = W - 30;
          m.vx = -m.vx;
        }
        m.y = m.y0 + Math.sin(m.t * 2.2) * 14;
      } else {
        m.x += m.vx * dt;
        if (m.x < 40) {
          m.x = 40;
          m.vx = -m.vx;
        } else if (m.x > W - 40) {
          m.x = W - 40;
          m.vx = -m.vx;
        }
        m.y = m.y0 + Math.sin(m.t * 1.5) * 8;
      }
      if (m.y > bottom + 160) this.monsters.splice(i, 1);
    }

    for (let i = this.holes.length - 1; i >= 0; i--) {
      const h = this.holes[i];
      h.t += dt;
      if (h.y > bottom + 100) this.holes.splice(i, 1);
    }

    for (let i = this.bullets.length - 1; i >= 0; i--) {
      const b = this.bullets[i];
      b.x += b.vx * dt;
      b.y += b.vy * dt;
      b.life -= dt;
      let hit = false;
      for (let j = 0; j < this.monsters.length; j++) {
        const m = this.monsters[j];
        if (m.dead) continue;
        if (Math.abs(b.x - m.x) < m.w / 2 + 3 && Math.abs(b.y - m.y) < m.h / 2 + 3) {
          this.hitMonster(m);
          hit = true;
          break;
        }
      }
      if (hit || b.life <= 0 || b.x < -20 || b.x > W + 20 || b.y < camY - 60) this.bullets.splice(i, 1);
    }

    for (let i = this.debris.length - 1; i >= 0; i--) {
      const q = this.debris[i];
      q.vy += 1300 * dt;
      q.x += q.vx * dt;
      q.y += q.vy * dt;
      q.rot += q.vr * dt;
      if (q.y > bottom + 100) this.debris.splice(i, 1);
    }

    for (let i = this.fx.length - 1; i >= 0; i--) {
      const f = this.fx[i];
      f.life -= dt;
      if (f.k === 'puff') {
        f.vx *= Math.exp(-3.5 * dt);
        f.vy *= Math.exp(-2.5 * dt);
        f.x += f.vx * dt;
        f.y += f.vy * dt;
      } else {
        f.r += 150 * dt * Math.max(0.15, f.life / f.max);
      }
      if (f.life <= 0) this.fx.splice(i, 1);
    }

    for (let i = this.sparks.length - 1; i >= 0; i--) {
      const s = this.sparks[i];
      s.life -= dt;
      s.vy += 500 * dt;
      s.x += s.vx * dt;
      s.y += s.vy * dt;
      if (s.life <= 0) this.sparks.splice(i, 1);
    }
  };
})();
