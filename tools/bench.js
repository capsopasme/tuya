'use strict';
// 渲染开销基准：在无头 Chromium（软件渲染，WebGL 走 SwiftShader，着色器开销会直接体现成 CPU 时间）里
// 反复画同一局游戏，分别统计整帧、3D 主角、背景的平均耗时。只用来比较优化前后的相对变化。
//   node tools/bench.js
const { serve, openPhone } = require('./browser');

(async () => {
  const { srv, url } = await serve();
  // 小米 13：1080x2400，约 393x873 CSS 像素、2.75 倍
  const { browser, page, errors } = await openPhone(url, { viewport: { width: 393, height: 873 }, dpr: 2.75 });
  try {
    const r = await page.evaluate(() => {
      DJ.seed(7);
      DJ.app.game.H = DJ.view.H;
      const g = DJ.app.game;
      g.reset();
      DJ.ui.go('play', true);
      const canvas = document.getElementById('c');
      const ctx = canvas.getContext('2d');
      const v = DJ.view;
      const input = { axis: 0.4, shots: [] };
      // 先跑一段，让场上有平台、有特效
      for (let i = 0; i < 240; i++) g.update(1 / 120, input);
      const N = 90;
      // 每画一帧时钟走 1/60 秒：3D 主角每帧都会真的重新光线步进（和真机 60 帧 / 120 帧每两帧一次的负载一样）
      let clock = 100;
      function frame() {
        clock += 1 / 60;
        DJ.clock = clock;
        g.update(1 / 120, input);
        ctx.setTransform(v.dpr * v.scale, 0, 0, v.dpr * v.scale, v.ox * v.dpr, v.oy * v.dpr);
        DJ.drawScene(ctx, g, 0.5);
      }
      // 强制把画布内容真的算出来（getImageData 会等 GPU / 光栅化完成）
      function flush() {
        ctx.getImageData(0, 0, 1, 1);
      }
      const out = {};
      for (const mode of ['3d', '2d']) {
        DJ.hero3d.on = mode === '3d';
        for (let i = 0; i < 10; i++) frame();
        flush();
        const t0 = performance.now();
        for (let i = 0; i < N; i++) {
          frame();
          flush();
        }
        out['frame_' + mode] = (performance.now() - t0) / N;
      }
      // 只画背景
      let t0 = performance.now();
      for (let i = 0; i < N; i++) {
        DJ.drawBackground(ctx, g.camY + i);
        flush();
      }
      out.background = (performance.now() - t0) / N;
      DJ.hero3d.on = true;
      out.canvas = canvas.width + 'x' + canvas.height;
      return out;
    });
    for (const k in r) console.log(k.padEnd(14), typeof r[k] === 'number' ? r[k].toFixed(2) + ' ms' : r[k]);
    if (errors.length) console.log(errors.join('\n'));
  } finally {
    await browser.close();
    srv.close();
  }
})();
