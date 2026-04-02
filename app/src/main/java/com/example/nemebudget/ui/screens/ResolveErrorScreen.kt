package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.RejectedNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ResolveErrorScreen - A full-page screen for resolving rejected notifications.
 * 
 * **Why this is a page instead of a bottom sheet:**
 * Bottom sheets are good for simple dialogs, but this screen needs:
 * - Full scroll space for long notification text
 * - Multiple form fields
 * - Clear navigation back to Transactions
 * - Better use of screen real estate
 * 
 * **Architecture:**
 * 1. TOP: Back button + Title
 * 2. MIDDLE: Scrollable notification details + form fields
 * 3. BOTTOM: Save and Delete buttons
 * 
 * The parent (TransactionsScreen/TransactionsViewModel) owns the navigation logic.
 * This screen just renders the UI and calls callbacks for Save/Delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveErrorScreen(
    error: RejectedNotification,
    onBack: () -> Unit,
    onDeleteError: () -> Unit,
    categoryOptions: List<CategoryDefinition>,
    onSaveAsTransaction: (merchant: String, amount: Double, category: CategoryDefinition) -> Unit
) {
    var merchant by remember(error.id) { mutableStateOf("") }
    var amountInput by remember(error.id) { mutableStateOf("") }
    var selectedCategory by remember(error.id) { mutableStateOf(categoryOptions.firstOrNull { it.label == "Other" } ?: categoryOptions.first()) }

    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolve Error") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // MIDDLE: Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Notification Details Section
                Text("Notification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (error.title.isNotBlank()) {
                            Text(
                                error.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            error.text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            dateFormatter.format(Date(error.postTimeMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Error Reason
                Text("Error Reason", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        error.errorMessage,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }

                HorizontalDivider()

                // Form Fields Section
                Text("Correct the Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("Category", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categoryOptions) { category ->
                        AssistChip(
                            onClick = { selectedCategory = category },
                            label = { Text(category.label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selectedCategory == category) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // BOTTOM: Action Buttons
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delete button on left
                TextButton(
                    onClick = onDeleteError,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete Error")
                }

                // Save and Cancel on right
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val parsed = amountInput.toDoubleOrNull() ?: 0.0
                            if (merchant.isNotBlank() && parsed > 0.0) {
                                onSaveAsTransaction(merchant, parsed, selectedCategory)
                            }
                        },
                        enabled = merchant.isNotBlank() && amountInput.toDoubleOrNull()?.let { it > 0.0 } == true
                    ) {
                        Text("Save to Database")
                    }
                }
            }
        }
    }
}



