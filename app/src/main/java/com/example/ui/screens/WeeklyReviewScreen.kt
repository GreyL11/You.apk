package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class WeeklyReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    
    data class ReviewState(
        val training: String,
        val hydration: String,
        val skinRoutine: String,
        val sleepRecovery: String,
        val hormonalLifestyle: String,
        val whatWentWell: String,
        val whatNeedsAttention: String,
        val nextWeekFocus: String
    )
    
    private val _state = MutableStateFlow<ReviewState?>(null)
    val state: StateFlow<ReviewState?> = _state

    init {
        generateReview()
    }

    private fun generateReview() {
        viewModelScope.launch {
            val outcomes = db.actionOutcomeDao().getAllSync()
            val allMealsCount = db.mealDao().getAllSync().size
            val allLogsCount = db.logEntryDao().getAllSync().size
            val allRowsCount = db.dayRowDao().getAllSync().size
            val totalHistorical = allMealsCount + allLogsCount + allRowsCount + outcomes.size
            val isNewUser = totalHistorical == 0

            val now = LocalDateTime.now()
            val weekAgo = now.minusDays(7).toString()
            
            val recentOutcomes = outcomes.filter { it.at >= weekAgo }
            
            if (isNewUser) {
                _state.value = ReviewState(
                    training = "No training history yet.\nLog your first session and your coach can start identifying patterns.",
                    hydration = "No hydration history yet.\nLog what you drink today so your coach can understand your routine.",
                    skinRoutine = "No routine history yet.\nStart logging your cleanser, serum or SPF use.",
                    sleepRecovery = "No sleep data yet.\nLog a few days before the coach tries to identify a recovery pattern.",
                    hormonalLifestyle = "Absent evidence.",
                    whatWentWell = "Your first week starts here. There's not enough history to identify trends yet. That's expected.",
                    whatNeedsAttention = "Start with the basics today.",
                    nextWeekFocus = "Build your baseline."
                )
                return@launch
            }
            
            // Hydration
            val hydCompleted = recentOutcomes.count { it.domain == "hydration" && it.event == "COMPLETED" }
            val hydrationStatus = if (hydCompleted > 0) "Logged $hydCompleted times this week." else "Insufficient data."
            
            // Skin
            val skinCompleted = recentOutcomes.count { it.domain == "skinRoutine" && it.event == "COMPLETED" }
            val skinStatus = if (skinCompleted > 0) "Completed $skinCompleted times this week." else "Insufficient data."
            
            // Training (checking log entries)
            val logs = db.logEntryDao().getAllSync().filter { it.at >= weekAgo }
            val trainingStatus = if (logs.isNotEmpty()) "Completed ${logs.size} workout events." else "Insufficient data."
            
            // Sleep/Hormonal (Checking day rows)
            val dayRows = db.dayRowDao().getAllSync().filter { it.dayKey >= weekAgo.substring(0, 10) }
            val sleepLoggedCount = dayRows.count { it.sleeps != null }
            val sleepStatus = if (sleepLoggedCount > 0) "Sleep logged $sleepLoggedCount times." else "Insufficient data."
            
            // Calculate what went well / needs attention
            var well = "You need more data before trends can be established."
            var attention = "Start logging hydration and sleep to build a baseline."
            var focus = "Focus on consistency: log your daily actions."
            
            val strengths = mutableListOf<String>()
            val weaknesses = mutableListOf<String>()
            
            if (hydCompleted >= 5) strengths.add("Hydration") else weaknesses.add("Hydration")
            if (skinCompleted >= 5) strengths.add("Skincare") else weaknesses.add("Skincare")
            if (logs.size >= 3) strengths.add("Training") else weaknesses.add("Training")
            
            if (strengths.isNotEmpty()) {
                well = strengths.joinToString(", ") + " showed great consistency."
            }
            if (weaknesses.isNotEmpty()) {
                attention = weaknesses.joinToString(", ") + " lacked logging or completion."
                focus = "Improve your consistency with ${weaknesses.first()}."
            }
            
            _state.value = ReviewState(
                training = trainingStatus,
                hydration = hydrationStatus,
                skinRoutine = skinStatus,
                sleepRecovery = sleepStatus,
                hormonalLifestyle = if (sleepStatus != "Insufficient data.") "Supported by sleep data." else "Absent evidence.",
                whatWentWell = well,
                whatNeedsAttention = attention,
                nextWeekFocus = focus
            )
        }
    }
}

@Composable
fun WeeklyReviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: WeeklyReviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    )
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text("Weekly Review") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Text("<")
                }
            }
        )
        
        if (state == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("YOUR WEEK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                
                ReviewSection("TRAINING", state!!.training)
                ReviewSection("HYDRATION", state!!.hydration)
                ReviewSection("SKIN ROUTINE", state!!.skinRoutine)
                ReviewSection("RECOVERY / SLEEP", state!!.sleepRecovery)
                ReviewSection("HORMONAL LIFESTYLE", state!!.hormonalLifestyle)
                
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("WHAT WENT WELL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state!!.whatWentWell, fontSize = 16.sp)
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text("WHAT NEEDS ATTENTION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state!!.whatNeedsAttention, fontSize = 16.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("NEXT WEEK'S FOCUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(state!!.nextWeekFocus, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewSection(title: String, content: String) {
    Column {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(content, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
