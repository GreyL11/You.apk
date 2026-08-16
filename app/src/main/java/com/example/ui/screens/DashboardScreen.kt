package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onNavigateToFaceScan: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application))
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)) // Premium dark background
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "YOU",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "STATS - PROGRESS",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Main Chart (Bodyweight)
        MainChartCard(state.filteredWeightHistory)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Time Range Selector
        TimeRangeSelector(
            ranges = state.timeRanges,
            selected = state.selectedTimeRange,
            onSelect = { viewModel.setTimeRange(it) }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Strength Progress
        if (state.filteredStrengthProgress.isNotEmpty()) {
            state.filteredStrengthProgress.forEach { progress ->
                StrengthProgressCard(progress)
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            // Empty state for strength
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "NO TRAINING DATA IN PERIOD",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Log workouts to see your strength progress.",
                        color = Color.White.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "FACE & SKIN",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onNavigateToFaceScan() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Guided Face Scan", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Check capture quality and track real scan history.", color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
                Text("→", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun MainChartCard(history: List<ChartPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "BODYWEIGHT",
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            if (history.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Start logging your weight to see your trend.",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(40.dp))
            } else {
                val latest = history.last().value
                val first = history.first().value
                val diff = latest - first
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${String.format("%.1f", latest)}",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " KG",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                    
                    if (history.size > 1 && diff != 0.0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        val sign = if (diff > 0) "+" else ""
                        val color = if (diff < 0) Color(0xFF4ADE80) else Color(0xFFF87171) // Green if lost weight, red if gained (assuming losing is goal, though arbitrary)
                        Text(
                            text = "$sign${String.format("%.1f", diff)}",
                            color = color,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (history.size > 1) {
                    LineChart(
                        points = history.map { it.value.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        lineColor = Color(0xFF818CF8)
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("More data needed for trend.", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeRangeSelector(
    ranges: List<TimeRange>,
    selected: TimeRange,
    onSelect: (TimeRange) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ranges.forEach { range ->
            val isSelected = range == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .background(
                        color = if (isSelected) Color(0xFF2D3342) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF3F475A) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelect(range) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = range.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun StrengthProgressCard(progress: StrengthProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161920)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                progress.name.uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (progress.history.size > 1) {
                LineChart(
                    points = progress.history.map { it.value.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    lineColor = Color(0xFF34D399) // Emerald green for strength
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Text("More data needed for trend.", color = Color.White.copy(alpha = 0.3f), style = MaterialTheme.typography.labelSmall)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val metricLabel = if (progress.isBodyweight) "MAX REPS:" else "MAX 1RM:"
            val metricUnit = if (progress.isBodyweight) "" else " KG"
            
            Text(
                text = "$metricLabel ${progress.currentMax.roundToInt()}$metricUnit",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (progress.previousMax != null && progress.history.size > 1) {
                val diff = progress.currentMax - progress.previousMax
                if (diff != 0.0) {
                    val sign = if (diff > 0) "+" else ""
                    val color = if (diff > 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                    val diffUnit = if (progress.isBodyweight) " REPS" else " KG"
                    Text(
                        text = "$sign${diff.roundToInt()}$diffUnit IN PERIOD",
                        color = color,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LineChart(points: List<Float>, modifier: Modifier = Modifier, lineColor: Color) {
    if (points.isEmpty()) return
    
    val min = points.minOrNull() ?: 0f
    val max = points.maxOrNull() ?: 0f
    val range = if (max == min) 1f else max - min
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        val path = Path()
        
        if (points.size == 1) {
            // Draw a straight line if only one point
            path.moveTo(0f, height / 2f)
            path.lineTo(width, height / 2f)
        } else {
            val xStep = width / (points.size - 1)
            
            points.forEachIndexed { index, value ->
                val x = index * xStep
                // Normalize value between 0.1 and 0.9 to give some padding top/bottom
                val normalized = 1f - ((value - min) / range)
                val y = height * (0.1f + 0.8f * normalized)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
        }
        
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
