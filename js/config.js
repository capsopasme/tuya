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
    HERO: 0.74, // 主角整体缩放（画面和碰撞框一起缩；1 = 旧版大小）
    FEET: 11, // 脚的半宽，用于平台碰撞（随主角缩小，略留余量）
    PROP_V: 600,
    PROP_T: 2.7,
    JET_V: 820,
    JET_T: 3.6,
    WING_V: 1350, // 飞翼：比喷气背包快六成、飞得更久，一路撞碎怪物，无视黑洞和 UFO
    WING_T: 5.2,
    WING_VX: 1.25, // 飞翼时左右移动更快（倍数）
    SHIELD_T: 1.6, // 飞翼结束后的护盾时间：碰到怪物/黑洞/UFO 不会死，怪物直接被撞飞
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

  // 畅快模式（设置里开关）：道具更多、出现得更早，怪物/黑洞更少
  // 每种道具：[出现所需分数, 每块可放道具的平台上出现的概率]；普通模式的对应值见 world.js genRow
  DJ.CHILL = {
    ITEMS: {
      wings: [1500, 0.012], // 普通：3500 分后 0.35%
      jet: [600, 0.026], // 普通：2500 分后 0.6%
      prop: [250, 0.04], // 普通：1200 分后 1.2%
      tramp: [0, 0.05], // 普通：700 分后 1.6%
      spring: [0, 0.13], // 普通：6%
    },
    HAZARD_FROM: 2000, // 普通：1100 分以后才刷怪
    HAZARD_GAP: 1700, // 两只怪物之间至少隔多少像素（普通：650）
    HAZARD_P: 0.03, // 满足间隔后每一排刷怪的概率（普通：0.06）
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
