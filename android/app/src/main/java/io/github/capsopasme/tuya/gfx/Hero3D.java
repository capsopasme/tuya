package io.github.capsopasme.tuya.gfx;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.util.Log;

import io.github.capsopasme.tuya.game.Hero;

/**
 * 3D 小海星：AGSL（安卓 13+ 的运行时着色器）做 SDF 光线步进——真的有体积、光照、高光、环境光遮蔽和投在纸面上的影子。
 * 着色器直接作为画笔画进同一帧（不再像旧版那样先画到离屏 WebGL 画布再拷贝回 2D 画布）。
 * 形状和 2D 版完全共用 {@link HeroArt.Geom}：中心椭球身体 + 会往外鼓的大肚子 + 五条根粗尖细的圆锥手臂，
 * 用 smooth-min 融成一整块软糖；眼睛是凸出来的小球，瞳孔、腮红、嘴和小颗粒画在曲面上。
 * 坐标：片元坐标就是“角色单位”（脚底为原点，y 向下），调用方已经平移缩放好画布。
 */
final class Hero3D {
    private static final String TAG = "tuya";
    // 渲染范围（角色单位）：左右留出手臂摊开 + 倾斜 + 投影的余量
    static final float BX = -58, BY = -94, BW = 116, BH = 112;

    private static final String SRC = String.join("\n",
            "uniform float uPx;",
            "uniform float3 uBody;",
            "uniform float2 uBelly;",
            "uniform float2 uT0;",
            "uniform float2 uT1;",
            "uniform float2 uT2;",
            "uniform float2 uT3;",
            "uniform float2 uT4;",
            "uniform float2 uYaw;",
            "uniform float4 uXf;",
            "uniform float2 uXo;",
            "uniform float2 uBound;",
            "uniform float3 uEyeL;",
            "uniform float3 uEyeR;",
            "uniform float3 uEyeP;",
            "uniform float4 uMouth;",
            "uniform float2 uMouthO;",
            "uniform float uDead;",
            "",
            "float sdEll(float3 p, float3 r) { float k0 = length(p / r); float k1 = length(p / (r * r)); return k0 * (k0 - 1.0) / k1; }",
            "float sdCone(float3 p, float3 a, float3 b, float r1, float r2) {",
            "  float3 ba = b - a; float l2 = dot(ba, ba); float rr = r1 - r2; float a2 = l2 - rr * rr; float il2 = 1.0 / l2;",
            "  float3 pa = p - a; float y = dot(pa, ba); float z = y - l2; float3 xv = pa * l2 - ba * y; float x2 = dot(xv, xv);",
            "  float y2 = y * y * l2; float z2 = z * z * l2; float k = sign(rr) * rr * rr * x2;",
            "  if (sign(z) * a2 * z2 > k) return sqrt(x2 + z2) * il2 - r2;",
            "  if (sign(y) * a2 * y2 < k) return sqrt(x2 + y2) * il2 - r1;",
            "  return (sqrt(x2 * a2 * il2) + y * rr) * il2 - r1;",
            "}",
            "float smin(float a, float b, float k) { float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0); return mix(b, a, h) - k * h * (1.0 - h); }",
            "float3 toModel(float3 p) {",
            "  float2 r = float2x2(uXf.xy, uXf.zw) * p.xy + uXo;",
            "  float c = uYaw.x; float s = uYaw.y;",
            "  return float3(c * r.x - s * p.z, r.y, s * r.x + c * p.z);",
            "}",
            "",
            "float2 map(float3 p) {",
            "  float3 q = toModel(p);",
            "  float by = uBody.x; float sx = uBody.y; float sy = uBody.z; float b2 = uBelly.x;",
            "  float3 c = float3(0.0, by, 0.0);",
            "  float zr = 13.0 * sqrt(sx);",
            "  float d = sdEll(q - c, float3(13.2 * sx, 13.0 * sy, zr));",
            "  float3 bc = float3(0.0, by + 9.5 * sy + b2 * 4.5, zr * 0.5 + b2 * 2.2);",
            "  float3 br = float3(12.0 * sx * (1.0 + b2 * 0.18), 9.5 * max(sy, 0.85) * (1.0 - b2 * 0.08), 9.0 * (1.0 + 0.32 * b2));",
            "  d = smin(d, sdEll(q - bc, max(br, float3(2.0))), 4.5);",
            "  float3 qa = float3(q.x, q.y, q.z * 1.35);",
            "  float a = sdCone(qa, c, float3(uT0, 0.0), 12.5, 7.6);",
            "  a = min(a, sdCone(qa, c, float3(uT1, 0.0), 12.5, 7.2));",
            "  a = min(a, sdCone(qa, c, float3(uT2, 0.0), 12.5, 7.2));",
            "  a = min(a, sdCone(qa, c, float3(uT3, 0.0), 12.5, 7.8));",
            "  a = min(a, sdCone(qa, c, float3(uT4, 0.0), 12.5, 7.8));",
            "  d = smin(d, a * 0.8, 3.2);",
            "  float e = min(length(q - uEyeL), length(q - uEyeR)) - uEyeP.x;",
            "  return e < d ? float2(e, 1.0) : float2(d, 0.0);",
            "}",
            "",
            "float3 calcNormal(float3 p) {",
            "  const float h = 0.03;",
            "  float3 k1 = float3(1.0, -1.0, -1.0); float3 k2 = float3(-1.0, -1.0, 1.0);",
            "  float3 k3 = float3(-1.0, 1.0, -1.0); float3 k4 = float3(1.0, 1.0, 1.0);",
            "  return normalize(k1 * map(p + k1 * h).x + k2 * map(p + k2 * h).x + k3 * map(p + k3 * h).x + k4 * map(p + k4 * h).x);",
            "}",
            "float calcAO(float3 p, float3 n) {",
            "  float occ = 0.0; float sca = 1.0;",
            "  for (int i = 0; i < 3; i++) { float h = 0.8 + 3.2 * float(i); occ += (h - map(p + n * h).x) * sca; sca *= 0.6; }",
            "  return clamp(1.0 - 0.085 * occ, 0.0, 1.0);",
            "}",
            "float blob(float2 p, float2 c, float2 r) { float2 d = (p - c) / r; return 1.0 - smoothstep(0.8, 1.05, length(d)); }",
            "float armDots(float2 q, float2 c, float2 tp) {",
            "  float d = blob(q, mix(c, tp, 0.55), float2(2.1));",
            "  d = max(d, blob(q, mix(c, tp, 0.76), float2(1.7)));",
            "  return max(d, blob(q, mix(c, tp, 0.94), float2(1.25)));",
            "}",
            "",
            "half4 main(float2 xy) {",
            "  float2 mq = float2x2(uXf.xy, uXf.zw) * xy + uXo;",
            "  if (length(float2(mq.x, mq.y - uBound.x)) > uBound.y + 14.0) { return half4(0.0); }",
            "  float3 ro = float3(xy, 30.0);",
            "  float t = 0.0; float minD = 1e9; float mid = 0.0; bool hit = false;",
            "  float eps = uPx * 0.25;",
            "  for (int i = 0; i < 56; i++) {",
            "    float2 h = map(ro - float3(0.0, 0.0, t));",
            "    minD = min(minD, h.x);",
            "    if (h.x < eps) { hit = true; mid = h.y; break; }",
            "    t += h.x * 0.95;",
            "    if (t > 60.0) { break; }",
            "  }",
            "  float3 edge = float3(0.13, 0.25, 0.09);",
            "  if (!hit) {",
            "    float cov = 1.0 - smoothstep(0.0, uPx * 1.4, minD);",
            "    float sd = map(float3(xy - float2(4.5, 5.5), 0.0)).x;",
            "    float sh = (1.0 - smoothstep(-3.0, 5.0, sd)) * 0.2;",
            "    float al = cov + sh * (1.0 - cov);",
            "    float3 cl = edge * cov + float3(0.2, 0.22, 0.14) * sh * (1.0 - cov);",
            "    return half4(float4(cl, al));",
            "  }",
            "  float3 p = ro - float3(0.0, 0.0, t);",
            "  float3 n = calcNormal(p);",
            "  float3 q = toModel(p);",
            "  float3 L = normalize(float3(-0.5, -0.8, 0.7));",
            "  float3 V = float3(0.0, 0.0, 1.0);",
            "  float ndl = dot(n, L);",
            "  float wrapL = max((ndl + 0.4) / 1.4, 0.0);",
            "  float ndv = max(dot(n, V), 0.0);",
            "  float fres = pow(1.0 - ndv, 3.0);",
            "  float ao = calcAO(p, n);",
            "  float sky = 0.5 - 0.5 * n.y;",
            "  float3 Hh = normalize(L + V);",
            "  float3 col;",
            "  float3 ink = float3(0.17, 0.17, 0.14);",
            "  if (mid < 0.5) {",
            "    float by = uBody.x; float sx = uBody.y; float sy = uBody.z; float b2 = uBelly.x;",
            "    float3 alb = float3(0.40, 0.76, 0.22);",
            "    float front = smoothstep(0.0, 3.0, q.z);",
            "    float2 c = float2(0.0, by);",
            "    float dots = armDots(q.xy, c, uT0);",
            "    dots = max(dots, armDots(q.xy, c, uT1));",
            "    dots = max(dots, armDots(q.xy, c, uT2));",
            "    dots = max(dots, armDots(q.xy, c, uT3));",
            "    dots = max(dots, armDots(q.xy, c, uT4));",
            "    alb = mix(alb, float3(0.86, 1.0, 0.66), dots * 0.9 * front);",
            "    float belly = blob(q.xy, float2(0.0, by + 10.5 * sy + b2 * 5.0), float2(9.5 * sx * (1.0 + b2 * 0.28), max(3.0, 7.2 * sy * (1.0 - b2 * 0.2))));",
            "    alb = mix(alb, float3(0.93, 1.0, 0.78), belly * 0.85 * front);",
            "    float blush = max(blob(q.xy, float2(-13.0 * sx, by + 3.5 * sy), float2(3.6, 2.4)), blob(q.xy, float2(13.0 * sx, by + 3.5 * sy), float2(3.6, 2.4)));",
            "    alb = mix(alb, float3(1.0, 0.45, 0.52), blush * 0.5 * front);",
            "    float2 m = uMouth.xy;",
            "    float mouth = 0.0; float3 mcol = ink;",
            "    if (uMouth.z < 0.5) {",
            "      float2 d = q.xy - (m - float2(0.0, 3.0));",
            "      float ang = atan(d.y, d.x);",
            "      float band = 1.0 - smoothstep(0.75, 1.05, abs(length(d) - 4.6));",
            "      mouth = band * step(0.2 * 3.14159, ang) * step(ang, 0.8 * 3.14159);",
            "    } else if (uMouth.z < 1.5) {",
            "      float k = uMouth.w;",
            "      float2 mc = m + uMouthO + float2(0.0, -1.0);",
            "      mouth = blob(q.xy, mc, float2(4.4 * k + 1.2, 5.0 * k + 0.8));",
            "      float tongue = blob(q.xy, mc + float2(0.0, 2.0), float2(2.6, 2.0)) * step(0.5, k);",
            "      mcol = mix(ink, float3(0.94, 0.44, 0.5), tongue);",
            "    } else {",
            "      float dx = q.x - m.x;",
            "      mouth = (1.0 - smoothstep(0.7, 1.05, abs(q.y - m.y - sin(dx * 0.9) * 1.6))) * (1.0 - smoothstep(3.6, 4.4, abs(dx)));",
            "    }",
            "    mouth *= front;",
            "    float spe = pow(max(dot(n, Hh), 0.0), 36.0);",
            "    col = alb * (0.26 + 0.22 * sky) * ao + alb * wrapL * 0.82 * mix(0.75, 1.0, ao);",
            "    col += float3(0.10, 0.20, 0.03) * (1.0 - max(ndl, 0.0)) * 0.5;",
            "    col += float3(1.0, 1.0, 0.9) * spe * 0.55 * (1.0 - mouth);",
            "    col += float3(0.7, 1.0, 0.5) * fres * 0.3;",
            "    col = mix(col, mcol * (0.6 + 0.4 * wrapL), mouth);",
            "  } else {",
            "    float3 ec = length(q - uEyeL) < length(q - uEyeR) ? uEyeL : uEyeR;",
            "    float3 e = q - ec;",
            "    float3 en = normalize(e);",
            "    float3 alb = float3(0.98);",
            "    if (uDead > 0.5) {",
            "      float x1 = abs(e.x - e.y); float x2 = abs(e.x + e.y);",
            "      float xm = (1.0 - smoothstep(0.7, 1.1, min(x1, x2))) * (1.0 - smoothstep(3.2, 3.8, max(abs(e.x), abs(e.y))));",
            "      alb = mix(alb, ink, xm * step(0.0, e.z));",
            "    } else {",
            "      float3 pd = normalize(float3(uEyeP.y * 0.85, uEyeP.z * 0.85, 1.0));",
            "      float pup = smoothstep(0.855, 0.885, dot(en, pd));",
            "      alb = mix(alb, ink * 0.6, pup);",
            "    }",
            "    float spe = pow(max(dot(n, Hh), 0.0), 90.0);",
            "    col = alb * (0.34 + 0.18 * sky) * ao + alb * wrapL * 0.72 + float3(1.0) * spe * 0.9;",
            "  }",
            "  col = mix(col, edge, (1.0 - smoothstep(0.0, 0.32, ndv)) * 0.55);",
            "  return half4(float4(col, 1.0));",
            "}");

    private final RuntimeShader shader;
    private final Paint paint = new Paint();
    private boolean failed;
    private final Matrix inv = new Matrix();
    private final float[] m9 = new float[9];

    private Hero3D(RuntimeShader s) {
        shader = s;
        paint.setShader(s);
    }

    /** 不支持（安卓 12 以下或着色器编译失败）时返回 null，调用方退回 2D */
    static Hero3D create() {
        if (Build.VERSION.SDK_INT < 33) return null;
        try {
            return new Hero3D(new RuntimeShader(SRC));
        } catch (RuntimeException e) {
            Log.w(TAG, "3D 主角着色器不可用，退回 2D", e);
            return null;
        }
    }

    boolean ok() {
        return !failed;
    }

    private static float eyeZ(float x, float y, HeroArt.Geom g, float re) {
        float sx = g.sxB;
        float zr = 13 * (float) Math.sqrt(sx);
        float u = x / (13.2f * sx);
        float w = (y - g.by) / (13 * g.syB);
        return zr * (float) Math.sqrt(Math.max(0.05f, 1 - u * u - w * w)) - re * 0.38f;
    }

    /** 包围球：身体中心到最远的手臂尖 + 手臂尖的半径 */
    private static float boundR(HeroArt.Geom g) {
        float r = 26;
        r = Math.max(r, (float) Math.hypot(g.top[0], g.top[1] - g.by) + 9.5f);
        for (int i = 0; i < 2; i++) {
            r = Math.max(r, (float) Math.hypot(g.sides[i][0], g.sides[i][1] - g.by) + 9.5f);
            r = Math.max(r, (float) Math.hypot(g.legs[i][0], g.legs[i][1] - g.by) + 9.5f);
        }
        r = Math.max(r, Math.max(9.5f * g.syB + Math.abs(g.b2) * 4.5f + 10 * Math.max(g.syB, 0.85f) + 2, 13.2f * g.sxB * 1.25f + 2));
        return r + 1.5f;
    }

    /**
     * body：身体真正的姿态（倾斜 ∘ 旋转），在着色器里转，所以光照和地上的投影始终朝同一个方向。
     * px：一个屏幕像素等于多少角色单位（抗锯齿用）。
     */
    void draw(Canvas c, Hero d, HeroArt.Geom g, Matrix body, float px) {
        try {
            boolean dead = d.state == Hero.DEAD;
            float look = g.look;
            float yaw = dead ? 0 : Pen.clamp(look, -1, 1) * 0.32f;
            float re = Pen.clamp(5.8f + (g.sxB - 1) * 1.2f, 5, 7) * 1.12f;
            float ex = 7 * g.sxB;
            float ey = g.by - 5 * g.syB;
            float ppx = look * 1.6f, ppy = g.pose * 1.2f;
            if (g.shootK > 0) {
                ppx = Pen.lerp(ppx, (float) Math.cos(g.aim) * 2.2f, g.shootK);
                ppy = Pen.lerp(ppy, (float) Math.sin(g.aim) * 2.2f, g.shootK);
            }
            float mode = dead ? 2 : g.shootK > 0.05f ? 1 : 0;
            body.invert(inv);
            inv.getValues(m9);
            // android Matrix: [a c e; b d f]（行主序 MSCALE_X, MSKEW_X, MTRANS_X, MSKEW_Y, MSCALE_Y, MTRANS_Y）
            // GLSL 的 mat2(col0, col1)：col0 = (a, b)，col1 = (c, d)
            RuntimeShader s = shader;
            s.setFloatUniform("uPx", px);
            s.setFloatUniform("uBody", g.by, g.sxB, g.syB);
            s.setFloatUniform("uBelly", g.b2, g.bd);
            s.setFloatUniform("uT0", g.top[0], g.top[1]);
            s.setFloatUniform("uT1", g.sides[0][0], g.sides[0][1]);
            s.setFloatUniform("uT2", g.sides[1][0], g.sides[1][1]);
            s.setFloatUniform("uT3", g.legs[0][0], g.legs[0][1]);
            s.setFloatUniform("uT4", g.legs[1][0], g.legs[1][1]);
            s.setFloatUniform("uYaw", (float) Math.cos(yaw), (float) Math.sin(yaw));
            s.setFloatUniform("uXf", m9[Matrix.MSCALE_X], m9[Matrix.MSKEW_Y], m9[Matrix.MSKEW_X], m9[Matrix.MSCALE_Y]);
            s.setFloatUniform("uXo", m9[Matrix.MTRANS_X], m9[Matrix.MTRANS_Y]);
            s.setFloatUniform("uBound", g.by, boundR(g));
            s.setFloatUniform("uEyeL", -ex, ey, eyeZ(-ex, ey, g, re));
            s.setFloatUniform("uEyeR", ex, ey, eyeZ(ex, ey, g, re));
            s.setFloatUniform("uEyeP", re, ppx / re, ppy / re);
            s.setFloatUniform("uMouth", 0.5f, g.by + 7 * g.syB, mode, g.shootK);
            s.setFloatUniform("uMouthO", (float) Math.cos(g.aim) * 1.5f, (float) Math.sin(g.aim) * 1.5f);
            s.setFloatUniform("uDead", dead ? 1 : 0);
            paint.setShader(s);
            c.drawRect(BX, BY, BX + BW, BY + BH, paint);
        } catch (RuntimeException e) {
            Log.w(TAG, "3D 主角绘制失败，退回 2D", e);
            failed = true;
        }
    }
}
