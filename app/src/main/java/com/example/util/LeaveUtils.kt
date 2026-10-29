package com.example.util

import com.example.data.LeaveRecord
import java.text.SimpleDateFormat
import java.util.*

object LeaveUtils {

    fun isDayInLeave(dayMidnightMs: Long, leaves: List<LeaveRecord>): Boolean {
        if (leaves.isEmpty()) return false
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayMidnightMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetDay = cal.timeInMillis

        return leaves.any { leave ->
            val startDay = Calendar.getInstance().apply {
                timeInMillis = leave.startDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endDay = leave.endDate?.let { endMs ->
                Calendar.getInstance().apply {
                    timeInMillis = endMs
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }

            if (endDay == null) {
                targetDay >= startDay
            } else {
                targetDay in startDay..endDay
            }
        }
    }

    fun findLeaveForDay(dayMidnightMs: Long, leaves: List<LeaveRecord>): LeaveRecord? {
        if (leaves.isEmpty()) return false.let { null }
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayMidnightMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetDay = cal.timeInMillis

        return leaves.firstOrNull { leave ->
            val startDay = Calendar.getInstance().apply {
                timeInMillis = leave.startDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val endDay = leave.endDate?.let { endMs ->
                Calendar.getInstance().apply {
                    timeInMillis = endMs
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
            }

            if (endDay == null) {
                targetDay >= startDay
            } else {
                targetDay in startDay..endDay
            }
        }
    }

    fun getCurrentlyActiveLeave(leaves: List<LeaveRecord>): LeaveRecord? {
        val now = System.currentTimeMillis()
        return findLeaveForDay(now, leaves)
    }

    fun formatLeaveDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale("ar"))
        return sdf.format(Date(timestamp))
    }

    fun formatLeavePeriodText(leave: LeaveRecord): String {
        val startStr = formatLeaveDate(leave.startDate)
        val endStr = if (leave.endDate != null) formatLeaveDate(leave.endDate) else "مستمرة (عدها لم تنتهي)"
        return "من $startStr إلى $endStr"
    }

    fun calculateLeaveDays(leave: LeaveRecord): Int {
        val startDay = Calendar.getInstance().apply {
            timeInMillis = leave.startDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endDay = (leave.endDate ?: System.currentTimeMillis()).let { endMs ->
            Calendar.getInstance().apply {
                timeInMillis = endMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }

        val diffMs = maxOf(0L, endDay - startDay)
        return (diffMs / 86400000L).toInt() + 1
    }
}
