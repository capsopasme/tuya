'use strict';
// 测试脚本共用：静态服务器 + 手机模拟的无头 Chromium
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
  '.png': 'image/png',
};

function serve() {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      let p = decodeURIComponent(req.url.split('?')[0]);
      if (p.endsWith('/')) p += 'index.html';
      const f = path.join(ROOT, p);
      if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) {
        res.writeHead(404);
        res.end('not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
      fs.createReadStream(f).pipe(res);
    });
    srv.listen(0, '127.0.0.1', () => resolve({ srv: srv, url: 'http://127.0.0.1:' + srv.address().port + '/' }));
  });
}

function loadPlaywright() {
  try {
    return require('playwright');
  } catch (e) {
    return require('/opt/node22/lib/node_modules/playwright');
  }
}

function findChromium() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  for (const d of ['chromium', 'chromium-1194/chrome-linux/chrome', 'chromium_headless_shell-1194/chrome-linux/headless_shell']) {
    const f = path.join(base, d);
    if (fs.existsSync(f) && fs.statSync(f).isFile()) return f;
  }
  return undefined;
}

// 一加 Ace 5 的逻辑分辨率约 412x915（3.5 倍），这里用同样比例
async function openPhone(url, opts) {
  opts = opts || {};
  const pw = loadPlaywright();
  let browser;
  try {
    browser = await pw.chromium.launch({ args: ['--no-sandbox'] });
  } catch (e) {
    browser = await pw.chromium.launch({ executablePath: findChromium(), args: ['--no-sandbox'] });
  }
  const ctx = await browser.newContext({
    viewport: opts.viewport || { width: 412, height: 915 },
    deviceScaleFactor: opts.dpr || 2,
    isMobile: true,
    hasTouch: true,
    userAgent: 'Mozilla/5.0 (Linux; Android 16; PKG110) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36',
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push('console.error: ' + m.text());
  });
  if (opts.init) await page.addInitScript(opts.init);
  await page.goto(url);
  await page.waitForFunction(() => window.DJ && DJ.app && DJ.view);
  return { browser: browser, ctx: ctx, page: page, errors: errors };
}

// 把逻辑坐标换成屏幕坐标
function toScreen(page, x, y) {
  return page.evaluate(
    ([x, y]) => ({ x: DJ.view.ox + x * DJ.view.scale, y: DJ.view.oy + y * DJ.view.scale }),
    [x, y]
  );
}

async function tapButton(page, id) {
  const pos = await page.evaluate((id) => {
    const b = DJ.ui.buttons.find((b) => b.id === id);
    if (!b) return null;
    return { x: DJ.view.ox + (b.x + b.w / 2) * DJ.view.scale, y: DJ.view.oy + (b.y + b.h / 2) * DJ.view.scale };
  }, id);
  if (!pos) throw new Error('当前界面没有按钮 ' + id);
  await page.touchscreen.tap(pos.x, pos.y);
}

// 模拟手机向右歪 deg 度：安卓上重力加速度的 X 分量为 -g*sin(deg)
async function tilt(page, deg) {
  await page.evaluate((deg) => {
    const r = (deg * Math.PI) / 180;
    // 真机会持续上报，这里连发几次让低通滤波收敛到目标角度
    for (let i = 0; i < 8; i++) {
      window.dispatchEvent(
        new DeviceMotionEvent('devicemotion', { accelerationIncludingGravity: { x: -9.81 * Math.sin(r), y: 9.81 * Math.cos(r), z: 0 } })
      );
    }
  }, deg);
}

module.exports = { serve, openPhone, toScreen, tapButton, tilt, ROOT };
