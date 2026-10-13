# 点击实验

Android 10 上的点击实验。屏幕按 **720×1080** 记坐标。编辑和执行都在手机上完成：抓取要看当时的画面，坐标也是这台屏幕上的点。导出只是把已经做好的行为模式写成一个文件夹，可以用 `adb pull` 拷走。没有单独的 Windows 编辑器。

平时悬浮窗缩在屏幕右边缘，只露出一只半透明的小熊。点小熊才出现四个按钮：开始/暂停、设置、停止、退出。上下拖小熊，它还是贴着右边缘。

设置打开全屏、不透明的行为模式列表。模式可以自己起名，能新建、读取、删除和导出，都保存在这台设备上。读取之后，回到小熊点开始，程序就循环执行这个模式。

一个模式里有两类东西：

- **抓取**：点「新建抓取」后，编辑页退到后台，全屏浮层留在最上面。在要看的画面上点一个中心，再拖出一个正方形。确定后把这块画面、中心和边长存进模式。
- **循环**：按顺序放点击、延时、对比。点击可以带横向和纵向的高斯抖动，默认是 0，也就是正好点在那个坐标。延时是固定的毫秒数。对比会拿出某一次抓取的图片，去当前屏幕上同一块正方形比较；匹配和不匹配各自再走一段步骤。

截屏用 root 的 `screencap`，点击用 root 的 `input tap`。对比只读那一块正方形，不把整屏反复拷进内存。

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
| 720×1080、正方形边长范围 | `core/src/main/kotlin/com/taplab/experiment/model/ScreenSpec.kt` |
| 行为模式、点击、延时、对比 | `core/src/main/kotlin/com/taplab/experiment/model/Behavior.kt` |
| 图片是否算匹配 | `core/src/main/kotlin/com/taplab/experiment/vision/TemplateMatcher.kt` |
| 循环怎么往下走 | `core/src/main/kotlin/com/taplab/experiment/engine/ModeRunner.kt` |
| 落点抖动 | `core/src/main/kotlin/com/taplab/experiment/human/AimPoint.kt` |
| 小熊和四个按钮 | `app/src/main/java/com/taplab/experiment/ui/BearBar.kt` |
| 模式列表、编辑页 | `ModeLibraryActivity.kt`、`ModeEditorActivity.kt` |

## 保存在哪里

行为模式在应用私有目录 `files/modes/`。抓到的图片在 `files/modes/templates/`。导出在外部目录，主界面上能看到完整路径，一般是：

```
/sdcard/Android/data/com.taplab.experiment/files/export/<名称-id前8位>/
```

里面有 `mode.json` 和每张抓取的 `.bin`。

## 这台机器

目标是 Android 10（API 29），大约 8 核、4GB 内存、45GB 存储。需要系统里已经有可用的 `su`。没有 root 时悬浮窗还能打开，开始后会停在「没有 root」。

循环两轮之间至少隔 80 毫秒。截屏文件只有一个，写在应用私有目录里，退出时删掉。一次读入内存的原始行超过约 16MB 会停下来。
