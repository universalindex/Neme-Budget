package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.nemebudget.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onOpenLab: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    var ruleInput by remember { mutableStateOf("") }

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onOpenLab) { Text("Open LLM Lab") }
            Button(onClick = viewModel::wipeAll) { Text("Wipe Data") }
        }
    }
}

