'use strict';
// 截图：把各种道具、怪物和角色状态摆出来拍成 PNG，输出到 tools/out/，用来肉眼检查画面
const fs = require('fs');
const path = require('path');
const { serve, openPhone, tapButton } = require('./browser');

const OUT = path.join(__dirname, 'out');
fs.mkdirSync(OUT, { recursive: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const { srv, url } = await serve();
  const { browser, page, errors } = await openPhone(url, { dpr: 2 });
  try {
    await page.screenshot({ path: path.join(OUT, 'menu.png') });
    for (const id of ['scores', 'settings']) {
      await tapButton(page, id);
      if (id === 'settings') await page.evaluate(() => DJ.input.setRaw(9));
      await sleep(700);
      await page.screenshot({ path: path.join(OUT, id + '.png') });
      await tapButton(page, 'back');
      await sleep(150);
    }
    await tapButton(page, 'play');
    await sleep(100);

    // 冻结游戏逻辑，只保留渲染，然后摆放元素
    async function stage(name, fn) {
      await page.evaluate((src) => {
        const g = DJ.app.game;
        if (!g._update) g._update = g.update;
        g.update = g._update;
        g.platforms.length = 0;
        g.monsters.length = 0;
        g.holes.length = 0;
        g.bullets.length = 0;
        g.debris.length = 0;
        g.sparks.length = 0;
        g.camY = 0;
        g.dd.state = 'normal';
        g.dd.vy = 0;
        g.dd.bounceT = 1;
        g.dd.shootT = 99;
        g.dd.shield = 0;
        g.dd.armPose = 0.35;
        g.dd.look = 0.4;
        g.dd.belly = g.dd.b2 = 0;
        g.dd.stateT = 0;
        g.dd.x = 160;
        g.dd.y = g.H - 60;
        g.dd.lean = g.dd.leanV = g.dd.roll = g.dd.rollV = 0;
        g.dd.leanAir = 1;
        g.dd.glide = g.dd.glideK = g.dd.fallK = 0;
        g.dd.shieldDark = false;
        g.fx.length = 0;
        g.flash = g.shake = 0;
        new Function('g', src)(g);
        g.update = function () {};
      }, fn);
      await sleep(120);
      await page.screenshot({ path: path.join(OUT, name + '.png') });
    }

    await stage(
      'items',
      `
      const P = (t, x, y, item) => { const p = g.addPlatform(t, x, y); p.vx = 0; if (item) p.item = Object.assign({ ox: 0, used: false, fired: false, t: 0 }, item); return p; };
      P('normal', 60, 120, { type: 'spring', w: 18, h: 14 });
      P('normal', 160, 120, { type: 'tramp', w: 42, h: 10 });
      P('normal', 260, 120, { type: 'prop', w: 24, h: 24 });
      P('normal', 60, 200, { type: 'jet', w: 26, h: 32 });
      P('moving', 160, 200);
      P('brown', 260, 200);
      P('white', 60, 280);
      const sp = P('normal', 160, 280, { type: 'spring', w: 18, h: 14 }); sp.item.fired = true; sp.item.t = 0.15;
      g.dd.y = g.H - 20; g.dd.x = 260;
      `
    );
    await stage(
      'monsters',
      `
      g.monsters.push(g.makeMonster('blob', 80, 120));
      g.monsters.push(g.makeMonster('flyer', 230, 200));
      g.monsters.push(g.makeMonster('ufo', 110, 300));
      g.holes.push({ x: 250, y: 330, r: 28, t: 1 });
      g.platforms.push({ type: 'normal', x: 160, y: g.H - 30, w: 58, h: 14, vx: 0, dead: false, fade: 0, item: null });
      g.dd.y = g.H - 30;
      `
    );
    await stage(
      'hero-states',
      `
      g.dd.x = 160; g.dd.y = 200; g.dd.vy = -500; g.dd.bounceT = 0.3;   // 上升
      g.camY = 0;
      `
    );
    await stage(
      'shoot',
      `
      g.dd.x = 120; g.dd.y = 300; g.dd.vy = -300; g.dd.bounceT = 0.3; g.dd.shootT = 0.05; g.dd.aim = -1.1; g.dd.facing = 1;
      g.monsters.push(g.makeMonster('blob', 200, 120));
      g.bullets.push({ x: 150, y: 220, vx: 0, vy: 0, life: 1 });
      g.bullets.push({ x: 170, y: 170, vx: 0, vy: 0, life: 1 });
      `
    );
    for (const [name, v] of [['belly-0', 0], ['belly-pos', 0.5], ['belly-neg', -0.5]]) {
      await stage(name, `g.dd.x = 160; g.dd.y = g.H - 60; g.dd.belly = ${v}; g.dd.vy = 0; g.dd.bounceT = 0.3;`);
    }
    await stage('propeller', `g.dd.state = 'propeller'; g.dd.x = 160; g.dd.y = 300; g.dd.vy = -500; g.dd.facing = -1;`);
    await stage('dead', `g.dd.state = 'dead'; g.dd.x = 160; g.dd.y = 300; g.dd.vy = 100; g.dd.facing = 1; g.dd.t = 0.3;`);
    await stage('jetpack', `g.dd.state = 'jetpack'; g.dd.x = 160; g.dd.y = 300; g.dd.vy = -800; g.dd.t = 0.4;`);

    // 飞翼：平台上的道具、拿到瞬间的特效、飞行中、结束后的护盾
    await stage(
      'wings-item',
      `
      const p = g.addPlatform('normal', 160, 220); p.vx = 0;
      p.item = { type: 'wings', ox: 0, w: 36, h: 30, used: false, fired: false, t: 0 };
      const q = g.addPlatform('normal', 80, 340); q.vx = 0;
      q.item = { type: 'jet', ox: 0, w: 26, h: 32, used: false, fired: false, t: 0 };
      `
    );
    await stage('wings-pickup', `g.dd.x = 160; g.dd.y = 330; g.itemFx('wings', 160, 330 - 24 * DJ.C.HERO); g.dd.state = 'wings'; g.dd.stateT = 0.3; g.dd.vy = -1300; g.dd.t = 0.2;`);
    await stage('wings-fly', `g.fx.length = 0; g.flash = 0; g.shake = 0; g.dd.state = 'wings'; g.dd.stateT = 1.5; g.dd.x = 160; g.dd.y = 330; g.dd.vy = -1350; g.dd.t = 1.1; g.dd.look = 0.6;
      for (let i = 0; i < 40; i++) g.trailFx(0.04);`);
    await stage('shield', `g.fx.length = 0; g.dd.state = 'normal'; g.dd.shield = 1.2; g.dd.x = 160; g.dd.y = 330; g.dd.vy = -400; g.dd.t = 0.5;
      g.monsters.push(g.makeMonster('blob', 230, 230));`);

    // 身体倾斜：落地蓄力（绕脚底压下去）、空中朝移动方向歪
    for (const mode of ['3d', '2d']) {
      await page.evaluate((m) => (DJ.hero3d.on = m === '3d'), mode);
      await stage(
        'lean-charge-' + mode,
        `const P = g.addPlatform('normal', 160, g.H - 60); P.vx = 0;
         g.dd.x = 160; g.dd.y = g.H - 60; g.dd.bounceT = 0.07; g.dd.belly = 0.7; g.dd.b2 = 0.8; g.dd.lean = 0.42; g.dd.leanAir = 0; g.dd.look = 0.8; g.dd.vy = -700;`
      );
    }
    await page.evaluate(() => (DJ.hero3d.on = true));
    await stage('lean-air', `g.dd.x = 160; g.dd.y = 300; g.dd.vy = -450; g.dd.vx = 300; g.dd.bounceT = 0.3; g.dd.lean = 0.2; g.dd.look = 1;`);
    // 道具用完后的慢落：手臂摊开，身后飘着淡淡的光点
    await stage(
      'glide',
      `g.dd.x = 160; g.dd.y = 300; g.dd.vy = 400; g.dd.bounceT = 1; g.dd.glide = 1.5; g.dd.glideK = 1; g.dd.fallK = 1; g.dd.armPose = -0.5; g.dd.lean = 0.12; g.dd.glideCol = 'gold';
       for (let i = 0; i < 30; i++) { g.trailFx(0.03); g.stepWorld(0.03); g.dd.y += 12; } g.dd.y = 300;`
    );
    // 紫黑飞翼：平台上的道具（和白色飞翼对比）、拿到瞬间（吸 -> 炸）、飞行中旋转、结束后的紫色护盾
    await stage(
      'dark-item',
      `const p = g.addPlatform('normal', 100, 240); p.vx = 0;
       p.item = { type: 'wings', ox: 0, w: 36, h: 30, used: false, fired: false, t: 0 };
       const q = g.addPlatform('normal', 220, 240); q.vx = 0;
       q.item = { type: 'dark', ox: 0, w: 38, h: 32, used: false, fired: false, t: 0 };`
    );
    for (const [name, tt] of [['dark-pickup-a', 0.08], ['dark-pickup-b', 0.26], ['dark-pickup-c', 0.5]]) {
      await stage(
        name,
        `g.dd.x = 160; g.dd.y = 360; g.itemFx('dark', 160, 360 - 24 * DJ.C.HERO); g.dd.state = 'darkwings'; g.dd.stateT = ${tt}; g.dd.vy = -1500; g.dd.t = 0.2; g.dd.roll = ${tt} * 9;
         for (let i = 0; i < ${tt} * 120; i++) g.stepWorld(1 / 120);`
      );
    }
    for (const mode of ['3d', '2d']) {
      await page.evaluate((m) => (DJ.hero3d.on = m === '3d'), mode);
      await stage(
        'dark-fly-' + mode,
        `g.dd.state = 'darkwings'; g.dd.stateT = 1.5; g.dd.x = 160; g.dd.y = 330; g.dd.vy = -1900; g.dd.t = 1.1; g.dd.look = 0.5; g.dd.spinDir = 1;
         for (let i = 0; i < 40; i++) { g.dd.roll = (i * 0.25) % (Math.PI * 2) - Math.PI; g.trailFx(0.03); g.stepWorld(0.03); }
         g.dd.roll = 0.9;`
      );
    }
    await page.evaluate(() => (DJ.hero3d.on = true));
    await stage('dark-shield', `g.dd.shield = 1.2; g.dd.shieldDark = true; g.dd.x = 160; g.dd.y = 330; g.dd.vy = -400; g.dd.t = 0.5;`);
    {
      await page.evaluate(() => { const g = DJ.app.game; g.update = g._update; g.fx.length = 0; g.platforms.length = 0; g.dd.state = 'darkwings'; g.dd.stateT = 2; g.dd.x = 160; g.dd.y = 400; g.dd.vy = -1900; g.dd.roll = 0.7; g.dd.lean = 0; g.dd.look = 0.3; g.dd.t = 2.2; g.dd.shootT = 99; g.camY = 0; g.update = function () {}; });
      await sleep(150);
      const pz = await page.evaluate(() => ({ x: DJ.view.ox + 80 * DJ.view.scale, y: DJ.view.oy + 310 * DJ.view.scale, w: 160 * DJ.view.scale, h: 110 * DJ.view.scale }));
      await page.screenshot({ path: path.join(OUT, 'dark-zoom.png'), clip: { x: pz.x, y: pz.y, width: pz.w, height: pz.h } });
    }

    // 2D / 3D 对比（同一套姿态）
    for (const mode of ['2d', '3d']) {
      await page.evaluate((m) => (DJ.hero3d.on = m === '3d'), mode);
      for (const [name, v] of [['rest', 0], ['squash', 0.8], ['stretch', -0.6]]) {
        await stage(
          'hero' + mode + '-' + name,
          `const P = g.addPlatform('normal', 160, g.H - 60); P.vx = 0;
           g.dd.x = 160; g.dd.y = g.H - 60; g.dd.belly = ${v}; g.dd.b2 = ${v} * 1.2; g.dd.vy = 0; g.dd.bounceT = 0.3; g.dd.look = 0.7;`
        );
      }
      await stage('hero' + mode + '-shoot', `g.dd.x = 160; g.dd.y = 300; g.dd.vy = -300; g.dd.bounceT = 0.3; g.dd.shootT = 0.05; g.dd.aim = -1.1; g.dd.look = 0.9;`);
      await stage('hero' + mode + '-dead', `g.dd.state = 'dead'; g.dd.x = 160; g.dd.y = 300; g.dd.vy = 100; g.dd.t = 0.3;`);
    }
    await page.evaluate(() => (DJ.hero3d.on = true));
    // 放大看 3D 主角细节
    await page.evaluate(() => { const g = DJ.app.game; g.update = g._update; g.platforms.length = 0; g.addPlatform('normal', 160, 400).vx = 0; g.dd.state = 'normal'; g.dd.x = 160; g.dd.y = 400; g.dd.vy = 0; g.dd.belly = 0.5; g.dd.b2 = 0.6; g.dd.look = 0.5; g.dd.shootT = 99; g.camY = 0; g.update = function () {}; });
    await sleep(120);
    const pos = await page.evaluate(() => ({ x: DJ.view.ox + 110 * DJ.view.scale, y: DJ.view.oy + 330 * DJ.view.scale, w: 100 * DJ.view.scale, h: 90 * DJ.view.scale }));
    await page.screenshot({ path: path.join(OUT, 'hero3d-zoom.png'), clip: { x: pos.x, y: pos.y, width: pos.w, height: pos.h } });
    console.log('3D 主角可用：' + (await page.evaluate(() => DJ.hero3d.supported() + ' ' + (DJ.hero3d.error || ''))));

    if (errors.length) {
      console.error(errors.join('\n'));
      process.exitCode = 1;
    }
    console.log('截图已写入 ' + OUT);
  } finally {
    await browser.close();
    srv.close();
  }
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
