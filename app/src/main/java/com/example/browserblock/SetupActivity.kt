package com.example.browserblock

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * SetupActivity — the LAUNCHER entry point.
 *
 * Shown on first launch and whenever required permissions are missing.
 * Walks the user through granting every permission BrowserBlock needs,
 * then launches [MainActivity] when all required grants are confirmed.
 *
 * Onboarding order (mirrors Block's pf4 pattern from the APK analysis):
 *  1. POST_NOTIFICATIONS   (optional  — Android 13+ only)
 *  2. PACKAGE_USAGE_STATS  (required  — usage access)
 *  3. SYSTEM_ALERT_WINDOW  (required  — overlay)
 *  4. BIND_ACCESSIBILITY_SERVICE (required)
 *  5. Notification Listener        (required  — needed for notification suppression)
 *  6. Battery optimisation exempt  (required  — keeps service alive)
 *  7. Device Admin                 (optional  — hard-lock fallback)
 *
 * Logic to be implemented in a later step.
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
    }

    // ── Navigation ──────────────────────────────────────────────────────────

    /** Called once every required permission has been granted. */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
