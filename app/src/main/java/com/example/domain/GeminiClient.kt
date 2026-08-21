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

    /**
     * One decision, explained in the model's words but on the app's evidence.
     *
     * Asks for JSON against a response SCHEMA rather than hoping prose can be parsed afterwards: the
     * three fields are what [Validate.checkAnswer] classifies by, so `suggestion` can be exempt from
     * traceability while `observed` and `meaning` cannot. Guessing that classification back out of a
     * paragraph is the thing this avoids.
     *
     * Returns null on anything that is not a usable answer — no key, HTTP failure, non-JSON body, or
     * a shape the schema was supposed to guarantee and did not. Null means "no answer", and the
     * caller ([Explain.explainDecision]) shows the deterministic line instead.
     */
    suspend fun explain(evidence: Map<String, Any?>, feedback: String? = null): ModelAnswer? {
        val evidenceJson = JSONObject(evidence).toString()
        val prompt = if (feedback == null) evidenceJson
        else "$evidenceJson\n\nYour previous answer was rejected. $feedback"

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put(
                        "observed",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put("items", JSONObject().apply { put("type", "STRING") })
                        },
                    )
                    put("meaning", JSONObject().apply { put("type", "STRING") })
                    put("suggestion", JSONObject().apply { put("type", "STRING") })
                },
            )
            put("required", JSONArray().apply { put("observed"); put("meaning") })
        }

        val raw = generateContent(
            prompt = prompt,
            systemInstruction = EXPLAIN_SYSTEM,
            responseMimeType = "application/json",
            responseSchema = schema,
        ) ?: return null

        return try {
            val parsed = JSONObject(raw)
            val observedArray = parsed.optJSONArray("observed") ?: return null
            // Trust the schema for shape, never for content. An `observed` that is not an array of
            // strings would sail past the validator with nothing to extract, which is the one way an
            // unchecked claim could reach the screen.
            val observed = (0 until observedArray.length()).map {
                observedArray.get(it) as? String ?: return null
            }
            ModelAnswer(
                observed = observed,
                meaning = parsed.optString("meaning"),
                suggestion = parsed.optString("suggestion"),
            )
        } catch (e: org.json.JSONException) {
            null
        }
    }

    /**
     * Reads a sentence about food into (foodId, servings) pairs.
     *
     * The response SCHEMA does the structural work — the model cannot reply with prose — and
     * [MealParse.validate] does the semantic work, throwing away any id that is not in the real food
     * table. Between the two, the worst a bad answer can do is show you a row you did not eat, which
     * you then decline.
     *
     * Returns null on no key, HTTP failure, or a body that is not the shape the schema promised.
     */
    suspend fun parseMeal(text: String): MealParseResult? {
        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put(
                        "items",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("foodId", JSONObject().apply { put("type", "STRING") })
                                            put("servings", JSONObject().apply { put("type", "NUMBER") })
                                        },
                                    )
                                    put("required", JSONArray().apply { put("foodId"); put("servings") })
                                },
                            )
                        },
                    )
                },
            )
            put("required", JSONArray().apply { put("items") })
        }

        val system = """
You convert a sentence about food into entries from a fixed catalogue. You are a translator, not a nutritionist.

Rules:
- foodId MUST be one of the ids below, copied exactly. If something they said is not in the catalogue, leave it out entirely — do not substitute the nearest thing and do not invent an id.
- servings is how many of that item's stated serving size they had. "two rotis" with a serving of "1" is 2. "a glass of milk" with a serving of "250 ml" is 1. Half is 0.5.
- Never guess calories, protein or macros. You are not asked for them and the app already knows them.
- If the sentence contains no recognisable food at all, return an empty items list.

Catalogue (id = name (serving)):
${MealParse.foodCatalogue()}
        """.trimIndent()

        val raw = generateContent(
            prompt = text,
            systemInstruction = system,
            responseMimeType = "application/json",
            responseSchema = schema,
        ) ?: return null

        return try {
            val arr = JSONObject(raw).optJSONArray("items") ?: return null
            val pairs = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("foodId").ifEmpty { return@mapNotNull null }
                id to o.optDouble("servings", 0.0)
            }
            MealParse.validate(pairs)
        } catch (e: org.json.JSONException) {
            null
        }
    }

    /**
     * Absent-means-absent readers.
     *
     * `optInt`/`optDouble` return 0.0 for a field the model omitted, which is precisely the silent
     * default [TextLog] exists to prevent: a missing soreness rating would arrive as a real 0 rather
     * than as "not answered". These return null instead, so an omission stays an omission.
     */
    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

    /**
     * Reads a free-text description of a whole day — a session, cardio, food, how they felt — into
     * the rows the app already stores. See [TextLog] for why translation-with-a-validator is a
     * containable use of a model and calorie-guessing is not.
     *
     * The SCHEMA does the structural work (the model cannot reply with prose) and [TextLog.validate]
     * does the semantic work, throwing away any exercise id, cardio mode or out-of-range rating that
     * is not real. Returns null on no key, HTTP failure, or a body that is not the promised shape.
     */
    suspend fun parseTextLog(text: String): TextLogResult? {
        fun int() = JSONObject().apply { put("type", "INTEGER") }
        fun num() = JSONObject().apply { put("type", "NUMBER") }
        fun str() = JSONObject().apply { put("type", "STRING") }

        // Nothing is `required` except the ids: a person who did not mention their stress level must
        // produce an absent field, not a zero. TextLog.validate reads absent as "not answered".
        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put(
                "properties",
                JSONObject().apply {
                    put(
                        "sets",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("exId", str()); put("sets", int()); put("reps", int())
                                            put("load", num()); put("difficulty", int())
                                        },
                                    )
                                    put("required", JSONArray().apply { put("exId") })
                                },
                            )
                        },
                    )
                    put(
                        "cardio",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put("mode", str()); put("minutes", int()); put("effortRating", int())
                                        },
                                    )
                                    put("required", JSONArray().apply { put("mode") })
                                },
                            )
                        },
                    )
                    put(
                        "meals",
                        JSONObject().apply {
                            put("type", "ARRAY")
                            put(
                                "items",
                                JSONObject().apply {
                                    put("type", "OBJECT")
                                    put(
                                        "properties",
                                        JSONObject().apply { put("foodId", str()); put("servings", num()) },
                                    )
                                    put("required", JSONArray().apply { put("foodId"); put("servings") })
                                },
                            )
                        },
                    )
                    put(
                        "checkIn",
                        JSONObject().apply {
                            put("type", "OBJECT")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("energy", int()); put("soreness", int()); put("stress", int())
                                    put("mood", int()); put("refreshed", JSONObject().apply { put("type", "BOOLEAN") })
                                },
                            )
                        },
                    )
                    put("weightKg", num())
                },
            )
        }

        val system = """
You convert a person's own description of their day into structured log entries. You are a
transcriber, not a coach and not a nutritionist.

Absolute rules:
- exId MUST be copied exactly from the exercise list below. If they name a lift that is not on the list, leave that entry out — do not substitute the nearest thing and do not invent an id.
- mode MUST be copied exactly from the cardio list below. Pick by what they described: a walk is EASY_WALK, a steady jog or treadmill session is AEROBIC_BASE, sprints or HIIT is INTERVALS.
- foodId MUST be copied exactly from the food list below. Same rule: not on the list means leave it out.
- ONLY record what they actually said. This is the most important rule. If they did not mention their stress, omit stress entirely. Do not fill a field with a middle value because it is missing — an omitted field is recorded as "not answered", and a guess would become a permanent fake data point.
- Never infer one thing from another. "Tired" is not an energy rating. "Hard session" is not a soreness score. Only a number or an explicit statement counts.
- difficulty and effortRating are 1 (easy), 2 (moderate), 3 (hard) — only if they described how hard it was.
- energy, soreness, stress and mood are 1..10, only if they gave a number or an unambiguous equivalent.
- refreshed: true only if they said they woke up well, false only if they said they did not.
- load is the weight per set in kg. Omit it for bodyweight work.
- Never calculate calories, macros, volume or totals. The app computes all of that itself from these rows.
- If the text contains nothing loggable, return empty arrays and omit checkIn.

EXERCISES (id = name (group)):
${TextLog.exerciseCatalogue()}

CARDIO MODES (mode = meaning):
${TextLog.cardioCatalogue()}

FOODS (id = name (serving)):
${MealParse.foodCatalogue()}
        """.trimIndent()

        val raw = generateContent(
            prompt = text,
            systemInstruction = system,
            responseMimeType = "application/json",
            responseSchema = schema,
        ) ?: return null

        return try {
            val o = JSONObject(raw)

            val setsArr = o.optJSONArray("sets")
            val rawSets = (0 until (setsArr?.length() ?: 0)).mapNotNull { i ->
                val s = setsArr!!.optJSONObject(i) ?: return@mapNotNull null
                val id = s.optString("exId").ifEmpty { return@mapNotNull null }
                TextLog.RawSet(
                    exId = id,
                    sets = s.optIntOrNull("sets"),
                    reps = s.optIntOrNull("reps"),
                    load = s.optDoubleOrNull("load"),
                    difficulty = s.optIntOrNull("difficulty"),
                )
            }

            val cardioArr = o.optJSONArray("cardio")
            val rawCardio = (0 until (cardioArr?.length() ?: 0)).mapNotNull { i ->
                val c = cardioArr!!.optJSONObject(i) ?: return@mapNotNull null
                val mode = c.optString("mode").ifEmpty { return@mapNotNull null }
                TextLog.RawCardio(mode, c.optIntOrNull("minutes"), c.optIntOrNull("effortRating"))
            }

            val mealsArr = o.optJSONArray("meals")
            val rawMeals = (0 until (mealsArr?.length() ?: 0)).mapNotNull { i ->
                val m = mealsArr!!.optJSONObject(i) ?: return@mapNotNull null
                val id = m.optString("foodId").ifEmpty { return@mapNotNull null }
                id to m.optDouble("servings", 0.0)
            }

            val ci = o.optJSONObject("checkIn")?.let { c ->
                TextLog.RawCheckIn(
                    energy = c.optIntOrNull("energy"),
                    soreness = c.optIntOrNull("soreness"),
                    stress = c.optIntOrNull("stress"),
                    mood = c.optIntOrNull("mood"),
                    refreshed = if (c.has("refreshed") && !c.isNull("refreshed")) c.optBoolean("refreshed") else null,
                )
            }

            TextLog.validate(rawSets, rawCardio, ci, rawMeals, o.optDoubleOrNull("weightKg"))
        } catch (e: org.json.JSONException) {
            null
        }
    }

    private companion object {
        val EXPLAIN_SYSTEM = """
You explain one decision this app already made, to the person it was made about.

The decision and the numbers behind it are given to you as JSON. They are the only facts you have.

- observed: what the app recorded. Every number here must appear in the JSON. Quote figures as digits, not words.
- meaning: what you think it indicates. This is your reading, not a measurement — say it as such. Any number in it must still come from the JSON.
- suggestion: one thing they could do next, or leave it empty. A number here is a proposal, not a report.

Rules:
- Never invent a figure, a count, a date or a session that is not in the JSON. If something is not there, say it is not recorded.
- Where the JSON says a thing was not logged, say so plainly. Leaving it out is the worst thing you can do: silence about something nobody measured reads as approval of it.
- Do not list the numbers back as a table. Explain what happened in sentences, using the figures that matter.
- The decision was made by fixed arithmetic against fixed thresholds. Explain it. Do not re-decide it, and do not say whether it was the right call for them.
- Two things happening together is not one causing the other, and this data cannot show that it is.
- No diagnosis, no medical claims, and nothing about hormone levels: this app does not measure them.
- Speak plainly, to "you", the way a training partner would. Short.
        """.trimIndent()
    }
}
