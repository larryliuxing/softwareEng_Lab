package com.taplab.experiment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.app.Activity
import android.widget.Button
import android.widget.TextView
import com.taplab.experiment.engine.RootShell
import com.taplab.experiment.store.TrialLog

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.log_path).text = "试验记录\n${TrialLog(this).path()}"
        findViewById<Button>(R.id.grant_overlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            startActivity(intent)
        }
        findViewById<Button>(R.id.grant_overlay_root).setOnClickListener {
            val status = findViewById<TextView>(R.id.root_status)
            status.text = "正在用 root 授予悬浮窗"
            Thread {
                val granted = RootShell().grantOverlay(packageName)
                runOnUiThread {
                    status.text = if (granted) "已尝试授予，回到这里后会再检查" else "root 授予失败"
                    refreshOverlay()
                }
            }.start()
        }
        findViewById<Button>(R.id.open_overlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                refreshOverlay()
                return@setOnClickListener
            }
            startForegroundService(Intent(this, OverlayService::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlay()
        val rootStatus = findViewById<TextView>(R.id.root_status)
        rootStatus.text = "root：检查中"
        Thread {
            val rooted = RootShell().available()
            runOnUiThread {
                rootStatus.text = if (rooted) "root：可用" else "root：不可用。截屏和点击需要 su"
            }
        }.start()
    }

    private fun refreshOverlay() {
        val allowed = Settings.canDrawOverlays(this)
        findViewById<TextView>(R.id.overlay_status).text = if (allowed) "悬浮窗：已允许" else "悬浮窗：还没允许"
        findViewById<Button>(R.id.open_overlay).isEnabled = allowed
    }
}
