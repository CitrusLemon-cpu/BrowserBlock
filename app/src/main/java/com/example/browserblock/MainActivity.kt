package com.example.browserblock

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.browserblock.databinding.ActivityMainBinding
import com.example.browserblock.databinding.ItemWatchedAppBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    data class AppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable,
        var isWatched: Boolean
    )

    inner class WatchedAppAdapter : RecyclerView.Adapter<WatchedAppAdapter.VH>() {

        private var items: List<AppItem> = emptyList()

        fun submit(newItems: List<AppItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class VH(val binding: ItemWatchedAppBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemWatchedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            with(holder.binding) {
                ivAppIcon.setImageDrawable(item.icon)
                tvAppName.text = item.label
                tvPackageName.text = item.packageName

                switchWatched.setOnCheckedChangeListener(null)
                switchWatched.isChecked = item.isWatched
                switchWatched.setOnCheckedChangeListener { _, isChecked ->
                    item.isWatched = isChecked
                    AppPreferences.setWatched(item.packageName, isChecked)
                }

                root.setOnClickListener {
                    AppDetailActivity.start(this@MainActivity, item.packageName)
                }
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val adapter = WatchedAppAdapter()
    private var allApps: List<AppItem> = emptyList()
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            val isPaused = AppPreferences.isPaused
            val remaining = AppPreferences.getPauseRemainingMs()
            if (isPaused && remaining > 0 && remaining != Long.MAX_VALUE) {
                refreshStatusCard()
                countdownHandler.postDelayed(this, 1_000L)
            } else if (!isPaused && binding.switchPause.isChecked.not()) {
                binding.switchPause.setOnCheckedChangeListener(null)
                binding.switchPause.isChecked = true
                setupPauseSwitch()
                refreshStatusCard()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter

        setupPauseSwitch()
        setupSearch()
        loadApps()
    }

    override fun onResume() {
        super.onResume()
        ForegroundPollingService.start(this)
        binding.switchPause.setOnCheckedChangeListener(null)
        binding.switchPause.isChecked = !AppPreferences.isPaused
        setupPauseSwitch()
        refreshStatusCard()
        val remaining = AppPreferences.getPauseRemainingMs()
        if (remaining > 0 && remaining != Long.MAX_VALUE) {
            startCountdownTimer()
        }
        if (allApps.isNotEmpty()) {
            val refreshed = allApps.map { it.copy(isWatched = AppPreferences.isWatched(it.packageName)) }
            allApps = sorted(refreshed)
            applySearch(binding.etSearch.text?.toString() ?: "")
        }
    }

    override fun onPause() {
        super.onPause()
        stopCountdownTimer()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_schedule -> {
                startActivity(Intent(this, ScheduleActivity::class.java))
                return true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshStatusCard() {
        val isPaused = AppPreferences.isPaused
        val accessibilityOn = BlockerAccessibilityService.instance != null
        val scheduledButInactive =
            AppPreferences.isScheduleEnabled && !AppPreferences.shouldBlock() && !isPaused

        val (title, desc, colorRes) = when {
            isPaused -> {
                val remaining = AppPreferences.getPauseRemainingMs()
                val description = if (remaining != Long.MAX_VALUE && remaining > 0) {
                    getString(R.string.status_desc_paused_timed, formatCountdown(remaining))
                } else {
                    getString(R.string.status_desc_paused)
                }
                Triple(
                    getString(R.string.status_title_paused),
                    description,
                    R.color.status_red
                )
            }
            scheduledButInactive -> Triple(
                getString(R.string.status_title_scheduled_inactive),
                getString(R.string.status_desc_scheduled_inactive),
                R.color.status_yellow
            )
            accessibilityOn -> Triple(
                getString(R.string.status_title_active),
                getString(R.string.status_desc_active),
                R.color.status_green
            )
            else -> Triple(
                getString(R.string.status_title_fallback),
                getString(R.string.status_desc_fallback),
                R.color.status_yellow
            )
        }

        binding.tvStatusTitle.text = title
        binding.tvStatusDesc.text = desc
        binding.viewStatusDot.background.mutate()
            .setTint(ContextCompat.getColor(this, colorRes))
    }

    private fun setupPauseSwitch() {
        binding.switchPause.isChecked = !AppPreferences.isPaused
        binding.switchPause.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                showPauseDurationDialog()
            } else {
                AppPreferences.clearPause()
                refreshStatusCard()
                stopCountdownTimer()
            }
        }
    }

    private fun showPauseDurationDialog() {
        val durations = listOf(
            getString(R.string.pause_duration_5min) to 5L * 60 * 1000,
            getString(R.string.pause_duration_15min) to 15L * 60 * 1000,
            getString(R.string.pause_duration_30min) to 30L * 60 * 1000,
            getString(R.string.pause_duration_1hour) to 60L * 60 * 1000,
            getString(R.string.pause_duration_2hours) to 2L * 60 * 60 * 1000,
            getString(R.string.pause_duration_indefinite) to -1L
        )
        val labels = durations.map { it.first }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pause_dialog_title)
            .setItems(labels) { _, which ->
                val durationMs = durations[which].second
                if (durationMs < 0) {
                    AppPreferences.startIndefinitePause()
                    stopCountdownTimer()
                } else {
                    AppPreferences.startTimedPause(durationMs)
                    startCountdownTimer()
                }
                binding.switchPause.isChecked = false
                refreshStatusCard()
            }
            .setNegativeButton(R.string.pause_dialog_cancel) { _, _ ->
                binding.switchPause.setOnCheckedChangeListener(null)
                binding.switchPause.isChecked = true
                setupPauseSwitch()
            }
            .setOnCancelListener {
                binding.switchPause.setOnCheckedChangeListener(null)
                binding.switchPause.isChecked = true
                setupPauseSwitch()
            }
            .show()
    }

    private fun startCountdownTimer() {
        countdownHandler.removeCallbacks(countdownRunnable)
        countdownHandler.postDelayed(countdownRunnable, 1_000L)
    }

    private fun stopCountdownTimer() {
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun formatCountdown(remainingMs: Long): String {
        val totalSeconds = (remainingMs / 1000).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                applySearch(s?.toString() ?: "")
            }
        })
    }

    private fun applySearch(query: String) {
        val filtered = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }
        adapter.submit(filtered)
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveList: List<ResolveInfo> = pm.queryIntentActivities(launcherIntent, 0)

            val apps = resolveList
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg in WatchedApps.NEVER_BLOCK) return@mapNotNull null
                    AppItem(
                        label = ri.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = ri.loadIcon(pm),
                        isWatched = AppPreferences.isWatched(pkg)
                    )
                }
                .distinctBy { it.packageName }
                .let { sorted(it) }

            withContext(Dispatchers.Main) {
                allApps = apps
                applySearch(binding.etSearch.text?.toString() ?: "")
            }
        }
    }

    private fun sorted(list: List<AppItem>): List<AppItem> =
        list.sortedWith(compareByDescending<AppItem> { it.isWatched }.thenBy { it.label.lowercase() })
}
