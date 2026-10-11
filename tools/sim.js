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
  g.reset();
  let steps = 0;
  const maxSteps = (opts.seconds || 900) / DT;
  let maxPlat = 0;
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
  }
  return { seed: seed, score: g.score, seconds: steps * DT, over: g.phase === 'over', maxPlat: maxPlat, nan: nan, state: g.dd.state, hazards: hazards };
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
