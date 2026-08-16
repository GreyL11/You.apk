package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.HealthCoachEngine
import com.example.domain.Nutrition
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    onNavigateToWeeklyReview: () -> Unit = {},
    onNavigateToLiveSession: (exId: String) -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application))
) {
    val state by viewModel.dashboardState.collectAsState()
    
    var showHydrationSheet by remember { mutableStateOf(false) }
    var showSkinSheet by remember { mutableStateOf(false) }
    var showMealSheet by remember { mutableStateOf(false) }
    var showWorkoutSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }

    if (showHydrationSheet) {
        HydrationSheet(
            onDismiss = { showHydrationSheet = false },
            onLog = { amount -> 
                viewModel.logHydration(amount)
                showHydrationSheet = false
            }
        )
    }

    if (showMealSheet) {
        MealSheet(
            onDismiss = { showMealSheet = false },
            onLog = { foodId, qty ->
                viewModel.logFood(foodId, qty)
                showMealSheet = false
            }
        )
    }

    if (showSkinSheet) {
        SkinSheet(
            onDismiss = { showSkinSheet = false },
            onLog = { data ->
                viewModel.logSkinRoutine(data)
                showSkinSheet = false
            }
        )
    }
    
    if (showWorkoutSheet) {
        WorkoutSheet(
            onDismiss = { showWorkoutSheet = false },
            onStartLiveSession = { exId ->
                showWorkoutSheet = false
                onNavigateToLiveSession(exId)
            },
            onManualLog = { exId, reps, load ->
                viewModel.logTraining(exId, reps, load)
                showWorkoutSheet = false
            }
        )
    }
    
    if (showSleepSheet) {
        SleepSheet(
            onDismiss = { showSleepSheet = false },
            onLog = { hours ->
                viewModel.logSleep(hours)
                showSleepSheet = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, bottom = 80.dp)
    ) {
        // 1. HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Today",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.profile?.name?.take(1)?.uppercase() ?: "U",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. TODAY'S TRAINING
            DashboardSectionTitle("Training")
            val hasTraining = state.todayTraining != null && state.todayTraining!!.exercises.isNotEmpty()
            if (hasTraining) {
                val trainingTitle = state.todayTraining!!.name
                val uncompleted = state.todayTraining!!.exercises.firstOrNull { ex -> 
                    state.todayLogEntries.none { log -> log.exId == ex.exId } 
                }
                
                DashboardCard(
                    icon = Icons.Filled.FitnessCenter,
                    title = if (state.hasCompletedTraining || uncompleted == null) "Workout Completed" else "Today's Workout: $trainingTitle",
                    subtitle = if (state.hasCompletedTraining || uncompleted == null) "Great job today!" else "${state.todayTraining!!.exercises.size} exercises planned",
                    onClick = {
                        if (uncompleted != null) {
                            onNavigateToLiveSession(uncompleted.exId)
                        } else {
                            showWorkoutSheet = true
                        }
                    },
                    buttonText = if (state.hasCompletedTraining || uncompleted == null) "Log Extra" else "Start Session",
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    onColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                state.todayTraining!!.exercises.forEach { ex ->
                    val isDone = state.todayLogEntries.any { log -> log.exId == ex.exId }
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToLiveSession(ex.exId) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(ex.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
                                Text("${ex.sets} sets x ${ex.reps} reps @ ${if (ex.load > 0) "${ex.load}kg" else "bodyweight"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            if (isDone) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Start", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                DashboardCard(
                    icon = Icons.Filled.SelfImprovement,
                    title = "Rest Day",
                    subtitle = "No workout scheduled today. Recover well!",
                    onClick = { showWorkoutSheet = true },
                    buttonText = "Log Anyway",
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. HEALTH COACH / NEXT ACTION
            if (state.nextBestAction != null) {
                DashboardSectionTitle("Next Best Action")
                HealthCoachCard(
                    nba = state.nextBestAction,
                    onSkip = { state.nextBestAction?.let { viewModel.skipAction(it.actionId, it.domain) } },
                    onLater = { state.nextBestAction?.let { viewModel.postponeAction(it.actionId, it.domain) } },
                    onStartHydration = { showHydrationSheet = true },
                    onStartMeal = { showMealSheet = true },
                    onStartSkin = { showSkinSheet = true },
                    onStartTraining = { showWorkoutSheet = true },
                    onStartSleep = { showSleepSheet = true }
                )
            }

            // 4. NUTRITION
            DashboardSectionTitle("Nutrition")
            val nonWaterMeals = state.todayMeals.filter { it.foodId != "water" }
            val mealDesc = if (nonWaterMeals.isEmpty()) "No meals logged yet." else "${nonWaterMeals.size} meals/snacks logged."
            DashboardCard(
                icon = Icons.Filled.Restaurant,
                title = "Food Logging",
                subtitle = mealDesc,
                onClick = { showMealSheet = true },
                buttonText = "Log Meal"
            )

            // 5. HYDRATION
            DashboardSectionTitle("Hydration")
            val hydrationPct = if (state.waterTarget > 0) (state.waterIntake.toDouble() / state.waterTarget) else 0.0
            DashboardCardWithProgress(
                icon = Icons.Filled.LocalDrink,
                title = "Water Intake",
                subtitle = "${state.waterIntake} ml / ${if (state.waterTarget > 0) state.waterTarget.toString() + " ml" else "Target not set"}",
                progress = hydrationPct.toFloat().coerceIn(0f, 1f),
                onClick = { showHydrationSheet = true },
                buttonText = "Add Water"
            )

            // 6. SKIN / ROUTINE
            DashboardSectionTitle("Skin Routine")
            val skinDesc = if (state.todayRow?.skin != null) "Routine completed." else "No routine logged."
            DashboardCard(
                icon = Icons.Filled.Face,
                title = "Skincare",
                subtitle = skinDesc,
                onClick = { showSkinSheet = true },
                buttonText = if (state.todayRow?.skin != null) "Update" else "Log Routine"
            )

            // 7. SLEEP / RECOVERY
            DashboardSectionTitle("Sleep")
            val sleepsDesc = if (state.todayRow?.sleeps != null) "Sleep logged." else "No sleep data."
            DashboardCard(
                icon = Icons.Filled.Nightlight,
                title = "Rest & Recovery",
                subtitle = sleepsDesc,
                onClick = { showSleepSheet = true },
                buttonText = if (state.todayRow?.sleeps != null) "Update" else "Log Sleep"
            )

            // 8. WEEKLY INSIGHTS
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onNavigateToWeeklyReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.Insights, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Weekly Insights", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun DashboardSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp)
    )
}

@Composable
fun DashboardCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    buttonText: String,
    color: Color = MaterialTheme.colorScheme.surface,
    onColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(onColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = title, tint = onColor)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onColor)
                    Text(text = subtitle, fontSize = 14.sp, color = onColor.copy(alpha = 0.7f))
                }
            }
            TextButton(onClick = onClick) {
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DashboardCardWithProgress(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit,
    buttonText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
                TextButton(onClick = onClick) {
                    Text(buttonText, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )
        }
    }
}

@Composable
fun HealthCoachCard(
    nba: HealthCoachEngine.Candidate?,
    onSkip: () -> Unit,
    onLater: () -> Unit,
    onStartHydration: () -> Unit,
    onStartMeal: () -> Unit,
    onStartSkin: () -> Unit,
    onStartTraining: () -> Unit,
    onStartSleep: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.onPrimaryContainer, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "COACH TIP",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }
            Text(
                text = nba?.tier?.name ?: "ANALYZING",
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Text(
            text = nba?.title ?: "All caught up for now.",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            lineHeight = 22.sp
        )
        
        Text(
            text = nba?.reason ?: "Your data looks great.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
        
        if (nba?.tier == HealthCoachEngine.Tier.ACTIONABLE_NOW || nba?.tier == HealthCoachEngine.Tier.DATA_COLLECTION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        when (nba.domain) {
                            "hydration" -> onStartHydration()
                            "nutrition" -> onStartMeal()
                            "skinRoutine" -> onStartSkin()
                            "training" -> onStartTraining()
                            "hormonalLifestyle" -> onStartSleep()
                            else -> onStartHydration()
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "Start", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onLater,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text(text = "Later", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onSkip,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text(text = "Skip", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
