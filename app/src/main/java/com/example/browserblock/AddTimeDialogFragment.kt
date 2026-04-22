package com.example.browserblock

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import com.example.browserblock.databinding.DialogAddTimeBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class AddTimeDialogFragment : DialogFragment() {

    interface OnTimeAddedListener {
        fun onTimeAdded(entry: ScheduleEntry)
    }

    private var _binding: DialogAddTimeBinding? = null
    private val binding get() = _binding!!

    private val selectedDays = mutableSetOf<Int>()
    private var fromTime: Pair<Int, Int>? = null
    private var untilTime: Pair<Int, Int>? = null

    private val dayButtons by lazy {
        listOf(
            Calendar.SUNDAY to binding.btnDaySun,
            Calendar.MONDAY to binding.btnDayMon,
            Calendar.TUESDAY to binding.btnDayTue,
            Calendar.WEDNESDAY to binding.btnDayWed,
            Calendar.THURSDAY to binding.btnDayThu,
            Calendar.FRIDAY to binding.btnDayFri,
            Calendar.SATURDAY to binding.btnDaySat
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddTimeBinding.inflate(layoutInflater)
        setupDayButtons()
        setupTimeButtons()
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnAdd.setOnClickListener {
            val from = fromTime ?: return@setOnClickListener
            val until = untilTime ?: return@setOnClickListener
            listener?.onTimeAdded(
                ScheduleEntry(
                    days = selectedDays.toSet(),
                    fromHour = from.first,
                    fromMinute = from.second,
                    toHour = until.first,
                    toMinute = until.second
                )
            )
            dismiss()
        }
        updateAddButton()
        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }

    private val listener: OnTimeAddedListener?
        get() = parentFragment as? OnTimeAddedListener ?: activity as? OnTimeAddedListener

    private fun setupDayButtons() {
        dayButtons.forEach { (day, button) ->
            updateDayButtonState(button, false)
            button.setOnClickListener {
                if (selectedDays.contains(day)) {
                    selectedDays.remove(day)
                } else {
                    selectedDays.add(day)
                }
                updateDayButtonState(button, selectedDays.contains(day))
                updateAddButton()
            }
        }
    }

    private fun setupTimeButtons() {
        binding.btnFrom.setOnClickListener {
            showTimePicker(
                tag = "from_picker",
                initial = fromTime ?: (9 to 0)
            ) { hour, minute ->
                fromTime = hour to minute
                binding.btnFrom.text = formatTime(hour, minute)
                updateAddButton()
            }
        }
        binding.btnUntil.setOnClickListener {
            showTimePicker(
                tag = "until_picker",
                initial = untilTime ?: (17 to 0)
            ) { hour, minute ->
                untilTime = hour to minute
                binding.btnUntil.text = formatTime(hour, minute)
                updateAddButton()
            }
        }
    }

    private fun showTimePicker(
        tag: String,
        initial: Pair<Int, Int>,
        onSelected: (hour: Int, minute: Int) -> Unit
    ) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
            .setHour(initial.first)
            .setMinute(initial.second)
            .build()
        picker.addOnPositiveButtonClickListener {
            onSelected(picker.hour, picker.minute)
        }
        picker.show(parentFragmentManager, tag)
    }

    private fun updateDayButtonState(button: MaterialButton, selected: Boolean) {
        val primary = MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimary, Color.BLACK)
        val primaryContainer = MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimaryContainer, primary)
        val onPrimaryContainer = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE)
        val outline = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOutline, primary)
        val onSurface = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        if (selected) {
            button.backgroundTintList = ColorStateList.valueOf(primaryContainer)
            button.strokeColor = ColorStateList.valueOf(primaryContainer)
            button.setTextColor(onPrimaryContainer)
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            button.strokeColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(outline, 220))
            button.setTextColor(onSurface)
        }
    }

    private fun updateAddButton() {
        val from = fromTime
        val until = untilTime
        val enabled = selectedDays.isNotEmpty() &&
            from != null &&
            until != null &&
            (from.first * 60 + from.second) < (until.first * 60 + until.second)
        binding.btnAdd.isEnabled = enabled
    }

    private fun formatTime(hour: Int, minute: Int): String = String.format("%02d:%02d", hour, minute)
}
