package com.example.nemebudget.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.model.ProcessingState
import com.example.nemebudget.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DeviceAppItem(
    val label: String,
    val packageName: String
)

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, pipeline: LlmPipeline, onOpenLab: () -> Unit) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "Notification permission is required for test alerts.", Toast.LENGTH_SHORT).show()
        }
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val isOptimizing by viewModel.isOptimizing.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingNotificationCount.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    var ruleInput by remember { mutableStateOf("") }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showIgnoreAppsSubmenu by remember { mutableStateOf(false) }
    var testNotificationTitle by remember { mutableStateOf("America First Alert") }
    var testNotificationText by remember {
        mutableStateOf("Card Guard Credit Card *3320 $9.13 at SQ *KIWI LOCO FROZEN Y. Tap for details.")
    }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.US) }
    val listenerEnabled = isNotificationListenerEnabled(context)
    val postNotificationsGranted = isPostNotificationsGranted(context)

    if (showIgnoreAppsSubmenu) {
        IgnoreAppsScreen(
            viewModel = viewModel,
            onBack = { showIgnoreAppsSubmenu = false }
        )
        return
    }

    fun submitRule() {
        if (ruleInput.isBlank()) return
        viewModel.addCustomRule(ruleInput)
        ruleInput = ""
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Required Permissions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Status: Listener ${if (listenerEnabled) "Granted" else "Missing"} | Notifications ${if (postNotificationsGranted) "Granted" else "Missing"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "For automatic bank scanning after install, grant both permissions once.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (!listenerEnabled) {
                        Button(
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Notification Listener")
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted) {
                        Button(
                            onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Post Notifications")
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = settings.primaryBank,
                onValueChange = viewModel::updatePrimaryBank,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Primary Bank (e.g. America First)") }
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showIgnoreAppsSubmenu = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Ignore apps", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${settings.ignoredApps.size} selected",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open ignore apps")
                }
            }
        }

        item {
            Text(
                "Ignored apps are saved immediately and apply to notification ingest once the listener pipeline is connected.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item { HorizontalDivider() }

        item {
            Text("Custom AI Rules", style = MaterialTheme.typography.titleMedium)
        }

        item {
            OutlinedTextField(
                value = ruleInput,
                onValueChange = { ruleInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chevron = Gas") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitRule() })
            )
        }

        item {
            Button(onClick = { submitRule() }, enabled = ruleInput.isNotBlank()) {
                Text("Add Rule")
            }
        }

        items(settings.customRules) { rule ->
            InputChip(
                selected = false,
                onClick = { viewModel.removeCustomRule(rule) },
                label = { Text(rule) },
                trailingIcon = {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Remove rule")
                }
            )
        }

        item { HorizontalDivider() }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Qwen Model Status", style = MaterialTheme.typography.titleMedium)
                    if (!modelStatus.isDownloaded) {
                        Text("Downloading Qwen AI (one-time, ${modelStatus.modelSizeLabel})")
                        LinearProgressIndicator(
                            progress = { modelStatus.downloadProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!modelStatus.isGpuOptimized) {
                        Text("Model is ready, but needs to be optimized for your device's GPU. This happens once and takes ~1 minute.")
                        Button(
                            onClick = { viewModel.optimizeEngine(pipeline) },
                            enabled = !isOptimizing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isOptimizing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(if (isOptimizing) "Compiling Shaders..." else "Optimize Now")
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Model ready",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Qwen 3 0.6B - Ready - ${modelStatus.modelSizeLabel} on device")
                        }
                    }
                }
            }
        }

        item { HorizontalDivider() }

        item {
            Text("🧪 Test Notification Generator", style = MaterialTheme.typography.titleMedium)
        }

        item {
            Text(
                "Post a test bank notification to debug the encryption pipeline. It will be intercepted and saved to the encrypted database.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            OutlinedTextField(
                value = testNotificationTitle,
                onValueChange = { testNotificationTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notification title") },
                placeholder = { Text("Card Guard") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }

        item {
            OutlinedTextField(
                value = testNotificationText,
                onValueChange = { testNotificationText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notification text") },
                placeholder = { Text("You spent $45.99 at Starbucks") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }

        item {
            Button(
                onClick = {
                    postTestNotification(
                        context = context,
                        notificationTitle = testNotificationTitle,
                        notificationText = testNotificationText
                    )
                },
                enabled = testNotificationText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Post Test Notification")
            }
        }

        item { HorizontalDivider() }

        item {
            Text("Transactions stored locally: $totalCount")
            Text("Queued notifications: $pendingCount")
        }

        item {
            Button(onClick = {
                viewModel.exportCsv { csvString ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_TEXT, csvString)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Export transactions"))
                }
            }) {
                Text("Export to CSV")
            }
        }

        item {
            val isRunning = processingState is ProcessingState.Processing
            val buttonLabel = when (val state = processingState) {
                is ProcessingState.Processing -> "Processing ${state.processedCount}/${state.totalCount}"
                else -> "Process Now"
            }
            Button(onClick = viewModel::processNow, enabled = !isRunning) {
                Text(buttonLabel)
            }
        }

        item {
            when (val state = processingState) {
                is ProcessingState.Processing -> {
                    val progress = if (state.totalCount <= 0) 0f else state.processedCount.toFloat() / state.totalCount.toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Processing ${state.processedCount} of ${state.totalCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        state.currentItemLabel?.takeIf { it.isNotBlank() }?.let { current ->
                            Text(
                                text = "Current: $current",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ProcessingState.Success -> {
                    Text(
                        "Last processed ${timeFormatter.format(Date(state.completedAtMillis))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is ProcessingState.Error -> {
                    Text(
                        "Processing failed: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                ProcessingState.Idle -> Unit
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLab) { Text("Open LLM Lab") }
                Button(
                    onClick = { showWipeDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Wipe All Data")
                }
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text("Wipe all local data?") },
            text = { Text("This deletes all transactions, budgets, and settings on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.wipeAll()
                    showWipeDialog = false
                }) {
                    Text("Wipe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun IgnoreAppsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var deviceApps by remember { mutableStateOf<List<DeviceAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var manualPackageInput by remember { mutableStateOf("") }

    LaunchedEffect(context) {
        deviceApps = loadInstalledLaunchableApps(context.packageManager)
    }

    val combinedApps = remember(deviceApps, settings.ignoredApps, viewModel.knownSpamApps) {
        buildMap<String, DeviceAppItem> {
            viewModel.knownSpamApps.forEach { known ->
                put(known.packageName, DeviceAppItem(label = known.label, packageName = known.packageName))
            }
            deviceApps.forEach { app -> put(app.packageName, app) }
            settings.ignoredApps.forEach { pkg ->
                putIfAbsent(pkg, DeviceAppItem(label = pkg, packageName = pkg))
            }
        }.values
            .sortedWith(compareBy<DeviceAppItem> { it.label.lowercase(Locale.US) }.thenBy { it.packageName })
    }

    val filteredApps = remember(combinedApps, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) combinedApps else {
            combinedApps.filter { app ->
                app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Ignore Apps", style = MaterialTheme.typography.headlineSmall)
            }
        }

        item {
            Text(
                "Choose any app to ignore. This is saved now and will be applied to notification ingest when the listener pipeline is connected.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search apps or package name") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = manualPackageInput,
                onValueChange = { manualPackageInput = it },
                label = { Text("Add package manually (com.example.app)") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val pkg = manualPackageInput.trim()
                    if (pkg.isNotBlank()) {
                        viewModel.setIgnoredApp(pkg, ignored = true)
                        manualPackageInput = ""
                    }
                }),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    val pkg = manualPackageInput.trim()
                    if (pkg.isNotBlank()) {
                        viewModel.setIgnoredApp(pkg, ignored = true)
                        manualPackageInput = ""
                    }
                },
                enabled = manualPackageInput.trim().isNotBlank()
            ) {
                Text("Add To Ignore List")
            }
        }

        items(filteredApps) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(app.label)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = app.packageName in settings.ignoredApps,
                    onCheckedChange = { ignored -> viewModel.setIgnoredApp(app.packageName, ignored) }
                )
            }
        }
    }
}

private fun loadInstalledLaunchableApps(packageManager: PackageManager): List<DeviceAppItem> {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    return packageManager.queryIntentActivities(intent, 0)
        .map { info ->
            DeviceAppItem(
                label = info.loadLabel(packageManager)?.toString().orEmpty().ifBlank { info.activityInfo.packageName },
                packageName = info.activityInfo.packageName
            )
        }
        .distinctBy { it.packageName }
}

/**
 * Posts a test notification that mimics a bank transaction notification.
 * This is used for debugging the BankNotificationListenerService encryption pipeline.
 */
private fun postTestNotification(
    context: android.content.Context,
    notificationTitle: String,
    notificationText: String
) {
    if (!isPostNotificationsGranted(context)) {
        Toast.makeText(context, "Grant Post Notifications first.", Toast.LENGTH_SHORT).show()
        return
    }

    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

    // Create a notification channel for Android 8+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            "test_bank_channel",
            "Test Bank Notifications",
            android.app.NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)
    }

    val resolvedTitle = notificationTitle.ifBlank { "Card Guard" }

    val notification = androidx.core.app.NotificationCompat.Builder(context, "test_bank_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(resolvedTitle)
        .setContentText(notificationText)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    // Post with a unique ID based on timestamp
    notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabledListeners = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    return enabledListeners.split(':').any { flattened ->
        val component = ComponentName.unflattenFromString(flattened)
        component?.packageName == context.packageName
    }
}
