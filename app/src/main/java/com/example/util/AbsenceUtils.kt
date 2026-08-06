package com.example.util

object AbsenceUtils {

    /**
     * Parses absence hours input string supporting formats like:
     * - "1.50" or "1.5" or "1:30" or "1.30" -> 1.5 hours (ساعة ونصف)
     * - "1.25" or "1.15" or "1:15" -> 1.25 hours (ساعة وربع)
     * - "1.45" or "1:45" -> 1.75 hours (ساعة و 45 دقيقة)
     * - "2.45" -> 2.75 hours (ساعتين و 45 دقيقة)
     * - "0.45" or ".45" -> 0.75 hours (45 دقيقة)
     * - "0.30" or "0.50" -> 0.5 hours (30 دقيقة)
     * - "0.15" or "0.25" -> 0.25 hours (15 دقيقة)
     * - "3" -> 3.0 hours
     */
    fun parseAbsenceHours(rawInput: String): Double {
        if (rawInput.isBlank()) return 0.0

        // Normalize Arabic / Persian digits to English ASCII digits
        var s = rawInput.trim()
            .replace('٠', '0').replace('١', '1').replace('٢', '2')
            .replace('٣', '3').replace('٤', '4').replace('٥', '5')
            .replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
            .replace(',', '.')

        if (s.contains(':')) {
            val parts = s.split(':')
            val h = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val m = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            return maxOf(0.0, minOf(12.0, h + (m / 60.0)))
        }

        if (s.contains('.')) {
            val parts = s.split('.')
            val h = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
            val fracStr = parts.getOrNull(1) ?: ""

            if (fracStr.isBlank()) return maxOf(0.0, minOf(12.0, h))

            val fracInt = fracStr.toIntOrNull()
            if (fracInt != null) {
                val totalHours = when {
                    fracStr.length == 2 && fracInt in 1..59 -> {
                        when (fracInt) {
                            45 -> h + 0.75
                            15, 25 -> h + 0.25
                            30, 50 -> h + 0.5
                            else -> h + (fracInt / 60.0)
                        }
                    }
                    fracStr.length == 1 -> {
                        val doubleVal = s.toDoubleOrNull()
                        doubleVal ?: (h + (fracInt / 10.0))
                    }
                    else -> {
                        s.toDoubleOrNull() ?: h
                    }
                }
                return maxOf(0.0, minOf(12.0, totalHours))
            }
        }

        val directVal = s.toDoubleOrNull() ?: 0.0
        return maxOf(0.0, minOf(12.0, directVal))
    }

    /**
     * Formats absence hours (e.g. 1.75, 1.5, 1.25) into rich Arabic text.
     */
    fun formatAbsenceHoursDescription(hours: Double): String {
        if (hours >= 12.0 || hours <= 0.0) return "12 ساعة (يوم كامل)"
        val totalMinutes = Math.round(hours * 60).toInt()
        if (totalMinutes <= 0) return "0 ساعة"

        val h = totalMinutes / 60
        val m = totalMinutes % 60

        if (h == 0) {
            return when (m) {
                15 -> "ربع ساعة (15 دقيقة)"
                30 -> "نصف ساعة (30 دقيقة)"
                45 -> "45 دقيقة"
                else -> "$m دقيقة"
            }
        }

        val hText = when (h) {
            1 -> "ساعة"
            2 -> "ساعتين"
            in 3..10 -> "$h ساعات"
            else -> "$h ساعة"
        }

        val mText = when (m) {
            0 -> ""
            15 -> "وربع"
            30 -> "ونصف"
            45 -> "و45 دقيقة"
            else -> "و$m دقيقة"
        }

        return if (mText.isBlank()) hText else "$hText $mText"
    }

    /**
     * Formats decimal absence hours (e.g. 1.75) back to user-friendly input string (e.g. "1.45").
     */
    fun formatAbsenceHoursForInput(hours: Double): String {
        if (hours <= 0.0 || hours >= 12.0) return "12"
        val totalMins = Math.round(hours * 60).toInt()
        val h = totalMins / 60
        val m = totalMins % 60
        if (m == 0) return "$h"
        val mFormatted = if (m < 10) "0$m" else "$m"
        return "$h.$mFormatted"
    }
}
