package com.kairo.assistant.nlu.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight client for Google Gemini Cloud API.
 * Allows testing high-intelligence agent reasoning alongside on-device LLaMA.
 */
object GeminiClient {

    private const val TAG = "GeminiClient"
    const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

    /**
     * Generate content via Gemini REST API with automatic quota fallback.
     *
     * @param prompt The user instruction
     * @param apiKey The user's Google AI Studio API key
     * @param model Gemini model identifier (e.g. "gemini-3.1-flash-lite", "gemini-flash-latest")
     * @param systemInstruction Optional system instruction
     * @param jsonMode If true, enforces strict application/json output
     */
    suspend fun generateContent(
        prompt: String,
        apiKey: String,
        model: String = DEFAULT_MODEL,
        systemInstruction: String? = null,
        jsonMode: Boolean = true
    ): String {
        return try {
            generateContentInternal(prompt, apiKey, model, systemInstruction, jsonMode)
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            if ((errorMsg.contains("429") || errorMsg.contains("RESOURCE_EXHAUSTED")) && model != "gemini-3.1-flash-lite") {
                Log.w(TAG, "Model $model hit quota 429, auto-switching to gemini-3.1-flash-lite fallback...")
                generateContentInternal(prompt, apiKey, "gemini-3.1-flash-lite", systemInstruction, jsonMode)
            } else {
                throw e
            }
        }
    }

    private suspend fun generateContentInternal(
        prompt: String,
        apiKey: String,
        model: String,
        systemInstruction: String?,
        jsonMode: Boolean
    ): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Gemini API key is empty. Please enter your API key in Settings.")
        }

        val cleanModel = model.trim().removePrefix("models/")
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
        var connection: HttpURLConnection? = null

        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                doInput = true
            }

            // Build request payload
            val requestJson = JSONObject().apply {
                if (!systemInstruction.isNullOrBlank()) {
                    val sysObj = JSONObject().apply {
                        val sysParts = JSONArray().apply {
                            put(JSONObject().apply { put("text", systemInstruction) })
                        }
                        put("parts", sysParts)
                    }
                    put("system_instruction", sysObj)
                }

                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 1024)
                    if (jsonMode) {
                        put("responseMimeType", "application/json")
                    }
                }
                put("generationConfig", generationConfig)
            }

            // Send payload
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }

                val responseJson = JSONObject(responseText)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "").trim()
                    }
                }
                throw IllegalStateException("Empty response from Gemini API")
            } else {
                val errorStream = connection.errorStream
                val errorBody = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "HTTP $responseCode"
                }
                Log.e(TAG, "Gemini API error ($responseCode): $errorBody")
                throw IllegalStateException("Gemini API Error ($responseCode): $errorBody")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Gemini API", e)
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Test connection to Gemini API with the given key.
     */
    suspend fun testApiKey(apiKey: String, model: String = DEFAULT_MODEL): Result<String> {
        return try {
            val response = generateContent(
                prompt = "Respond with JSON: {\"status\": \"ok\", \"message\": \"connected\"}",
                apiKey = apiKey,
                model = model,
                jsonMode = true
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
