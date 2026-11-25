#!/usr/bin/env bash
# 在 CI 的安卓模拟器里跑一遍：旧版 -> 新版的成绩迁移、各个场景截图、自动演示的帧统计、logcat。
# 结果都写进 out/，由工作流推到 ci-shots 分支。
set -x
PKG=io.github.capsopasme.tuya
ACT=$PKG/.app.MainActivity
OUT=out
mkdir -p $OUT
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
# 不要弹“全屏查看”的提示（会挡住点击）
adb shell settings put secure immersive_mode_confirmations confirmed
# 模拟器是纯软件 GPU，1080x2400 下一帧要画好几百毫秒；降到一半分辨率（比例不变，逻辑布局完全一样）
adb shell wm size 540x1200
adb shell wm density 210
# 改分辨率后桌面会卡一下弹“无响应”，挡住画面：关掉错误弹窗，重启桌面
adb shell settings put global hide_error_dialogs 1
adb shell am force-stop com.google.android.apps.nexuslauncher
sleep 10
adb shell input keyevent KEYCODE_HOME
sleep 5
adb shell wm size >> $OUT/device.txt
adb shell wm density >> $OUT/device.txt
adb shell getprop ro.build.version.sdk >> $OUT/device.txt

shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

# ---- 1) 迁移测试：先装旧版（WebView 版）玩一局，再覆盖安装新版 ----
if [ -f old.apk ]; then
  adb install -r old.apk
  adb shell am start -n $PKG/.MainActivity
  sleep 30
  shot old-menu
  # 开始游戏按钮（逻辑坐标 (160, 0.34H+24)，1080x2400 屏上约 (540, 897)）
  adb shell input tap 270 448
  sleep 8
  adb shell input tap 270 448
  sleep 8
  shot old-play
  # 把手机“歪”过去，让小海星掉下去结束这一局
  adb emu sensor set acceleration -5:8.4:0
  sleep 40
  shot old-over
  adb emu sensor set acceleration 0:9.81:0
  adb shell am force-stop $PKG
fi
adb install -r app.apk
adb logcat -c
adb shell am start -n $ACT --es scene scores
sleep 15
shot migrated-scores
adb logcat -d | grep -E "tuya|AndroidRuntime|FATAL" > $OUT/logcat-migrate.txt

# ---- 2) 真实点击流程：菜单 -> 开始 -> 返回键暂停 ----
adb shell am force-stop $PKG
adb shell am start -n $ACT
sleep 10
shot menu
adb shell input tap 270 448
sleep 5
shot touch-play
adb shell input keyevent KEYCODE_BACK
sleep 4
shot touch-pause
adb shell input keyevent KEYCODE_BACK
sleep 1

# ---- 3) 各个场景（状态冻结在某一帧，方便检查画面） ----
for s in hud items dragon dragon-end life luck revive settings scores play spring tramp prop jet wings dark glide shield monsters dead pause over 2d-play 2d-wings 2d-dark 2d-jet dragon2 2d-dragon2; do
  adb shell am force-stop $PKG
  adb shell am start -n $ACT --es scene $s
  sleep 9
  shot $s
done

# ---- 3b) 模拟挖孔屏：顶栏挤满时各元素不能压到挖孔、也不能互相交叠 ----
adb shell cmd overlay enable com.android.internal.display.cutout.emulation.hole || true
sleep 3
adb shell dumpsys window displays | grep -i cutout | head -3 >> $OUT/device.txt
for s in hud luck; do
  adb shell am force-stop $PKG
  adb shell am start -n $ACT --es scene $s
  sleep 9
  shot cutout-$s
done
adb shell cmd overlay disable com.android.internal.display.cutout.emulation.hole || true
sleep 2

# ---- 4) 自动演示：机器人一直玩，统计帧 ----
adb shell am force-stop $PKG
adb shell dumpsys gfxinfo $PKG reset > /dev/null
adb shell am start -n $ACT --es scene live
sleep 6
shot live-1
sleep 6
shot live-2
sleep 8
shot live-3
adb shell dumpsys gfxinfo $PKG > $OUT/gfxinfo.txt
adb shell dumpsys SurfaceFlinger --latency > /dev/null 2>&1
adb logcat -d | grep -E "tuya|AndroidRuntime|FATAL|RuntimeShader|SkSL|Choreographer" > $OUT/logcat.txt
adb shell pidof $PKG > $OUT/pid.txt
exit 0
