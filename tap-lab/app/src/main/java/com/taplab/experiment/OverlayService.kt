package com.taplab.experiment

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.os.Process
import com.taplab.experiment.engine.ExperimentEngine
import com.taplab.experiment.engine.RootShell
import com.taplab.experiment.engine.ScreenGrabber
import com.taplab.experiment.engine.TapInjector
import com.taplab.experiment.engine.ThreadWaiter
import com.taplab.experiment.model.BranchId
import com.taplab.experiment.model.ExperimentConfig
import com.taplab.experiment.store.ConfigStore
import com.taplab.experiment.store.TrialLog
import com.taplab.experiment.ui.ControlBarView
import com.taplab.experiment.ui.RunState
import com.taplab.experiment.ui.SettingsPanelView
import com.taplab.experiment.ui.dp
import com.taplab.experiment.ui.screenHeight
import com.taplab.experiment.ui.screenWidth
import com.taplab.experiment.vision.ColorMatcher
import com.taplab.experiment.vision.ScanWindow
import java.util.Locale
import kotlin.math.roundToInt

class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var control: ControlBarView
    private lateinit var controlParams: WindowManager.LayoutParams
    private lateinit var shell: RootShell
    private lateinit var grabber: ScreenGrabber
    private lateinit var engine: ExperimentEngine
    private lateinit var trialLog: TrialLog
    private var settings: SettingsPanelView? = null
    private var settingsParams: WindowManager.LayoutParams? = null

    @Volatile
    private var state = RunState.IDLE

    @Volatile
    private var config = ExperimentConfig.default()

    @Volatile
    private var alive = true

    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        shell = RootShell()
        grabber = ScreenGrabber(shell, java.io.File(filesDir, "frame.raw"))
        val store = ConfigStore(this)
        trialLog = TrialLog(this)
        config = store.load()
        val tapper = TapInjector(shell)
        engine = ExperimentEngine(
            capture = grabber,
            tap = { x, y -> tapper.tap(x, y) },
            waiter = ThreadWaiter,
            clock = { System.currentTimeMillis() },
            configSource = { config },
            isActive = { state == RunState.RUNNING },
            onTrial = { record -> trialLog.append(record) },
            onStatus = { text -> postStatus(text) },
        )
        showControl(store)
        worker = Thread(this::loop, "taplab-engine").also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        alive = false
        state = RunState.IDLE
        worker?.interrupt()
        shell.cancel()
        if (this::grabber.isInitialized) grabber.release()
        if (this::trialLog.isInitialized) trialLog.close()
        removeSettings()
        if (this::control.isInitialized) {
            runCatching { windowManager.removeView(control) }
        }
        super.onDestroy()
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        while (alive && !Thread.currentThread().isInterrupted) {
            if (state != RunState.RUNNING) {
                if (!sleepQuietly(80)) break
                continue
            }
            try {
                engine.step()
            } catch (error: Exception) {
                postStatus(error.message ?: "出错")
                if (!sleepQuietly(400)) break
            }
        }
    }

    private fun showControl(store: ConfigStore) {
        control = ControlBarView(
            this,
            onStartPause = { onStartPause() },
            onSettings = { toggleSettings(store) },
            onStop = {
                state = RunState.IDLE
                control.render(state)
                postStatus("已停止")
            },
            onExit = { exit() },
        )
        control.render(state)
        controlParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, focusable = false)
        controlParams.x = dp(12)
        controlParams.y = dp(72)
        attachDrag(control.dragHandle, control, controlParams)
        windowManager.addView(control, controlParams)
    }

    private fun onStartPause() {
        when (state) {
            RunState.RUNNING -> {
                state = RunState.PAUSED
                control.render(state)
                postStatus("已暂停")
            }
            RunState.PAUSED -> {
                state = RunState.RUNNING
                control.render(state)
                postStatus("继续")
            }
            RunState.IDLE -> {
                postStatus("检查 root")
                Thread {
                    val rooted = shell.available()
                    main.post {
                        if (state != RunState.IDLE) return@post
                        if (!rooted) {
                            postStatus("没有 root，不能截屏或点击")
                            return@post
                        }
                        engine.reseedFromConfig()
                        state = RunState.RUNNING
                        control.render(state)
                        postStatus("开始")
                    }
                }.start()
            }
        }
    }

    private fun toggleSettings(store: ConfigStore) {
        if (settings != null) {
            removeSettings()
            return
        }
        val panelHeight = (screenHeight() * 0.72f).roundToInt()
        val panel = SettingsPanelView(
            context = this,
            config = config,
            logPath = trialLog.path(),
            panelHeightPx = panelHeight,
            onSample = { id, x, y -> sampleColor(id, x, y) },
            onSave = { next ->
                config = next
                store.save(next)
            },
            onClose = { removeSettings() },
        )
        settings = panel
        val width = minOf(dp(360), screenWidth() - dp(16))
        settingsParams = overlayParams(width, panelHeight, focusable = true).also {
            it.x = dp(8)
            it.y = dp(128)
        }
        val params = settingsParams ?: return
        attachDrag(panel.dragHandle, panel, params)
        windowManager.addView(panel, params)
        Thread {
            try {
                val size = grabber.use { cropper -> cropper.screenWidth to cropper.screenHeight }
                main.post { panel.setScreenInfo("当前截屏 ${size.first}×${size.second}") }
            } catch (error: Exception) {
                main.post { panel.setScreenInfo(error.message ?: "还没截到屏幕") }
            }
        }.start()
    }

    private fun sampleColor(id: BranchId, x: Int, y: Int) {
        val panel = settings ?: return
        Thread {
            try {
                val sample = grabber.use { cropper ->
                    val frame = cropper.crop(ScanWindow(x, y, 1, 1))
                    Triple(cropper.screenWidth, cropper.screenHeight, ColorMatcher.colorAt(frame, x, y))
                }
                val color = sample.third
                main.post {
                    panel.setScreenInfo("当前截屏 ${sample.first}×${sample.second}")
                    val editor = panel.editor(id) ?: return@post
                    if (color == null) {
                        editor.setHint("瞄准点不在这张截屏里")
                    } else {
                        editor.setColor(color)
                        editor.setHint("已采样 ${String.format(Locale.US, "%06X", color)}")
                    }
                }
            } catch (error: Exception) {
                main.post { panel.editor(id)?.setHint(error.message ?: "采样失败") }
            }
        }.start()
    }

    private fun removeSettings() {
        val panel = settings ?: return
        runCatching { windowManager.removeView(panel) }
        settings = null
        settingsParams = null
    }

    private fun exit() {
        alive = false
        state = RunState.IDLE
        worker?.interrupt()
        shell.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private var postedStatus = ""

    private fun postStatus(text: String) {
        if (text == postedStatus) return
        postedStatus = text
        main.post {
            if (this::control.isInitialized) control.setStatus(text)
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "点击实验", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("点击实验")
            .setContentText("悬浮窗开着")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun overlayParams(width: Int, height: Int, focusable: Boolean): WindowManager.LayoutParams {
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            if (focusable) {
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            }
        }
    }

    private fun attachDrag(handle: View, root: View, params: WindowManager.LayoutParams) {
        var originX = 0
        var originY = 0
        var downX = 0f
        var downY = 0f
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    originX = params.x
                    originY = params.y
                    downX = event.rawX
                    downY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = originX + (event.rawX - downX).roundToInt()
                    params.y = originY + (event.rawY - downY).roundToInt()
                    runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }
                else -> false
            }
        }
    }

    private fun sleepQuietly(millis: Long): Boolean {
        return try {
            Thread.sleep(millis)
            true
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private companion object {
        const val CHANNEL = "taplab"
        const val NOTIFICATION_ID = 1001
    }
}
