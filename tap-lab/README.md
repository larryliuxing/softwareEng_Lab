# 点击实验

Android 10 上的点击实验。悬浮条负责开始、暂停、设置、停止和退出。设置会再打开一块浮层。里面有一号功能、二号功能，每个功能有一号分支和二号分支。

勾选的分支只观察自己的矩形区域。区域里的颜色达到设定比例后，按反应时间分布等一段时间，再在瞄准点附近点一下。落点是瞄准点加上横向、纵向两个独立的高斯噪声。同一种子可以得到同一串延迟和落点。

截屏用 root 的 `screencap`，点击用 root 的 `input tap`。四个分支默认都是关的。

## 本地打开和修改

把这个仓库的分支拉下来，用 Android Studio 打开 `tap-lab` 目录。Studio 会生成 `local.properties`。命令行编译时自己写一行：

```
sdk.dir=/你的/Android/Sdk
```

```
./gradlew :core:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

常用修改位置：

| 想改什么 | 文件 |
| --- | --- |
| 四个分支的默认区域、颜色、反应时、抖动 | `core/src/main/kotlin/com/taplab/experiment/model/Models.kt` 里的 `defaultBranch` |
| 颜色算不算命中 | `core/src/main/kotlin/com/taplab/experiment/vision/ColorMatcher.kt` |
| 反应时间分布 | `core/src/main/kotlin/com/taplab/experiment/human/ReactionTime.kt` |
| 落点抖动 | `core/src/main/kotlin/com/taplab/experiment/human/AimPoint.kt` |
| 先点哪个分支 | `core/src/main/kotlin/com/taplab/experiment/engine/TrialPlanner.kt` |
| 悬浮条按钮 | `app/src/main/java/com/taplab/experiment/ui/ControlBarView.kt` |
| 设置浮层里的输入项 | `app/src/main/java/com/taplab/experiment/ui/SettingsPanelView.kt` |

浮层里改完要点保存。保存后的下一轮扫描就用新参数。随机种子在停止之后再点开始时重新从头走。

## 记录

每次真正点下去会追加一行 CSV。路径在主界面和设置浮层里都能看到，一般是：

```
/sdcard/Android/data/com.taplab.experiment/files/trials.csv
```

列：刺激时刻、功能、分支、分布、计划延迟、实际等待、瞄准点、实际落点。

## 这台机器

目标是 Android 10（API 29）。需要系统里已经有可用的 `su`。没有 root 时悬浮窗还能打开，开始后会停在「没有 root」。
