'use strict';
// 无头模拟：在 Node 里直接跑游戏逻辑，用一个会瞄准平台的机器人验证
//  1) 关卡永远可达（相邻平台的高度差不超过起跳高度）
//  2) 没有 NaN / 数组无限增长等数值问题
//  3) 机器人在没有怪物干扰的情况下能一路爬到很高的分数
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function loadGame() {
  const ctx = vm.createContext({ console, Math, Date, performance: { now: () => 0 } });
  ctx.globalThis = ctx;
  for (const f of ['config.js', 'world.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'js', f), 'utf8'), ctx, { filename: f });
  }
  return ctx.DJ;
}

const DJ = loadGame();
const C = DJ.C;
const W = DJ.W;
const DT = 1 / 120;

// ---------- 机器人 ----------
// 按抛物线预测落到每个平台高度时的时间，选“够得着的最高平台”，再左右（含环绕）靠过去
function pickTarget(g) {
  const dd = g.dd;
  let best = null;
  for (const p of g.platforms) {
    if (p.dead || p.type === 'brown') continue;
    // 下落阶段穿过 p.y 的时间：dd.y + vy*t + g/2*t^2 = p.y
    const a = C.GRAVITY / 2;
    const b = dd.vy;
    const c = dd.y - p.y;
    const disc = b * b - 4 * a * c;
    if (disc < 0) continue;
    const t = (-b + Math.sqrt(disc)) / (2 * a);
    if (t < 0.02) continue;
    const dx = DJ.wrapDx(p.x, dd.x);
    const reach = C.MAX_VX * t * 0.9 + C.PLAT_W / 2;
    if (Math.abs(dx) > reach) continue;
    // 越高越好；同高度选更近的
    const score = -p.y * 10 - Math.abs(dx) * 0.01;
    if (!best || score > best.score) best = { p: p, dx: dx, score: score, t: t };
  }
  return best;
}

function botInput(g, wantShoot) {
  const dd = g.dd;
  const shots = [];
  let axis = 0;
  const tg = pickTarget(g);
  if (tg) {
    // 预测到达时的位置，慢慢靠近平台中心
    const px = tg.p.x + (tg.p.vx || 0) * tg.t;
    const dx = DJ.wrapDx(px, dd.x);
    axis = DJ.clamp(dx / 18, -1, 1);
  }
  if (wantShoot) {
    for (const m of g.monsters) {
      if (m.dead) continue;
      const sy = m.y - g.camY;
      if (sy > 0 && sy < g.H * 0.55 && m.y < dd.y - 20) {
        shots.push({ tx: m.x, ty: sy });
        break;
      }
    }
  }
  return { axis: axis, shots: shots };
}

// ---------- 单局 ----------
function run(seed, opts) {
  opts = opts || {};
  DJ.seed(seed);
  const g = new DJ.Game();
  g.H = opts.H || 560;
  g.chill = !!opts.chill;
  g.reset();
  let steps = 0;
  const maxSteps = (opts.seconds || 900) / DT;
  let maxPlat = 0;
  let minRel = Infinity; // 角色在屏幕里的最高位置（相对镜头顶部）
  let nan = false;
  let hazards = 0;
  while (g.phase !== 'over' && steps < maxSteps) {
    if (opts.noHazards) {
      g.monsters.length = 0;
      g.holes.length = 0;
    }
    g.update(DT, botInput(g, true));
    g.events.length = 0;
    steps++;
    maxPlat = Math.max(maxPlat, g.platforms.length);
    const dd = g.dd;
    if (!isFinite(dd.x) || !isFinite(dd.y) || !isFinite(dd.vy) || !isFinite(g.camY)) {
      nan = true;
      break;
    }
    if (g.monsters.length + g.holes.length > 0) hazards++;
    if (dd.state === 'normal' || DJ.FLYING[dd.state] === 1) minRel = Math.min(minRel, dd.y - g.camY);
  }
  return { seed: seed, score: g.score, seconds: steps * DT, over: g.phase === 'over', maxPlat: maxPlat, nan: nan, state: g.dd.state, hazards: hazards, minRel: minRel, H: g.H };
}

// ---------- 静态检查：生成的关卡里，相邻“主平台”高度差必须小于起跳高度 ----------
function checkLevelGaps() {
  const jumpH = (C.JUMP_V * C.JUMP_V) / (2 * C.GRAVITY);
  let worst = 0;
  for (let seed = 1; seed <= 40; seed++) {
    DJ.seed(seed * 7919);
    const g = new DJ.Game();
    g.H = 560;
    g.reset();
    // 一路生成到很高
    while (g.scoreAtY(g.topY) < 30000) g.genRow();
    const mains = g.platforms.filter((p) => p.type !== 'brown').sort((a, b) => b.y - a.y);
    for (let i = 1; i < mains.length; i++) {
      const gap = mains[i - 1].y - mains[i].y;
      worst = Math.max(worst, gap);
    }
  }
  return { jumpH: jumpH, worstGap: worst };
}

// ---------- 针对具体机制的小测试 ----------
function bareGame(seed) {
  DJ.seed(seed);
  const g = new DJ.Game();
  g.H = 560;
  g.reset();
  g.platforms.length = 0;
  g.monsters.length = 0;
  g.holes.length = 0;
  g.camY = 0;
  return g;
}

// 角色从平台上方落下，返回落地时触发的音效名和肚子晃动的轨迹
function dropOnto(x, item) {
  const g = bareGame(1);
  const p = g.addPlatform('normal', 160, 400);
  p.vx = 0;
  if (item) p.item = Object.assign({ used: false, fired: false, t: 0 }, item);
  g.dd.x = x;
  g.dd.y = 300;
  g.dd.prevY = 300;
  g.dd.vy = 200;
  const names = [];
  const belly = [];
  let hit = -1;
  for (let i = 0; i < 360; i++) {
    g.update(DT, { axis: 0, shots: [] });
    const ev = g.events.splice(0);
    if (ev.length && hit < 0) hit = i;
    names.push.apply(names, ev);
    belly.push(g.dd.belly);
  }
  return { names: names, belly: belly, hit: hit };
}

function unitTests(fails) {
  const has = (r, n) => r.names.indexOf(n) >= 0;
  const spring = { type: 'spring', ox: -24, w: 18, h: 14 };

  // 弹簧在平台左侧 24px，角色身体中心在平台内、但离弹簧很远：半个身子压在砖上就该触发
  let r = dropOnto(186, spring);
  if (!has(r, 'spring') || has(r, 'jump')) fails.push('身体中心在平台内、离弹簧较远时应当触发弹簧，实际：' + r.names.slice(0, 3));
  // 身体中心已经出了平台边缘（只剩脚还搭着）：只算普通落地
  r = dropOnto(196, spring);
  if (has(r, 'spring') || !has(r, 'jump')) fails.push('身体中心在平台外时不应触发弹簧，实际：' + r.names.slice(0, 3));
  // 正踩在弹簧上
  r = dropOnto(136, spring);
  if (!has(r, 'spring')) fails.push('正踩弹簧没有触发');
  // 蹦床同理
  r = dropOnto(186, { type: 'tramp', ox: -24, w: 42, h: 10 });
  if (!has(r, 'tramp')) fails.push('蹦床：身体中心在平台内应当触发');

  // 肚子：落地后明显晃动，随后衰减，始终有界
  r = dropOnto(160, null);
  // 只看第一次落地之后、下一次落地之前（一次完整跳跃约 1 秒）
  const after = r.belly.slice(r.hit);
  const peak = Math.max.apply(null, after.slice(0, 60).map(Math.abs));
  const tail = Math.max.apply(null, after.slice(95, 110).map(Math.abs)); // 落地后约 0.8s
  const crossings = after.slice(0, 100).filter((v, i, a) => i && Math.sign(v) !== Math.sign(a[i - 1])).length;
  console.log('肚子晃动：峰值 %s，过零 %d 次，末段残余 %s', peak.toFixed(2), crossings, tail.toFixed(3));
  if (!(peak > 0.4 && peak <= 1)) fails.push('肚子晃动幅度不合理：' + peak);
  if (crossings < 2) fails.push('肚子只晃了不到一个来回（应该 duang duang 好几下）');
  if (tail > 0.12) fails.push('肚子没有收住：' + tail);

  // 自动瞄准
  let g = bareGame(2);
  g.dd.x = 100;
  g.dd.y = 450;
  const m = g.makeMonster('blob', 260, 200);
  m.amp = 0;
  g.monsters.push(m);
  g.fire({ tx: 20, ty: 450 }); // 点在左下角，但怪物在右上方
  let b = g.bullets[0];
  let ang = Math.atan2(b.vy, b.vx);
  let want = Math.atan2(200 - (450 - 32), 260 - 100);
  if (Math.abs(ang - want) > 0.05) fails.push('自动瞄准没有指向怪物：' + ang.toFixed(2) + ' vs ' + want.toFixed(2));

  // 没有怪物：朝点击的方向
  g = bareGame(3);
  g.dd.x = 100;
  g.dd.y = 450;
  g.fire({ tx: 250, ty: 100 });
  b = g.bullets[0];
  ang = Math.atan2(b.vy, b.vx);
  want = Math.atan2(100 - (450 - 32), 250 - 100);
  if (Math.abs(ang - want) > 0.05) fails.push('没有怪物时应朝点击方向：' + ang.toFixed(2) + ' vs ' + want.toFixed(2));

  // 怪物在脚下（打不到）或在屏幕外：不应被选中，仍朝点击方向
  g = bareGame(4);
  g.dd.x = 100;
  g.dd.y = 300;
  const below = g.makeMonster('blob', 160, 520);
  const above = g.makeMonster('blob', 200, -100);
  g.monsters.push(below, above);
  g.fire({ tx: 250, ty: 100 });
  b = g.bullets[0];
  ang = Math.atan2(b.vy, b.vx);
  want = Math.atan2(100 - (300 - 32), 250 - 100);
  if (Math.abs(ang - want) > 0.05) fails.push('下方/屏幕外的怪物不应被自动瞄准');

  // 子弹真的能打死自动瞄准的怪物（含移动的飞行怪，验证提前量）
  g = bareGame(5);
  g.dd.x = 60;
  g.dd.y = 480;
  const fl = g.makeMonster('flyer', 200, 150);
  fl.vx = 80;
  g.monsters.push(fl);
  g.fire({ tx: 0, ty: 0 });
  for (let i = 0; i < 360 && !fl.dead && fl.hp > 0; i++) g.update(DT, { axis: 0, shots: [] });
  if (fl.hp === 2) fails.push('自动瞄准的子弹没有命中移动中的怪物（提前量不对？）');

  // 棕色平台和真平台在同一个物理步里都被穿过：棕色的碎掉，但要稳稳落在真平台上（以前会直接穿过去）
  g = bareGame(6);
  const green = g.addPlatform('normal', 160, 400);
  const brn = g.addPlatform('brown', 160, 396);
  g.dd.x = 160;
  g.dd.y = 390;
  g.dd.prevY = 390;
  g.dd.vy = 1100;
  const evs = [];
  for (let i = 0; i < 3; i++) {
    g.update(DT, { axis: 0, shots: [] });
    evs.push.apply(evs, g.events.splice(0));
  }
  if (!brn.dead) fails.push('同一步里被穿过的棕色平台没有碎');
  if (evs.indexOf('jump') < 0 || g.dd.vy >= 0) fails.push('棕色平台挡住了同一步里的真平台，角色没有起跳：' + evs);
  void green;

  // 飞翼：拿到后进入 wings 状态，飞得比喷气背包高得多；撞碎怪物、无视黑洞；结束后有护盾
  function flyHeight(type) {
    const g2 = bareGame(7);
    const p = g2.addPlatform('normal', 160, 450);
    p.item = { type: type, ox: 0, w: type === 'dark' ? 38 : type === 'wings' ? 36 : 26, h: 30, used: false, fired: false, t: 0 };
    g2.dd.x = 160;
    g2.dd.y = 440;
    g2.dd.prevY = 440;
    g2.dd.vy = 50;
    const y0 = g2.dd.y;
    let st = null;
    let top = y0;
    for (let i = 0; i < 120 * 8; i++) {
      g2.update(DT, { axis: 0, shots: [] });
      g2.events.length = 0;
      if (!st && g2.dd.state !== 'normal') st = g2.dd.state;
      top = Math.min(top, g2.dd.y);
      if (st && g2.dd.state === 'normal' && g2.dd.vy > 0) break;
    }
    return { st: st, h: y0 - top, g: g2 };
  }
  const jet = flyHeight('jet');
  const wing = flyHeight('wings');
  console.log('飞行高度：喷气背包 %d px，飞翼 %d px', Math.round(jet.h), Math.round(wing.h));
  if (wing.st !== 'wings') fails.push('拿到飞翼后没有进入 wings 状态：' + wing.st);
  if (!(wing.h > jet.h * 1.8)) fails.push('飞翼应该比喷气背包飞得高得多：' + wing.h + ' vs ' + jet.h);
  if (!(wing.g.dd.shield > 0 || wing.g.dd.state === 'normal')) fails.push('飞翼结束后状态异常');

  g = bareGame(8);
  g.dd.x = 160;
  g.dd.y = 400;
  g.dd.state = 'wings';
  g.dd.stateT = 0;
  g.dd.vy = -1000;
  const mon = g.makeMonster('blob', 160, 300);
  mon.amp = 0;
  g.monsters.push(mon);
  g.holes.push({ x: 160, y: 120, r: 28, t: 0 });
  for (let i = 0; i < 60; i++) g.update(DT, { axis: 0, shots: [] });
  if (!mon.dead) fails.push('飞翼没有撞碎路上的怪物');
  if (g.holes.length) fails.push('飞翼没有撞散路上的黑洞');
  if (g.dd.state !== 'wings') fails.push('飞翼飞行中被怪物/黑洞打断了：' + g.dd.state);

  // 紫黑飞翼：比白色飞翼飞得更快更高；飞行中身体一直在转；结束后带紫色护盾，旋转弹回正位
  {
    const dark = flyHeight('dark');
    console.log('飞行高度：紫黑飞翼 %d px（白色飞翼的 %s 倍）', Math.round(dark.h), (dark.h / wing.h).toFixed(2));
    if (dark.st !== 'darkwings') fails.push('拿到紫黑飞翼后没有进入 darkwings 状态：' + dark.st);
    if (!(dark.h > wing.h * 1.2)) fails.push('紫黑飞翼应该比白色飞翼飞得更高：' + dark.h + ' vs ' + wing.h);
    if (!dark.g.dd.shieldDark) fails.push('紫黑飞翼结束后的护盾不是紫色的');
    const g3 = bareGame(10);
    g3.dd.x = 160;
    g3.dd.y = 400;
    g3.dd.state = 'darkwings';
    g3.dd.stateT = 0;
    g3.dd.vy = -900;
    let turned = 0;
    let prevR = g3.dd.roll;
    let maxVy = 0;
    for (let i = 0; i < 120 * 2; i++) {
      g3.update(DT, { axis: 0, shots: [] });
      let d = g3.dd.roll - prevR;
      if (d > Math.PI) d -= Math.PI * 2;
      else if (d < -Math.PI) d += Math.PI * 2;
      turned += d;
      prevR = g3.dd.roll;
      maxVy = Math.min(maxVy, g3.dd.vy);
    }
    console.log('紫黑飞翼 2 秒内转了 %s 圈，最高速度 %d px/s', (Math.abs(turned) / (Math.PI * 2)).toFixed(1), Math.round(-maxVy));
    if (Math.abs(turned) < Math.PI * 2 * 3) fails.push('紫黑飞翼飞行中小海星没有转起来：' + turned);
    if (!(-maxVy > C.WING_V * 1.2)) fails.push('紫黑飞翼速度不够快：' + -maxVy);
    // 结束后 1.5 秒内旋转要稳稳停在正位
    g3.dd.stateT = C.DARK_T;
    for (let i = 0; i < 120 * 1.5; i++) g3.update(DT, { axis: 0, shots: [] });
    if (g3.dd.state !== 'normal' || Math.abs(g3.dd.roll) > 0.02) fails.push('紫黑飞翼结束后没有回正：' + g3.dd.state + ' roll=' + g3.dd.roll);
  }

  // 慢落：道具用完后的下落比普通下落慢，落到平台上就恢复正常
  {
    const fallSpeed = function (glide) {
      const g4 = bareGame(11);
      g4.dd.x = 160;
      g4.dd.y = 300;
      g4.dd.vy = 0;
      g4.dd.glide = glide ? C.GLIDE_T : 0;
      g4.dd.glideK = glide ? 1 : 0;
      for (let i = 0; i < 120 * 0.8; i++) g4.update(DT, { axis: 0, shots: [] });
      return g4.dd.vy;
    };
    const vN = fallSpeed(false);
    const vG = fallSpeed(true);
    console.log('下落 0.8 秒后的速度：普通 %d，慢落 %d px/s', Math.round(vN), Math.round(vG));
    if (!(vG < vN * 0.6)) fails.push('慢落没有明显变慢：' + vG + ' vs ' + vN);
    // 拿喷气背包飞完之后应该进入慢落
    if (!(jet.g.dd.glide > 0 || jet.g.dd.glideK > 0)) fails.push('喷气背包用完后没有进入慢落');
    // 弹簧把人弹起来时上升段不受影响（不会因为慢落飞得更高）
    const g5 = bareGame(12);
    const sp = g5.addPlatform('normal', 160, 450);
    sp.item = { type: 'spring', ox: 0, w: 18, h: 14, used: false, fired: false, t: 0 };
    g5.dd.x = 160;
    g5.dd.y = g5.dd.prevY = 430;
    g5.dd.vy = 200;
    let topY = 999;
    for (let i = 0; i < 120 * 1.5; i++) {
      g5.update(DT, { axis: 0, shots: [] });
      topY = Math.min(topY, g5.dd.y);
    }
    const rise = 436 - topY;
    const ideal = (C.SPRING_V * C.SPRING_V) / (2 * C.GRAVITY);
    if (Math.abs(rise - ideal) > ideal * 0.08) fails.push('弹簧起跳高度被慢落改变了：' + rise.toFixed(0) + ' vs ' + ideal.toFixed(0));
    if (!(g5.dd.glide > 0 || g5.dd.fallK > 0)) fails.push('弹簧之后没有慢落');
  }

  // 蓄力：落地瞬间身体朝倾斜方向压下去
  {
    const g6 = bareGame(13);
    g6.addPlatform('normal', 160, 400);
    g6.dd.x = 160;
    g6.dd.y = g6.dd.prevY = 396;
    g6.dd.vy = 300;
    g6.dd.vx = 0;
    let maxLean = 0;
    for (let i = 0; i < 18; i++) {
      g6.update(DT, { axis: 1, shots: [] });
      maxLean = Math.max(maxLean, g6.dd.lean);
    }
    console.log('向右倾斜落地蓄力：身体最大倾斜 %s°', ((maxLean * 180) / Math.PI).toFixed(1));
    if (!(maxLean > 0.2)) fails.push('落地蓄力时身体没有朝倾斜方向压下去：' + maxLean);
  }

  // 护盾：落地后侧面撞到怪物不会死，怪物被撞飞；黑洞也吸不走
  g = bareGame(9);
  g.dd.shield = 1;
  g.dd.x = 160;
  g.dd.y = 300;
  g.dd.vy = -200;
  const m2 = g.makeMonster('blob', 160, 290);
  m2.amp = 0;
  g.monsters.push(m2);
  g.holes.push({ x: 160, y: 250, r: 28, t: 0 });
  for (let i = 0; i < 10; i++) g.update(DT, { axis: 0, shots: [] });
  if (g.dd.state !== 'normal') fails.push('护盾期间仍然被怪物/黑洞干掉了：' + g.dd.state);
  if (!m2.dead) fails.push('护盾期间撞到的怪物应当被撞飞');
  g.dd.shield = 0;
  const m3 = g.makeMonster('blob', g.dd.x, g.dd.y - 10);
  m3.amp = 0;
  g.monsters.push(m3);
  g.holes.length = 0;
  for (let i = 0; i < 5; i++) g.update(DT, { axis: 0, shots: [] });
  if (g.dd.state !== 'dead') fails.push('护盾结束后撞到怪物应该会死：' + g.dd.state);
}

// ---------- 畅快模式：同样生成到 15000 分，统计道具和怪物数量 ----------
function levelStats(chill) {
  const st = { items: 0, fly: 0, hazards: 0, wings: 0, worstGap: 0 };
  for (let seed = 1; seed <= 30; seed++) {
    DJ.seed(seed * 104729);
    const g = new DJ.Game();
    g.H = 560;
    g.chill = chill;
    g.reset();
    let seen = 0;
    while (g.scoreAtY(g.topY) < 15000) {
      g.genRow();
      // 不跑物理，平台/怪物数组只增不减，直接数新增的
      st.hazards += g.monsters.length + g.holes.length - seen;
      seen = g.monsters.length + g.holes.length;
    }
    const mains = g.platforms.filter((p) => p.type !== 'brown').sort((a, b) => b.y - a.y);
    for (let i = 1; i < mains.length; i++) st.worstGap = Math.max(st.worstGap, mains[i - 1].y - mains[i].y);
    for (const p of g.platforms) {
      if (!p.item) continue;
      st.items++;
      if (p.item.type === 'prop' || p.item.type === 'jet' || p.item.type === 'wings') st.fly++;
      if (p.item.type === 'wings') st.wings++;
    }
  }
  return st;
}

function median(a) {
  const s = a.slice().sort((x, y) => x - y);
  return s[Math.floor(s.length / 2)];
}

function main() {
  const fails = [];
  const gaps = checkLevelGaps();
  console.log('起跳高度 %s px，生成关卡里最大的相邻平台间距 %s px', gaps.jumpH.toFixed(1), gaps.worstGap.toFixed(1));
  if (gaps.worstGap > gaps.jumpH * 0.8) fails.push('平台间距过大，离起跳高度太近');

  // 无怪物：机器人应该能爬到很高（验证纯平台关卡可达）
  const clean = [];
  for (let s = 1; s <= 16; s++) clean.push(run(s, { noHazards: true, seconds: 400 }));
  const cleanScores = clean.map((r) => r.score);
  console.log('无怪物 16 局得分中位数 %d，最低 %d', median(cleanScores), Math.min.apply(null, cleanScores));
  for (const r of clean) {
    if (r.nan) fails.push('seed ' + r.seed + ' 出现 NaN');
    if (r.minRel < r.H * 0.1) fails.push('seed ' + r.seed + ' 镜头缓动后角色跑出了屏幕上方（' + r.minRel.toFixed(0) + 'px）');
    if (r.score < 4000) fails.push('seed ' + r.seed + ' 无怪物下只到 ' + r.score + '（机器人应该能上去）');
  }

  // 有怪物：机器人会开枪，但不躲避，只做数值健康检查和粗略难度统计
  const full = [];
  for (let s = 100; s < 116; s++) full.push(run(s, { seconds: 400 }));
  const fullScores = full.map((r) => r.score);
  console.log('有怪物 16 局得分中位数 %d，最高 %d', median(fullScores), Math.max.apply(null, fullScores));
  for (const r of full) {
    if (r.nan) fails.push('seed ' + r.seed + ' 出现 NaN');
    if (r.maxPlat > 120) fails.push('seed ' + r.seed + ' 平台数组过大 ' + r.maxPlat + '（没有及时回收？）');
  }
  if (median(fullScores) < 1500) fails.push('有怪物时中位数过低，开局难度可能过高');

  // 畅快模式：道具明显更多、怪物明显更少，关卡同样可达，机器人跑起来数值正常
  const nS = levelStats(false);
  const cS = levelStats(true);
  console.log('普通模式 30 关：道具 %d（飞行类 %d，飞翼 %d），怪物/黑洞 %d', nS.items, nS.fly, nS.wings, nS.hazards);
  console.log('畅快模式 30 关：道具 %d（飞行类 %d，飞翼 %d），怪物/黑洞 %d', cS.items, cS.fly, cS.wings, cS.hazards);
  if (!(cS.items > nS.items * 2)) fails.push('畅快模式道具没有明显变多');
  if (!(cS.fly > nS.fly * 3)) fails.push('畅快模式飞行道具没有明显变多');
  if (!(cS.hazards < nS.hazards * 0.5)) fails.push('畅快模式怪物没有明显变少');
  if (cS.worstGap > gaps.jumpH * 0.8) fails.push('畅快模式平台间距过大');
  const chillRuns = [];
  for (let s = 200; s < 212; s++) chillRuns.push(run(s, { chill: true, seconds: 400 }));
  const chillScores = chillRuns.map((r) => r.score);
  console.log('畅快模式 12 局得分中位数 %d（普通有怪物 %d）', median(chillScores), median(fullScores));
  for (const r of chillRuns) {
    if (r.nan) fails.push('畅快 seed ' + r.seed + ' 出现 NaN');
    if (r.maxPlat > 120) fails.push('畅快 seed ' + r.seed + ' 平台数组过大 ' + r.maxPlat);
  }
  if (!(median(chillScores) > median(fullScores))) fails.push('畅快模式得分中位数没有比普通模式高');

  unitTests(fails);

  // 同种子应复现同样结果
  const a = run(5, { noHazards: true, seconds: 60 });
  const b = run(5, { noHazards: true, seconds: 60 });
  if (a.score !== b.score) fails.push('同一种子两次结果不同：' + a.score + ' vs ' + b.score);

  if (fails.length) {
    console.error('\n失败：\n - ' + fails.join('\n - '));
    process.exit(1);
  }
  console.log('sim 通过');
}

if (require.main === module) main();
module.exports = { loadGame, run, botInput };
