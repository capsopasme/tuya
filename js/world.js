var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const C = DJ.C;
  const clamp = DJ.clamp;
  const lerp = DJ.lerp;
  const rand = DJ.rand;
  const wrapDx = DJ.wrapDx;
  // 主角缩放后的身体尺寸（单位：逻辑像素）。脚底是原点，身体中心在脚底上方 CENTER 处
  const HS = C.HERO;
  const BODY_H = 46 * HS; // 身体（含头顶手臂）的高度，用于怪物/道具碰撞
  const HALF_W = 15 * HS; // 身体半宽
  const CENTER = 24 * HS; // 身体中心离脚底的高度
  const MOUTH = 32 * HS; // 子弹从嘴里吐出来的高度

  const ITEM = {
    spring: { w: 18, h: 14 },
    tramp: { w: 42, h: 10 },
    prop: { w: 24, h: 24 },
    jet: { w: 26, h: 32 },
    wings: { w: 36, h: 30 }, // 终极道具：飞翼
    dark: { w: 38, h: 32 }, // 比飞翼更稀有更快：紫黑飞翼
  };
  const FLYING = { propeller: 1, jetpack: 1, wings: 1, darkwings: 1 };
  const WINGED = { wings: 1, darkwings: 1 };
  DJ.FLYING = FLYING;

  // 游戏逻辑：不依赖 DOM，可在 Node 中直接跑（见 tools/sim.js）
  function Game() {
    this.H = DJ.MIN_H;
    this.best = 0;
    this.chill = false; // 畅快模式：要在 reset() 之前设好，关卡生成时读取
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
    this.fx = []; // 纯视觉特效：尘土、冲击波、粒子、残影、拟声字……
    this.shake = 0; // 屏幕震动幅度（像素），自己衰减
    this.flash = 0; // 全屏闪光亮度（0..1），自己衰减
    this.flashCol = '255,255,255';
    this.ghostT = 0; // >0 时每隔一小会儿留一个残影
    this.ghostCd = 0;
    this.trailT = 0; // 喷气/螺旋桨飞行时的尾迹发射计时
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
      state: 'normal', // normal propeller jetpack wings dead blackhole abducted fell gone
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
      shield: 0, // 飞翼结束后的护盾剩余时间
      shieldDark: false, // 护盾是紫黑飞翼留下的（画成紫色）
      lean: 0, // 身体倾斜角（弧度，向右为正），阻尼弹簧
      leanV: 0,
      leanAir: 1, // 倾斜的转轴：0 = 绕脚底（踩在平台上蓄力），1 = 绕身体中心（腾空），平滑过渡
      roll: 0, // 紫黑飞翼时整个身体的旋转角，结束后弹簧回正
      rollV: 0,
      spinDir: 1,
      axis: 0, // 这一步的倾斜输入（-1..1），蓄力时身体朝这个方向压
      glide: 0, // 道具用完后的“慢落”剩余时间（只在下落阶段倒数）
      glideK: 0, // 慢落强度（0..1，平滑）
      fallK: 0, // 慢落实际生效的程度（还要看是不是已经在往下落）
      glideCol: 'gold',
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

    // 怪物 / 黑洞（两次之间至少隔 650px，且主平台会避开它；畅快模式更晚、更稀）
    const CH = this.chill ? DJ.CHILL : null;
    const hzFrom = CH ? CH.HAZARD_FROM : 1100;
    const hzGap = CH ? CH.HAZARD_GAP : 650;
    const hzP = CH ? CH.HAZARD_P : 0.06;
    let avoidX = null;
    if (sc > hzFrom && this.lastHazardY - y > hzGap && DJ.random() < hzP) {
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
      let pd, pw, pj, pp, pt, ps;
      if (CH) {
        const I = CH.ITEMS;
        const at = function (k) {
          return sc > I[k][0] ? I[k][1] : 0;
        };
        pd = at('dark');
        pw = at('wings');
        pj = at('jet');
        pp = at('prop');
        pt = at('tramp');
        ps = at('spring');
      } else {
        pd = sc > 5000 ? 0.0025 : 0; // 紫黑飞翼：比飞翼还稀有，5000 分以后才会出现
        pw = sc > 3500 ? 0.0035 : 0; // 飞翼：比喷气背包还稀有，3500 分以后才会出现
        pj = sc > 2500 ? 0.006 : 0;
        pp = sc > 1200 ? 0.012 : 0;
        pt = sc > 700 ? 0.016 : 0;
        ps = 0.06;
      }
      let it = null;
      if (r2 < pd) it = 'dark';
      else if (r2 < pd + pw) it = 'wings';
      else if (r2 < pd + pw + pj) it = 'jet';
      else if (r2 < pd + pw + pj + pp) it = 'prop';
      else if (r2 < pd + pw + pj + pp + pt) it = 'tramp';
      else if (r2 < pd + pw + pj + pp + pt + ps) it = 'spring';
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
    // 慢落时两条手臂向上摊开，像跳伞一样兜着风
    dd.armPose += (lerp(clamp(dd.vy / 350, -1, 1), -0.5, dd.fallK) - dd.armPose) * ease(dd.fallK > 0.05 ? 6 : 16);
    const lookT = dd.shootT < 0.35 ? Math.sign(Math.cos(dd.aim)) * 0.9 : clamp(dd.vx / 140, -1, 1);
    dd.look += (lookT - dd.look) * ease(12);
    if (dd.shield > 0) dd.shield = Math.max(0, dd.shield - dt);
    const st = dd.state;
    const flying = FLYING[st] === 1;
    dd.axis = clamp(input.axis || 0, -1, 1);
    this.posture(dt, st, flying);

    // 水平方向
    if (st === 'normal' || flying) {
      const vk = st === 'wings' ? C.WING_VX : st === 'darkwings' ? C.DARK_VX : 1;
      const target = clamp(input.axis || 0, -1, 1) * C.MAX_VX * vk;
      const a = C.ACCEL * vk * dt;
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
      // 道具用完后的慢落：只在快到顶点、开始往下掉的时候减小重力（上升段完全不变，不会因此飞得更高），
      // 并压低最大下落速度；最后 0.6 秒慢慢恢复正常，落到平台上立刻结束
      if (dd.glide > 0 && dd.vy > -220) dd.glide = Math.max(0, dd.glide - dt);
      dd.glideK += ((dd.glide > 0 ? Math.min(1, dd.glide / 0.6) : 0) - dd.glideK) * ease(8);
      if (dd.glideK < 0.002) dd.glideK = 0;
      dd.fallK = dd.glideK * clamp((dd.vy + 320) / 320, 0, 1);
      const maxFall = lerp(C.MAX_FALL, C.GLIDE_FALL, dd.glideK);
      dd.vy += C.GRAVITY * lerp(1, C.GLIDE_G, dd.fallK) * dt;
      if (dd.vy > maxFall) dd.vy = Math.max(maxFall, dd.vy - 2600 * dt);
      dd.y += dd.vy * dt;
      if (dd.vy > 0) this.landing();
    } else if (flying) {
      dd.fallK = dd.glideK = dd.glide = 0;
      const prop = st === 'propeller';
      const dark = st === 'darkwings';
      const wings = WINGED[st] === 1;
      const T = prop ? C.PROP_T : dark ? C.DARK_T : wings ? C.WING_T : C.JET_T;
      const V = prop ? C.PROP_V : dark ? C.DARK_V : wings ? C.WING_V : C.JET_V;
      const ramp = wings ? 1 : 0.7; // 最后这段时间慢慢减速
      const left = T - dd.stateT;
      const tv = -(left > ramp ? V : lerp(250, V, Math.max(0, left) / ramp));
      const acc = dark ? 4600 : wings ? 3600 : 2600;
      dd.vy += clamp(tv - dd.vy, -acc * dt, acc * dt);
      dd.y += dd.vy * dt;
      this.whirT -= dt;
      if (this.whirT <= 0) {
        this.whirT = dark ? 0.17 : wings ? 0.2 : 0.16;
        this.sfx(prop ? 'prop' : dark ? 'darkflap' : wings ? 'flap' : 'jet');
      }
      if (wings) {
        this.ghostT = Math.max(this.ghostT, 0.05); // 一路拖着金色（紫黑飞翼是紫色、跟着一起转的）残影
        this.smash();
      }
      if (left <= 0) {
        dd.state = 'normal';
        dd.stateT = 0;
        if (wings) {
          // 收翅膀前最后用力扇一下，再给一小段护盾，免得一落下来就撞上怪物
          dd.shield = C.SHIELD_T;
          dd.shieldDark = dark;
          this.bounce(C.JUMP_V * 1.15, 1.6);
          if (dark) this.darkEndFx();
          else this.wingEndFx();
          this.sfx(dark ? 'darkflap' : 'flap');
        }
        // 道具用完：接下来的下落是慢慢飘下来的
        dd.glide = C.GLIDE_T;
        dd.glideCol = dark ? 'dark' : wings ? 'gold' : prop ? 'sky' : 'fire';
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

    this.trailFx(dt);

    if (dd.state === 'normal') {
      this.pickups();
      if (dd.state === 'normal') this.hazards();
    }

    // 相机只往上走，而且是缓动跟随（不是硬贴着角色），冲得太快时才硬拉住别让角色出屏幕
    const alive = dd.state === 'normal' || FLYING[dd.state] === 1;
    if (alive) {
      const target = dd.y - this.H * C.CAM_FRAC;
      if (target < this.camY) this.camY += (target - this.camY) * (1 - Math.exp(-C.CAM_K * dt));
      const topLimit = dd.y - this.H * C.CAM_TOP;
      if (topLimit < this.camY) this.camY = topLimit;
    }
    if (alive) {
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

  // 身体倾斜 + 紫黑飞翼的旋转（都是阻尼弹簧，纯视觉，不影响碰撞）
  //  - 空中：身体朝水平速度的方向歪一点（C.LEAN）
  //  - 落到平台上的那一下（腿被压扁的 0.16 秒）是“蓄力”：身体朝倾斜手机的方向狠狠压下去（C.LEAN_CHARGE），
  //    起跳时弹簧把它甩回来、带一点过冲，像真的侧身发力蹬出去
  //  - 慢落时左右轻轻摆，像荡着降落伞
  const LEAN_K = 420;
  const LEAN_C = 17;
  const CHARGE_T = 0.16;
  P.posture = function (dt, st, flying) {
    const dd = this.dd;
    let target = 0;
    let airT = 1;
    if (st === 'normal' || flying) {
      const vLean = clamp(dd.vx / C.MAX_VX, -1, 1) * C.LEAN;
      target = vLean;
      if (st === 'normal' && dd.bounceT < CHARGE_T) {
        const k = Math.sin((dd.bounceT / CHARGE_T) * Math.PI); // 0 -> 1 -> 0
        target = lerp(vLean, dd.axis * C.LEAN_CHARGE, k);
        airT = 0;
      }
      if (st === 'darkwings') target *= 0.5; // 自己在转，倾斜收一收
      target += Math.sin(dd.t * 2.6) * 0.16 * dd.fallK;
    }
    dd.leanV += (LEAN_K * (target - dd.lean) - LEAN_C * dd.leanV) * dt;
    dd.lean = clamp(dd.lean + dd.leanV * dt, -0.8, 0.8);
    dd.leanAir += (airT - dd.leanAir) * (1 - Math.exp(-(airT > dd.leanAir ? 9 : 30) * dt));

    // 旋转：飞行中加速到 DARK_SPIN；结束后朝“前方最近的一个整圈”减速回正，带一点过冲
    if (st === 'darkwings') {
      const ramp = clamp(dd.stateT / 0.35, 0, 1);
      dd.rollV += (C.DARK_SPIN * dd.spinDir * ramp - dd.rollV) * (1 - Math.exp(-6 * dt));
      dd.roll += dd.rollV * dt;
    } else if (dd.roll !== 0 || dd.rollV !== 0) {
      const tgt = Math.round((dd.roll + dd.rollV * 0.2) / TAU) * TAU;
      dd.rollV += (110 * (tgt - dd.roll) - 15 * dd.rollV) * dt;
      dd.roll += dd.rollV * dt;
      if (Math.abs(dd.roll - tgt) < 0.003 && Math.abs(dd.rollV) < 0.08) dd.roll = dd.rollV = 0;
    }
    if (dd.roll > Math.PI) dd.roll -= TAU;
    else if (dd.roll < -Math.PI) dd.roll += TAU;
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

  // ---------- 道具触发瞬间的特效（全是纯视觉，用 Math.random，不碰会影响关卡生成的种子随机数）----------
  const rnd = Math.random;
  const TAU = Math.PI * 2;
  P.addFx = function (o) {
    o.max = o.life;
    if (this.fx.length < 360) this.fx.push(o);
  };
  // 冲击波：扁椭圆（flat 小）或正圆（flat=1）向外扩，越扩越慢、越细
  P.shockwave = function (x, y, o) {
    this.addFx({ k: 'shock', x: x, y: y, r: 6, vr: o.vr, drag: 7, w: o.w, flat: o.flat === undefined ? 0.3 : o.flat, col: o.col, delay: o.delay || 0, life: o.life });
  };
  // 一把粒子（星星/圆点）朝 a0..a1 角度范围喷出去，带重力
  P.burst = function (x, y, n, o) {
    for (let i = 0; i < n; i++) {
      const a = o.a0 + (o.a1 - o.a0) * rnd();
      const sp = o.s0 + (o.s1 - o.s0) * rnd();
      this.addFx({
        k: 'part',
        delay: o.delay || 0,
        shape: o.shapes[(rnd() * o.shapes.length) | 0],
        x: x + (rnd() - 0.5) * (o.spread || 0),
        y: y,
        vx: Math.cos(a) * sp,
        vy: Math.sin(a) * sp,
        g: o.g,
        drag: o.drag,
        r: o.r0 + (o.r1 - o.r0) * rnd(),
        rot: rnd() * TAU,
        vr: (rnd() - 0.5) * 16,
        col: o.cols[(rnd() * o.cols.length) | 0],
        life: o.l0 + (o.l1 - o.l0) * rnd(),
      });
    }
  };
  P.popText = function (x, y, txt, size, col) {
    this.addFx({ k: 'text', x: x, y: y, txt: txt, size: size, col: col, rot: (rnd() - 0.5) * 0.3, life: 0.8 });
  };
  P.addGhost = function () {
    const dd = this.dd;
    const col = dd.state === 'darkwings' ? '#a46bff' : dd.state === 'wings' ? '#ffe07a' : null;
    this.addFx({ k: 'ghost', d: Object.assign({}, dd), x: dd.x, y: dd.y, col: col, life: dd.state === 'darkwings' ? 0.26 : 0.3 });
  };
  // 一根飘落的羽毛：左右摆着慢慢往下飘
  P.feather = function (x, y, vx, vy, life, dark, delay) {
    this.addFx({ k: 'feather', dark: !!dark, delay: delay || 0, x: x, y: y, vx: vx, vy: vy, ph: rnd() * TAU, w: 4 + rnd() * 3, r: (4 + rnd() * 2.5) * (dark ? 1.1 : 1), rot: rnd() * TAU, rot0: rnd() * TAU, life: life });
  };
  // 飞翼撞碎怪物 / 黑洞的瞬间
  P.smashFx = function (x, y) {
    if (this.dd.state === 'darkwings') {
      this.shockwave(x, y, { vr: 480, w: 6, col: '170,90,255', flat: 1, life: 0.38 });
      this.shockwave(x, y, { vr: 340, w: 2.5, col: '125,240,255', flat: 1, life: 0.3, delay: 0.04 });
      this.addFx({ k: 'glow', x: x, y: y, r: 56, col: '190,120,255', life: 0.22 });
      this.burst(x, y, 14, { a0: 0, a1: TAU, s0: 140, s1: 420, g: 600, drag: 1.3, r0: 3.5, r1: 6.5, l0: 0.4, l1: 0.7, spread: 10, shapes: ['star', 'dot'], cols: ['#c77dff', '#ff6ef0', '#7df9ff', '#ffffff'] });
      this.popText(x, y - 26, 'POW!', 24, '#c77dff');
      this.shake = Math.max(this.shake, 6);
      return;
    }
    this.shockwave(x, y, { vr: 420, w: 5, col: '255,214,92', flat: 1, life: 0.36 });
    this.addFx({ k: 'glow', x: x, y: y, r: 50, col: '255,240,170', life: 0.2 });
    this.burst(x, y, 12, { a0: 0, a1: TAU, s0: 120, s1: 380, g: 700, drag: 1.2, r0: 3.5, r1: 6.5, l0: 0.4, l1: 0.7, spread: 10, shapes: ['star', 'dot'], cols: ['#ffd93b', '#ffffff', '#ff8a5c'] });
    this.popText(x, y - 26, 'POW!', 24, '#ff8a3d');
    this.shake = Math.max(this.shake, 5);
  };
  // 紫黑飞翼收翅膀：一圈紫色冲击波 + 霓虹细环，抖落一把黑紫色羽毛
  P.darkEndFx = function () {
    const dd = this.dd;
    const y = dd.y - CENTER;
    this.shockwave(dd.x, y, { vr: 360, w: 5, col: '170,90,255', flat: 1, life: 0.4 });
    this.shockwave(dd.x, y, { vr: 260, w: 2, col: '125,240,255', flat: 1, life: 0.34, delay: 0.05 });
    this.addFx({ k: 'spiral', x: dd.x, y: y, n: 4, R: 70, w: 3, a0: rnd() * TAU, spin: -5, flat: 1, col: '199,125,255', life: 0.5 });
    for (let i = 0; i < 10; i++) this.feather(dd.x + (rnd() - 0.5) * 44, y + (rnd() - 0.5) * 18, (rnd() - 0.5) * 240, -40 + rnd() * 120, 1.1 + rnd() * 0.6, true);
  };
  // 飞翼时间到，翅膀最后扇一下、抖落一把羽毛
  P.wingEndFx = function () {
    const dd = this.dd;
    const y = dd.y - CENTER;
    this.shockwave(dd.x, y, { vr: 300, w: 4, col: '255,226,120', flat: 1, life: 0.36 });
    for (let i = 0; i < 8; i++) this.feather(dd.x + (rnd() - 0.5) * 40, y + (rnd() - 0.5) * 16, (rnd() - 0.5) * 220, -40 + rnd() * 120, 1 + rnd() * 0.6);
  };

  // 道具被用上的那一瞬间：弹簧 / 蹦床在脚下 (x, y) 炸开；螺旋桨帽 / 喷气背包在身上炸开
  P.itemFx = function (type, x, y) {
    const PI = Math.PI;
    if (type === 'spring') {
      const cols = ['#ffd93b', '#fff6a8', '#ff7a5c', '#ffffff'];
      this.shockwave(x, y - 2, { vr: 400, w: 5, col: '255,236,120', life: 0.4 });
      this.shockwave(x, y - 2, { vr: 300, w: 3, col: '255,255,255', life: 0.34, delay: 0.05 });
      this.addFx({ k: 'beam', x: x, y: y, w: 26, h: 150, col: '255,230,110', life: 0.3 });
      this.addFx({ k: 'rays', x: x, y: y - 4, n: 9, a0: -PI * 0.92, a1: -PI * 0.08, R: 84, w: 3.6, col: '255,206,40', life: 0.34 });
      this.burst(x, y - 6, 12, { a0: -PI * 0.95, a1: -PI * 0.05, s0: 140, s1: 430, g: 900, drag: 1.2, r0: 4, r1: 7, l0: 0.5, l1: 0.85, spread: 16, shapes: ['star', 'star', 'dot'], cols: cols });
      this.popText(x, y - 40, 'BOING!', 24, '#ffd93b');
      this.shake = Math.max(this.shake, 3.5);
      this.ghostT = 0.3;
    } else if (type === 'tramp') {
      const cols = ['#ff5c5c', '#ffd93b', '#ffffff', '#7be0ff', '#ff8fd0'];
      const rc = ['255,92,92', '255,217,59', '255,255,255'];
      for (let k = 0; k < 3; k++) this.shockwave(x, y - 2, { vr: 600 - k * 70, w: 7.5 - k * 1.8, col: rc[k], life: 0.5 + k * 0.04, delay: k * 0.06 });
      this.addFx({ k: 'beam', x: x, y: y, w: 46, h: 290, col: '255,170,160', life: 0.38 });
      this.addFx({ k: 'glow', x: x, y: y - 14, r: 78, col: '255,255,255', life: 0.24 });
      this.addFx({ k: 'rays', x: x, y: y - 6, n: 15, a0: -PI * 0.99, a1: -PI * 0.01, R: 140, w: 4.4, col: '255,255,255', life: 0.42 });
      this.addFx({ k: 'rays', x: x, y: y - 6, n: 11, a0: -PI * 0.95, a1: -PI * 0.05, R: 100, w: 6, col: '255,92,92', life: 0.36 });
      this.burst(x, y - 8, 22, { a0: -PI * 0.97, a1: -PI * 0.03, s0: 230, s1: 660, g: 1000, drag: 1.1, r0: 4.5, r1: 8, l0: 0.6, l1: 1.0, spread: 24, shapes: ['star', 'star', 'dot'], cols: cols });
      this.popText(x, y - 48, 'SUPER BOING!!', 28, '#ff6b6b');
      this.shake = Math.max(this.shake, 8);
      this.flash = Math.max(this.flash, 0.3);
      this.flashCol = '255,255,255';
      this.ghostT = 0.5;
    } else if (type === 'prop') {
      const cols = ['#7fd0ff', '#ffd93b', '#ffffff', '#ff7a7a'];
      this.shockwave(x, y, { vr: 320, w: 4.5, col: '127,208,255', flat: 1, life: 0.4 });
      this.shockwave(x, y, { vr: 230, w: 3, col: '255,255,255', flat: 1, life: 0.34, delay: 0.06 });
      this.addFx({ k: 'glow', x: x, y: y, r: 54, col: '190,235,255', life: 0.22 });
      this.addFx({ k: 'rays', x: x, y: y, n: 12, a0: 0, a1: (TAU * 11) / 12, R: 66, w: 3.2, col: '150,222,255', life: 0.34 });
      this.burst(x, y, 16, { a0: 0, a1: TAU, s0: 90, s1: 280, g: 140, drag: 2.2, r0: 3.5, r1: 6.5, l0: 0.5, l1: 0.8, spread: 8, shapes: ['dot', 'star'], cols: cols });
      this.popText(x, y - 48, 'WHIRR!', 22, '#7fd0ff');
      this.shake = Math.max(this.shake, 2.5);
      this.flash = Math.max(this.flash, 0.12);
      this.flashCol = '200,235,255';
      this.ghostT = 0.3;
    } else if (type === 'jet') {
      const cols = ['#ffb02e', '#ff6a3d', '#fff29a', '#ffffff'];
      this.shockwave(x, y + 22, { vr: 520, w: 7, col: '255,176,46', life: 0.46 });
      this.shockwave(x, y, { vr: 340, w: 4.5, col: '255,106,61', flat: 1, life: 0.4, delay: 0.03 });
      this.shockwave(x, y, { vr: 250, w: 3, col: '255,255,255', flat: 1, life: 0.34, delay: 0.08 });
      this.addFx({ k: 'glow', x: x, y: y + 6, r: 72, col: '255,200,110', life: 0.26 });
      this.addFx({ k: 'rays', x: x, y: y, n: 14, a0: 0, a1: (TAU * 13) / 14, R: 82, w: 3.6, col: '255,176,46', life: 0.36 });
      this.burst(x, y + 16, 12, { a0: PI * 0.12, a1: PI * 0.88, s0: 160, s1: 460, g: 300, drag: 1.6, r0: 5, r1: 9, l0: 0.35, l1: 0.6, spread: 18, shapes: ['fire'], cols: cols });
      this.burst(x, y, 12, { a0: 0, a1: TAU, s0: 120, s1: 360, g: 500, drag: 1.4, r0: 4, r1: 7, l0: 0.5, l1: 0.85, spread: 10, shapes: ['star', 'dot'], cols: cols });
      this.popText(x, y - 52, 'BOOM!', 26, '#ff8a3d');
      this.shake = Math.max(this.shake, 6);
      this.flash = Math.max(this.flash, 0.26);
      this.flashCol = '255,200,120';
      this.ghostT = 0.4;
    } else if (type === 'dark') {
      // 紫黑飞翼：先“吸”——一圈紫色星屑被吸进小海星，0.15 秒后再“炸”——
      // 深紫色的粗冲击波外面套霓虹紫、品红、青色三圈细环，五臂漩涡甩开，满天星屑和黑紫色羽毛，屏幕暗下去一下
      const cols = ['#c77dff', '#9d4edd', '#ff6ef0', '#7df9ff', '#ffffff'];
      const D = 0.15;
      for (let i = 0; i < 20; i++) {
        const a = (i / 20) * TAU + rnd() * 0.2;
        const r = 95 + rnd() * 30;
        this.addFx({ k: 'part', shape: i % 3 ? 'dot' : 'star', x: x + Math.cos(a) * r, y: y + Math.sin(a) * r, vx: (-Math.cos(a) * r) / D, vy: (-Math.sin(a) * r) / D, g: 0, drag: 0, r: 2.6 + rnd() * 2, rot: rnd() * TAU, vr: 8, col: cols[i % 4], life: D });
      }
      this.addFx({ k: 'glow', x: x, y: y, r: 60, col: '120,40,220', life: D + 0.05 });
      this.shockwave(x, y, { vr: 820, w: 13, col: '45,12,75', flat: 1, life: 0.6, delay: D });
      this.shockwave(x, y, { vr: 720, w: 6, col: '170,90,255', flat: 1, life: 0.55, delay: D + 0.03 });
      this.shockwave(x, y, { vr: 560, w: 3.5, col: '255,110,235', flat: 1, life: 0.5, delay: D + 0.08 });
      this.shockwave(x, y, { vr: 980, w: 2.5, col: '125,240,255', flat: 1, life: 0.45, delay: D + 0.05 });
      this.shockwave(x, y + CENTER, { vr: 1000, w: 6, col: '150,70,255', life: 0.5, delay: D });
      this.addFx({ k: 'glow', x: x, y: y, r: 170, col: '150,70,255', life: 0.42, delay: D });
      this.addFx({ k: 'beam', x: x, y: y + 40, w: 80, h: 620, col: '150,80,255', life: 0.6, delay: D });
      this.addFx({ k: 'spiral', x: x, y: y, n: 5, R: 140, w: 4, a0: rnd() * TAU, spin: 6, flat: 1, col: '199,125,255', life: 0.7, delay: D });
      this.addFx({ k: 'spiral', x: x, y: y, n: 5, R: 110, w: 2, a0: rnd() * TAU, spin: 6, flat: 1, col: '255,110,235', life: 0.6, delay: D + 0.04 });
      this.addFx({ k: 'rays', x: x, y: y, n: 24, a0: 0, a1: (TAU * 23) / 24, R: 185, w: 4.5, col: '199,125,255', life: 0.5, delay: D });
      this.addFx({ k: 'rays', x: x, y: y, n: 12, a0: 0.13, a1: 0.13 + (TAU * 11) / 12, R: 130, w: 2.5, col: '255,255,255', life: 0.42, delay: D });
      this.burst(x, y, 26, { delay: D, a0: 0, a1: TAU, s0: 200, s1: 620, g: 220, drag: 1.5, r0: 3.5, r1: 7.5, l0: 0.6, l1: 1.2, spread: 12, shapes: ['star', 'star', 'dot'], cols: cols });
      for (let i = 0; i < 16; i++) {
        const a = rnd() * TAU;
        const sp = 140 + rnd() * 280;
        this.feather(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 1.2 + rnd() * 0.7, true, D);
      }
      this.addFx({ k: 'text', x: x, y: y - 58, txt: '暗翼!!', size: 36, col: '#c77dff', rot: (rnd() - 0.5) * 0.3, life: 0.9, delay: D });
      this.shake = Math.max(this.shake, 11);
      this.flash = Math.max(this.flash, 0.42);
      this.flashCol = '70,20,130';
      this.ghostT = 0.7;
    } else if (type === 'wings') {
      // 终极道具：金色 + 白色 + 粉色三层冲击波、巨型光柱、满天星星和羽毛、金色闪屏、狠狠一震
      const cols = ['#ffe27a', '#fff6c8', '#ffffff', '#ffb3e0', '#9be8ff'];
      this.shockwave(x, y, { vr: 760, w: 9, col: '255,214,92', flat: 1, life: 0.6 });
      this.shockwave(x, y, { vr: 600, w: 6, col: '255,255,255', flat: 1, life: 0.52, delay: 0.05 });
      this.shockwave(x, y, { vr: 470, w: 4, col: '255,160,220', flat: 1, life: 0.46, delay: 0.1 });
      this.shockwave(x, y + CENTER, { vr: 900, w: 7, col: '255,236,150', life: 0.5 });
      this.addFx({ k: 'glow', x: x, y: y, r: 150, col: '255,240,170', life: 0.4 });
      this.addFx({ k: 'beam', x: x, y: y + 40, w: 76, h: 560, col: '255,226,120', life: 0.6 });
      this.addFx({ k: 'rays', x: x, y: y, n: 22, a0: 0, a1: (TAU * 21) / 22, R: 170, w: 5, col: '255,214,92', life: 0.5 });
      this.addFx({ k: 'rays', x: x, y: y, n: 14, a0: 0.11, a1: 0.11 + (TAU * 13) / 14, R: 120, w: 3, col: '255,255,255', life: 0.42 });
      this.burst(x, y, 22, { a0: 0, a1: TAU, s0: 180, s1: 560, g: 260, drag: 1.5, r0: 4, r1: 8, l0: 0.6, l1: 1.1, spread: 12, shapes: ['star', 'star', 'dot'], cols: cols });
      for (let i = 0; i < 14; i++) {
        const a = rnd() * TAU;
        const sp = 120 + rnd() * 260;
        this.feather(x, y, Math.cos(a) * sp, Math.sin(a) * sp, 1.1 + rnd() * 0.7);
      }
      this.popText(x, y - 56, '飞翼!!', 34, '#ffd84a');
      this.shake = Math.max(this.shake, 9);
      this.flash = Math.max(this.flash, 0.55);
      this.flashCol = '255,236,170';
      this.ghostT = 0.6;
    }
  };

  const GLIDE_COLS = {
    gold: ['#ffe27a', '#fff6c8', '#ffffff'],
    dark: ['#c77dff', '#ff9ef5', '#a0f6ff'],
    sky: ['#9fdcff', '#ffffff', '#ffe27a'],
    fire: ['#ffd27a', '#ffffff', '#ffb37a'],
  };
  // 每步都跑的“持续”特效：残影、喷气背包的火焰尾迹、螺旋桨扇出来的风、慢落的光点
  P.trailFx = function (dt) {
    const dd = this.dd;
    if (this.ghostT > 0) {
      this.ghostT -= dt;
      this.ghostCd -= dt;
      if (this.ghostCd <= 0) {
        this.ghostCd = 0.034;
        this.addGhost();
      }
    }
    if (this.fx.length > 260) return;
    if (dd.state === 'normal' && dd.fallK > 0.4) {
      // 慢落：身后一路飘起几颗很淡的小光点（颜色跟刚用完的道具走），不抢画面
      this.trailT -= dt;
      if (this.trailT > 0) return;
      this.trailT = 0.06;
      const cs = GLIDE_COLS[dd.glideCol] || GLIDE_COLS.gold;
      this.addFx({
        k: 'part', shape: rnd() < 0.3 ? 'star' : 'dot', x: dd.x + (rnd() - 0.5) * 30 * HS, y: dd.y - CENTER + (rnd() - 0.5) * 20,
        vx: (rnd() - 0.5) * 30, vy: -40 - rnd() * 40, g: -30, drag: 1.2, r: 2.2 + rnd() * 2, rot: rnd() * TAU, vr: (rnd() - 0.5) * 6,
        col: cs[(rnd() * cs.length) | 0], life: 0.6 + rnd() * 0.4,
      });
      if (dd.glideCol === 'dark' && rnd() < 0.18) this.feather(dd.x + (rnd() - 0.5) * 30, dd.y - CENTER, (rnd() - 0.5) * 60, -30, 1, true);
      return;
    }
    if (FLYING[dd.state] !== 1) return;
    this.trailT -= dt;
    if (this.trailT > 0) return;
    if (dd.state === 'darkwings') {
      this.trailT = 0.024;
      const s = rnd() < 0.5 ? -1 : 1;
      if (rnd() < 0.4) this.feather(dd.x + s * (20 + rnd() * 24) * HS, dd.y - CENTER + (rnd() - 0.5) * 10, s * (40 + rnd() * 70), 160 + rnd() * 160, 0.8 + rnd() * 0.5, true);
      // 星屑螺旋：沿着身体的旋转方向甩出去
      const a = dd.roll + rnd() * 0.6;
      const r = 16 * HS;
      this.addFx({
        k: 'part', shape: rnd() < 0.55 ? 'star' : 'dot', x: dd.x + Math.cos(a) * r, y: dd.y - CENTER + Math.sin(a) * r,
        vx: -Math.sin(a) * 120 * dd.spinDir + (rnd() - 0.5) * 30, vy: 180 + rnd() * 160, g: 0, drag: 1.6, r: 2 + rnd() * 2.2, rot: rnd() * TAU, vr: (rnd() - 0.5) * 14,
        col: ['#c77dff', '#ff6ef0', '#7df9ff', '#ffffff', '#9d4edd'][(rnd() * 5) | 0], life: 0.45 + rnd() * 0.3,
      });
      return;
    }
    if (dd.state === 'jetpack') {
      this.trailT = 0.022;
      // 火焰从两个油罐的喷口喷出（油罐在身体两侧约 19 个单位处）
      for (const s of [-1, 1]) {
        this.addFx({ k: 'fire', x: dd.x + s * 19 * HS + (rnd() - 0.5) * 4, y: dd.y - 8 * HS, vx: (rnd() - 0.5) * 50, vy: 160 + rnd() * 170, r: (5 + rnd() * 3.5) * Math.sqrt(HS), life: 0.26 + rnd() * 0.2 });
      }
    } else if (dd.state === 'wings') {
      this.trailT = 0.03;
      const s = rnd() < 0.5 ? -1 : 1;
      if (rnd() < 0.45) this.feather(dd.x + s * (18 + rnd() * 22) * HS, dd.y - CENTER + (rnd() - 0.5) * 10, s * (30 + rnd() * 60), 140 + rnd() * 140, 0.8 + rnd() * 0.5);
      this.addFx({
        k: 'part', shape: rnd() < 0.5 ? 'star' : 'dot', x: dd.x + (rnd() - 0.5) * 24, y: dd.y - 4,
        vx: (rnd() - 0.5) * 60, vy: 160 + rnd() * 160, g: 0, drag: 1.5, r: 2.2 + rnd() * 2, rot: rnd() * TAU, vr: (rnd() - 0.5) * 12,
        col: ['#ffe27a', '#fff6c8', '#ffffff', '#ffb3e0'][(rnd() * 4) | 0], life: 0.45 + rnd() * 0.3,
      });
    } else {
      this.trailT = 0.06;
      const s = rnd() < 0.5 ? -1 : 1;
      this.addFx({ k: 'streak', x: dd.x + s * (14 + rnd() * 14) * HS, y: dd.y - 70 * HS - rnd() * 20, vx: s * 10, vy: 230 + rnd() * 120, life: 0.24 });
    }
  };

  P.bounce = function (v, kick) {
    const dd = this.dd;
    // 脚一下子被平台挡住，身体靠惯性往前栽一下
    dd.leanV += clamp(dd.vx / C.MAX_VX, -1.5, 1.5) * 2.5;
    // 速度从“下落”突变成“上冲”，肚子跟不上，先被甩下去再弹回来（kick 是额外放大的力度）
    dd.bellyV += (v + dd.vy) * C.BELLY_GAIN * (kick || 1);
    dd.vy = -v;
    dd.bounceT = 0;
  };

  P.landing = function () {
    const dd = this.dd;
    const prev = dd.prevY;
    const cur = dd.y;
    let best = null;
    let brown = null; // 这一步踩到的棕色平台：只会被踩碎，不能挡住同一步里本来能踩到的真平台
    for (let i = 0; i < this.platforms.length; i++) {
      const p = this.platforms[i];
      if (p.dead) continue;
      if (prev <= p.y + 0.01 && cur >= p.y && Math.abs(wrapDx(dd.x, p.x)) <= p.w / 2 + C.FEET) {
        if (p.type === 'brown') (brown || (brown = [])).push(p);
        else if (!best || p.y < best.sy) best = { sy: p.y, p: p, it: null };
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
    if (brown) {
      // 只踩碎落脚点上方（或同高）的棕色平台；在落脚点下面的根本碰不到
      for (let i = 0; i < brown.length; i++) if (!best || brown[i].y <= best.sy) this.breakPlatform(brown[i]);
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
      this.itemFx(best.it.type, dd.x, best.sy);
      if (best.it.type === 'spring') {
        this.bounce(C.SPRING_V, 1.5);
        this.sfx('spring');
      } else {
        this.bounce(C.TRAMP_V, 2);
        this.sfx('tramp');
      }
      // 弹簧 / 蹦床也算道具：冲到顶以后慢慢飘下来
      dd.glide = C.GLIDE_T * 0.8;
      dd.glideCol = best.it.type === 'spring' ? 'gold' : 'fire';
      return;
    }
    dd.y = best.sy;
    p.dipV += 150; // 平台被踩得往下沉一点再弹回来
    this.puff(dd.x, best.sy, 4, 70);
    dd.glide = 0; // 落回普通平台，慢落结束
    // 蓄力：倾斜手机越狠，落地时身体压得越扁（起跳那一下肚子甩得也越狠）
    this.bounce(C.JUMP_V, 1 + 0.45 * Math.abs(dd.axis || 0));
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
      if (!it || it.used || (it.type !== 'prop' && it.type !== 'jet' && it.type !== 'wings' && it.type !== 'dark')) continue;
      const dx = wrapDx(dd.x, p.x + it.ox);
      if (Math.abs(dx) < it.w / 2 + HALF_W - 3 && dd.y > p.y - it.h - 4 && dd.y - BODY_H < p.y) {
        it.used = true;
        dd.state = it.type === 'prop' ? 'propeller' : it.type === 'jet' ? 'jetpack' : it.type === 'dark' ? 'darkwings' : 'wings';
        dd.stateT = 0;
        dd.vy = Math.min(dd.vy, it.type === 'dark' ? -560 : it.type === 'wings' ? -450 : -300);
        dd.glide = dd.glideK = dd.fallK = 0;
        if (it.type === 'dark') dd.spinDir = dd.vx < -20 ? -1 : dd.vx > 20 ? 1 : dd.facing || 1;
        this.whirT = 0;
        this.trailT = 0;
        this.itemFx(it.type, dd.x, dd.y - CENTER);
        this.sfx(it.type === 'dark' ? 'darkwings' : it.type === 'wings' ? 'wings' : 'pickup');
        return;
      }
    }
  };

  P.hazards = function () {
    const dd = this.dd;
    const top = dd.y - BODY_H;
    const bot = dd.y;
    const hw = HALF_W;
    const shielded = dd.shield > 0; // 飞翼刚结束的护盾：怪物直接撞飞，黑洞和 UFO 光束无效
    for (let i = 0; i < this.monsters.length; i++) {
      const m = this.monsters[i];
      if (m.dead) continue;
      const dx = wrapDx(dd.x, m.x);
      if (!shielded && m.kind === 'ufo' && Math.abs(dx) < 30 && bot > m.y + 12 && top < m.y + 150) {
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
          dd.glide = 0;
          this.bounce(C.STOMP_V);
          this.sfx('stomp');
        } else if (shielded) {
          this.killMonster(m);
          this.smashFx(m.x, m.y);
          this.sfx('smash');
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
    if (shielded) return;
    for (let i = 0; i < this.holes.length; i++) {
      const h = this.holes[i];
      const dx = wrapDx(dd.x, h.x);
      const dy = dd.y - CENTER - h.y;
      const rr = h.r * 0.85 + 12 * HS;
      if (dx * dx + dy * dy < rr * rr) {
        dd.state = 'blackhole';
        dd.stateT = 0;
        dd.sx = dd.x;
        dd.sy = dd.y;
        dd.tx = h.x;
        dd.ty = h.y + CENTER;
        dd.vx = 0;
        this.sfx('hole');
        return;
      }
    }
  };

  // 飞翼飞行中：撞到的怪物全部撞飞，黑洞直接被撞散
  P.smash = function () {
    const dd = this.dd;
    const cy = dd.y - CENTER;
    const R = (dd.state === 'darkwings' ? 34 : 30) * HS; // 张开翅膀后的撞击半径
    for (let i = 0; i < this.monsters.length; i++) {
      const m = this.monsters[i];
      if (m.dead) continue;
      const dx = wrapDx(dd.x, m.x);
      if (Math.abs(dx) < m.w / 2 + R && Math.abs(cy - m.y) < m.h / 2 + R) {
        this.killMonster(m);
        m.vy = -520;
        m.vx = (dx >= 0 ? -1 : 1) * rand(120, 220);
        this.smashFx(m.x, m.y);
        this.sfx('smash');
      }
    }
    for (let i = this.holes.length - 1; i >= 0; i--) {
      const h = this.holes[i];
      const dx = wrapDx(dd.x, h.x);
      const dy = cy - h.y;
      const rr = h.r + R;
      if (dx * dx + dy * dy < rr * rr) {
        this.holes.splice(i, 1);
        this.smashFx(h.x, h.y);
        this.sfx('smash');
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
    const by = dd.y - MOUTH;
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
    this.bullets.push({ x: bx + cx * 28 * HS, y: by + cy * 28 * HS, vx: cx * C.BULLET_V, vy: cy * C.BULLET_V, life: 1.2 });
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

  // 平台移动、怪物、子弹、碎片等不涉及小海星的更新
  P.stepWorld = function (dt) {
    this.shake = this.shake > 0.05 ? this.shake * Math.exp(-13 * dt) : 0;
    this.flash = this.flash > 0.004 ? Math.max(0, this.flash - dt * 2.4) : 0;
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
      if (f.delay > 0) {
        f.delay -= dt;
        continue;
      }
      f.life -= dt;
      if (f.k === 'puff') {
        f.vx *= Math.exp(-3.5 * dt);
        f.vy *= Math.exp(-2.5 * dt);
        f.x += f.vx * dt;
        f.y += f.vy * dt;
      } else if (f.k === 'ring') {
        f.r += 150 * dt * Math.max(0.15, f.life / f.max);
      } else if (f.k === 'shock') {
        f.vr *= Math.exp(-f.drag * dt);
        f.r += f.vr * dt;
      } else if (f.k === 'part') {
        const d = Math.exp(-f.drag * dt);
        f.vx *= d;
        f.vy = f.vy * d + f.g * dt;
        f.x += f.vx * dt;
        f.y += f.vy * dt;
        f.rot += f.vr * dt;
      } else if (f.k === 'feather') {
        // 羽毛：速度很快被空气拖慢，然后左右摆着慢慢往下飘
        const d = Math.exp(-2.4 * dt);
        f.vx *= d;
        f.vy = 45 + (f.vy - 45) * d;
        f.ph += f.w * dt;
        f.x += (f.vx + Math.sin(f.ph) * 26) * dt;
        f.y += f.vy * dt;
        f.rot = f.rot0 + Math.sin(f.ph) * 0.7;
      } else if (f.k === 'fire' || f.k === 'streak') {
        f.vy *= Math.exp(-1.2 * dt);
        f.x += f.vx * dt;
        f.y += f.vy * dt;
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
