package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.data.SettingsManager

@Composable
fun ProfileScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    val currentKey by settingsManager.getSetting("geminiKey").collectAsState(initial = "")
    var keyInput by remember { mutableStateOf("") }
    
    LaunchedEffect(currentKey) {
        if (currentKey != null) {
            keyInput = currentKey!!
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
    }
}
