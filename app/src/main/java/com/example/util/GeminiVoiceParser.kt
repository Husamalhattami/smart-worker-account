package com.example.util

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class SystemInstruction(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val responseSchema: ResponseSchema? = null
)

@JsonClass(generateAdapter = true)
data class ResponseSchema(
    val type: String,
    val properties: Map<String, PropertySchema>,
    val required: List<String>
)

@JsonClass(generateAdapter = true)
data class PropertySchema(
    val type: String,
    val description: String? = null,
    val enum: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: SystemInstruction? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    val text: String?
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val parts: List<PartResponse>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponse?
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class VoiceParseResult(
    val type: String, // SPENDING, DRAW, ABSENCE
    val amount: Double
)

interface GeminiService {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun parseVoiceCommand(
        @Body request: GeminiRequest,
        @Query("key") apiKey: String = BuildConfig.GEMINI_API_KEY
    ): GeminiResponse
}

object GeminiClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiService = retrofit.create(GeminiService::class.java)

    suspend fun parseArabicVoiceText(text: String): VoiceParseResult? {
        val systemPrompt = """
            You are a transaction parsing engine for an Arabic salary tracking app.
            Your job is to read an Arabic voice transcription and return a JSON object with:
            - "type": "SPENDING" (صرفة/مصروف يومي), "DRAW" (سحبية/سلفة من الراتب), or "ABSENCE" (غياب/لم يشتغل/غائب)
            - "amount": The numeric amount parsed (default to 0.0 if not mentioned, e.g. for ABSENCE).
            
            Examples:
            "صرفت خمسين ريال" -> {"type": "SPENDING", "amount": 50.0}
            "سحبت مية" -> {"type": "DRAW", "amount": 100.0}
            "اليوم غايب" -> {"type": "ABSENCE", "amount": 0.0}
            "سجل غياب" -> {"type": "ABSENCE", "amount": 0.0}
            "سلفة خمسمئة ريال" -> {"type": "DRAW", "amount": 500.0}
        """.trimIndent()

        val schema = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "type" to PropertySchema(type = "STRING", enum = listOf("SPENDING", "DRAW", "ABSENCE")),
                "amount" to PropertySchema(type = "NUMBER")
            ),
            required = listOf("type", "amount")
        )

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = text)))),
            systemInstruction = SystemInstruction(parts = listOf(Part(text = systemPrompt))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema
            )
        )

        return try {
            val response = service.parseVoiceCommand(request)
            val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (responseText != null) {
                moshi.adapter(VoiceParseResult::class.java).fromJson(responseText)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

object LocalArabicParser {
    fun parse(text: String): VoiceParseResult {
        val normalizedText = normalizeArabic(text)
        
        // Check transaction type
        val type = when {
            normalizedText.contains("غياب") || normalizedText.contains("غبت") || normalizedText.contains("غايب") || normalizedText.contains("غائب") || normalizedText.contains("ما اشتغلت") -> "ABSENCE"
            normalizedText.contains("سحب") || normalizedText.contains("سلفة") || normalizedText.contains("سلفه") || normalizedText.contains("سحبية") || normalizedText.contains("سحبيه") || normalizedText.contains("اخذت") || normalizedText.contains("دين") -> "DRAW"
            else -> "SPENDING" // Default is spending
        }
        
        // Find amount
        val amount = extractNumber(normalizedText)
        
        return VoiceParseResult(type, amount)
    }

    private fun normalizeArabic(text: String): String {
        return text.replace("ة", "ه")
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
    }

    private fun extractNumber(text: String): Double {
        // 1. Convert Hindi digits to standard Western digits
        var normalized = text
        val hindiDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        for (i in 0..9) {
            normalized = normalized.replace(hindiDigits[i], i.toString())
        }

        // 2. Check explicitly for digit followed by "الف" / "آلاف" / "الاف" / "k" / "ك"
        // Example: "7 الف", "7الف", "7.5 الف", "7 الاف", "7,000"
        val thousandPattern = """(\d+(?:\.\d+)?)\s*(?:الف|الاف|آلاف|الآلاف|k|ك)""".toRegex(RegexOption.IGNORE_CASE)
        val thousandMatch = thousandPattern.find(normalized)
        if (thousandMatch != null) {
            val baseNum = thousandMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return baseNum * 1000.0
        }

        val millionPattern = """(\d+(?:\.\d+)?)\s*(?:مليون|ملايين)""".toRegex(RegexOption.IGNORE_CASE)
        val millionMatch = millionPattern.find(normalized)
        if (millionMatch != null) {
            val baseNum = millionMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            return baseNum * 1000000.0
        }

        // 3. Spoken Arabic words combined with "الف" (e.g. "سبعة الف", "خمسة آلاف", "عشرة الف")
        val wordMultipliers = mapOf(
            "واحد" to 1.0, "واحدة" to 1.0, "اثنين" to 2.0, "ثلاثة" to 3.0, "ثلاثه" to 3.0,
            "اربعة" to 4.0, "اربعه" to 4.0, "خمسة" to 5.0, "خمسه" to 5.0, "ستة" to 6.0, "سته" to 6.0,
            "سبعة" to 7.0, "سبعه" to 7.0, "ثمانية" to 8.0, "ثمانيه" to 8.0, "تسعة" to 9.0, "تسعه" to 9.0,
            "عشرة" to 10.0, "عشره" to 10.0, "عشرين" to 20.0, "ثلاثين" to 30.0, "اربعين" to 40.0,
            "خمسين" to 50.0, "ستين" to 60.0, "سبعين" to 70.0, "ثمانين" to 80.0, "تسعين" to 90.0, "مية" to 100.0
        )
        for ((word, numVal) in wordMultipliers) {
            if (normalized.contains("$word الف") || normalized.contains("$word الاف") || normalized.contains("$word آلاف")) {
                return numVal * 1000.0
            }
        }

        // 4. Try extracting standard multi-digit numbers (e.g., 7000, 1500, 500)
        val digitRegex = """\d+""".toRegex()
        val allDigitMatches = digitRegex.findAll(normalized).map { it.value.toDoubleOrNull() ?: 0.0 }.toList()
        if (allDigitMatches.isNotEmpty()) {
            val maxNum = allDigitMatches.maxOrNull() ?: 0.0
            if (maxNum > 0) return maxNum
        }

        // 5. Standalone Arabic word numbers fallback
        val standaloneWords = mapOf(
            "الفين" to 2000.0, "الف" to 1000.0, "خمسمية" to 500.0, "خمسمائه" to 500.0,
            "ميتين" to 200.0, "مية" to 100.0, "مائة" to 100.0, "تسعين" to 90.0,
            "ثمانين" to 80.0, "سبعين" to 70.0, "ستين" to 60.0, "خمسين" to 50.0,
            "اربعين" to 40.0, "ثلاثين" to 30.0, "عشرين" to 20.0, "عشرة" to 10.0, "عشره" to 10.0
        )
        for ((word, value) in standaloneWords) {
            if (normalized.contains(word)) {
                return value
            }
        }

        return 0.0
    }
}
