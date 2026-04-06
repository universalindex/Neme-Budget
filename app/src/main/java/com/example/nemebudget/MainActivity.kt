package com.example.nemebudget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.nemebudget.ui.theme.NemeBudgetTheme
import com.example.nemebudget.llm.ExtractedTransaction
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.pipeline.NotificationBatchProcessor
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.repository.FakeRepository
import com.example.nemebudget.repository.RealRepository
import com.example.nemebudget.ui.dashboard.DashboardScreen
import com.example.nemebudget.ui.navigation.AppDestination
import com.example.nemebudget.ui.navigation.bottomDestinations
import com.example.nemebudget.ui.screens.ManageRulesScreen
import com.example.nemebudget.ui.screens.OnboardingFlowScreen
import com.example.nemebudget.ui.screens.BudgetsScreen
import com.example.nemebudget.ui.screens.ResolveErrorScreen
import com.example.nemebudget.ui.screens.SettingsScreen
import com.example.nemebudget.ui.screens.TransactionsScreen
import com.example.nemebudget.viewmodel.BudgetsViewModel
import com.example.nemebudget.viewmodel.DashboardViewModel
import com.example.nemebudget.viewmodel.SettingsViewModel
import com.example.nemebudget.viewmodel.TransactionsViewModel
import kotlinx.coroutines.launch

private const val APP_PREFS_NAME = "nemebudget_prefs"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NemeBudgetTheme {
                MainApp()
            }
        }
    }
}

@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomDestinations.any { it.route == currentRoute }

    val context = LocalContext.current
    // Hoist LlmPipeline so it stays in RAM across tab navigation
    val pipeline = remember { LlmPipeline(context) }
    // Open the encrypted database (SQLCipher)
    val database = remember { AppDatabase.getDatabase(context) }
    // Use RealRepository that reads/writes to the encrypted database
    val repo = remember { RealRepository(context, database, pipeline) }
    val dashboardViewModel = remember { DashboardViewModel(repo) }
    val transactionsViewModel = remember { TransactionsViewModel(repo) }
    val budgetsViewModel = remember { BudgetsViewModel(repo) }
    val settingsViewModel = remember { SettingsViewModel(repo) }
    val transactionCategoryOptions by transactionsViewModel.categoryOptions.collectAsStateWithLifecycle()
    val modelStatus by settingsViewModel.modelStatus.collectAsStateWithLifecycle()

    fun navigateToTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val onboardingPrefs = context.applicationContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    var onboardingCompleted by remember(context) {
        mutableStateOf(onboardingPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    }

    var showListenerPermissionDialog by remember { mutableStateOf(false) }
    var showModelImportDialog by remember { mutableStateOf(false) }
    var modelImportError by remember { mutableStateOf<String?>(null) }
    var isImportingModel by remember { mutableStateOf(false) }
    val appScope = rememberCoroutineScope()

    val modelZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        appScope.launch {
            isImportingModel = true
            val installed = pipeline.installModelFromZipUri(uri)
            isImportingModel = false

            if (installed) {
                repo.refreshModelStatus()
                modelImportError = null
                showModelImportDialog = false
            } else {
                modelImportError = "Could not install model from selected ZIP. Verify it contains mlc-chat-config.json."
            }
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            modelImportError = null
            modelZipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        } else {
            modelImportError = "Storage permission denied. Can't scan Downloads automatically on this Android version."
        }
    }

    val postNotificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: status is checked on recomposition */ }

    if (!onboardingCompleted) {
        LaunchedEffect(Unit) {
            val installedFromAssets = pipeline.installBundledModelIfPresent()
            if (installedFromAssets) {
                repo.refreshModelStatus()
            }
        }

        OnboardingFlowScreen(
            modelStatus = modelStatus,
            isImportingModel = isImportingModel,
            modelImportError = modelImportError,
            onSelectModelZip = {
                if (needsLegacyStoragePermission() && !isLegacyStoragePermissionGranted(context)) {
                    legacyStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    modelImportError = null
                    modelZipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }
            },
            onFinished = {
                onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                onboardingCompleted = true
            }
        )
        return
    }

    LaunchedEffect(Unit) {
        settingsViewModel.processOnAppOpenIfNeeded()

        val installedFromAssets = pipeline.installBundledModelIfPresent()
        if (installedFromAssets) {
            repo.refreshModelStatus()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationsGranted(context)) {
            postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!isNotificationListenerEnabled(context)) {
            showListenerPermissionDialog = true
        }
        if (!pipeline.isModelInstalled()) {
            showModelImportDialog = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTopLevel(destination.route) },
                            icon = {
                                androidx.compose.material3.Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onSeeAllTransactions = { navigateToTopLevel(AppDestination.Transactions.route) }
                )
            }
            composable(AppDestination.Transactions.route) {
                TransactionsScreen(viewModel = transactionsViewModel, navController = navController)
            }
            composable(AppDestination.Budgets.route) {
                BudgetsScreen(viewModel = budgetsViewModel)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    pipeline = pipeline,
                    onOpenLab = { navController.navigate(AppDestination.Lab.route) },
                    onOpenManageRules = { navController.navigate(AppDestination.ManageRules.route) }
                )
            }
            composable(AppDestination.Lab.route) {
                LlmTestingScreen(pipeline = pipeline, modifier = Modifier.fillMaxSize())
            }
            composable(AppDestination.ManageRules.route) {
                ManageRulesScreen(
                    viewModel = settingsViewModel,
                    categoryOptions = transactionCategoryOptions,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = AppDestination.ResolveError.route,
                arguments = listOf(navArgument("errorId") { type = NavType.IntType })
            ) { backStackEntry ->
                val errorId = backStackEntry.arguments?.getInt("errorId") ?: return@composable
                val rejectedItems by transactionsViewModel.rejectedNotifications.collectAsStateWithLifecycle()
                val error = rejectedItems.find { it.id == errorId }
                
                if (error != null) {
                    ResolveErrorScreen(
                        error = error,
                        categoryOptions = transactionCategoryOptions,
                        onBack = { navController.popBackStack() },
                        onDeleteError = {
                            transactionsViewModel.deleteRejectedItem(errorId)
                            navController.popBackStack()
                        },
                        onSaveAsTransaction = { merchant, amount, category ->
                            transactionsViewModel.resolveRejectedAsTransaction(
                                errorId = errorId,
                                merchant = merchant,
                                amount = amount,
                                category = category,
                                rawNotificationText = error.text
                            )
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }

    if (showListenerPermissionDialog && !isNotificationListenerEnabled(context)) {
        AlertDialog(
            onDismissRequest = { showListenerPermissionDialog = false },
            title = { Text("Enable Notification Access") },
            text = {
                Text("Grant Notification Listener once so bank alerts can be scanned automatically after install.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showListenerPermissionDialog = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showListenerPermissionDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    if (showModelImportDialog && !pipeline.isModelInstalled()) {
        AlertDialog(
            onDismissRequest = { showModelImportDialog = false },
            title = { Text("Model Setup Needed") },
            text = {
                Text(
                    "The local model folder is missing. Select your model ZIP (for example, Qwen3-o.6B-q4f16-MLC.zip) so the app can install it into internal storage."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isImportingModel,
                    onClick = {
                        if (needsLegacyStoragePermission() && !isLegacyStoragePermissionGranted(context)) {
                            legacyStoragePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        } else {
                            modelImportError = null
                            modelZipPickerLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    }
                ) {
                    Text(if (isImportingModel) "Installing..." else "Select ZIP")
                }
            },
            dismissButton = {
                TextButton(onClick = { showModelImportDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    modelImportError?.let { message ->
        AlertDialog(
            onDismissRequest = { modelImportError = null },
            title = { Text("Model Import Failed") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { modelImportError = null }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun LlmTestingScreen(pipeline: LlmPipeline, modifier: Modifier = Modifier) {
    var rawNotificationInput by remember {
        mutableStateOf("Card Guard Credit Card *3320 $9.13 at SQ *KIWI LOCO FROZEN Y. Tap for details.")
    }
    var outputTransaction by remember { mutableStateOf<ExtractedTransaction?>(null) }
    var rawLlmJsonOutput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var detectedDevice by remember { mutableStateOf("Detecting...") }
    var retriesUsed by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        detectedDevice = pipeline.getBestLlmDevice()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "The Brain Testing Lab", style = MaterialTheme.typography.headlineMedium)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }) {
                Text("Grant Listener Permission")
            }
            
            Button(onClick = {
                sendTestNotification(context, rawNotificationInput)
            }) {
                Text("Post as System Notification")
            }
        }

        Text(text = "Detected LLM Device: $detectedDevice", style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = rawNotificationInput,
            onValueChange = { rawNotificationInput = it },
            label = { Text("Notification Text to Test") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    isProcessing = true
                    val result = pipeline.extractWithRetry(rawNotificationInput, maxAttempts = 2)
                    rawLlmJsonOutput = result.rawJson
                    outputTransaction = result.transaction
                    retriesUsed = result.retries
                    isProcessing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {
            Text(if (isProcessing) "LLM Thinking..." else "Process Manual Text")
        }

        HorizontalDivider()

        Text(text = "Raw LLM JSON Output (Retries: $retriesUsed):", style = MaterialTheme.typography.titleMedium)
        Text(text = rawLlmJsonOutput, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Verification Result:", style = MaterialTheme.typography.titleMedium)
        outputTransaction?.let { transaction ->
            val resultColor = if (transaction.isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

            Surface(color = resultColor.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Verified: ${transaction.isVerified}", color = resultColor, style = MaterialTheme.typography.titleSmall)
                    Text("Merchant: ${transaction.merchant}")
                    Text("Amount: $${transaction.amount}")
                    Text("Category: ${transaction.category}")
                    Text("Notes: ${transaction.verificationNotes}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Helper to post a real Android notification so we can test the BankNotificationListenerService.
 */
private fun sendTestNotification(context: Context, text: String) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "test_channel"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Test Notifications", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Neme Test Alert")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun needsLegacyStoragePermission(): Boolean {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2
}

private fun isLegacyStoragePermissionGranted(context: Context): Boolean {
    if (!needsLegacyStoragePermission()) return true
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    val thisComponent = ComponentName(context, com.example.nemebudget.notifications.BankNotificationListenerService::class.java)
        .flattenToString()
    return enabled.split(":").any { it.equals(thisComponent, ignoreCase = true) }
}
