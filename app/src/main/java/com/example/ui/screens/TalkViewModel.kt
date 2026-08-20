package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.domain.Digest
import com.example.domain.GeminiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import org.json.JSONObject

class TalkViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val geminiClient = GeminiClient(SettingsManager(application))
    
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping

    init {
        loadMessages()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _messages.value = db.chatMessageDao().getAllSync()
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            val now = LocalDateTime.now().toString()
            val userMsg = ChatMessage(role = "user", content = content, at = now)
            db.chatMessageDao().insert(userMsg)
            _messages.value = _messages.value + userMsg
            _isTyping.value = true

            // Gather context
            val dayKey = LocalDateTime.now().toLocalDate().toString()
            val todayRow = db.dayRowDao().getSync(dayKey)
            val recentLogs = db.logEntryDao().getAllSync().takeLast(10)

            val evidenceMap = mutableMapOf<String, Any?>()
            
            // Get NBA Context
            val recentMeals = db.mealDao().getByDateSync(dayKey)
            val recentOutcomes = db.actionOutcomeDao().getAllSync()
            val ctx = com.example.domain.HealthCoachEngine.Context(
                now = LocalDateTime.now(),
                todayRow = todayRow,
                recentMeals = recentMeals,
                recentLogEntries = recentLogs,
                recentOutcomes = recentOutcomes,
                profile = db.profileDao().getProfileSync()
            )
            val nba = com.example.domain.HealthCoachEngine.selectNextBestAction(ctx)
            
            evidenceMap["coach"] = mapOf(
                "current_recommendation" to nba?.title,
                "reason" to nba?.reason,
                "limitation" to "This app relies solely on observed lifestyle behaviors. We cannot and do not measure hormone levels or provide medical diagnosis."
            )
            
            if (todayRow != null) {
                // Sleep goes in as numbers, not as the raw JSON blob it is stored in: the model
                // cannot be asked to parse a string and then be held to the figures inside it. Main
                // and total stay separate for the same reason the engine keeps them apart — a short
                // night plus a nap is not a long night.
                val sleep = com.example.domain.MoodInsights.sleepSummary(todayRow)
                evidenceMap["today"] = mapOf(
                    "mood" to todayRow.mood,
                    "sleep" to if (sleep.main == null) "Not logged today." else mapOf(
                        "mainHours" to sleep.main,
                        "totalHours" to sleep.total,
                        "naps" to sleep.naps,
                        "wokeAt" to todayRow.wake,
                    ),
                    "skin" to todayRow.skin
                )
            } else {
                evidenceMap["today"] = "Absent. No check-in completed today."
            }

            // The 28-day lifestyle read, so "what should I do about my sleep" is answered from the
            // record rather than from today alone. The engine's own line rides along with it: the
            // model is being asked to talk about a decision, not to make one.
            val lifestyle = com.example.domain.TInputs.read(
                rows = db.dayRowDao().getAllSync(),
                weights = db.weightDao().getAllSync(),
                logs = db.logEntryDao().getAllSync(),
                rounds = db.roundDao().getAllSync(),
                today = LocalDateTime.now().toLocalDate(),
            )
            evidenceMap["lifestyle_28d"] = mapOf(
                "sleep" to mapOf(
                    "verdict" to lifestyle.sleep.verdict,
                    "mainAvgHours" to lifestyle.sleep.avg,
                    "nightsLogged" to lifestyle.sleep.nights,
                ),
                "wake" to lifestyle.wake?.let {
                    mapOf(
                        "usual" to it.median,
                        "spreadHours" to it.spreadHours,
                        "regular" to it.regular,
                    )
                },
                "trainingDays" to lifestyle.training.days,
                "weightChangeKg" to lifestyle.weight.kg,
                "app_decided_next" to lifestyle.advice.text,
                "limitation" to com.example.domain.TInputs.HORMONAL_BOUNDARY,
            )
            if (recentLogs.isNotEmpty()) {
                evidenceMap["recent_workouts"] = recentLogs.map { mapOf("exId" to it.exId, "reps" to it.reps, "load" to it.load) }
            }

            val pruned = Digest.prune(evidenceMap)
            val systemContext = Digest.RULES + "\n\nEVIDENCE:\n" + JSONObject(pruned).toString(2)
            
            // Reconstruct history (simple text compilation for this client)
            val historyText = _messages.value.takeLast(10).joinToString("\n") { "${if(it.role=="user") "User" else "Coach"}: ${it.content}" }
            
            val prompt = "$historyText\nUser: $content\nCoach:"
            
            val replyText = geminiClient.generateContent(prompt, systemContext)
            
            val aiMsg = ChatMessage(role = "model", content = replyText ?: "I'm sorry, I cannot connect right now.", at = LocalDateTime.now().toString())
            db.chatMessageDao().insert(aiMsg)
            db.chatMessageDao().enforceCap() // defined but never invoked before — chat_message grew unbounded
            _messages.value = _messages.value + aiMsg
            _isTyping.value = false
        }
    }
}
