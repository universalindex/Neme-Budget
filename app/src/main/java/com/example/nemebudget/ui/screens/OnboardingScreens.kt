package com.example.nemebudget.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.notifications.BankNotificationListenerService
import kotlinx.coroutines.delay

@Composable
fun OnboardingFlowScreen(
    modelStatus: ModelStatus,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var postNotificationsGranted by remember { mutableStateOf(isPostNotificationsGranted(context)) }

    val postNotificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        postNotificationsGranted = granted || isPostNotificationsGranted(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            listenerEnabled = isNotificationListenerEnabled(context)
            postNotificationsGranted = isPostNotificationsGranted(context)
            delay(1000)
        }
    }

    when (step) {
        0 -> OnboardingWelcomeScreen(onGetStarted = { step = 1 })
        1 -> OnboardingPermissionScreen(
            listenerEnabled = listenerEnabled,
            postNotificationsGranted = postNotificationsGranted,
            onGrantNotificationAccess = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onGrantPostNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    postNotificationsGranted = true
                }
            },
            onNext = { step = 2 }
        )
        else -> OnboardingModelScreen(
            modelStatus = modelStatus,
            onReady = onFinished
        )
    }
}

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
    postNotificationsGranted: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onGrantPostNotifications: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Neme Budget needs notification access to scan bank alerts locally, and notification permission so the in-app test alert can be posted during setup.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onGrantNotificationAccess, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Notification Listener")
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(onClick = onGrantPostNotifications, modifier = Modifier.fillMaxWidth()) {
                Text("Grant Post Notifications")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PermissionStatusRow(
                label = "Notification listener",
                granted = listenerEnabled
            )
            PermissionStatusRow(
                label = "Post notifications",
                granted = postNotificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onNext,
            enabled = listenerEnabled && (postNotificationsGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun PermissionStatusRow(label: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (granted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "$label granted",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = "$label: ${if (granted) "Granted" else "Missing"}",
            style = MaterialTheme.typography.bodyMedium
        )
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

private fun isPostNotificationsGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    val thisComponent = ComponentName(
        context,
        BankNotificationListenerService::class.java
    ).flattenToString()

    return enabledListeners.split(':').any { flattened ->
        flattened.equals(thisComponent, ignoreCase = true)
    }
}
