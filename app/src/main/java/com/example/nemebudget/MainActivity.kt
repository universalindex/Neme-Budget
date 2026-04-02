package com.example.nemebudget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.ComponentName
import android.content.Intent
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
    val modelStatus by settingsViewModel.modelStatus.collectAsStateWithLifecycle()

    val onboardingPrefs = context.applicationContext.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE)
    var onboardingCompleted by remember(context) {
        mutableStateOf(onboardingPrefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    }

    if (!onboardingCompleted) {
        OnboardingFlowScreen(
            modelStatus = modelStatus,
            onFinished = {
                onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
                onboardingCompleted = true
            }
        )
        return
    }

    var showListenerPermissionDialog by remember { mutableStateOf(false) }
    val postNotificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: status is checked on recomposition */ }
    LaunchedEffect(Unit) {
        settingsViewModel.processOnAppOpenIfNeeded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationsGranted(context)) {
            postNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!isNotificationListenerEnabled(context)) {
            showListenerPermissionDialog = true
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
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
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
                    onSeeAllTransactions = { navController.navigate(AppDestination.Transactions.route) }
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
                    onOpenLab = { navController.navigate(AppDestination.Lab.route) }
                )
            }
            composable(AppDestination.Lab.route) {
                LlmTestingScreen(pipeline = pipeline, modifier = Modifier.fillMaxSize())
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
}

@Composable
fun LlmTestingScreen(pipeline: LlmPipeline, modifier: Modifier = Modifier) {
    var rawNotificationInput by remember { mutableStateOf("Chase: You spent $5.40 at Starbucks today.") }
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

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false

    val thisComponent = ComponentName(context, com.example.nemebudget.notifications.BankNotificationListenerService::class.java)
        .flattenToString()
    return enabled.split(":").any { it.equals(thisComponent, ignoreCase = true) }
}
