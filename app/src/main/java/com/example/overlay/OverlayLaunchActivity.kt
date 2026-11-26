package com.example.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.example.MainActivity

class OverlayLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wantsCrosshair = intent?.getBooleanExtra(EXTRA_START_CROSSHAIR, false) == true

        if (Settings.canDrawOverlays(this)) {
            if (wantsCrosshair) {
                JarvisOverlayService.startCrosshair(this)
            } else {
                JarvisOverlayService.showOverlay(this)
            }
        } else {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NEEDS_OVERLAY_PERMISSION, true)
            }
            startActivity(mainIntent)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_NEEDS_OVERLAY_PERMISSION = "extra_needs_overlay_permission"
        const val EXTRA_START_CROSSHAIR = "extra_start_crosshair"
    }
}
