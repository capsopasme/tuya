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
