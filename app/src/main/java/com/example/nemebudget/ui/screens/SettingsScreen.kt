package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.model.ProcessingState
import com.example.nemebudget.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenLab: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingNotificationCount.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    var ruleInput by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.US) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = settings.primaryBank,
            onValueChange = viewModel::updatePrimaryBank,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Primary bank") }
        )

        OutlinedTextField(
            value = ruleInput,
            onValueChange = { ruleInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Add custom rule") }
        )
        Button(onClick = {
            viewModel.addCustomRule(ruleInput)
            ruleInput = ""
        }) {
            Text("Add rule")
        }

        Text("Rules: ${settings.customRules.joinToString()}")
        Text("Model: ${if (modelStatus.isDownloaded) "Ready" else "Downloading"} (${modelStatus.modelSizeLabel})")
        Text("Transactions stored: $totalCount")
        Text("Queued notifications: $pendingCount")

        val isRunning = processingState is ProcessingState.Processing
        Button(onClick = viewModel::processNow, enabled = !isRunning) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(if (isRunning) "Processing..." else "Process Now")
        }

        val processingLabel = when (val state = processingState) {
            is ProcessingState.Idle -> ""
            is ProcessingState.Processing -> "Processing ${state.pendingCount} queued notifications..."
            is ProcessingState.Success -> {
                "Processed ${state.processedCount} notifications at ${timeFormatter.format(Date(state.completedAtMillis))}"
            }
            is ProcessingState.Error -> "Processing failed: ${state.message}"
        }
        if (processingLabel.isNotBlank()) {
            Text(processingLabel, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenLab) { Text("Open LLM Lab") }
            Button(onClick = viewModel::wipeAll) { Text("Wipe Data") }
        }
    }
}

