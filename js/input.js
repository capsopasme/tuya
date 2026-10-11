var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  const clamp = DJ.clamp;

  // 输入汇总：重力感应（唯一的触屏/手机操控方式）/ 键盘（电脑调试用）-> axis（-1..1）；点击屏幕 -> 射击队列
  const I = (DJ.input = {
    left: false,
    right: false,
    raw: null, // 传感器最新读数（度，向右歪为正），还没收到过为 null
    tilt: 0, // 经过时间滤波后的倾斜角
    tiltSeen: false,
    sens: 1, // 灵敏度倍数
    shots: [],
  });

  const DEAD = 1.2; // 死区（度）
  const FULL = 17; // 灵敏度 1 时，歪到多少度就是满速

  // 唯一的传感器入口：读数一到就记下；第一次读数直接对齐，不做渐变
  I.setRaw = function (deg) {
    if (deg === null || deg === undefined || isNaN(deg)) return;
    if (I.raw === null) I.tilt = deg;
    I.raw = deg;
    I.tiltSeen = true;
  };

  // 由重力加速度的 X 分量换算倾斜角：竖着像方向盘一样转、或后仰着左右歪都适用
  // （Android 上手机向右歪时 x 为负；iOS 相反，由调用方传入 sign）
  I.setAccel = function (ax, sign) {
    if (ax === null || ax === undefined || isNaN(ax)) return;
    I.setRaw((Math.asin(clamp((-ax * (sign || 1)) / 9.81, -1, 1)) * 180) / Math.PI);
  };

  // 切回应用 / 开局 / 继续游戏时：滤波值立刻对齐当前读数，不留旧姿势的“尾巴”
  I.snapTilt = function () {
    if (I.raw !== null) I.tilt = I.raw;
  };

  // 倾斜角 -> 速度比例：死区之外先缓后快，两端平滑（不会有突然的拐点）
  I.response = function (deg) {
    const full = FULL / I.sens;
    const n = clamp((Math.abs(deg) - DEAD) / (full - DEAD), 0, 1);
    const e = n * n * (3 - 2 * n);
    return Math.sign(deg) * (0.55 * n + 0.45 * e);
  };

  // 每帧调用一次（dt 为帧间隔），时间常数 40ms 的一阶滤波：滤掉手抖又不拖手
  I.axis = function (dt) {
    if (I.left !== I.right) return I.left ? -1 : 1;
    if (I.raw === null) return 0;
    I.tilt += (I.raw - I.tilt) * (1 - Math.exp(-(dt || 0) / 0.04));
    return I.response(I.tilt);
  };

  I.shootUp = function () {
    I.shots.push({ dx: 0, dy: -1 });
  };
  // 点击屏幕（x,y 为逻辑坐标）：射击，方向由游戏自动瞄准怪物，没有怪物时朝点击处
  I.shootAt = function (x, y) {
    I.shots.push({ tx: x, ty: y });
  };
  I.reset = function () {
    I.shots.length = 0;
    I.left = I.right = false;
  };
})();
