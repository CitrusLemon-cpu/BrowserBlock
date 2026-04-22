package com.example.browserblock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import java.util.Calendar
import kotlin.math.min

class WeeklyScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val entries = mutableListOf<ScheduleEntry>()

    private val density = resources.displayMetrics.density
    private val leftMargin = 44f * density
    private val dayLabelHeight = 24f * density
    private val columnGap = 3f * density
    private val cornerRadius = 10f * density

    private val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    private val dayOrder = listOf(
        Calendar.SUNDAY,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY
    )

    private val columnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * density
    }
    private val timeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        textSize = 11f * density
    }

    init {
        val fallbackColumn = ContextCompat.getColor(context, R.color.schedule_column_bg)
        val surfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant, fallbackColumn)
        val onSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

        columnPaint.color = ColorUtils.setAlphaComponent(surfaceVariant, 92)
        blockPaint.color = ContextCompat.getColor(context, R.color.schedule_block_fill)
        dayLabelPaint.color = onSurface
        timeLabelPaint.color = ColorUtils.setAlphaComponent(onSurfaceVariant, 180)
    }

    fun setEntries(entries: List<ScheduleEntry>) {
        this.entries.clear()
        this.entries.addAll(entries)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val contentLeft = paddingLeft.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentRight = width - paddingRight.toFloat()
        val contentBottom = height - paddingBottom.toFloat()

        val gridLeft = contentLeft + leftMargin
        val gridTop = contentTop + dayLabelHeight
        val gridHeight = (contentBottom - gridTop).coerceAtLeast(0f)
        val totalGapWidth = columnGap * 6f
        val columnWidth = ((contentRight - gridLeft - totalGapWidth) / 7f).coerceAtLeast(0f)

        val dayBaseline = contentTop + dayLabelHeight - 6f * density
        dayOrder.forEachIndexed { index, _ ->
            val columnLeft = gridLeft + index * (columnWidth + columnGap)
            val columnRight = columnLeft + columnWidth
            val rect = RectF(columnLeft, gridTop, columnRight, contentBottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, columnPaint)
            canvas.drawText(dayLabels[index], rect.centerX(), dayBaseline, dayLabelPaint)
        }

        entries.forEach { entry ->
            entry.days.forEach { day ->
                val index = dayOrder.indexOf(day)
                if (index == -1) return@forEach
                val columnLeft = gridLeft + index * (columnWidth + columnGap)
                val top = gridTop + (entry.fromTotalMinutes / 1440f) * gridHeight
                val bottom = gridTop + (entry.toTotalMinutes / 1440f) * gridHeight
                val rect = RectF(
                    columnLeft,
                    top,
                    columnLeft + columnWidth,
                    maxOf(top, bottom)
                )
                if (rect.height() > 0f) {
                    canvas.drawRoundRect(rect, min(cornerRadius, rect.height() / 2f), min(cornerRadius, rect.height() / 2f), blockPaint)
                }
            }
        }

        val timeLabels = listOf(
            0 to "00:00",
            360 to "06:00",
            720 to "12:00",
            1080 to "18:00",
            1440 to "00:00"
        )
        val fontMetrics = timeLabelPaint.fontMetrics
        val baselineOffset = -(fontMetrics.ascent + fontMetrics.descent) / 2f
        timeLabels.forEach { (minutes, label) ->
            val y = gridTop + (minutes / 1440f) * gridHeight
            canvas.drawText(label, gridLeft - 8f * density, y + baselineOffset, timeLabelPaint)
        }
    }
}
