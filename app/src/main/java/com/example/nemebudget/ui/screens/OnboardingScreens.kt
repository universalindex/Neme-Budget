package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nemebudget.model.ModelStatus

@Composable
fun OnboardingWelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your money. Your device. No cloud.",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("- Local AI processing", style = MaterialTheme.typography.bodyLarge)
        Text("- Zero bank logins", style = MaterialTheme.typography.bodyLarge)
        Text("- Encrypted on your phone", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onGetStarted) {
            Text("Get Started")
        }
    }
}

@Composable
fun OnboardingPermissionScreen(
    listenerEnabled: Boolean,
    onGrantPermission: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Notification access", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Neme Budget reads bank alerts on your device so spending updates stay local and private.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onGrantPermission, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Notification Access")
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (listenerEnabled) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Permission granted",
                tint = MaterialTheme.colorScheme.primary
            )
            Text("Permission granted", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("Permission not granted yet", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onNext, enabled = listenerEnabled, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun OnboardingModelScreen(
    modelStatus: ModelStatus,
    onReady: () -> Unit
) {
    LaunchedEffect(modelStatus.isDownloaded) {
        if (modelStatus.isDownloaded) onReady()
    }

    val progress = modelStatus.downloadProgress.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Preparing on-device AI", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Downloading Qwen AI to your device. This only happens once.",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { if (modelStatus.isDownloaded) 1f else progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (modelStatus.isDownloaded) {
                        "Model ready (${modelStatus.modelSizeLabel})"
                    } else {
                        "${(progress * 100).toInt()}% (${modelStatus.modelSizeLabel})"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

