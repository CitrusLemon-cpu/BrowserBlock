package com.example.browserblock

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ScheduleEntry(
    val id: String = UUID.randomUUID().toString(),
    val days: Set<Int>,
    val fromHour: Int,
    val fromMinute: Int,
    val toHour: Int,
    val toMinute: Int
) {
    val fromTotalMinutes: Int get() = fromHour * 60 + fromMinute
    val toTotalMinutes: Int get() = toHour * 60 + toMinute

    fun coversTime(dayOfWeek: Int, minutesFromMidnight: Int): Boolean {
        if (dayOfWeek !in days) return false
        return minutesFromMidnight >= fromTotalMinutes && minutesFromMidnight < toTotalMinutes
    }

    fun formatTimeRange(): String =
        String.format("%02d:%02d – %02d:%02d", fromHour, fromMinute, toHour, toMinute)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("days", JSONArray(days.toList()))
        put("fromHour", fromHour)
        put("fromMinute", fromMinute)
        put("toHour", toHour)
        put("toMinute", toMinute)
    }

    companion object {
        fun fromJson(json: JSONObject): ScheduleEntry {
            val daysArray = json.getJSONArray("days")
            val days = (0 until daysArray.length()).map { daysArray.getInt(it) }.toSet()
            return ScheduleEntry(
                id = json.getString("id"),
                days = days,
                fromHour = json.getInt("fromHour"),
                fromMinute = json.getInt("fromMinute"),
                toHour = json.getInt("toHour"),
                toMinute = json.getInt("toMinute")
            )
        }

        fun listToJson(entries: List<ScheduleEntry>): String =
            JSONArray(entries.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<ScheduleEntry> = try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
