var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const W = DJ.W;
  const clamp = DJ.clamp;

  // 输入汇总：键盘 / 触屏 / 重力感应 -> axis（-1..1）和射击队列
  const I = (DJ.input = {
    left: false,
    right: false,
    tilt: null, // 手机左右倾斜角（度）
    tiltSeen: false,
    coarse: false, // 主要输入是触屏
    mode: 'auto', // auto | tilt | touch
    touches: new Map(),
    shots: [],
  });

  // 是否使用重力感应控制
  I.tiltActive = function () {
    if (!I.tiltSeen) return false;
    if (I.mode === 'tilt') return true;
    if (I.mode === 'touch') return false;
    return I.coarse;
  };

  // 直接设置倾斜角（度，向右为正）
  I.setTilt = function (deg) {
    if (deg === null || deg === undefined || isNaN(deg)) return;
    // 轻微低通，滤掉手抖
    I.tilt = I.tilt === null ? deg : I.tilt + (deg - I.tilt) * 0.55;
    I.tiltSeen = true;
  };

  // 由重力加速度的 X 分量换算倾斜角：竖着像方向盘一样转、或后仰着左右歪都适用
  // （Android 上手机向右歪时 x 为负；iOS 相反，由调用方传入 sign）
  I.setAccel = function (ax, sign) {
    if (ax === null || ax === undefined || isNaN(ax)) return;
    I.setTilt((Math.asin(clamp((-ax * (sign || 1)) / 9.81, -1, 1)) * 180) / Math.PI);
  };

  I.axis = function (now) {
    if (I.left !== I.right) return I.left ? -1 : 1;
    if (I.tiltActive()) {
      if (I.tilt === null) return 0;
      const dead = 1.5;
      const g = I.tilt;
      const v = Math.abs(g) < dead ? 0 : (Math.abs(g) - dead) / 17;
      return clamp(Math.sign(g) * v, -1, 1);
    }
    // 触屏：按住左 / 右半屏移动（按住超过 100ms 才算，避免点按射击时误动）
    for (const t of I.touches.values()) {
      if (t.steer && now - t.t0 > 100) return t.x < W / 2 ? -1 : 1;
    }
    return 0;
  };

  I.shootUp = function () {
    I.shots.push({ dx: 0, dy: -1 });
  };

  // 指针按下（x,y 为逻辑坐标）
  I.down = function (id, type, x, y, now) {
    const steer = type === 'touch' && !I.tiltActive();
    I.touches.set(id, { x: x, y: y, x0: x, y0: y, t0: now, steer: steer });
    if (!steer) I.shots.push({ tx: x, ty: y });
  };
  I.move = function (id, x, y) {
    const t = I.touches.get(id);
    if (t) {
      t.x = x;
      t.y = y;
    }
  };
  I.up = function (id, x, y, now) {
    const t = I.touches.get(id);
    if (!t) return;
    I.touches.delete(id);
    // 触屏移动模式下，快速轻点 = 射击
    if (t.steer && now - t.t0 < 220 && Math.hypot(x - t.x0, y - t.y0) < 28) I.shots.push({ tx: x, ty: y });
  };
  I.clearPointers = function () {
    I.touches.clear();
  };
  I.reset = function () {
    I.touches.clear();
    I.shots.length = 0;
    I.left = I.right = false;
  };
})();
