package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SettingsManager
import com.example.data.AppDatabase
import com.example.data.Profile
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)
    private val db = AppDatabase.getDatabase(application)

    fun completeOnboarding(primaryGoal: String, name: String, bodyweight: Double, daysPerWeek: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            db.profileDao().insert(
                Profile(
                    id = 1,
                    name = name,
                    bodyweight = bodyweight,
                    daysPerWeek = daysPerWeek,
                    bar = 20.0,
                    plates = "[25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25]",
                    experience = 1,
                    // The training SCHEME, which is a separate thing from primaryGoal below:
                    // primaryGoal decides which domain the coach asks about first, this decides
                    // sets and reps. Changing the scheme needs a question this flow does not ask.
                    goal = "hypertrophy",
                    equipment = "[\"barbell\",\"dumbbell\",\"cable\",\"bodyweight\"]",
                    injuries = "[]",
                    kcalTarget = null,
                    poseModel = "lite"
                )
            )
            settingsManager.setSetting("primary_goal", primaryGoal)
            settingsManager.setSetting("onboarding_complete", "true")
            onComplete()
        }
    }
}

/** What choosing a goal actually changes, said out loud rather than left to be guessed. */
private val GOALS = listOf(
    Triple("training", "Training", "Sessions, progression, and what to put on the bar"),
    Triple("hormonalLifestyle", "Sleep and recovery", "Sleep length, wake regularity, the lifestyle inputs"),
    Triple("hydration", "Hydration", "Daily fluid against a target from your bodyweight"),
    Triple("skinRoutine", "Skin", "Routine consistency, and what your habits track with"),
)

/** Bodyweight bounds. Outside these it is a typo, and a typo here silently sets every starting load. */
private const val MIN_KG = 30.0
private const val MAX_KG = 250.0

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application))
) {
    var step by remember { mutableStateOf(1) }
    // No preselection. A default here is a decision made on someone's behalf, and it would be the
    // one the coach then nags them about for weeks.
    var selectedGoal by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    // Empty, not "75.0". A prefilled weight that is never touched gets recorded as a measurement,
    // and Planner computes every starting load from it while Nutrition computes the water target.
    var bodyweight by remember { mutableStateOf("") }
    var daysPerWeek by remember { mutableStateOf(3) }

    val kg = bodyweight.replace(',', '.').toDoubleOrNull()
    val kgValid = kg != null && kg >= MIN_KG && kg <= MAX_KG

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 24.dp),
    ) {
        // Where you are and how to go back. Three anonymous screens in a row is the thing that makes
        // a setup flow feel endless.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 1) {
                IconButton(onClick = { step -= 1 }, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text(
                "Step $step of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { step / 3f },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                1 -> {
                    Text(
                        "You",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Lifts, food and recovery in one place. It reads what you log and tells you the next thing worth doing — never a score, and never a verdict on something it has not seen.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 26.sp,
                    )
                }

                2 -> {
                    Text(
                        "What should it look after first?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "It tracks all of them. This just decides what it asks you about first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))

                    GOALS.forEach { (id, label, detail) ->
                        val selected = selectedGoal == id
                        // A Card, not Modifier.background(): a Card sets the CONTENT colour for
                        // everything inside it. A raw background paints the surface and leaves the
                        // text at whatever colour it inherited, which is how these labels ended up
                        // dark-grey-on-dark-grey and effectively unreadable.
                        Card(
                            onClick = { selectedGoal = id },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                // Top, not centre: centred, the check sits level with the middle of a
                                // two-line subtitle and the text wraps around it, which reads as a
                                // layout accident rather than a tick.
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp,
                                    )
                                }
                                // One affordance, not a radio button beside a tappable row.
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Text(
                        "Your starting point",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Bodyweight sets your starting loads and your fluid target, so it is worth being real. Nothing here is guessed for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = bodyweight,
                        onValueChange = { bodyweight = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Bodyweight") },
                        suffix = { Text("kg") },
                        singleLine = true,
                        isError = bodyweight.isNotEmpty() && !kgValid,
                        supportingText = if (bodyweight.isNotEmpty() && !kgValid) {
                            { Text("Somewhere between ${MIN_KG.toInt()} and ${MAX_KG.toInt()} kg") }
                        } else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Training days a week",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Seven values, so seven targets. Typing a digit into a text field to choose a
                    // number between 1 and 7 is a keyboard for no reason.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = daysPerWeek == d,
                                onClick = { daysPerWeek = d },
                                label = { Text("$d") },
                                // The default selected chip picks up secondaryContainer, which in this
                                // palette is green — the only green on a screen whose selection colour
                                // is blue everywhere else. Selection should look like one thing.
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                when (step) {
                    1 -> step = 2
                    2 -> step = 3
                    // kgValid gates the button, so this is a real number by the time it is read —
                    // no "?: 75.0" fallback quietly inventing a bodyweight.
                    else -> viewModel.completeOnboarding(
                        selectedGoal ?: "training", name.trim(), kg!!, daysPerWeek,
                    ) { onFinish() }
                }
            },
            enabled = when (step) {
                2 -> selectedGoal != null
                3 -> kgValid
                else -> true
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                when (step) {
                    1 -> "Get started"
                    2 -> "Continue"
                    else -> "Finish setup"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
