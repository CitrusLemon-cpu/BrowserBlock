package com.example.browserblock

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity — the primary UI screen, reached after [SetupActivity] confirms
 * all required permissions have been granted.
 *
 * Exported: false — can only be started by components within this app.
 *
 * Responsibilities (to be implemented in later steps):
 *  - Display the list of watched apps / blocked keywords.
 *  - Allow toggling [BlockingMode] (KEYWORD vs ALLOWLIST).
 *  - Show blocking statistics / session history.
 *  - Provide a settings screen shortcut.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
