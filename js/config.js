var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';

  // 逻辑画布宽度固定为 320（和原版 iPhone 一致），高度随屏幕比例变化：长屏手机（20:9）会铺满，超出范围才留边
  DJ.W = 320;
  DJ.MIN_H = 480;
  DJ.MAX_H = 780;

  // 物理参数（单位：像素 / 秒）
  DJ.C = {
    GRAVITY: 1400,
    JUMP_V: 730, // 普通平台起跳速度，跳高约 190px
    SPRING_V: 1100, // 弹簧
    TRAMP_V: 1350, // 蹦床
    STOMP_V: 800, // 踩怪物
    MAX_FALL: 1100,
    MAX_VX: 300,
    ACCEL: 3200,
    PLAT_W: 58,
    PLAT_H: 14,
    FEET: 13, // 脚的半宽，用于平台碰撞
    PROP_V: 600,
    PROP_T: 2.7,
    JET_V: 820,
    JET_T: 3.6,
    BULLET_V: 780,
    SHOOT_CD: 0.16,
    ITEM_REACH: 1.0, // 弹簧/蹦床触发范围：身体中心在平台范围内（乘以平台半宽）就算踩到，不必脚踩在道具上
    BELLY_K: 380, // 肚子果冻弹簧：刚度（约 3.1Hz）
    BELLY_C: 5.2, // 阻尼（偏小，会 duang duang 晃 4~5 个来回才收住）
    BELLY_GAIN: 0.0105, // 弹跳瞬间速度突变 -> 肚子被甩出去的力度（越大晃得越狠）
    BELLY2_K: 650, // 肚皮是挂在肚子弹簧上的第二层弹簧，比肚子晃得更晚、更软，叠出波浪感
    BELLY2_C: 9,
    DIP_K: 650, // 落地时平台被压下去的弹簧
    DIP_C: 21,
    CAM_K: 15, // 镜头往上跟随的缓动速度（越大越紧）
    CAM_TOP: 0.12, // 角色最高只能到屏幕上方 12% 处，再快镜头就硬拉
    CAM_FRAC: 0.45, // 小海星最高只会到屏幕 45% 的位置，再往上就卷动屏幕
  };

  // 可设种子的随机数（方便测试复现）
  let rng = Math.random;
  DJ.random = function () {
    return rng();
  };
  DJ.seed = function (s) {
    let a = s >>> 0;
    rng = function () {
      a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  };
  DJ.unseed = function () {
    rng = Math.random;
  };
  DJ.rand = function (a, b) {
    return a + rng() * (b - a);
  };
  DJ.clamp = function (v, a, b) {
    return v < a ? a : v > b ? b : v;
  };
  DJ.lerp = function (a, b, t) {
    return a + (b - a) * t;
  };
  // 左右循环屏幕下 a 相对 b 的水平偏移（取最短路径）
  DJ.wrapDx = function (a, b) {
    let d = a - b;
    if (d > DJ.W / 2) d -= DJ.W;
    else if (d < -DJ.W / 2) d += DJ.W;
    return d;
  };

  // 本地存储（失败时退回内存）
  const mem = {};
  DJ.store = {
    get: function (k, d) {
      try {
        const v = localStorage.getItem('tuya.' + k);
        if (v !== null) return JSON.parse(v);
      } catch (e) {}
      return k in mem ? mem[k] : d;
    },
    set: function (k, v) {
      mem[k] = v;
      try {
        localStorage.setItem('tuya.' + k, JSON.stringify(v));
      } catch (e) {}
    },
  };
})();
