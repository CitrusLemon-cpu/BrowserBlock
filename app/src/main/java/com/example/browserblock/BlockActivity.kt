package com.example.browserblock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class BlockActivity : AppCompatActivity() {

    companion object {
        @Volatile
        var instance: BlockActivity? = null

        fun finishIfShowing() {
            instance?.runOnUiThread { instance?.finish() }
        }
    }

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)
        instance = this
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(ForegroundPollingService.ACTION_DISMISS_BLOCK),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    override fun onDestroy() {
        try { unregisterReceiver(dismissReceiver) } catch (_: Exception) {}
        if (instance === this) instance = null
        super.onDestroy()
    }
}
