package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.Nutrition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HydrationSheet(
    onDismiss: () -> Unit,
    onLog: (Double) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Log Hydration", style = MaterialTheme.typography.titleLarge)
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { onLog(1.0) }) { Text("250ml") }
                Button(onClick = { onLog(2.0) }) { Text("500ml") }
                Button(onClick = { onLog(4.0) }) { Text("1L") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Log a real food entry — the Android equivalent of the legacy Eat tab's "Add" list
 * (www/nutrition.js's FOODS table + www/app.js's logFood()). Scope is deliberately basic, matching
 * what the legacy app's core logging interaction actually is: pick a food, log a serving (or a
 * half/double of one). No calorie/macro totals, no targets, no custom foods here — that's the
 * separate nutrition-intelligence layer (targets()/verdict()/coachLine() etc. in legacy), which
 * this task does not port. Logging itself must never be fake: every button here calls onLog with
 * a real foodId from Nutrition.FOODS and a real quantity, nothing hardcoded or sampled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSheet(
    onDismiss: () -> Unit,
    onLog: (foodId: String, qty: Double) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Log a Meal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                for (cat in Nutrition.FOOD_CATS) {
                    val foods = Nutrition.FOODS.filter { it.cat == cat }
                    if (foods.isEmpty()) continue
                    item {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(foods) { food ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(food.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    food.serving,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { onLog(food.id, 0.5) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("½") }
                                Button(onClick = { onLog(food.id, 1.0) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Add") }
                                OutlinedButton(onClick = { onLog(food.id, 2.0) }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("×2") }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheet(
    onDismiss: () -> Unit,
    onLog: (String) -> Unit
) {
    var spf by remember { mutableStateOf(false) }
    var cleanser by remember { mutableStateOf(false) }
    var moisturize by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Log Skin Routine", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = cleanser, onCheckedChange = { cleanser = it })
                Text("Cleanser / Face Wash")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = moisturize, onCheckedChange = { moisturize = it })
                Text("Moisturizer / Serum")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = spf, onCheckedChange = { spf = it })
                Text("Sunscreen (SPF)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { 
                    val data = """{"cleanser":$cleanser,"moisturize":$moisturize,"spf":$spf}"""
                    onLog(data) 
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Routine")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Every exercise in the real catalogue, so picking one is never limited to squat/bench —
 *  the prior hardcoded two-button row (matching what LiveSessionScreen used to assume) is gone;
 *  the picker and the live session now read from the same `Exercises.EXERCISES` source. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val ex = com.example.domain.EXERCISES[selected]
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(ex?.name ?: selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (group in com.example.domain.GROUPS) {
                Text(
                    group,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                for (def in com.example.domain.byGroup(group)) {
                    DropdownMenuItem(text = { Text(def.name) }, onClick = { onSelect(def.id); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSheet(
    onDismiss: () -> Unit,
    onStartLiveSession: (exId: String) -> Unit,
    onManualLog: (exId: String, reps: Int, load: Double) -> Unit
) {
    var isManualMode by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var repsStr by remember { mutableStateOf("") }
    var loadStr by remember { mutableStateOf("") }
    var selectedEx by remember { mutableStateOf("squat") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Log Training", style = MaterialTheme.typography.titleLarge)

            if (isManualMode) {
                Text("Manual Workout Logging", color = MaterialTheme.colorScheme.onSurfaceVariant)

                ExercisePicker(selectedEx) { selectedEx = it }

                OutlinedTextField(
                    value = repsStr,
                    onValueChange = { repsStr = it },
                    label = { Text("Reps") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = loadStr,
                    onValueChange = { loadStr = it },
                    label = { Text("Load (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        val reps = repsStr.toIntOrNull() ?: 0
                        val load = loadStr.toDoubleOrNull() ?: 0.0
                        if (reps > 0 && !isSaving) {
                            isSaving = true
                            onManualLog(selectedEx, reps, load)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Manual Log")
                }
            } else {
                Text("Start a live session to track your reps automatically, or enter them manually.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                ExercisePicker(selectedEx) { selectedEx = it }

                Button(
                    onClick = { onStartLiveSession(selectedEx) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Live Session (Camera)")
                }

                OutlinedButton(
                    onClick = { isManualMode = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Manual Workout Logging")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(
    onDismiss: () -> Unit,
    onLog: (Double) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Log Recovery / Sleep", style = MaterialTheme.typography.titleLarge)
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { onLog(6.0) }) { Text("~6 Hours") }
                Button(onClick = { onLog(7.5) }) { Text("~7.5 Hours") }
                Button(onClick = { onLog(9.0) }) { Text("~9 Hours") }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
