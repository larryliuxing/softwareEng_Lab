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
import android.os.Process
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.taplab.experiment.engine.ModeRunner
import com.taplab.experiment.engine.RootShell
import com.taplab.experiment.engine.ScreenGrabber
import com.taplab.experiment.engine.TapInjector
import com.taplab.experiment.engine.ThreadWaiter
import com.taplab.experiment.model.GrabSpec
import com.taplab.experiment.model.GrabSquare
import com.taplab.experiment.model.ScreenSpec
import com.taplab.experiment.store.ModeStore
import com.taplab.experiment.ui.BearBar
import com.taplab.experiment.ui.GrabOverlayView
import com.taplab.experiment.ui.RunState
import com.taplab.experiment.ui.dp
import com.taplab.experiment.vision.TemplateMatcher
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private lateinit var bar: BearBar
    private lateinit var barParams: WindowManager.LayoutParams
    private lateinit var shell: RootShell
    private lateinit var grabber: ScreenGrabber
    private lateinit var runner: ModeRunner
    private lateinit var store: ModeStore

    @Volatile
    private var state = RunState.IDLE

    @Volatile
    private var alive = true

    @Volatile
    private var grabbing = false

    @Volatile
    private var grabBusy = false

    private var expanded = false
    private var grabModeId: String? = null
    private var grabView: GrabOverlayView? = null
    private var worker: Thread? = null
    private var postedStatus = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        shell = RootShell()
        store = ModeStore(this)
        grabber = ScreenGrabber(shell, java.io.File(filesDir, "frame.raw"))
        val tapper = TapInjector(shell)
        runner = ModeRunner(
            capture = grabber,
            tap = { x, y -> tapper.tap(x, y) },
            waiter = ThreadWaiter,
            clock = { System.currentTimeMillis() },
            isActive = { state == RunState.RUNNING && !grabbing },
            onStatus = { text -> postStatus(text) },
            random = java.util.Random(),
            modeSource = { store.activeMode() },
            templates = { mode -> store.loadTemplates(mode) },
        )
        showBar()
        worker = Thread(this::loop, "taplab-engine").also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_GRAB) {
            val modeId = intent.getStringExtra(ModeEditorActivity.EXTRA_ID)
            if (!modeId.isNullOrBlank()) beginGrab(modeId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        alive = false
        state = RunState.IDLE
        grabbing = false
        worker?.interrupt()
        shell.cancel()
        if (this::grabber.isInitialized) grabber.release()
        removeGrab()
        if (this::bar.isInitialized) {
            runCatching { windowManager.removeView(bar) }
        }
        super.onDestroy()
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        while (alive && !Thread.currentThread().isInterrupted) {
            if (state != RunState.RUNNING || grabbing) {
                if (!sleepQuietly(80)) break
                continue
            }
            try {
                runner.runOnce()
            } catch (error: Exception) {
                postStatus(error.message ?: "出错")
                if (!sleepQuietly(400)) break
            }
        }
    }

    private fun showBar() {
        bar = BearBar(
            context = this,
            onBear = { toggleExpanded() },
            onStartPause = { onStartPause() },
            onSettings = { openLibrary() },
            onStop = {
                state = RunState.IDLE
                bar.render(state)
                postStatus("已停止")
            },
            onExit = { exit() },
        )
        bar.render(state)
        bar.setExpanded(false)
        barParams = overlayParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = -dp(24)
            y = dp(160)
        }
        windowManager.addView(bar, barParams)
        attachHeadGesture()
    }

    private fun toggleExpanded() {
        placeBar(peek = expanded)
    }

    private fun placeBar(peek: Boolean) {
        expanded = !peek
        bar.setExpanded(expanded)
        barParams.x = if (expanded) dp(8) else -dp(24)
        if (bar.isAttachedToWindow) {
            runCatching { windowManager.updateViewLayout(bar, barParams) }
        }
    }

    private fun attachHeadGesture() {
        val threshold = dp(8)
        var downRawY = 0f
        var originY = 0
        var dragging = false
        bar.bear.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawY = event.rawY
                    originY = barParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downRawY
                    if (!dragging && abs(dy) > threshold) dragging = true
                    if (dragging) {
                        val maxY = (resources.displayMetrics.heightPixels - bar.height).coerceAtLeast(0)
                        barParams.y = (originY + dy).roundToInt().coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(bar, barParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) bar.tapHead()
                    true
                }
                else -> false
            }
        }
    }

    private fun onStartPause() {
        when (state) {
            RunState.RUNNING -> {
                state = RunState.PAUSED
                bar.render(state)
                postStatus("已暂停")
            }
            RunState.PAUSED -> {
                state = RunState.RUNNING
                bar.render(state)
                postStatus("继续")
            }
            RunState.IDLE -> {
                postStatus("检查 root")
                Thread {
                    val rooted = shell.available()
                    main.post {
                        if (!alive || state != RunState.IDLE) return@post
                        if (!rooted) {
                            postStatus("没有 root，不能截屏或点击")
                            return@post
                        }
                        state = RunState.RUNNING
                        bar.render(state)
                        postStatus("开始")
                    }
                }.start()
            }
        }
    }

    private fun openLibrary() {
        placeBar(peek = true)
        startActivity(
            Intent(this, ModeLibraryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun beginGrab(modeId: String) {
        if (state == RunState.RUNNING) {
            state = RunState.PAUSED
            bar.render(state)
            postStatus("已暂停")
        }
        grabModeId = modeId
        grabbing = true
        placeBar(peek = true)
        bar.visibility = View.GONE
        if (grabView == null) showGrab()
    }

    private fun showGrab() {
        val overlay = GrabOverlayView(
            context = this,
            onConfirm = { square -> confirmGrab(square) },
            onCancel = { finishGrab(returnToEditor = true) },
        )
        grabView = overlay
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(overlay, params)
    }

    private fun confirmGrab(square: GrabSquare) {
        if (grabBusy) return
        val modeId = grabModeId ?: return
        grabBusy = true
        postStatus("正在抓取")
        Thread {
            try {
                val image = grabber.use { cropper ->
                    val window = ScreenSpec.toFrameWindow(square, cropper.screenWidth, cropper.screenHeight)
                    TemplateMatcher.fromFrame(cropper.crop(window))
                }
                if (image == null) {
                    main.post {
                        grabBusy = false
                        if (alive) postStatus("这块抓不到，换一块再试")
                    }
                    return@Thread
                }
                val mode = store.load(modeId)
                if (mode == null) {
                    main.post {
                        if (!alive) return@post
                        postStatus("这个行为模式不在了")
                        finishGrab(returnToEditor = false)
                    }
                    return@Thread
                }
                val grab = GrabSpec(
                    id = UUID.randomUUID().toString(),
                    name = "抓取${mode.grabs.size + 1}",
                    centerX = square.centerX,
                    centerY = square.centerY,
                    side = square.side,
                )
                store.addGrab(modeId, grab, image)
                main.post {
                    if (!alive) return@post
                    postStatus("已保存${grab.name}")
                    finishGrab(returnToEditor = true)
                }
            } catch (error: Exception) {
                main.post {
                    grabBusy = false
                    if (alive) postStatus(error.message ?: "抓取失败")
                }
            }
        }.start()
    }

    private fun finishGrab(returnToEditor: Boolean) {
        val modeId = grabModeId
        grabbing = false
        grabBusy = false
        removeGrab()
        if (this::bar.isInitialized) bar.visibility = View.VISIBLE
        if (returnToEditor && modeId != null) {
            startActivity(
                Intent(this, ModeEditorActivity::class.java)
                    .putExtra(ModeEditorActivity.EXTRA_ID, modeId)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    ),
            )
        }
    }

    private fun removeGrab() {
        val overlay = grabView ?: return
        runCatching { windowManager.removeView(overlay) }
        grabView = null
    }

    private fun exit() {
        alive = false
        state = RunState.IDLE
        grabbing = false
        worker?.interrupt()
        shell.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun postStatus(text: String) {
        if (text == postedStatus) return
        postedStatus = text
        main.post {
            if (alive && this::bar.isInitialized) bar.setStatus(text)
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, "点击实验", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("点击实验")
            .setContentText("小熊贴在屏幕边上")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
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

    companion object {
        const val ACTION_GRAB = "com.taplab.experiment.action.GRAB"
        private const val CHANNEL = "taplab"
        private const val NOTIFICATION_ID = 1001
    }
}
