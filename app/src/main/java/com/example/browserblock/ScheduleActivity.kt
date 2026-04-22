package com.example.browserblock

import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.example.browserblock.databinding.ActivityScheduleBinding
import com.example.browserblock.databinding.ItemScheduleEntryBinding
import com.google.android.material.color.MaterialColors
import java.util.Calendar

class ScheduleActivity : AppCompatActivity(), AddTimeDialogFragment.OnTimeAddedListener {

    private lateinit var binding: ActivityScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.cbScheduleEnabled.isChecked = AppPreferences.isScheduleEnabled
        binding.cbScheduleEnabled.setOnCheckedChangeListener { _, isChecked ->
            AppPreferences.isScheduleEnabled = isChecked
            refreshUI()
        }
        binding.btnAddTime.setOnClickListener {
            if (!AppPreferences.isScheduleEnabled) return@setOnClickListener
            AddTimeDialogFragment().show(supportFragmentManager, "add_time")
        }

        refreshUI()
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onTimeAdded(entry: ScheduleEntry) {
        AppPreferences.addScheduleEntry(entry)
        refreshUI()
    }

    private fun refreshUI() {
        val entries = AppPreferences.getScheduleEntries()
            .sortedWith(compareBy<ScheduleEntry> { it.days.minOrNull() ?: 0 }.thenBy { it.fromTotalMinutes }.thenBy { it.toTotalMinutes })
        val enabled = AppPreferences.isScheduleEnabled
        val contentAlpha = if (enabled) 1f else 0.45f

        binding.cbScheduleEnabled.isChecked = enabled
        binding.scheduleGrid.alpha = contentAlpha
        binding.containerEntries.alpha = contentAlpha
        binding.scheduleGrid.setEntries(entries)
        binding.btnAddTime.isEnabled = enabled

        binding.containerEntries.removeAllViews()
        entries.forEach { entry ->
            val itemBinding = ItemScheduleEntryBinding.inflate(layoutInflater, binding.containerEntries, false)
            itemBinding.tvFromTime.text = formatTime(entry.fromHour, entry.fromMinute)
            itemBinding.tvToTime.text = formatTime(entry.toHour, entry.toMinute)
            itemBinding.btnDelete.setOnClickListener {
                AppPreferences.removeScheduleEntry(entry.id)
                refreshUI()
            }
            bindDayLabels(itemBinding, entry)
            binding.containerEntries.addView(itemBinding.root)
        }
    }

    private fun bindDayLabels(binding: ItemScheduleEntryBinding, entry: ScheduleEntry) {
        val activeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, 0)
        val inactiveBase = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, 0)
        val inactiveColor = ColorUtils.setAlphaComponent(inactiveBase, (255 * 0.4f).toInt())

        mapOf(
            Calendar.SUNDAY to binding.tvSun,
            Calendar.MONDAY to binding.tvMon,
            Calendar.TUESDAY to binding.tvTue,
            Calendar.WEDNESDAY to binding.tvWed,
            Calendar.THURSDAY to binding.tvThu,
            Calendar.FRIDAY to binding.tvFri,
            Calendar.SATURDAY to binding.tvSat
        ).forEach { (day, view) ->
            val active = day in entry.days
            view.setTextColor(if (active) activeColor else inactiveColor)
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String = String.format("%02d:%02d", hour, minute)
}
