package com.example.util

object ArabicNumbersToWords {
    private val ones = arrayOf("", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر")
    private val tens = arrayOf("", "عشرة", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون")
    private val hundreds = arrayOf("", "مائة", "مائتان", "ثلاثمائة", "أربعمائة", "خمسمائة", "ستمائة", "سبعمائة", "ثمانمائة", "تسعمائة")

    fun convert(number: Int): String {
        if (number == 0) return "صفر"
        if (number < 0) return "ناقص " + convert(-number)

        var result = ""

        // Millions
        if (number >= 1000000) {
            val millionPart = number / 1000000
            val remaining = number % 1000000
            val millionStr = when (millionPart) {
                1 -> "مليون"
                2 -> "مليونان"
                in 3..10 -> "${convert(millionPart)} ملايين"
                else -> "${convert(millionPart)} مليون"
            }
            result += millionStr
            if (remaining > 0) {
                result += " و " + convert(remaining)
            }
            return result
        }

        // Thousands
        if (number >= 1000) {
            val thousandPart = number / 1000
            val remaining = number % 1000
            val thousandStr = when (thousandPart) {
                1 -> "ألف"
                2 -> "ألفين"
                in 3..10 -> "${ones[thousandPart]} آلاف"
                else -> "${convert(thousandPart)} ألف"
            }
            result += thousandStr
            if (remaining > 0) {
                result += " و " + convert(remaining)
            }
            return result
        }

        // Hundreds
        if (number >= 100) {
            val hundredPart = number / 100
            val remaining = number % 100
            result += hundreds[hundredPart]
            if (remaining > 0) {
                result += " و " + convert(remaining)
            }
            return result
        }

        // Tens & Ones
        if (number >= 20) {
            val tenPart = number / 10
            val onePart = number % 10
            if (onePart > 0) {
                result += ones[onePart] + " و " + tens[tenPart]
            } else {
                result += tens[tenPart]
            }
            return result
        }

        // Below 20
        result += ones[number]
        return result
    }
}
