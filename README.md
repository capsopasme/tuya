# 涂鸦蹦蹦

一路向上跳、躲开怪物、别掉下去的手绘风小游戏，纯 HTML5 Canvas 实现，玩法和操作参照经典的"向上跳"类游戏。主角"小海星"、怪物和全部美术都是原创的。

## 在手机上玩

**安卓 APK（推荐）**

1. 打 tag 触发构建：`git tag v1.0.0 && git push origin v1.0.0`
2. 等 Actions 里的 *Build Android APK* 跑完，在仓库 **Releases** 页下载 `tuya-1.0.0.apk` 安装。
3. 日常 push 到 main 也会构建，APK 在该次运行的 Artifacts 里。

APK 是个全屏 WebView 外壳，游戏离线运行，不需要任何权限；倾斜用的是原生加速度计，返回键会先暂停/返回菜单再退出。

签名：默认用 `android/fallback.keystore`（公开的非机密密钥，只是保证每次构建签名一致、能覆盖安装升级）。想用自己的密钥，在仓库 Secrets 里配 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 即可，换密钥后需要先卸载旧版一次。

**网页版（可选）**

仓库 Settings → Pages → Source 选 "GitHub Actions"，然后手动运行 *Deploy web version*。手机浏览器打开后"添加到主屏幕"即可全屏游玩（倾斜依赖浏览器开放运动传感器）。

## 操作

| | 手机 | 键盘 |
|---|---|---|
| 左右移动 | 左右倾斜手机（只有倾斜这一种方式；设置里可调灵敏度，并有实时倾斜指示条） | ← → 或 A D |
| 射击 | 点击屏幕：自动瞄准屏幕里最近的怪物（带提前量），没有怪物时朝点击方向发射 | 空格 / ↑ |
| 暂停 | 右上角暂停键，或返回键 | P / Esc |

角色走出屏幕一侧会从另一侧出现。踩到怪物头上可以踩死它；碰到怪物、黑洞、UFO 光束或掉出屏幕底部则游戏结束。

道具：弹簧、蹦床、螺旋桨帽、喷气背包。弹簧和蹦床只要身体中心落在它所在的那块平台范围内就会触发，不必精确踩在道具上（`DJ.C.ITEM_REACH` 可调）。小海星的肚子是两层阻尼弹簧，每次起跳都会 duang duang 狠狠晃几下（`BELLY_*` 可调）。平台：绿色（普通）、蓝色（会移动）、白色（踩一次消失）、棕色（一踩就碎）。

## 动画为什么顺

- 物理固定按 1/120 秒一步走，画面在两步之间插值，60/90/120Hz 的屏幕都匀速平滑，掉帧也不会一顿一顿。
- 镜头是缓动跟随（不是硬贴着角色）；角色的肚子、肚皮、手臂、脸朝向、空中拉伸都是弹簧或指数平滑，没有任何姿态是瞬间切换的。
- 落地有尘土、平台会被踩得下沉再弹回，弹簧/蹦床有冲击环，高速时有速度线；界面切换有淡入弹出，按钮依次出现、按下有弹簧回弹，分数数字滚动追上。
- 安卓外壳向系统申请 120Hz 刷新率。

## 开发

不需要构建，直接用任意静态服务器打开：

```sh
npm start            # http://localhost:8080
npm test             # 关卡可达性模拟 + 手机浏览器冒烟测试（需要 Playwright）
npm run snap         # 把各种状态截图到 tools/out/，肉眼检查画面
```

```
index.html, css/, js/     游戏本体
  config.js               常量、物理参数、随机数
  world.js                游戏逻辑（不依赖 DOM，可在 Node 里直接跑）
  render.js               画面：背景、平台、道具、主角、怪物
  ui.js                   菜单、设置、排行榜、结算
  input.js, main.js       输入（倾斜/触屏/键盘）与主循环
  audio.js                WebAudio 现场合成音效，没有音频文件
tools/                    sim.js 模拟、smoke.js 冒烟、snap.js 截图
android/                  安卓外壳（构建时把上面的游戏文件复制进 assets）
.github/workflows/        android.yml 构建 APK，pages.yml 部署网页版
```

想调手感，改 `js/config.js` 里的 `DJ.C`（重力、起跳速度、水平加速度等）；改名字需要同时改 `js/ui.js` 的标题、`index.html`、`manifest.webmanifest` 和 `android/app/src/main/res/values/strings.xml`。
