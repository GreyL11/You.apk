package com.example.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.example.data.SettingsManager
import java.io.IOException

class GeminiClient(private val settingsManager: SettingsManager) {
    private val client = OkHttpClient()

    suspend fun generateContent(
        prompt: String, 
        systemInstruction: String? = null,
        responseMimeType: String? = null,
        responseSchema: JSONObject? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.getSetting("geminiKey").firstOrNull() ?: return@withContext null
        if (apiKey.isEmpty()) return@withContext null
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"
        
        val contentsArray = JSONArray().apply {
            put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            })
        }
        
        val payload = JSONObject().apply {
            put("contents", contentsArray)
            if (systemInstruction != null) {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    })
                })
            }
            if (responseMimeType != null || responseSchema != null) {
                put("generationConfig", JSONObject().apply {
                    responseMimeType?.let { put("responseMimeType", it) }
                    responseSchema?.let { put("responseSchema", it) }
                })
            }
        }
        
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
            
        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return@withContext null
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text")
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
