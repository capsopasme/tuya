# 涂鸦蹦蹦

一路向上跳、躲开怪物、别掉下去的手绘风小游戏，玩法和操作参照经典的"向上跳"类游戏。主角"小海星"、怪物和全部美术都是原创的。

从 v2.0.0 起是**纯原生安卓应用**（以前是 WebView 套网页），画面和手感一样，但流畅得多、也更省电。

## 安装

1. 发版：把 `android/version.txt` 改成新版本号（如 `2.0.1`）push 到 main，这个版本还没有 Release 就会自动发一个；也可以照旧推 tag（`git tag v2.0.1 && git push origin v2.0.1`）或者在 GitHub 网页上新建 Release。
2. 等 Actions 里的 *Build Android APK* 跑完，APK 会自动挂到这个 Release 上，在 **Releases** 页下载 `tuya-2.0.0.apk` 这样的文件安装。
   某个版本没挂上 APK 时，可以在 Actions 里手动运行 *Build Android APK*，填上那个 tag 补发。
3. 日常 push 到 main 也会构建，APK 在该次运行的 Artifacts 里。

可以直接覆盖安装旧版（网页版），第一次打开时会自动把旧版的**最高分、排行榜和设置**搬过来。需要 Android 12 以上；3D 主角需要 Android 13 以上（12 上自动用 2D 主角）。不需要任何权限，完全离线。

签名：默认用 `android/fallback.keystore`（公开的非机密密钥，只是保证每次构建签名一致、能覆盖安装升级）。想用自己的密钥，在仓库 Secrets 里配 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 即可，换密钥后需要先卸载旧版一次。

## 操作

| | 手机 |
|---|---|
| 左右移动 | 左右倾斜手机（设置里可调灵敏度，并有实时倾斜指示条） |
| 射击 | 点击屏幕：自动瞄准屏幕里最近的怪物（带提前量），没有怪物时朝点击方向发射 |
| 暂停 | 右上角暂停键，或返回键 |

角色走出屏幕一侧会从另一侧出现。踩到怪物头上可以踩死它；碰到怪物、黑洞、UFO 光束或掉出屏幕底部则游戏结束。

道具：弹簧、蹦床、螺旋桨帽、喷气背包，以及终极道具**飞翼**（3500 分以后才会出现）：一对大翅膀带着小海星以喷气背包 1.6 倍的速度连飞 5 秒多，一路撞飞怪物、撞散黑洞、无视 UFO 光束，收翅膀时再用力扇一下，并附带 1.6 秒金色护盾。

**紫黑飞翼**（5000 分以后才会出现，更稀有）：速度是白色飞翼的 1.4 倍，拿到的一瞬间先把一圈紫色星屑“吸”进来再炸开；飞行中小海星整个旋转起来（3D 模式下是真的在光照里转），身后拖着紫色残影、星屑、符文环和绕身彗星，结束时带紫色护盾。

**慢落**：用完任何道具之后，冲到顶开始往下掉的那段会慢慢飘下来，落到普通平台上立刻恢复正常；上升段不受影响。

**身体倾斜和蓄力**：空中身体会朝移动方向歪一点；落到平台上腿被压扁的那一下身体朝你倾斜手机的方向压下去，起跳时被甩回来。纯视觉，不影响碰撞和手感。

**畅快模式**（设置里，默认关，下一局生效）：道具更多、出现得更早，怪物和黑洞更少更晚。

主角可在设置里切换 **2D / 3D**（默认 3D）：3D 小海星是实时光线步进渲染的软糖公仔，有光照、高光、阴影和投在纸面上的影子，大肚子落地时 duang duang 地晃。

## 为什么现在不卡了

旧版是 WebView 里的网页：每帧要跑 JS、在 2D 画布上重新描几百条矢量路径，3D 主角先在离屏 WebGL 画布上渲染、再拷贝回 2D 画布，最后还要经过 WebView 自己的合成器，任何一环抖一下就掉帧。新版全部换成安卓原生：

- **渲染**：系统的硬件加速 Canvas（Skia GPU）。UI 线程每帧只录一份显示列表，真正的绘制在系统 RenderThread 上交给 GPU。不会变形的东西（平台、静止道具、背景方格）按屏幕像素密度预先画成 GPU 常驻的硬件位图，每帧只是贴图。
- **3D 主角**：原来的 WebGL 着色器改写成 **AGSL**（Android 13 起的原生运行时着色器），直接作为画笔画进同一帧，没有离屏画布和拷贝。
- **主循环**：Choreographer 跟着屏幕的垂直同步走；物理固定 1/120 秒一步，画面在两步之间插值，60/90/120Hz 屏幕都匀速。游戏过程中不分配对象（特效对象池、画笔和路径全部复用），没有 GC 卡顿。
- **刷新率**：主动申请 120Hz 显示模式，并在 Android 15+ 上让画面明确“投票”120Hz（这个游戏全程靠倾斜、不碰屏幕，不投票会被系统压到 60Hz）。设置里可以切到 60Hz 省电档。
- **传感器**：加速度计 100Hz，在单独的线程上读，主循环每帧取最新值，不再经过 JS 桥。
- **音效**：启动时离线合成（配方和旧版一样，没有音频文件），交给 SoundPool 原生混音。
- **性能信息**（设置里打开）：显示屏幕刷新率、每秒画了几帧、最近 2 秒的卡顿次数、UI 线程耗时和渲染线程耗时，觉得卡的时候看一眼就知道是哪儿的问题。

## 开发

```
android/app/src/main/java/io/github/capsopasme/tuya/
  game/     游戏逻辑（纯 Java，不依赖安卓）：常量 C、关卡生成和物理 Game、主角状态 Hero、特效 Fx、倾斜滤波 Tilt
            Sim 是无头模拟测试（关卡可达、机器人能爬高、各道具机制），Bot 是测试用的自动驾驶
  gfx/      画面：Scene（整个场景）、Art（平台/道具/怪物）、HeroArt（主角 2D）、Hero3D（AGSL 3D 主角）、Sprites（贴图缓存）
  app/      MainActivity（窗口、传感器、刷新率）、GameView（主循环、触屏）、Ui（菜单/设置/排行榜/结算）、
            Synth + Sfx（音效合成和播放）、Store（存档）、Migrate（从旧版迁移成绩）
ci/shots.sh 模拟器里的自动截图脚本
```

```sh
# 逻辑模拟测试（只需要 JDK）
javac -d /tmp/sim android/app/src/main/java/io/github/capsopasme/tuya/game/*.java
java -cp /tmp/sim io.github.capsopasme.tuya.game.Sim

# 打包
cd android && ./gradlew assembleRelease
```

调试场景：`adb shell am start -n io.github.capsopasme.tuya/.app.MainActivity --es scene wings` 会直接摆出某个状态（`play`、`spring`、`tramp`、`prop`、`jet`、`wings`、`dark`、`glide`、`shield`、`monsters`、`dead`、`pause`、`over`，前面加 `2d-` 用 2D 主角；`live` 是机器人一直自动玩）。*Emulator check* 工作流会在模拟器里把这些场景全部截图，推到 `ci-shots` 分支。

想调手感，改 `game/C.java`（重力、起跳速度、水平加速度等）；改名字改 `android/app/src/main/res/values/strings.xml` 和 `app/Ui.java` 里的标题。
