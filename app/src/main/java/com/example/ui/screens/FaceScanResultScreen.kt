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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.FaceCaptureIssue
import com.example.domain.FaceScan
import com.example.domain.SkinAnalysis
import kotlin.math.roundToInt

/** Shows the just-completed scan: real FACE CHECK numbers from FaceScan.Record, and an honest
 *  SKIN ANALYSIS placeholder — always SkinAnalysis.NotAvailable today, since no real skin model
 *  exists. Rendering already switches on the sealed interface, so plugging in a real model later
 *  is a data change, not a screen rewrite. */
@Composable
fun FaceScanResultScreen(
    onBack: () -> Unit,
    onViewHistory: () -> Unit,
    viewModel: FaceScanViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)),
) {
    val history by viewModel.history.collectAsState()
    val latest = history.firstOrNull()
    val skinAnalysis: SkinAnalysis = SkinAnalysis.NotAvailable

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("SCAN RESULT", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(modifier = Modifier.height(16.dp))

        if (latest == null) {
            Text("No scan found.", color = Color.White.copy(alpha = 0.6f))
        } else {
            // Re-diagnosing the same stored metrics is the single source of truth for these labels
            // (no duplicated threshold literals) — always "good" here since only a scan that
            // already passed FaceScan.diagnose ever gets saved.
            val issue = FaceScan.diagnose(1, latest.metrics)

            SectionCard(title = "FACE CHECK") {
                MetricRow("Scan validity", if (latest.valid) "Valid ✓" else "Not valid")
                MetricRow("Framing", "${(latest.metrics.faceSizeFraction * 100).roundToInt()}% of frame")
                MetricRow("Head angle", if (issue == FaceCaptureIssue.LOOK_STRAIGHT) "Off angle" else "Straight on")
                MetricRow("Sharpness", if (issue == FaceCaptureIssue.HOLD_STILL) "Soft" else "Sharp")
                MetricRow(
                    "Lighting",
                    when (issue) {
                        FaceCaptureIssue.TOO_DARK -> "Too dark"
                        FaceCaptureIssue.TOO_BRIGHT -> "Too bright"
                        else -> "Good"
                    },
                )
                MetricRow("Captured", latest.at)
            }

            Spacer(modifier = Modifier.height(16.dp))

            SectionCard(title = "SKIN ANALYSIS") {
                when (skinAnalysis) {
                    is SkinAnalysis.NotAvailable -> Text(
                        "Skin trend analysis requires additional image-analysis capability. This app does not perform medical or dermatological diagnosis.",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is SkinAnalysis.Available -> Text(skinAnalysis.summary, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        OutlinedButton(onClick = onViewHistory, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text("View History")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Done")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
