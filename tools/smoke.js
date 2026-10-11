'use strict';
// 冒烟测试：在模拟的手机浏览器里真的点按钮、倾斜、射击、暂停、死亡，确认整条流程没有报错
const fs = require('fs');
const path = require('path');
const { serve, openPhone, tapButton, tilt, toScreen } = require('./browser');

const OUT = path.join(__dirname, 'out');
fs.mkdirSync(OUT, { recursive: true });

const fails = [];
function check(cond, msg) {
  console.log((cond ? '  ok  ' : ' FAIL ') + msg);
  if (!cond) fails.push(msg);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

(async () => {
  const { srv, url } = await serve();
  const { browser, page, errors } = await openPhone(url);
  try {
    // 菜单
    check(await page.evaluate(() => DJ.ui.screen === 'menu'), '启动后在菜单界面');
    const view = await page.evaluate(() => ({ H: DJ.view.H, scale: DJ.view.scale }));
    check(view.H >= 480 && view.H <= 780, '逻辑高度在范围内（' + view.H + '）');
    check(Math.abs(view.H * view.scale - 915) < 2, '长屏手机上画面铺满整个屏幕高度');
    await sleep(300);
    await page.screenshot({ path: path.join(OUT, 'menu.png') });

    // 设置里切换操控和声音
    await tapButton(page, 'settings');
    await sleep(100);
    check(await page.evaluate(() => DJ.ui.screen === 'settings'), '能进入设置');
    await tapButton(page, 'sound');
    check(await page.evaluate(() => DJ.app.settings.sound === false), '声音开关生效');
    await tapButton(page, 'sound');
    await tapButton(page, 'back');
    check(await page.evaluate(() => DJ.ui.screen === 'menu'), '能从设置返回');

    // 开始游戏
    await tapButton(page, 'play');
    await sleep(200);
    check(await page.evaluate(() => DJ.ui.screen === 'play'), '点“开始游戏”进入游戏');
    check(await page.evaluate(() => DJ.input.coarse === true), '识别为触屏设备');

    // 起始平台上能持续弹跳（高度来回变化）
    const ys = [];
    for (let i = 0; i < 12; i++) {
      ys.push(await page.evaluate(() => DJ.app.game.dd.y));
      await sleep(80);
    }
    const span = Math.max.apply(null, ys) - Math.min.apply(null, ys);
    check(span > 40, '角色在弹跳（高度变化 ' + span.toFixed(0) + 'px）');

    // 倾斜控制：向右倾，x 应当往右走（或环绕）
    await tilt(page, 0);
    check(await page.evaluate(() => DJ.input.tiltActive()), '收到重力感应后启用倾斜控制');
    const x0 = await page.evaluate(() => DJ.app.game.dd.x);
    await tilt(page, 22);
    await sleep(350);
    const x1 = await page.evaluate(() => DJ.app.game.dd.x);
    const dxR = ((x1 - x0 + 480) % 320) - 160 > 0 ? 1 : -1; // 环绕后的方向
    check(dxR > 0 && Math.abs(x1 - x0) > 5, '向右倾斜，角色向右移动（' + x0.toFixed(0) + ' → ' + x1.toFixed(0) + '）');
    await tilt(page, -22);
    await sleep(700);
    const vx = await page.evaluate(() => DJ.app.game.dd.vx);
    check(vx < 0, '向左倾斜，角色向左移动（vx=' + vx.toFixed(0) + '）');
    await tilt(page, 0);

    // 点击屏幕射击
    const before = await page.evaluate(() => DJ.app.game.bullets.length);
    const p = await toScreen(page, 160, 150);
    await page.touchscreen.tap(p.x, p.y);
    await sleep(60);
    const after = await page.evaluate(() => DJ.app.game.bullets.length);
    check(after > before, '点击屏幕发射子弹（' + before + ' → ' + after + '）');

    // 暂停 / 继续
    const pp = await toScreen(page, 296, 16);
    await page.touchscreen.tap(pp.x, pp.y);
    await sleep(100);
    check(await page.evaluate(() => DJ.ui.screen === 'pause'), '点右上角暂停键进入暂停');
    const yp = await page.evaluate(() => DJ.app.game.dd.y);
    await sleep(300);
    check((await page.evaluate(() => DJ.app.game.dd.y)) === yp, '暂停期间游戏不更新');
    await page.screenshot({ path: path.join(OUT, 'pause.png') });
    await tapButton(page, 'resume');
    check(await page.evaluate(() => DJ.ui.screen === 'play'), '能继续游戏');

    // 往上爬：给个大力道直接升空，确认分数和镜头在走
    await page.evaluate(() => {
      const g = DJ.app.game;
      g.dd.state = 'jetpack';
      g.dd.stateT = 0;
    });
    await sleep(1500);
    const sc = await page.evaluate(() => DJ.app.game.score);
    check(sc > 100, '喷气背包升空后分数增加（' + sc + '）');
    await page.screenshot({ path: path.join(OUT, 'jetpack.png') });

    // 失败结算：把角色扔到屏幕下方
    await page.evaluate(() => {
      const g = DJ.app.game;
      g.dd.state = 'normal';
      g.dd.y = g.camY + g.H + 200;
      g.dd.vy = 800;
    });
    for (let i = 0; i < 30 && (await page.evaluate(() => DJ.ui.screen)) !== 'over'; i++) await sleep(100);
    check(await page.evaluate(() => DJ.ui.screen === 'over'), '掉出屏幕后进入结算界面');
    check(await page.evaluate(() => DJ.store.get('best', 0) > 0), '最高分已存档');
    await sleep(200);
    await page.screenshot({ path: path.join(OUT, 'over.png') });

    // 再来一局
    await tapButton(page, 'again');
    await sleep(150);
    check(await page.evaluate(() => DJ.ui.screen === 'play' && DJ.app.game.t < 1 && DJ.app.game.phase === 'play'), '“再玩一次”重新开局');

    // 切后台自动暂停
    await page.evaluate(() => window.dispatchEvent(new Event('blur')));
    await sleep(80);
    check(await page.evaluate(() => DJ.ui.screen === 'pause'), '失去焦点时自动暂停');

    // 返回键（安卓壳调用 DJ.onBack）：暂停时继续，游戏中暂停，设置里返回菜单，菜单里放行退出
    check((await page.evaluate(() => DJ.onBack())) === true && (await page.evaluate(() => DJ.ui.screen)) === 'play', '返回键：暂停 → 继续');
    check((await page.evaluate(() => DJ.onBack())) === true && (await page.evaluate(() => DJ.ui.screen)) === 'pause', '返回键：游戏中 → 暂停');
    await tapButton(page, 'menu');
    await sleep(100);
    await tapButton(page, 'settings');
    await sleep(100);
    check((await page.evaluate(() => DJ.onBack())) === true && (await page.evaluate(() => DJ.ui.screen)) === 'menu', '返回键：设置 → 菜单');
    check((await page.evaluate(() => DJ.onBack())) === false, '返回键：菜单里不拦截（交给系统退出）');

    // 不同屏幕比例（短屏 16:9、折叠屏近方形）都能正常布局
    for (const vp of [
      { width: 360, height: 640 },
      { width: 412, height: 915 },
      { width: 600, height: 700 },
    ]) {
      await page.setViewportSize(vp);
      await sleep(150);
      const ok = await page.evaluate(() => {
        const v = DJ.view;
        return v.scale > 0 && v.H >= 480 && v.H <= 780 && v.ox * 2 + 320 * v.scale <= innerWidth + 1;
      });
      check(ok, '视口 ' + vp.width + 'x' + vp.height + ' 布局正常');
    }

    // 安卓壳的原生倾斜桥：window.NativeTilt.get() 返回角度，游戏每帧轮询
    {
      const native = await openPhone(url, { init: 'window.__t = 0; window.NativeTilt = { get: function () { return window.__t; } };' });
      try {
        await tapButton(native.page, 'play');
        await sleep(200);
        await native.page.evaluate(() => (window.__t = 20));
        await sleep(500);
        const vxN = await native.page.evaluate(() => DJ.app.game.dd.vx);
        check(vxN > 100, '原生倾斜桥向右 20° → 角色向右加速（vx=' + vxN.toFixed(0) + '）');
        await native.page.evaluate(() => (window.__t = -20));
        await sleep(800);
        check((await native.page.evaluate(() => DJ.app.game.dd.vx)) < -100, '原生倾斜桥向左 20° → 角色向左加速');
        check(native.errors.length === 0, '原生桥场景没有脚本报错' + (native.errors.length ? '：' + native.errors.join(' | ') : ''));
      } finally {
        await native.browser.close();
      }
    }

    check(errors.length === 0, '没有脚本报错' + (errors.length ? '：' + errors.join(' | ') : ''));
  } finally {
    await browser.close();
    srv.close();
  }
  if (fails.length) {
    console.error('\n冒烟测试失败 ' + fails.length + ' 项');
    process.exit(1);
  }
  console.log('smoke 通过');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
