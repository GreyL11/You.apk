package com.example.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application))
) {
    var step by remember { mutableStateOf(1) }
    var selectedGoal by remember { mutableStateOf("hydration") }
    var name by remember { mutableStateOf("") }
    var bodyweight by remember { mutableStateOf("75.0") }
    var daysPerWeek by remember { mutableStateOf("3") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (step == 1) {
            Text(
                text = "Your Health Coach",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "One place to understand what matters, build better habits, and get guidance based on what you actually do.",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                lineHeight = 26.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = { step = 2 },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (step == 2) {
            Text(
                text = "What do you want to improve?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            val goals = listOf(
                "training" to "Build muscle / training consistency",
                "hydration" to "Hydration",
                "skinRoutine" to "Skincare routine",
                "hormonalLifestyle" to "Recovery / sleep"
            )
            
            goals.forEach { (id, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(
                            if (selectedGoal == id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                        .clickable { selectedGoal = id },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedGoal == id,
                        onClick = { selectedGoal = id }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = label, fontSize = 16.sp, fontWeight = if (selectedGoal == id) FontWeight.Bold else FontWeight.Normal)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { step = 3 },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        } else if (step == 3) {
            Text(
                text = "Let's build your starting point.",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = bodyweight,
                onValueChange = { bodyweight = it },
                label = { Text("Bodyweight (kg)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = daysPerWeek,
                onValueChange = { daysPerWeek = it },
                label = { Text("Training Days per Week") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = {
                    val bw = bodyweight.toDoubleOrNull() ?: 75.0
                    val days = daysPerWeek.toIntOrNull() ?: 3
                    viewModel.completeOnboarding(selectedGoal, name, bw, days) {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Start today's check-in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
