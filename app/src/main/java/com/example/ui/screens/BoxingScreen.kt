package com.example.ui.screens

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BoxingScreen() {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Boxing", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Live Boxing Session: Camera feed placeholder.")
        Spacer(modifier = Modifier.height(16.dp))
        
        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                RearCameraPreview()
            }
        } else {
            Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                Text("Grant Camera Permission")
            }
        }
    }
}

