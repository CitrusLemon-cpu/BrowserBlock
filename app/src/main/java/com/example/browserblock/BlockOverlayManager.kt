package com.example.browserblock

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.button.MaterialButton

/**
 * BlockOverlayManager — draws a TYPE_APPLICATION_OVERLAY hard-block window.
 *
 * Used exclusively by [ForegroundPollingService] when
 * [BlockerAccessibilityService.instance] is null (accessibility is off).
 *
 * When accessibility IS enabled, [BlockerAccessibilityService] handles blocking
 * via [BlockActivity] (the full Activity path) — this class is not involved.
 *
 * The overlay:
 *  - Fills the entire screen (MATCH_PARENT × MATCH_PARENT)
 *  - Uses FLAG_NOT_FOCUSABLE so the system back gesture still works but
 *    the overlay intercepts all touch events (browser is unreachable)
 *  - Has no dismiss/back button — the only exit is re-enabling accessibility
 *  - Is idempotent: calling [show] when already visible is a no-op
 *
 * Requires SYSTEM_ALERT_WINDOW permission (Settings.canDrawOverlays).
 */
class BlockOverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)

    @Volatile
    private var overlayView: View? = null

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Show the hard-block overlay. No-op if already visible or if
     * SYSTEM_ALERT_WINDOW permission is not granted.
     */
    fun show() {
        if (overlayView != null) return
        if (!Settings.canDrawOverlays(context)) return

        val themedContext = ContextThemeWrapper(context, R.style.Theme_BrowserBlock)
        val view = LayoutInflater.from(themedContext)
            .inflate(R.layout.overlay_hard_block, null)

        view.findViewById<MaterialButton>(R.id.btn_re_enable_accessibility)
            .setOnClickListener {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            // FLAG_NOT_FOCUSABLE: system key events (e.g. back gesture) pass through
            // FLAG_LAYOUT_IN_SCREEN: view fills the entire display including notch
            // Intentionally NOT FLAG_NOT_TOUCH_MODAL: touches on the overlay are
            // consumed here — the browser underneath is unreachable.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
            overlayView = view
        } catch (e: Exception) {
            // WindowManager.addView can throw if SYSTEM_ALERT_WINDOW was revoked
            // between the canDrawOverlays check and addView.
            android.util.Log.e("BlockOverlayManager", "Failed to add overlay view", e)
        }
    }

    /**
     * Dismiss and remove the overlay. No-op if not currently visible.
     * Safe to call from any thread.
     */
    fun dismiss() {
        overlayView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // View may have already been removed (e.g., process restart)
            }
            overlayView = null
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
}
