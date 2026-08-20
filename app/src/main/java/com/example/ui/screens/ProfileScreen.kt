package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.data.AppDatabase
import com.example.data.Profile
import com.example.data.SettingsManager
import com.example.domain.EQUIPMENT
import com.example.domain.HealthConnectSync
import com.example.domain.INJURIES
import com.example.domain.Planner

@Composable
fun ProfileScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    val currentKey by settingsManager.getSetting("geminiKey").collectAsState(initial = "")
    var keyInput by remember { mutableStateOf("") }

    val health = remember { HealthConnectSync(context) }
    val db = remember { AppDatabase.getDatabase(context) }
    var healthGranted by remember { mutableStateOf(false) }
    var importNote by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    // What Coach snaps loads to (bar, plates) and will never load a joint against (injuries).
    // Previously write-once at onboarding with no way to ever change it -- meaning the injury
    // filter in Planner.kt, real and tested on the domain side, had nothing a real user could
    // ever set to make it do anything.
    val profileFlow = remember { db.profileDao().getProfile() }
    val storedProfile by profileFlow.collectAsState(initial = null)
    var barInput by remember { mutableStateOf("20") }
    var selectedPlates by remember { mutableStateOf(setOf<Double>()) }
    var selectedEquipment by remember { mutableStateOf(setOf<String>()) }
    var selectedInjuries by remember { mutableStateOf(setOf<String>()) }
    var trainingSaved by remember { mutableStateOf(false) }

    LaunchedEffect(storedProfile) {
        storedProfile?.toTrainingProfile()?.let { tp ->
            barInput = if (tp.bar % 1 == 0.0) tp.bar.toInt().toString() else tp.bar.toString()
            selectedPlates = tp.plates.toSet()
            selectedEquipment = tp.equipment.toSet()
            selectedInjuries = tp.injuries.toSet()
        }
    }

    // Health Connect grants each record type separately and can be revoked from its own settings at
    // any time, so this is re-read on every visit rather than remembered.
    LaunchedEffect(Unit) { healthGranted = health.hasAnyPermission() }

    val requestHealth = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.health.connect.client.PermissionController
            .createRequestPermissionResultContract(),
    ) { grantedNow ->
        healthGranted = grantedNow.any { it in HealthConnectSync.PERMISSIONS }
        if (healthGranted) {
            scope.launch {
                importing = true
                val summary = health.importInto(db)
                importing = false
                importNote = when {
                    summary.nothingNew && summary.sleepNightsKept > 0 ->
                        "Nothing new — you had already logged those ${summary.sleepNightsKept} nights yourself."
                    summary.nothingNew -> "Connected, but there was nothing in the last 28 days to read."
                    else -> "Imported ${summary.sleepNightsAdded} nights of sleep, " +
                        "${summary.stepDaysAdded} days of steps and ${summary.weighInsAdded} weigh-ins."
                }
            }
        }
    }
    
    LaunchedEffect(currentKey) {
        if (currentKey != null) {
            keyInput = currentKey!!
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Profile & Settings", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Text("AI Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Enter your Gemini API key below. This is stored locally on your device in DataStore preferences for security.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text("Gemini API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    settingsManager.setSetting("geminiKey", keyInput.ifEmpty { null })
                }
            }
        ) {
            Text("Save Key")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Health Connect", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        when (health.availability()) {
            HealthConnectSync.Availability.UNAVAILABLE -> Text(
                "Health Connect is not available on this device, so sleep and weight stay hand-logged.",
                style = MaterialTheme.typography.bodySmall,
            )
            HealthConnectSync.Availability.UPDATE_REQUIRED -> Text(
                "Health Connect needs updating in the Play Store before it can be connected.",
                style = MaterialTheme.typography.bodySmall,
            )
            HealthConnectSync.Availability.AVAILABLE -> {
                Text(
                    if (healthGranted)
                        "Connected. Sleep, steps and weight are read from Health Connect for the last 28 days. Anything you logged by hand is left exactly as you wrote it."
                    else
                        "Read sleep, steps and weight from your phone or watch instead of logging them by hand. Read-only — this app never writes health data back.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!healthGranted) {
                        Button(onClick = { requestHealth.launch(HealthConnectSync.PERMISSIONS) }) {
                            Text("Connect")
                        }
                    } else {
                        Button(
                            enabled = !importing,
                            onClick = {
                                scope.launch {
                                    importing = true
                                    val summary = health.importInto(db)
                                    importing = false
                                    importNote = if (summary.nothingNew) "Already up to date."
                                    else "Imported ${summary.sleepNightsAdded} nights, " +
                                        "${summary.stepDaysAdded} step days, ${summary.weighInsAdded} weigh-ins."
                                }
                            },
                        ) { Text(if (importing) "Reading…" else "Import now") }
                    }
                }
                importNote?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Training setup", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "What Coach snaps your loads to, and what it will never load up an injury with.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = barInput,
            onValueChange = { barInput = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Bar weight (kg)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            ),
            modifier = Modifier.width(180.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text("Plates you have (per side, any number of each)", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Planner.PLATE_SIZES.forEach { kg ->
                val label = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
                FilterChip(
                    selected = kg in selectedPlates,
                    onClick = {
                        selectedPlates = if (kg in selectedPlates) selectedPlates - kg else selectedPlates + kg
                    },
                    label = { Text(label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Equipment available", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EQUIPMENT.forEach { id ->
                FilterChip(
                    selected = id in selectedEquipment,
                    onClick = {
                        selectedEquipment = if (id in selectedEquipment) selectedEquipment - id else selectedEquipment + id
                    },
                    label = { Text(id) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("Injuries to train around", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Lifts that load these are dropped from your plan entirely, not just flagged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            INJURIES.forEach { id ->
                FilterChip(
                    selected = id in selectedInjuries,
                    onClick = {
                        selectedInjuries = if (id in selectedInjuries) selectedInjuries - id else selectedInjuries + id
                    },
                    label = { Text(id) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        val barValid = barInput.toDoubleOrNull()?.let { it > 0 } == true
        Button(
            enabled = barValid,
            onClick = {
                val bar = barInput.toDoubleOrNull() ?: return@Button
                scope.launch {
                    val existing = storedProfile ?: Profile(
                        id = 1, name = "", bodyweight = 75.0, daysPerWeek = 3,
                        bar = bar, plates = "[]", experience = 1, goal = "hypertrophy",
                        equipment = "[]", injuries = "[]", kcalTarget = null, poseModel = "lite",
                    )
                    db.profileDao().insert(
                        existing.copy(
                            bar = bar,
                            plates = doubleListToJson(selectedPlates.sortedDescending()),
                            equipment = listToJson(selectedEquipment.toList()),
                            injuries = listToJson(selectedInjuries.toList()),
                        )
                    )
                    trainingSaved = true
                }
            },
        ) { Text("Save training setup") }
        if (trainingSaved) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Saved. Tomorrow's plan will use it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
