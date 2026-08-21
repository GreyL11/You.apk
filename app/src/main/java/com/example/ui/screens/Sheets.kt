package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.MealParseResult
import com.example.domain.Nutrition
import com.example.domain.ParsedMeal
import com.example.domain.Skin
import kotlinx.coroutines.launch

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
    onLog: (foodId: String, qty: Double) -> Unit,
    /** Hands a sentence to the model. Returns null when there is no key or no answer. */
    onParse: suspend (String) -> MealParseResult? = { null },
    onLogAll: (List<ParsedMeal>) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<MealParseResult?>(null) }
    var parseFailed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Log a Meal", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))

            // Say it in one line instead of hunting the list. The model only ever maps words to ids
            // that already exist below — it never invents a food and never guesses a calorie — and
            // nothing is written until you have seen the rows it came back with.
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it; parsed = null; parseFailed = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What did you have?") },
                placeholder = { Text("two rotis, dal, a glass of milk") },
                maxLines = 3,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        parsing = true
                        parseFailed = false
                        val result = onParse(typed)
                        parsed = result
                        parseFailed = result == null
                        parsing = false
                    }
                },
                enabled = typed.isNotBlank() && !parsing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (parsing) "Reading…" else "Read it") }

            if (parseFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Could not read that — add a Gemini key in Profile, or pick from the list below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            parsed?.let { result ->
                Spacer(modifier = Modifier.height(12.dp))
                if (result.items.isEmpty()) {
                    Text(
                        "Nothing in that matched the food list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text("READ AS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    result.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "×${if (item.qty == kotlin.math.floor(item.qty)) item.qty.toInt().toString() else item.qty.toString()}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // Unmatched words are shown, never silently dropped: knowing it missed the paratha
                    // is the difference between an incomplete log and a wrong one.
                    if (result.unknown.isNotEmpty()) {
                        Text(
                            "Not in the list, so not logged: ${result.unknown.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // What THIS entry adds — real kcal/macros off the food table, not a guess. The
                    // model only ever mapped words to a foodId; this number never came from it.
                    val m = Nutrition.macros(result.items.map { it.foodId to it.qty })
                    Text(
                        "${m.kcal} kcal · ${m.protein}g protein · ${m.carbs}g carbs · ${m.fat}g fat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onLogAll(result.items) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Log these") }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("OR PICK ONE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

/** Backed by [Skin]'s real habit/flag catalogue — the exact same 4 evidence-based habits and 6
 *  descriptive (never diagnostic) flags the skin-diet association engine reads, so a day logged
 *  here is data [Skin.advice]/[Skin.associations] can actually use, not a separate 3-checkbox
 *  counter that nothing analyzes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSheet(
    onDismiss: () -> Unit,
    onLog: (String) -> Unit
) {
    var score by remember { mutableStateOf(3) }
    val checkedFlags = remember { mutableStateMapOf<String, Boolean>() }
    val checkedHabits = remember { mutableStateMapOf<String, Boolean>() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Log Skin", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))

            Text("How's your skin today? (1 = bad day, 5 = good day)", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in Skin.SCALE) {
                    FilterChip(selected = score == n, onClick = { score = n }, label = { Text("$n") })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Anything worth noting?", style = MaterialTheme.typography.bodyMedium)
            for (flag in Skin.FLAGS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checkedFlags[flag.id] ?: false,
                        onCheckedChange = { checkedFlags[flag.id] = it },
                    )
                    Text(flag.label)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Today's routine", style = MaterialTheme.typography.bodyMedium)
            for (habit in Skin.HABITS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checkedHabits[habit.id] ?: false,
                        onCheckedChange = { checkedHabits[habit.id] = it },
                    )
                    Text(habit.label)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val flags = Skin.FLAGS.filter { checkedFlags[it.id] == true }.map { it.id }
                    val habits = Skin.HABITS.filter { checkedHabits[it.id] == true }.map { it.id }
                    onLog(Skin.toJson(score, flags, habits))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** The daily check-in the whole closed loop depends on: energy, soreness, stress and whether you
 *  woke rested. Every field is skippable, and a skipped field stays null rather than becoming a
 *  neutral score -- [com.example.domain.ReadinessEngine] treats absent and average differently. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInSheet(
    onDismiss: () -> Unit,
    onLog: (energy: Int?, soreness: Int?, stress: Int?, refreshed: Boolean?) -> Unit,
) {
    var energy by remember { mutableStateOf<Int?>(null) }
    var soreness by remember { mutableStateOf<Int?>(null) }
    var stress by remember { mutableStateOf<Int?>(null) }
    var refreshed by remember { mutableStateOf<Boolean?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Daily check-in", style = MaterialTheme.typography.titleLarge)
            Text(
                "Skip anything you'd rather not answer — a blank stays blank rather than becoming an average.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ScaleRow("Energy", "1 = drained, 10 = great", energy) { energy = it }
            ScaleRow("Soreness", "1 = none, 10 = severe", soreness) { soreness = it }
            ScaleRow("Stress", "1 = calm, 10 = very high", stress) { stress = it }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Did you wake up feeling rested?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = refreshed == true, onClick = { refreshed = true }, label = { Text("Yes") })
                FilterChip(selected = refreshed == false, onClick = { refreshed = false }, label = { Text("No") })
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onLog(energy, soreness, stress, refreshed) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleRow(label: String, hint: String, value: Int?, onSelect: (Int) -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (n in 1..10) {
            FilterChip(selected = value == n, onClick = { onSelect(n) }, label = { Text("$n") })
        }
    }
}

/** Logging a cardio session that actually happened. Modes come from [com.example.domain.Cardio]
 *  so a logged session is directly comparable with what the decision engine prescribed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardioSheet(
    suggested: com.example.domain.Cardio.Mode,
    suggestedMinutes: Int?,
    onDismiss: () -> Unit,
    onLog: (com.example.domain.Cardio.Session) -> Unit,
) {
    val initial = if (suggested == com.example.domain.Cardio.Mode.NONE) com.example.domain.Cardio.Mode.EASY_WALK else suggested
    var mode by remember { mutableStateOf(initial) }
    var minutes by remember { mutableStateOf((suggestedMinutes ?: 20).toString()) }
    var effort by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Log cardio", style = MaterialTheme.typography.titleLarge)

            Text("What did you do?", style = MaterialTheme.typography.bodyMedium)
            com.example.domain.Cardio.Mode.entries
                .filter { it != com.example.domain.Cardio.Mode.NONE }
                .forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(com.example.domain.Cardio.label(m), style = MaterialTheme.typography.bodyMedium)
                    }
                }

            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Minutes") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("How did it feel?", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Easy", 2 to "Right", 3 to "Hard").forEach { (v, l) ->
                    FilterChip(selected = effort == v, onClick = { effort = v }, label = { Text(l) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            val mins = minutes.toIntOrNull() ?: 0
            Button(
                onClick = { onLog(com.example.domain.Cardio.Session(mode, mins, effort)) },
                enabled = mins > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** A single 1-10 mood self-report, feeding [com.example.domain.WellbeingEngine] -- the one
 *  self-report dimension the schema already has ([DayRow.mood]). Never asks why, never labels
 *  the number, just records it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSheet(
    onDismiss: () -> Unit,
    onLog: (Int) -> Unit
) {
    var score by remember { mutableStateOf(5) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Log Mood", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text("How are you feeling today? (1 = rough, 10 = great)", style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..10) {
                    FilterChip(selected = score == n, onClick = { score = n }, label = { Text("$n") })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onLog(score) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Every exercise in the real catalogue, so picking one is never limited to squat/bench —
 *  the prior hardcoded two-button row is gone;
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
    onManualLog: (exId: String, reps: Int, load: Double, difficulty: Int?) -> Unit
) {
    // Straight into the form: there is no camera path to choose between any more, so a mode
    // selector would be a menu with one item on it.
    var isManualMode by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var repsStr by remember { mutableStateOf("") }
    var loadStr by remember { mutableStateOf("") }
    var selectedEx by remember { mutableStateOf("squat") }
    var difficulty by remember { mutableStateOf<Int?>(null) }

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
                
                // Real difficulty feedback, optional on purpose: this is what gates progression, so
                // an unanswered prompt must stay unanswered rather than defaulting to "moderate".
                Text("How did that feel? (optional)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Easy", 2 to "Right", 3 to "Hard").forEach { (v, l) ->
                        FilterChip(selected = difficulty == v, onClick = { difficulty = v }, label = { Text(l) })
                    }
                }

                Button(
                    onClick = {
                        val reps = repsStr.toIntOrNull() ?: 0
                        val load = loadStr.toDoubleOrNull() ?: 0.0
                        if (reps > 0 && !isSaving) {
                            isSaving = true
                            onManualLog(selectedEx, reps, load, difficulty)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Manual Log")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Two clock times, not a rough length.
 *
 * "~6 / ~7.5 / ~9 hours" buttons are quicker to tap and they cost the app everything downstream: a
 * length carries no wake time, and without wake times there is no wake pattern, no regularity and no
 * bedtime the app can name (see `TInputs.wakePattern`). So this asks when you fell asleep and when
 * you got up, and nothing is saved until both are real.
 *
 * ponytail: the platform's own TimePickerDialog rather than a Compose picker — it is one call, it is
 * already localised, and it already handles 24-hour settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(
    onDismiss: () -> Unit,
    onLog: (bed: String, wake: String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bed by remember { mutableStateOf<String?>(null) }
    var wake by remember { mutableStateOf<String?>(null) }

    fun pick(initialHour: Int, onPicked: (String) -> Unit) {
        android.app.TimePickerDialog(
            context,
            { _, h, m -> onPicked("%02d:%02d".format(h, m)) },
            initialHour, 0, true
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Log sleep", style = MaterialTheme.typography.titleLarge)
            Text(
                "The times, not a guess at the length — the wake time is what lets the app work out a bedtime worth naming.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { pick(23) { bed = it } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(bed?.let { "Fell asleep at $it" } ?: "Fell asleep at…") }

            OutlinedButton(
                onClick = { pick(7) { wake = it } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(wake?.let { "Woke at $it" } ?: "Woke at…") }

            Button(
                onClick = { onLog(bed!!, wake!!) },
                enabled = bed != null && wake != null && bed != wake,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Type in a value from a real blood test.
 *
 * The one place a hormone number may enter this app, and it enters because a person read it off their
 * own results. The unit is stored as printed rather than converted: ng/dL and nmol/L differ by a
 * factor of ~28.8, and a silent conversion is how a value ends up meaning something else entirely.
 *
 * ponytail: the platform DatePickerDialog, not a Compose one — a draw date is the kind of thing the
 * OS picker already does correctly, including locale and the "no future dates" case below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSheet(
    onDismiss: () -> Unit,
    onLog: (marker: String, value: Double, unit: String, at: String) -> Unit,
) {
    // Marker to the unit its report is usually printed in. Editable, because labs differ.
    val markers = listOf(
        "totalTestosterone" to ("Total testosterone" to "ng/dL"),
        "freeTestosterone" to ("Free testosterone" to "pg/mL"),
        "shbg" to ("SHBG" to "nmol/L"),
        "vitaminD" to ("Vitamin D (25-OH)" to "ng/mL"),
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    var markerId by remember { mutableStateOf(markers.first().first) }
    var expanded by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(markers.first().second.second) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }

    val label = markers.first { it.first == markerId }.second.first
    val parsed = value.trim().toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add a lab result", style = MaterialTheme.typography.titleLarge)
            Text(
                "Measured values only, as printed on your report. The app will show them beside your logged lifestyle inputs and will never estimate one for you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    markers.forEach { (id, pair) ->
                        DropdownMenuItem(
                            text = { Text(pair.first) },
                            onClick = {
                                markerId = id
                                unit = pair.second
                                expanded = false
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedButton(
                onClick = {
                    val today = java.time.LocalDate.now()
                    val shown = java.time.LocalDate.parse(date)
                    android.app.DatePickerDialog(
                        context,
                        { _, y, m, d -> date = java.time.LocalDate.of(y, m + 1, d).toString() },
                        shown.year, shown.monthValue - 1, shown.dayOfMonth,
                    ).apply {
                        // A draw cannot have happened tomorrow.
                        datePicker.maxDate = System.currentTimeMillis()
                        show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Drawn on $date") }

            Button(
                onClick = { onLog(markerId, parsed!!, unit.trim(), date) },
                enabled = parsed != null && parsed > 0.0 && unit.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save result") }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * A weigh-in.
 *
 * The scale is the only real feedback loop on a calorie target — the target itself is a population
 * average applied to one person, and [com.example.domain.Nutrition.suggestion] can only correct it
 * from measurements. Before this sheet existed the only way weight got into the app at all was a
 * Health Connect import, so anyone without a smart scale could never close that loop.
 *
 * Pre-filled with your last weigh-in, because the useful edit is almost always a small one from
 * there, and typing a fresh three-digit number every morning is how a habit dies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightSheet(
    lastKg: Double?,
    onDismiss: () -> Unit,
    onLog: (Double) -> Unit,
) {
    var input by remember {
        mutableStateOf(lastKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
    }
    val kg = input.replace(',', '.').toDoubleOrNull()
    // Same bounds the onboarding bodyweight field uses: outside these it is a typo, and a typo here
    // corrupts the trend that every calorie correction is computed from.
    val valid = kg != null && kg >= 30.0 && kg <= 250.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Log your weight", style = MaterialTheme.typography.titleLarge)
            Text(
                "Same conditions each time is what makes the trend readable — first thing, before eating or drinking. One reading means nothing; three weeks of them is the only honest check on your calorie target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text("Weight") },
                suffix = { Text("kg") },
                singleLine = true,
                isError = input.isNotEmpty() && !valid,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onLog(kg!!) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
