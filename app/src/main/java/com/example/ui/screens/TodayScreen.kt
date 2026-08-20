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
    /** The action a notification was tapped for, so the tap lands on the thing it asked about. */
    openActionId: String? = null,
    viewModel: TodayViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application))
) {
    val state by viewModel.dashboardState.collectAsState()

    var showHydrationSheet by remember { mutableStateOf(false) }
    var showSkinSheet by remember { mutableStateOf(false) }
    var showMealSheet by remember { mutableStateOf(false) }
    var showWorkoutSheet by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showWeightSheet by remember { mutableStateOf(false) }

    // A notification that asks a question and then drops you on a dashboard has wasted the
    // interruption. The actionId the coach already puts in the notification opens the sheet that
    // answers it. "build_baseline" and the GOING_WELL ids open nothing — there is no one sheet that
    // answers "start somewhere", and a tap must never open a sheet at random.
    LaunchedEffect(Unit) { viewModel.syncHealthConnect() }

    LaunchedEffect(openActionId) {
        when (openActionId) {
            "meal_log" -> showMealSheet = true
            "hydrate_now" -> showHydrationSheet = true
            "skin_log" -> showSkinSheet = true
            "hormone_sleep" -> showSleepSheet = true
            "train_today" -> showWorkoutSheet = true
        }
    }

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
            },
            onParse = { text -> viewModel.parseMeal(text) },
            onLogAll = { items ->
                viewModel.logFoods(items)
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
                // Without the real profile, Coach's plate-snapped progression silently used the
                // default plate set for every user regardless of what they actually own.
                viewModel.logTraining(exId, reps, load, profile = state.trainingProfile)
                showWorkoutSheet = false
            }
        )
    }
    
    if (showWeightSheet) {
        WeightSheet(
            lastKg = state.weightTrend?.now,
            onDismiss = { showWeightSheet = false },
            onLog = { kg ->
                viewModel.logWeight(kg)
                showWeightSheet = false
            },
        )
    }

    if (showSleepSheet) {
        SleepSheet(
            onDismiss = { showSleepSheet = false },
            onLog = { bed, wake ->
                viewModel.logSleep(bed, wake)
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Today",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    style = MaterialTheme.typography.labelLarge,
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
            // Only shown when it's saying something worth interrupting for -- a real full-session,
            // high-confidence day says nothing here rather than a banner every single day.
            state.trainingIntensity?.let { intensity ->
                if (intensity.decision != com.example.domain.TrainingIntensityDecision.Decision.FULL_SESSION) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                when (intensity.decision) {
                                    com.example.domain.TrainingIntensityDecision.Decision.RECOVERY_DAY -> "Recovery day recommended"
                                    com.example.domain.TrainingIntensityDecision.Decision.REDUCED_SESSION -> "Reduced session recommended"
                                    else -> "Not enough data for a training call yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(intensity.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
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
                                Text(ex.name, style = MaterialTheme.typography.titleMedium, color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${ex.sets} sets x ${ex.reps} reps @ " + if (ex.load > 0)
                                        // The number alone leaves the actual loading as homework at
                                        // the rack. This is the same Planner.loadout() that Coach
                                        // already snaps progression to -- now shown, not just used.
                                        com.example.domain.Planner.loadoutText(ex.load, state.trainingProfile)
                                    else "bodyweight",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
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
            // The macro total includes water/coffee/chai too (a chai's calories are real), even
            // though the meal COUNT above only counts food — a drink is not a "meal logged".
            val mealDesc = if (nonWaterMeals.isEmpty()) {
                "No meals logged yet."
            } else {
                val m = state.todayMacros
                "${nonWaterMeals.size} meals/snacks · ${m.kcal} kcal, ${m.protein}g protein, " +
                    "${m.carbs}g carbs, ${m.fat}g fat"
            }
            DashboardCard(
                icon = Icons.Filled.Restaurant,
                title = "Food Logging",
                subtitle = mealDesc,
                onClick = { showMealSheet = true },
                buttonText = "Log Meal"
            )

            // Only what's actually known — unknownServings items (whole fruit, rice, etc.) are
            // never silently counted as zero sugar, see Nutrition.SugarStatus's own doc comment.
            state.sugarTargetGrams?.let { target ->
                val s = state.sugarStatus
                val unknownNote = if (s.unknownServings > 0) " (${s.unknownServings} logged item${if (s.unknownServings == 1) "" else "s"} not sugar-tracked)" else ""
                Text(
                    "Sugar: ${s.knownGrams}g known of a ${target}g target$unknownNote",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // 4b. FAT LOSS / BODY COMPOSITION
            //
            // The honest version of "help me lose fat": a target you confirmed, today's intake
            // against it, and the scale correcting the target over weeks. No body-fat percentage
            // anywhere -- a scale measures MASS, and mass is not composition. Nothing here targets
            // fat in one place either: spot reduction is not a thing, so there is no "face fat"
            // reading to show, only whole-body change over time.
            state.targets?.let { t ->
                DashboardSectionTitle(
                    when (state.phase) {
                        Nutrition.Phase.CUT -> "Fat loss"
                        Nutrition.Phase.GAIN -> "Gaining"
                        Nutrition.Phase.MAINTAIN -> "Body composition"
                    }
                )

                // Phase picker: cutting, holding, gaining. Separate from the training scheme.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Nutrition.Phase.CUT to "Lose fat",
                        Nutrition.Phase.MAINTAIN to "Hold",
                        Nutrition.Phase.GAIN to "Gain",
                    ).forEach { (p, label) ->
                        FilterChip(
                            selected = state.phase == p,
                            onClick = { viewModel.setPhase(p) },
                            label = { Text(label) },
                        )
                    }
                }

                val eaten = state.todayMacros
                val kcalLeft = t.kcal - eaten.kcal
                val proteinLeft = t.protein - eaten.protein
                DashboardCardWithProgress(
                    icon = Icons.Filled.Restaurant,
                    title = if (kcalLeft >= 0) "$kcalLeft kcal left today" else "${-kcalLeft} kcal over",
                    subtitle = "${eaten.kcal} of ${t.kcal} kcal - protein ${eaten.protein}/${t.protein}g" +
                        if (proteinLeft > 0) " ($proteinLeft to go)" else " (hit)",
                    progress = (eaten.kcal.toFloat() / t.kcal).coerceIn(0f, 1f),
                    onClick = { showMealSheet = true },
                    buttonText = "Log Food"
                )

                // The weigh-in, and the trend that is the only real check on the target above.
                val trend = state.weightTrend
                val trendDesc = when {
                    trend?.now == null -> "No weigh-in yet. This is what makes the calorie target checkable."
                    trend.changeKg == null -> "${trend.now} kg. One more weigh-in and this starts showing a trend."
                    trend.changeKg == 0.0 -> "${trend.now} kg, flat over ${trend.days} days (${trend.points} weigh-ins)."
                    trend.changeKg!! < 0 -> "${trend.now} kg, down ${-trend.changeKg!!} kg over ${trend.days} days."
                    else -> "${trend.now} kg, up ${trend.changeKg} kg over ${trend.days} days."
                }
                DashboardCard(
                    icon = Icons.Filled.MonitorWeight,
                    title = "Weight",
                    subtitle = trendDesc,
                    onClick = { showWeightSheet = true },
                    buttonText = if (trend?.now == null) "Log Weight" else "Update"
                )

                // The one line about the last four weeks, and only when there is something real to
                // say -- coachLine already refuses to speak from too little data.
                state.nutritionCoachLine?.let { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                // The scale's correction to the target. Shown only when the data earns it, and it is
                // never applied without this button being pressed.
                state.kcalSuggestion?.let { sg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("FROM YOUR SCALE", style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Target ${sg.from} to ${sg.to} kcal",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "You are ${sg.reason}. You have actually been eating ${sg.eating} a day.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { viewModel.acceptKcalSuggestion(sg.to) }) {
                                Text("Use ${sg.to} kcal")
                            }
                        }
                    }
                }
            }

            // 5. HYDRATION
            DashboardSectionTitle("Hydration")
            val hydrationPct = if (state.waterTarget > 0) (state.waterIntake.toDouble() / state.waterTarget) else 0.0
            DashboardCardWithProgress(
                icon = Icons.Filled.LocalDrink,
                title = "Water Intake",
                subtitle = "${state.waterIntake} ml / ${if (state.waterTarget > 0) state.waterTarget.toString() + " ml" else "Target not set"}" +
                    (state.hydrationVsBaseline?.let { " · $it" } ?: ""),
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
            val sleepsDesc = if (state.todayRow?.sleeps != null) {
                "Sleep logged." + (state.sleepVsBaseline?.let { " $it" } ?: "")
            } else "No sleep data."
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
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    Text(text = title, style = MaterialTheme.typography.titleMedium, color = onColor)
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = onColor.copy(alpha = 0.75f))
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
                        Text(text = title, style = MaterialTheme.typography.titleMedium)
                        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "DO THIS NEXT",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = when (nba?.tier) {
                    HealthCoachEngine.Tier.ACTIONABLE_NOW -> "NOW"
                    HealthCoachEngine.Tier.DATA_COLLECTION -> "TO LOG"
                    HealthCoachEngine.Tier.GOING_WELL -> "ON TRACK"
                    null -> "READING"
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        
        Text(
            text = nba?.title ?: "All caught up for now.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        
        Text(
            text = nba?.reason ?: "Your data looks great.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
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
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "Start", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onLater,
                    modifier = Modifier.height(40.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text(text = "Later", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onSkip,
                    modifier = Modifier.height(40.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurfaceVariant, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text(text = "Skip", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
