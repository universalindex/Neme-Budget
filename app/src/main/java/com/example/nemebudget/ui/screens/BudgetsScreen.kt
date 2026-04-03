package com.example.nemebudget.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.model.Budget
import com.example.nemebudget.viewmodel.BudgetsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private const val CATEGORY_LABEL_MAX_LENGTH = 50
private val CATEGORY_LABEL_ALLOWED = Regex("""^[A-Za-z0-9 !@#$%^&*()_+\-:;.,/?]*$""")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val overBudget = budgets.filter { it.limit > 0 && it.spent > it.limit }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var showingAddCustomDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showingAddCustomDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add custom budget category")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Budgets", style = MaterialTheme.typography.headlineSmall)
            }

            item {
                AnimatedVisibility(visible = overBudget.isNotEmpty()) {
                    val offender = overBudget.firstOrNull()
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Warning: ${offender?.label ?: "A category"} is over budget",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .clickable {
                                    val offenderId = offender?.id ?: return@clickable
                                    val budgetIndex = budgets.indexOfFirst { it.id == offenderId }
                                    if (budgetIndex >= 0) {
                                        scope.launch { listState.animateScrollToItem(budgetIndex + 2) }
                                    }
                                }
                                .padding(12.dp)
                        )
                    }
                }
            }

            items(budgets, key = { it.id }) { budget ->
                BudgetCard(
                    budget = budget,
                    modifier = Modifier.clickable { editingBudget = budget }
                )
            }
        }
    }

    editingBudget?.let { budget ->
        BudgetEditBottomSheet(
            budget = budget,
            onDismiss = { editingBudget = null },
            onSave = { updatedBudget, newLabel, newEmoji ->
                viewModel.upsertBudget(updatedBudget)
                if (newLabel != budget.label || newEmoji != budget.emoji) {
                    viewModel.updateBudgetCategoryMeta(budget.id, newLabel, newEmoji)
                }
                editingBudget = null
                scope.launch {
                    snackbarHostState.showSnackbar("Saved ${budget.label} budget")
                }
            },
            onDelete = { budgetToDelete ->
                if (budgetToDelete.isCustom) {
                    viewModel.deleteCustomCategory(budgetToDelete.id)
                } else {
                    budgetToDelete.category?.let(viewModel::deleteBudget)
                }
                editingBudget = null
                scope.launch {
                    snackbarHostState.showSnackbar("Deleted ${budgetToDelete.label}")
                }
            }
        )
    }

    if (showingAddCustomDialog) {
        AddCustomCategoryDialog(
            onDismiss = { showingAddCustomDialog = false },
            onCreate = { label, emoji, limit ->
                viewModel.addCustomCategory(label, emoji, limit)
                showingAddCustomDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar("Added custom budget category: $label")
                }
            }
        )
    }
}

@Composable
private fun BudgetCard(
    budget: Budget,
    modifier: Modifier = Modifier
) {
    val safeLimit = budget.limit.coerceAtLeast(0.01)
    val progress = (budget.spent / safeLimit).toFloat().coerceIn(0f, 1f)
    val targetColor = when {
        progress < 0.60f -> Color(0xFF4CAF50)
        progress < 0.85f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(600), label = "budget_color")
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(800), label = "budget_progress")

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${budget.emoji} ${budget.label}")
                Text("$${String.format(Locale.US, "%.2f", budget.spent)} / $${String.format(Locale.US, "%.2f", budget.limit)}")
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = animatedColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "Tap to edit limit, label, or icon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditBottomSheet(
    budget: Budget,
    onDismiss: () -> Unit,
    onSave: (updatedBudget: Budget, newLabel: String, newEmoji: String) -> Unit,
    onDelete: (budget: Budget) -> Unit
) {
    var sliderMax by remember(budget.id) {
        mutableFloatStateOf(maxOf(1000f, (budget.limit.toFloat() / 0.8f).coerceAtLeast(1000f)))
    }
    var sliderValue by remember(budget.id) { mutableFloatStateOf(budget.limit.toFloat().coerceAtLeast(0f)) }
    var limitInput by remember(budget.id) { mutableStateOf(String.format(Locale.US, "%.2f", budget.limit)) }
    var labelInput by remember(budget.id) { mutableStateOf(budget.label) }
    var emojiInput by remember(budget.id) { mutableStateOf(budget.emoji) }

    val parsedLimit = limitInput.toDoubleOrNull()
    val canSave = labelInput.trim().isNotBlank() && emojiInput.trim().isNotBlank() && parsedLimit != null && parsedLimit >= 0.0
    val labelHint = "Max $CATEGORY_LABEL_MAX_LENGTH chars. Allowed: letters, numbers, spaces, and !@#\$%^&*()_+-:;.,/?"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Edit Budget: ${budget.label}", style = MaterialTheme.typography.titleLarge)

            // Scrubbing slider for limit with quick adjustment
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Budget Limit", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "$${String.format(Locale.US, "%.2f", sliderValue)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        limitInput = String.format(Locale.US, "%.2f", it)
                    },
                    valueRange = 0f..sliderMax,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Drag slider to scrub quickly (default 0-1000). Typing a higher value expands max so your typed value is 80% of the slider range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = limitInput,
                onValueChange = { next ->
                    limitInput = next
                    val typed = next.toDoubleOrNull() ?: return@OutlinedTextField
                    if (typed < 0.0) return@OutlinedTextField
                    val targetMax = maxOf(1000f, (typed / 0.8).toFloat())
                    sliderMax = targetMax
                    sliderValue = typed.toFloat().coerceIn(0f, sliderMax)
                },
                label = { Text("Budget limit") },
                supportingText = {
                    Text(if (parsedLimit == null || parsedLimit < 0.0) "Enter a non-negative number." else "Typed value syncs slider position.")
                },
                isError = parsedLimit == null || parsedLimit < 0.0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            // Label editing
            OutlinedTextField(
                value = labelInput,
                onValueChange = { next ->
                    if (next.length <= CATEGORY_LABEL_MAX_LENGTH && CATEGORY_LABEL_ALLOWED.matches(next)) {
                        labelInput = next
                    }
                },
                label = { Text("Label") },
                supportingText = { Text(labelHint) },
                modifier = Modifier.fillMaxWidth()
            )

            // Icon editing
            OutlinedTextField(
                value = emojiInput,
                onValueChange = { emojiInput = it },
                label = { Text("Icon (emoji)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                if (budget.isCustom) {
                    TextButton(
                        onClick = { onDelete(budget) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(
                        onClick = { onDelete(budget) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                }

                TextButton(
                    onClick = {
                        onSave(
                            budget.copy(limit = parsedLimit ?: budget.limit),
                            labelInput.trim(),
                            emojiInput.trim()
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun AddCustomCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (label: String, emoji: String, limit: Double) -> Unit
) {
    var labelInput by remember { mutableStateOf("") }
    var emojiInput by remember { mutableStateOf("📁") }
    var limitInput by remember { mutableStateOf("100") }

    val parsedLimit = limitInput.toDoubleOrNull()
    val canCreate = labelInput.trim().isNotBlank() && emojiInput.trim().isNotBlank() && parsedLimit != null && parsedLimit > 0.0
    val labelHint = "Max $CATEGORY_LABEL_MAX_LENGTH chars. Allowed: letters, numbers, spaces, and !@#\$%^&*()_+-:;.,/?"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { next ->
                        if (next.length <= CATEGORY_LABEL_MAX_LENGTH && CATEGORY_LABEL_ALLOWED.matches(next)) {
                            labelInput = next
                        }
                    },
                    label = { Text("Category label") },
                    supportingText = { Text(labelHint) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { emojiInput = it },
                    label = { Text("Icon (emoji)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Initial limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(labelInput.trim(), emojiInput.trim(), parsedLimit ?: 0.0) }, enabled = canCreate) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

