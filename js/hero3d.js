var DJ = (globalThis.DJ = globalThis.DJ || {});
(function () {
  'use strict';
  // 3D 小海星：用 WebGL 在一块小的离屏画布上做 SDF 光线步进（真的有体积、光照、高光、环境光遮蔽和投影），
  // 再把这块画布贴回 2D 场景里。整个世界还是手绘 2D，只有主角是 3D 的，形成“立体公仔贴在笔记本上”的对比。
  //
  // 形状和 2D 版完全共用 world.js 的弹簧（肚子 belly / 肚皮 b2 / 拉伸 stretch / 手臂姿态……）和 render.js 的 starGeom：
  //   中心是一个椭球身体，前面再融合一个会 duang duang 往外鼓的大肚子椭球；五条胖手臂是“根粗尖细”的圆锥，
  //   所有部件用 smooth-min 融成一整块软糖；眼睛是凸出来的小球，瞳孔、腮红、嘴和小颗粒是画在曲面上的。
  //   角色左右移动时整个身体会转过去一点（yaw），侧面就能看到肚子鼓出来的轮廓在晃。
  //
  // 坐标：和 2D 一样以“单位”为准（脚底为原点，y 向下），调用方已经做好了 ctx 的平移和缩放。

  const H3 = (DJ.hero3d = { on: true, ok: false, failed: false, lost: false });
  // 渲染范围（单位）：左右各留出手臂摊开 + 身体倾斜 + 投影的余量，上面留出肚子被拉长时头顶手臂的高度
  const BOX = { x: -58, y: -94, w: 116, h: 112 };
  // 省电：一个像素最多对应这么多“单位”的倒数（再高肉眼也看不出区别，片元着色器的开销却按像素数平方涨）
  const MAX_PPU = 2.2;
  // 省电：姿态最多每 1/60 秒重新光线步进一次；120Hz 屏幕上中间那一帧直接复用上一张图，
  // 只把这段时间里身体转过的角度（倾斜 / 旋转）用 2D 变换补上，所以转起来照样是 120Hz 顺滑的
  const MIN_GAP = 1 / 70;
  H3.BOX = BOX;

  let canvas = null;
  let gl = null;
  let prog = null;
  let U = null;
  let have = false; // 离屏画布里有没有一张能用的图
  let lastT = -1; // 上一次真正光线步进的时间
  let cw = 0;
  let ch = 0;

  const VS = 'attribute vec2 aPos; varying vec2 vUv; void main(){ vUv = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }';

  const FS = [
    '#ifdef GL_FRAGMENT_PRECISION_HIGH',
    'precision highp float;',
    '#else',
    'precision mediump float;',
    '#endif',
    'varying vec2 vUv;',
    'uniform vec4 uBox;', // 渲染范围 x, y, w, h（单位，y 向下）
    'uniform float uPx;', // 一个像素等于多少单位（抗锯齿用）
    'uniform vec3 uBody;', // 身体中心 by，横向缩放 sxB，纵向缩放 syB
    'uniform vec2 uBelly;', // 肚皮弹簧 b2，肚子弹簧 bd
    'uniform vec2 uTip[5];', // 五条手臂的尖：头顶、左手、右手、左脚、右脚
    'uniform vec2 uYaw;', // 身体转向（cos, sin）
    'uniform vec4 uXf;', // 世界 -> 模型的 2x2 矩阵（身体倾斜、紫黑飞翼时的旋转）
    'uniform vec2 uXo;', // 同上的平移
    'uniform vec2 uBound;', // 包围球：中心 y（模型空间，x=0,z=0）、半径；离得远的光线直接跳过
    'uniform vec3 uEyeL;',
    'uniform vec3 uEyeR;',
    'uniform vec3 uEyeP;', // 眼睛半径，瞳孔偏移 x, y（相对眼睛半径）
    'uniform vec4 uMouth;', // 嘴的位置 x, y，样式（0 微笑 1 张嘴 2 歪嘴），张嘴程度
    'uniform vec2 uMouthO;', // 张嘴时嘴朝瞄准方向的偏移
    'uniform float uDead;',
    '',
    'float sdEll(vec3 p, vec3 r) { float k0 = length(p / r); float k1 = length(p / (r * r)); return k0 * (k0 - 1.0) / k1; }',
    // 根粗尖细的圆锥（iq 的 round cone）
    'float sdCone(vec3 p, vec3 a, vec3 b, float r1, float r2) {',
    '  vec3 ba = b - a; float l2 = dot(ba, ba); float rr = r1 - r2; float a2 = l2 - rr * rr; float il2 = 1.0 / l2;',
    '  vec3 pa = p - a; float y = dot(pa, ba); float z = y - l2; vec3 xv = pa * l2 - ba * y; float x2 = dot(xv, xv);',
    '  float y2 = y * y * l2; float z2 = z * z * l2; float k = sign(rr) * rr * rr * x2;',
    '  if (sign(z) * a2 * z2 > k) return sqrt(x2 + z2) * il2 - r2;',
    '  if (sign(y) * a2 * y2 < k) return sqrt(x2 + y2) * il2 - r1;',
    '  return (sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;',
    '}',
    'float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }',
    'vec3 toModel(vec3 p) { vec2 r = mat2(uXf.xy, uXf.zw) * p.xy + uXo; float c = uYaw.x; float s = uYaw.y; return vec3(c * r.x - s * p.z, r.y, s * r.x + c * p.z); }',
    '',
    'vec2 map(vec3 p) {',
    '  vec3 q = toModel(p);',
    '  float by = uBody.x; float sx = uBody.y; float sy = uBody.z; float b2 = uBelly.x;',
    '  vec3 c = vec3(0.0, by, 0.0);',
    '  float zr = 13.0 * sqrt(sx);',
    '  float d = sdEll(q - c, vec3(13.2 * sx, 13.0 * sy, zr));',
    // 大肚子：挂在肚皮弹簧上，被压扁时往前、往两边鼓得更厉害，被拉长时收回去
    '  vec3 bc = vec3(0.0, by + 9.5 * sy + b2 * 4.5, zr * 0.5 + b2 * 2.2);',
    '  vec3 br = vec3(12.0 * sx * (1.0 + b2 * 0.18), 9.5 * max(sy, 0.85) * (1.0 - b2 * 0.08), 9.0 * (1.0 + 0.32 * b2));',
    '  d = smin(d, sdEll(q - bc, max(br, vec3(2.0))), 4.5);',
    // 手臂：比身体扁一些（z 方向压扁），所以像海星一样中间鼓、手臂薄
    '  vec3 qa = vec3(q.x, q.y, q.z * 1.35);',
    '  float a = sdCone(qa, c, vec3(uTip[0], 0.0), 12.5, 7.6);',
    '  a = min(a, sdCone(qa, c, vec3(uTip[1], 0.0), 12.5, 7.2));',
    '  a = min(a, sdCone(qa, c, vec3(uTip[2], 0.0), 12.5, 7.2));',
    '  a = min(a, sdCone(qa, c, vec3(uTip[3], 0.0), 12.5, 7.8));',
    '  a = min(a, sdCone(qa, c, vec3(uTip[4], 0.0), 12.5, 7.8));',
    '  d = smin(d, a * 0.8, 3.2);',
    '  float e = min(length(q - uEyeL), length(q - uEyeR)) - uEyeP.x;',
    '  return e < d ? vec2(e, 1.0) : vec2(d, 0.0);',
    '}',
    '',
    'vec3 calcNormal(vec3 p) {',
    '  const vec2 k = vec2(1.0, -1.0); const float h = 0.03;',
    '  return normalize(k.xyy * map(p + k.xyy * h).x + k.yyx * map(p + k.yyx * h).x + k.yxy * map(p + k.yxy * h).x + k.xxx * map(p + k.xxx * h).x);',
    '}',
    'float calcAO(vec3 p, vec3 n) {',
    '  float occ = 0.0; float sca = 1.0;',
    '  for (int i = 0; i < 3; i++) { float h = 0.8 + 3.2 * float(i); occ += (h - map(p + n * h).x) * sca; sca *= 0.6; }',
    '  return clamp(1.0 - 0.085 * occ, 0.0, 1.0);',
    '}',
    // 软边椭圆贴花：返回 0..1 的覆盖度
    'float blob(vec2 p, vec2 c, vec2 r) { vec2 d = (p - c) / r; return 1.0 - smoothstep(0.8, 1.05, length(d)); }',
    '',
    'void main() {',
    '  vec2 xy = vec2(uBox.x + vUv.x * uBox.z, uBox.y + (1.0 - vUv.y) * uBox.w);',
    // 整块渲染区域里大部分像素离身体很远（连投影都碰不到）：一次乘加就判掉，不做任何步进
    '  vec2 mq = mat2(uXf.xy, uXf.zw) * xy + uXo;',
    '  if (length(vec2(mq.x, mq.y - uBound.x)) > uBound.y + 14.0) { gl_FragColor = vec4(0.0); return; }',
    '  vec3 ro = vec3(xy, 30.0);',
    '  float t = 0.0; float minD = 1e9; float mat = 0.0; bool hit = false;',
    '  float eps = uPx * 0.25;',
    '  for (int i = 0; i < 56; i++) {',
    '    vec2 h = map(ro - vec3(0.0, 0.0, t));',
    '    minD = min(minD, h.x);',
    '    if (h.x < eps) { hit = true; mat = h.y; break; }',
    '    t += h.x * 0.95;',
    '    if (t > 60.0) break;',
    '  }',
    '  vec3 edge = vec3(0.13, 0.25, 0.09);', // 轮廓的深绿色
    '  if (!hit) {',
    // 没打中：轮廓边缘抗锯齿 + 落在纸面上的柔和投影（光从左上来，影子落在右下）
    '    float cov = 1.0 - smoothstep(0.0, uPx * 1.4, minD);',
    '    float sd = map(vec3(xy - vec2(4.5, 5.5), 0.0)).x;',
    '    float sh = (1.0 - smoothstep(-3.0, 5.0, sd)) * 0.2;',
    '    float a = cov + sh * (1.0 - cov);',
    '    vec3 col = edge * cov + vec3(0.2, 0.22, 0.14) * sh * (1.0 - cov);',
    '    gl_FragColor = vec4(col, a);',
    '    return;',
    '  }',
    '  vec3 p = ro - vec3(0.0, 0.0, t);',
    '  vec3 n = calcNormal(p);',
    '  vec3 q = toModel(p);',
    '  vec3 L = normalize(vec3(-0.5, -0.8, 0.7));', // y 向下：光从左上前方来
    '  vec3 V = vec3(0.0, 0.0, 1.0);',
    '  float ndl = dot(n, L);',
    '  float wrapL = max((ndl + 0.4) / 1.4, 0.0);',
    '  float ndv = max(dot(n, V), 0.0);',
    '  float fres = pow(1.0 - ndv, 3.0);',
    '  float ao = calcAO(p, n);',
    '  float sky = 0.5 - 0.5 * n.y;',
    '  vec3 Hh = normalize(L + V);',
    '  vec3 col;',
    '  vec3 ink = vec3(0.17, 0.17, 0.14);',
    '  if (mat < 0.5) {',
    '    float by = uBody.x; float sx = uBody.y; float sy = uBody.z; float b2 = uBelly.x;',
    '    vec3 alb = vec3(0.40, 0.76, 0.22);',
    '    float front = smoothstep(0.0, 3.0, q.z);',
    // 手臂上的小颗粒
    '    vec2 c = vec2(0.0, by);',
    '    float dots = 0.0;',
    '    for (int i = 0; i < 5; i++) {',
    '      vec2 tp = uTip[i];',
    '      dots = max(dots, blob(q.xy, mix(c, tp, 0.55), vec2(2.1)));',
    '      dots = max(dots, blob(q.xy, mix(c, tp, 0.76), vec2(1.7)));',
    '      dots = max(dots, blob(q.xy, mix(c, tp, 0.94), vec2(1.25)));',
    '    }',
    '    alb = mix(alb, vec3(0.86, 1.0, 0.66), dots * 0.9 * front);',
    // 浅色肚皮（和 2D 版一样挂在第二层弹簧上）
    '    float belly = blob(q.xy, vec2(0.0, by + 10.5 * sy + b2 * 5.0), vec2(9.5 * sx * (1.0 + b2 * 0.28), max(3.0, 7.2 * sy * (1.0 - b2 * 0.2))));',
    '    alb = mix(alb, vec3(0.93, 1.0, 0.78), belly * 0.85 * front);',
    // 腮红
    '    float blush = max(blob(q.xy, vec2(-13.0 * sx, by + 3.5 * sy), vec2(3.6, 2.4)), blob(q.xy, vec2(13.0 * sx, by + 3.5 * sy), vec2(3.6, 2.4)));',
    '    alb = mix(alb, vec3(1.0, 0.45, 0.52), blush * 0.5 * front);',
    // 嘴
    '    vec2 m = uMouth.xy;',
    '    float mouth = 0.0; vec3 mcol = ink;',
    '    if (uMouth.z < 0.5) {',
    '      vec2 d = q.xy - (m - vec2(0.0, 3.0));',
    '      float ang = atan(d.y, d.x);',
    '      float band = 1.0 - smoothstep(0.75, 1.05, abs(length(d) - 4.6));',
    '      mouth = band * step(0.2 * 3.14159, ang) * step(ang, 0.8 * 3.14159);',
    '    } else if (uMouth.z < 1.5) {',
    '      float k = uMouth.w;',
    '      vec2 mc = m + uMouthO + vec2(0.0, -1.0);',
    '      mouth = blob(q.xy, mc, vec2(4.4 * k + 1.2, 5.0 * k + 0.8));',
    '      float tongue = blob(q.xy, mc + vec2(0.0, 2.0), vec2(2.6, 2.0)) * step(0.5, k);',
    '      mcol = mix(ink, vec3(0.94, 0.44, 0.5), tongue);',
    '    } else {',
    '      float dx = q.x - m.x;',
    '      mouth = (1.0 - smoothstep(0.7, 1.05, abs(q.y - m.y - sin(dx * 0.9) * 1.6))) * (1.0 - smoothstep(3.6, 4.4, abs(dx)));',
    '    }',
    '    mouth *= front;',
    '    float spe = pow(max(dot(n, Hh), 0.0), 36.0);',
    '    col = alb * (0.26 + 0.22 * sky) * ao + alb * wrapL * 0.82 * mix(0.75, 1.0, ao);',
    '    col += vec3(0.10, 0.20, 0.03) * (1.0 - max(ndl, 0.0)) * 0.5;', // 像软糖一样透一点光
    '    col += vec3(1.0, 1.0, 0.9) * spe * 0.55 * (1.0 - mouth);',
    '    col += vec3(0.7, 1.0, 0.5) * fres * 0.3;',
    '    col = mix(col, mcol * (0.6 + 0.4 * wrapL), mouth);',
    '  } else {',
    // 眼睛：白色小球，瞳孔是球面上的一块黑色（所以转头时瞳孔是贴着球面转的）
    '    vec3 ec = length(q - uEyeL) < length(q - uEyeR) ? uEyeL : uEyeR;',
    '    vec3 e = q - ec;',
    '    vec3 en = normalize(e);',
    '    vec3 alb = vec3(0.98);',
    '    if (uDead > 0.5) {',
    '      float x1 = abs(e.x - e.y); float x2 = abs(e.x + e.y);',
    '      float xm = (1.0 - smoothstep(0.7, 1.1, min(x1, x2))) * (1.0 - smoothstep(3.2, 3.8, max(abs(e.x), abs(e.y))));',
    '      alb = mix(alb, ink, xm * step(0.0, e.z));',
    '    } else {',
    '      vec3 pd = normalize(vec3(uEyeP.y * 0.85, uEyeP.z * 0.85, 1.0));',
    '      float pup = smoothstep(0.855, 0.885, dot(en, pd));',
    '      alb = mix(alb, ink * 0.6, pup);',
    '    }',
    '    float spe = pow(max(dot(n, Hh), 0.0), 90.0);',
    '    col = alb * (0.34 + 0.18 * sky) * ao + alb * wrapL * 0.72 + vec3(1.0) * spe * 0.9;',
    '  }',
    // 贴着轮廓的边缘压暗一点，和纸面分得开
    '  col = mix(col, edge, (1.0 - smoothstep(0.0, 0.32, ndv)) * 0.55);',
    '  gl_FragColor = vec4(col, 1.0);',
    '}',
  ].join('\n');

  function compile(type, src) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(s);
      gl.deleteShader(s);
      throw new Error('3D 主角着色器编译失败：' + log);
    }
    return s;
  }

  function setup() {
    const vs = compile(gl.VERTEX_SHADER, VS);
    const fs = compile(gl.FRAGMENT_SHADER, FS);
    prog = gl.createProgram();
    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.bindAttribLocation(prog, 0, 'aPos');
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw new Error('3D 主角着色器链接失败：' + gl.getProgramInfoLog(prog));
    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    gl.enableVertexAttribArray(0);
    gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    gl.useProgram(prog);
    U = {};
    for (const n of ['uBox', 'uPx', 'uBody', 'uBelly', 'uTip', 'uYaw', 'uXf', 'uXo', 'uBound', 'uEyeL', 'uEyeR', 'uEyeP', 'uMouth', 'uMouthO', 'uDead']) U[n] = gl.getUniformLocation(prog, n);
    gl.disable(gl.DEPTH_TEST);
    gl.disable(gl.BLEND);
    have = false;
  }

  function init() {
    if (H3.ok) return true;
    if (H3.failed || H3.lost) return false;
    try {
      if (typeof document === 'undefined') throw new Error('no document');
      canvas = document.createElement('canvas');
      canvas.width = canvas.height = 2;
      const opts = { alpha: true, premultipliedAlpha: true, antialias: false, depth: false, stencil: false, preserveDrawingBuffer: true };
      gl = canvas.getContext('webgl', opts) || canvas.getContext('experimental-webgl', opts);
      if (!gl) throw new Error('WebGL 不可用');
      // 系统回收 GPU 资源时先退回 2D，恢复后重新编译
      canvas.addEventListener('webglcontextlost', function (e) {
        e.preventDefault();
        H3.ok = false;
        H3.lost = true;
      });
      canvas.addEventListener('webglcontextrestored', function () {
        H3.lost = false;
        try {
          setup();
          H3.ok = true;
        } catch (err) {
          H3.failed = true;
        }
      });
      setup();
      H3.ok = true;
      return true;
    } catch (e) {
      H3.failed = true;
      H3.error = String((e && e.message) || e);
      if (globalThis.console) console.warn('3D 主角不可用，退回 2D：' + H3.error);
      return false;
    }
  }

  H3.supported = function () {
    return init();
  };
  H3.active = function () {
    return H3.on && init();
  };

  // 眼睛表面高度：眼球中心埋在身体曲面下面一点，露出 2/3 个球
  function eyeZ(x, y, g, re) {
    const sx = g.sxB;
    const zr = 13 * Math.sqrt(sx);
    const u = x / (13.2 * sx);
    const w = (y - g.by) / (13 * g.syB);
    return zr * Math.sqrt(Math.max(0.05, 1 - u * u - w * w)) - re * 0.38;
  }

  const vals = new Float32Array(48);
  const prev = new Float32Array(48);
  // 上一次渲染时的“世界 -> 身体”变换（canvas 的 a,b,c,d,e,f），复用旧图时用来补上身体转过的角度
  const M0 = [1, 0, 0, 1, 0, 0];
  const ID = [1, 0, 0, 1, 0, 0];
  function inv(m) {
    const det = m[0] * m[3] - m[1] * m[2] || 1;
    const a = m[3] / det;
    const b = -m[1] / det;
    const c = -m[2] / det;
    const d = m[0] / det;
    return [a, b, c, d, -(a * m[4] + c * m[5]), -(b * m[4] + d * m[5])];
  }
  function mul(m, n) {
    return [
      m[0] * n[0] + m[2] * n[1],
      m[1] * n[0] + m[3] * n[1],
      m[0] * n[2] + m[2] * n[3],
      m[1] * n[2] + m[3] * n[3],
      m[0] * n[4] + m[2] * n[5] + m[4],
      m[1] * n[4] + m[3] * n[5] + m[5],
    ];
  }
  // 包围球：身体中心到最远的手臂尖 + 手臂尖的半径；z 方向肚子最多鼓到 ~24 个单位
  function boundR(g) {
    let r = 26;
    const tips = [g.top, g.sides[0], g.sides[1], g.legs[0], g.legs[1]];
    for (let i = 0; i < 5; i++) r = Math.max(r, Math.hypot(tips[i][0], tips[i][1] - g.by) + 9.5);
    // 大肚子往下鼓得最远的地方
    r = Math.max(r, 9.5 * g.syB + Math.abs(g.b2) * 4.5 + 10 * Math.max(g.syB, 0.85) + 2, 13.2 * g.sxB * 1.25 + 2);
    return r + 1.5;
  }

  // 在已经平移缩放到“单位”坐标的 ctx 里画 3D 主角；g 是 starGeom(dd)。
  // lean / body 是 canvas 变换矩阵：ctx 上已经叠了 lean（整个角色一起倾斜，道具配件也跟着歪），
  // body = lean ∘ 旋转，是身体真正的姿态 —— 交给着色器在模型空间里转，这样光照和地上的投影始终朝同一个方向
  H3.draw = function (ctx, dd, g, lean, body) {
    lean = lean || ID;
    body = body || lean;
    const v = DJ.view;
    const ppu = DJ.clamp((DJ.C.HERO || 1) * (v.scale || 1) * (v.dpr || 1), 1, MAX_PPU);
    const w = Math.ceil(BOX.w * ppu);
    const h = Math.ceil(BOX.h * ppu);
    if (w !== cw || h !== ch) {
      canvas.width = cw = w;
      canvas.height = ch = h;
      have = false;
    }
    const dead = dd.state === 'dead';
    const look = g.look;
    const yaw = dead ? 0 : DJ.clamp(look, -1, 1) * 0.32;
    const re = DJ.clamp(5.8 + (g.sxB - 1) * 1.2, 5, 7) * 1.12; // 3D 眼球比 2D 稍大一点（没有黑描边撑着）
    const ex = 7 * g.sxB;
    const ey = g.by - 5 * g.syB;
    let px = look * 1.6;
    let py = g.pose * 1.2;
    if (g.shootK > 0) {
      px = DJ.lerp(px, Math.cos(g.aim) * 2.2, g.shootK);
      py = DJ.lerp(py, Math.sin(g.aim) * 2.2, g.shootK);
    }
    const mode = dead ? 2 : g.shootK > 0.05 ? 1 : 0;
    let i = 0;
    const put = function () {
      for (let k = 0; k < arguments.length; k++) vals[i++] = arguments[k];
    };
    put(g.by, g.sxB, g.syB, g.b2, g.bd, yaw);
    put(g.top[0], g.top[1], g.sides[0][0], g.sides[0][1], g.sides[1][0], g.sides[1][1], g.legs[0][0], g.legs[0][1], g.legs[1][0], g.legs[1][1]);
    put(re, px, py, mode, g.shootK, g.aim, dead ? 1 : 0, w, h);
    put(body[0], body[1], body[2], body[3], body[4], body[5]);
    // 姿态完全没变（暂停、结算、菜单里两帧之间、屏幕边缘的第二份）就不必重画
    let same = have;
    for (let k = 0; k < i && same; k++) if (Math.abs(vals[k] - prev[k]) > 0.004) same = false;
    const now = DJ.clock || 0;
    // 姿态变了，但离上次渲染还不到 1/60 秒：先复用旧图（120Hz 屏幕上每两帧才真正渲染一次）
    const reuse = !same && have && now - lastT >= 0 && now - lastT < MIN_GAP;
    if (!same && !reuse) {
      prev.set(vals);
      lastT = now;
      have = true;
      for (let k = 0; k < 6; k++) M0[k] = body[k];
      const mi = inv(body);
      gl.viewport(0, 0, w, h);
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
      gl.uniform4f(U.uBox, BOX.x, BOX.y, BOX.w, BOX.h);
      gl.uniform1f(U.uPx, BOX.w / w);
      gl.uniform3f(U.uBody, g.by, g.sxB, g.syB);
      gl.uniform2f(U.uBelly, g.b2, g.bd);
      gl.uniform2fv(U.uTip, [g.top[0], g.top[1], g.sides[0][0], g.sides[0][1], g.sides[1][0], g.sides[1][1], g.legs[0][0], g.legs[0][1], g.legs[1][0], g.legs[1][1]]);
      gl.uniform2f(U.uYaw, Math.cos(yaw), Math.sin(yaw));
      gl.uniform4f(U.uXf, mi[0], mi[1], mi[2], mi[3]);
      gl.uniform2f(U.uXo, mi[4], mi[5]);
      gl.uniform2f(U.uBound, g.by, boundR(g));
      gl.uniform3f(U.uEyeL, -ex, ey, eyeZ(-ex, ey, g, re));
      gl.uniform3f(U.uEyeR, ex, ey, eyeZ(ex, ey, g, re));
      gl.uniform3f(U.uEyeP, re, px / re, py / re);
      gl.uniform4f(U.uMouth, 0.5, g.by + 7 * g.syB, mode, g.shootK);
      gl.uniform2f(U.uMouthO, Math.cos(g.aim) * 1.5, Math.sin(g.aim) * 1.5);
      gl.uniform1f(U.uDead, dead ? 1 : 0);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
    // 图是在“没有倾斜”的角色坐标里渲染的：先撤掉 ctx 上的 lean；复用旧图时再补上身体从那时到现在转过的角度
    ctx.save();
    ctx.transform.apply(ctx, inv(lean));
    if (reuse) ctx.transform.apply(ctx, mul(body, inv(M0)));
    ctx.drawImage(canvas, BOX.x, BOX.y, BOX.w, BOX.h);
    ctx.restore();
  };
})();
