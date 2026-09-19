package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.AuraPipelineResponse
import com.example.data.model.TaskItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class OrchestrationResult(
    val response: AuraPipelineResponse,
    val rawJson: String,
    val isFallback: Boolean,
    val note: String? = null
)

class GeminiOrchestrator {

    companion object {
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

        val SYSTEM_PROMPT = """
            You are the core AI orchestration engine for AuraMind, an elite smart automation utility. Your objective is to analyze chaotic, unstructured text or multi-modal images submitted by professionals and convert them into an actionable workflow pipeline.

            You must strictly evaluate the user inputs and return a valid, clean JSON object. Do not include markdown code block formatting (such as ```json) in your final response—return only raw JSON.

            The structured output must precisely match this schema:
            {
              "summary": "A concise, single-sentence summary of the overall goal or situation described by the user.",
              "tasks": [
                {
                  "title": "A highly precise, action-oriented title for the task (e.g., 'Fix critical bug on landing page').",
                  "priority": "Classify as either 'High', 'Medium', or 'Low' based on urgency signals or implied context.",
                  "deadline": "Extract explicit dates/times if given. If no deadline is mentioned, return 'None'."
                }
              ],
              "draft_reply": "A professionally formatted email draft or team update response that acknowledges the situation and lays out the next operational steps for stakeholders."
            }

            If the user input contains zero actionable context, return an empty tasks array, a brief message in the summary, and 'None' for the draft_reply.
        """.trimIndent()
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(AuraPipelineResponse::class.java)

    suspend fun orchestrate(
        inputText: String,
        imageBitmap: Bitmap?
    ): OrchestrationResult = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        // Check if API Key is configured and not default placeholder
        val isValidKey = apiKey.isNotBlank() &&
                apiKey != "MY_GEMINI_API_KEY" &&
                !apiKey.contains("PLACEHOLDER", ignoreCase = true)

        if (!isValidKey) {
            // Run intelligent local heuristic orchestrator
            return@withContext runLocalFallback(
                inputText,
                imageBitmap,
                "Gemini API key is not configured in Secrets. Executed via AuraMind Edge Engine."
            )
        }

        try {
            val url = "$BASE_URL$MODEL_NAME:generateContent?key=$apiKey"

            val jsonBody = JSONObject().apply {
                // System Instruction
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", SYSTEM_PROMPT))
                    })
                })

                // Contents
                val partsArray = JSONArray()

                val promptToSend = if (inputText.isNotBlank()) {
                    inputText
                } else if (imageBitmap != null) {
                    "Analyze this document/image and extract the operational workflow pipeline according to the AuraMind specification."
                } else {
                    ""
                }

                if (promptToSend.isNotBlank()) {
                    partsArray.put(JSONObject().put("text", promptToSend))
                }

                if (imageBitmap != null) {
                    val base64Data = bitmapToBase64(imageBitmap)
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Data)
                    }
                    partsArray.put(JSONObject().put("inlineData", inlineData))
                }

                put("contents", JSONArray().apply {
                    put(JSONObject().put("parts", partsArray))
                })

                // Generation Config
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (!response.isSuccessful || responseBodyString.isNullOrBlank()) {
                val errorMsg = "API returned HTTP ${response.code}"
                return@withContext runLocalFallback(inputText, imageBitmap, errorMsg)
            }

            // Extract candidate text
            val rootJson = JSONObject(responseBodyString)
            val candidates = rootJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text")

            if (rawText.isNullOrBlank()) {
                return@withContext runLocalFallback(inputText, imageBitmap, "Empty response from Gemini")
            }

            val cleanJsonString = cleanJson(rawText)
            val parsedResponse = responseAdapter.fromJson(cleanJsonString)
                ?: return@withContext runLocalFallback(inputText, imageBitmap, "Failed to parse JSON response")

            OrchestrationResult(
                response = parsedResponse,
                rawJson = cleanJsonString,
                isFallback = false
            )
        } catch (e: Exception) {
            runLocalFallback(inputText, imageBitmap, "Network or API call failed: ${e.message}")
        }
    }

    private fun cleanJson(raw: String): String {
        var cleaned = raw.trim()
        if (cleaned.startsWith("```json", ignoreCase = true)) {
            cleaned = cleaned.substring(7).trim()
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3).trim()
        }
        return cleaned
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if excessively large to avoid payload limit
        val scaledBitmap = if (bitmap.width > 1280 || bitmap.height > 1280) {
            val scale = 1280f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * High-precision local fallback engine that evaluates chaotic text or image signals
     * and produces valid, strictly conforming schema JSON.
     */
    fun runLocalFallback(
        inputText: String,
        imageBitmap: Bitmap?,
        fallbackReason: String? = null
    ): OrchestrationResult {
        val trimmed = inputText.trim()

        // Check for zero actionable context
        if (trimmed.isBlank() && imageBitmap == null) {
            val emptyResponse = AuraPipelineResponse(
                summary = "No actionable operational context was provided in the input.",
                tasks = emptyList(),
                draftReply = "None"
            )
            val rawJson = responseAdapter.indent("  ").toJson(emptyResponse)
            return OrchestrationResult(
                response = emptyResponse,
                rawJson = rawJson,
                isFallback = true,
                note = fallbackReason
            )
        }

        // Check if user input is too short or nonsensical (e.g. "hi", "test", "123")
        val isTrivialGreeting = trimmed.matches(Regex("^(hi|hello|hey|test|testing|asdf|foo|bar)[!.]*$", RegexOption.IGNORE_CASE))
        if (isTrivialGreeting && imageBitmap == null) {
            val emptyResponse = AuraPipelineResponse(
                summary = "Zero actionable context detected in greeting or test input.",
                tasks = emptyList(),
                draftReply = "None"
            )
            val rawJson = responseAdapter.indent("  ").toJson(emptyResponse)
            return OrchestrationResult(
                response = emptyResponse,
                rawJson = rawJson,
                isFallback = true,
                note = fallbackReason
            )
        }

        // Extract tasks intelligently from chaotic text
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val detectedTasks = mutableListOf<TaskItem>()

        val highUrgencyWords = listOf("urgent", "asap", "critical", "p0", "blocker", "emergency", "immediately", "outage", "down", "severe", "crashed")
        val lowUrgencyWords = listOf("low", "minor", "nice to have", "whenever", "backlog", "future", "optional", "p3", "p4")

        val deadlinePatterns = listOf(
            Pattern.compile("(?i)(by\\s+)?(today|tomorrow|eod|tonight|cob|end of day)"),
            Pattern.compile("(?i)(by\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?)?"),
            Pattern.compile("(?i)(by\\s+)?(\\d{1,2}(:\\d{2})?\\s*(am|pm))"),
            Pattern.compile("(?i)(by\\s+)?([A-Z][a-z]{2,8}\\s+\\d{1,2}(st|nd|rd|th)?)"),
            Pattern.compile("(?i)(in\\s+\\d+\\s*(hours?|days?|hrs?))")
        )

        // Split text into potential action items
        val candidates = mutableListOf<String>()
        if (lines.size > 1) {
            candidates.addAll(lines)
        } else {
            // Split by punctuation or semicolon or bullet markers
            val split = trimmed.split(Regex("(?<=[.?!;])\\s+|(?=[-•*])|\\b(?:and then|also|need to|must|please)\\b", RegexOption.IGNORE_CASE))
            candidates.addAll(split.map { it.trim() }.filter { it.length > 5 })
        }

        for (candidate in candidates) {
            val clean = candidate
                .replace(Regex("^[-*•0-9.)\\]]+\\s*"), "")
                .trim()

            if (clean.length < 5) continue

            // Determine Priority
            val lower = clean.lowercase()
            val priority = when {
                highUrgencyWords.any { lower.contains(it) } -> "High"
                lowUrgencyWords.any { lower.contains(it) } -> "Low"
                else -> {
                    if (lower.contains("fix") || lower.contains("error") || lower.contains("deploy") || lower.contains("client")) "High"
                    else "Medium"
                }
            }

            // Extract Deadline
            var extractedDeadline = "None"
            for (pattern in deadlinePatterns) {
                val matcher = pattern.matcher(clean)
                if (matcher.find()) {
                    extractedDeadline = matcher.group().trim().replaceFirstChar { it.uppercase() }
                    break
                }
            }

            // Format precise action-oriented title
            var actionTitle = clean
            // If candidate starts with conversational junk, clean it
            actionTitle = actionTitle.replace(Regex("^(we need to|can you|please|i think we should|make sure to)\\s*", RegexOption.IGNORE_CASE), "")
            actionTitle = actionTitle.replaceFirstChar { it.uppercase() }
            if (actionTitle.length > 80) {
                actionTitle = actionTitle.take(77) + "..."
            }

            detectedTasks.add(
                TaskItem(
                    title = actionTitle,
                    priority = priority,
                    deadline = extractedDeadline
                )
            )
        }

        // If no tasks were extracted from text (or only image), synthesize sensible baseline
        if (detectedTasks.isEmpty()) {
            if (imageBitmap != null) {
                detectedTasks.add(
                    TaskItem(
                        title = "Review submitted visual attachment and verify key operational parameters",
                        priority = "High",
                        deadline = "Today"
                    )
                )
                detectedTasks.add(
                    TaskItem(
                        title = "Coordinate with project stakeholders regarding image deliverables",
                        priority = "Medium",
                        deadline = "None"
                    )
                )
            } else {
                detectedTasks.add(
                    TaskItem(
                        title = "Analyze and resolve ${trimmed.take(50).replaceFirstChar { it.uppercase() }}",
                        priority = "Medium",
                        deadline = "None"
                    )
                )
            }
        }

        // Generate concise single-sentence summary
        val summary = if (imageBitmap != null && trimmed.isBlank()) {
            "Visual workflow artifact submitted for operational pipeline review and execution."
        } else {
            val highCount = detectedTasks.count { it.priority == "High" }
            if (highCount > 0) {
                "Critical priority operational workflow requiring immediate stakeholder alignment and rapid execution of high-urgency milestones."
            } else {
                "Multi-phase execution workflow designed to coordinate deliverables, resolve pending action items, and maintain operational momentum."
            }
        }

        // Generate professionally formatted draft reply
        val draftReply = buildString {
            append("Hi Team,\n\n")
            append("Thank you for flagging this situation. We have thoroughly reviewed the input and established an immediate operational action plan:\n\n")
            detectedTasks.take(4).forEachIndexed { index, task ->
                val deadlineText = if (task.deadline != "None") " [Target: ${task.deadline}]" else ""
                append("${index + 1}. [${task.priority} Priority] ${task.title}$deadlineText\n")
            }
            append("\nOur engineering and operations leads are currently executing the highest priority items. We will provide our next progress checkpoint within two hours.\n\n")
            append("Best regards,\nAuraMind Automation Lead")
        }

        val fallbackResponse = AuraPipelineResponse(
            summary = summary,
            tasks = detectedTasks,
            draftReply = draftReply
        )

        val rawJson = responseAdapter.indent("  ").toJson(fallbackResponse)

        return OrchestrationResult(
            response = fallbackResponse,
            rawJson = rawJson,
            isFallback = true,
            note = fallbackReason
        )
    }
}
