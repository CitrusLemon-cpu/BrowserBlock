package com.example.browserblock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * BlockActivity — the "you've been blocked" screen.
 *
 * Shown by [BlockOverlayManager] as a full-screen overlay whenever the user
 * navigates to a blocked URL or opens a blocked app.
 *
 * Manifest configuration:
 *  - launchMode="singleTop" — avoids stacking multiple instances; if already
 *    on top, [onNewIntent] receives the next block event.
 *  - excludeFromRecents="true" — must not pollute the recents screen.
 *  - taskAffinity set to a dedicated affinity — runs in its own back stack
 *    so finishing it returns to the previous app, not BrowserBlock.
 *  - showOnLockScreen="true" — visible even above the lock screen.
 *
 * The user can:
 *  - Tap "Go back"         → finish() returns to the previous task.
 *  - Tap "Open elsewhere"  → handled by [BlockOverlayManager] to deep-link
 *                            into an allowed browser.
 *
 * Logic to be implemented in a later step.
 */
class BlockActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var instance: BlockActivity? = null

        fun finishIfShowing() {
            instance?.runOnUiThread { instance?.finish() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)
        instance = this
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // TODO: update UI for the new blocked URL passed via intent extras
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}
