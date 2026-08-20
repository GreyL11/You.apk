package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TalkScreen(
    viewModel: TalkViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember(context) { com.example.data.SettingsManager(context) }
    val hasKey by settingsManager.getSetting("geminiKey").collectAsState(initial = null)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (hasKey.isNullOrEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    "Add a free Gemini key in Profile to start chatting.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true
            ) {
                if (isTyping) {
                    item {
                        Text("Coach is thinking…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                    }
                }
                items(messages.reversed()) { msg ->
                    val isUser = msg.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp)
                                .widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.content,
                                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask your coach...") },
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = inputText.isNotBlank() && !isTyping
                ) {
                    Text("Send")
                }
            }
        }
    }
}

/** Real skin-vs-lifestyle association reading, backed by [com.example.domain.Skin] — the same
 *  engine [SkinSheet] logs into. Never a diagnosis, never a score: an evidence-graded read of your
 *  own log against your own skin score. */
@Composable
fun SkinScreen(
    onBack: () -> Unit = {},
    viewModel: SkinViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application)
    ),
) {
    val advice by viewModel.advice.collectAsState()
    val associations by viewModel.associations.collectAsState()
    val adherence by viewModel.adherence.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Skin", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ONE THING TO DO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text(advice?.text ?: "Log your skin to start seeing something here.")
                advice?.evidence?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        adherence?.let { a ->
            Spacer(modifier = Modifier.height(16.dp))
            Text("Full routine done on ${a.complete} of your last ${a.of} logged days.", style = MaterialTheme.typography.bodyMedium)
        }

        if (associations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("WHAT YOUR OWN LOG SHOWS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            for (assoc in associations) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Skin scored ${assoc.diff} higher with less ${assoc.label}.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(assoc.change, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${assoc.lowDays} lower-exposure days vs ${assoc.highDays} higher — your own log, not a study.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(com.example.domain.Skin.SEE_SOMEONE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** 7.0 reads as "7", 7.5 as "7.5" — an hour count, not a measurement to two places. */
private fun hours(n: Double?): String =
    if (n == null) "—" else if (n == Math.floor(n)) n.toInt().toString() else n.toString()

@Composable
fun HormonalScreen(
    onBack: () -> Unit = {},
    viewModel: HormonalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val read = state.read
    var showLabSheet by remember { mutableStateOf(false) }

    if (showLabSheet) {
        LabSheet(
            onDismiss = { showLabSheet = false },
            onLog = { marker, value, unit, at ->
                viewModel.logLab(marker, value, unit, at)
                showLabSheet = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Hormonal Lifestyle", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            com.example.domain.TInputs.HORMONAL_BOUNDARY,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (read == null) {
            Text("Reading your record…")
            return@Column
        }

        // Sleep. Below the nights floor the average is not reported at all — an average of four
        // nights is not a pattern, and printing it would invite a decision it cannot carry.
        Text(
            when (read.sleep.verdict) {
                "unknown" ->
                    "Sleep: only ${read.sleep.nights} of ${com.example.domain.TInputs.WINDOW} nights logged."
                else ->
                    "Sleep: ${hours(read.sleep.avg)}h main sleep across ${read.sleep.nights} nights (${read.sleep.verdict})."
            },
            style = MaterialTheme.typography.bodyLarge
        )
        read.sleep.totalAvg?.let {
            Text(
                "With naps: ${hours(it)}h on ${read.sleep.napDays} of those days — counted separately, not added to the night.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            read.wake?.let { w ->
                if (w.regular) "Wake time: usually ${w.median}, moving under ${hours(w.spreadHours)}h across ${w.nights} nights."
                else "Wake time: moves across about ${hours(w.spreadHours)}h, so there is no usual hour."
            } ?: "Wake time: none logged, so no bedtime can be worked out.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            if (read.weight.verdict == "known")
                "Weight: ${read.weight.kg} kg across ${read.weight.points} weigh-ins — direction only, not judged."
            else "Weight: fewer than two weigh-ins in the window.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Training: ${read.training.days} days in ${com.example.domain.TInputs.WINDOW} (floor is ${com.example.domain.TInputs.TRAIN_LOW}).",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))

        // WHICH INPUTS ARE ACTUALLY SUPPORTED. Three states, no score: a weighted total of these
        // would be a testosterone estimate in disguise, and no published weighting exists to build
        // one from.
        Text("THE INPUTS WITH EVIDENCE BEHIND THEM", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        state.factors.forEach { factor ->
            val tint = when (factor.state) {
                com.example.domain.TInputs.FactorState.SUPPORTED -> MaterialTheme.colorScheme.secondary
                com.example.domain.TInputs.FactorState.PARTIAL -> MaterialTheme.colorScheme.primary
                com.example.domain.TInputs.FactorState.ABSENT -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(factor.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        factor.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when (factor.state) {
                        com.example.domain.TInputs.FactorState.SUPPORTED -> "DOING IT"
                        com.example.domain.TInputs.FactorState.PARTIAL -> "PARTLY"
                        com.example.domain.TInputs.FactorState.ABSENT -> "NO DATA"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MEASURED VALUES. The only numbers on this screen that are actually about a hormone, and
        // they are here because someone read them off their own blood test.
        Text("MEASURED", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        if (state.labs.isEmpty()) {
            Text(
                "No lab results yet. A real testosterone figure is a morning blood draw, twice, read by a doctor — add yours here and it will sit beside these inputs over time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.labs.reversed().forEach { lab ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            when (lab.marker) {
                                "totalTestosterone" -> "Total testosterone"
                                "freeTestosterone" -> "Free testosterone"
                                "shbg" -> "SHBG"
                                "vitaminD" -> "Vitamin D (25-OH)"
                                else -> lab.marker
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(lab.at, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${hours(lab.value)} ${lab.unit}",
                        style = com.example.ui.theme.NumberStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { showLabSheet = true }) { Text("Add a lab result") }

        Spacer(modifier = Modifier.height(24.dp))

        // The next move. The engine decided it; the model only got to word it, and only if every
        // figure it used was already in the record.
        read.advice.plan?.let { plan ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    plan,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.loading) {
            Text(
                read.advice.text ?: "",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            state.explanation?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }

        state.explanationNote?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
