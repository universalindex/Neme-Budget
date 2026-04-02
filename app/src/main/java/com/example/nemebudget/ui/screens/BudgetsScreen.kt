package com.example.nemebudget.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
private val CATEGORY_LABEL_ALLOWED = Regex("""^[A-Za-z0-9 !@#\$%^&*()_+\-:;.,/?]*$""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val overBudget = budgets.filter { it.limit > 0 && it.spent > it.limit }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedForActions by remember { mutableStateOf<Budget?>(null) }
    var editingMetaBudget by remember { mutableStateOf<Budget?>(null) }
    var showingAddCustomDialog by remember { mutableStateOf(false) }

    var inlineEditingBudgetId by remember { mutableStateOf<String?>(null) }
    var inlineLimitDraft by remember { mutableStateOf("") }

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
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            inlineEditingBudgetId = budget.id
                            inlineLimitDraft = String.format(Locale.US, "%.2f", budget.limit)
                        },
                        onLongClick = { selectedForActions = budget }
                    ),
                    isEditing = inlineEditingBudgetId == budget.id,
                    limitDraft = if (inlineEditingBudgetId == budget.id) inlineLimitDraft else "",
                    onLimitDraftChange = { inlineLimitDraft = it },
                    onSaveLimit = { parsedLimit ->
                        viewModel.upsertBudget(budget.copy(limit = parsedLimit))
                        inlineEditingBudgetId = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved ${budget.label} budget")
                        }
                    },
                    onCancelEdit = { inlineEditingBudgetId = null }
                )
            }
        }
    }

    selectedForActions?.let { selected ->
        AlertDialog(
            onDismissRequest = { selectedForActions = null },
            title = { Text("Category Actions") },
            text = {
                Text(
                    if (selected.isCustom) {
                        "${selected.label}: edit label/icon, edit limit inline, or delete this custom category."
                    } else {
                        "${selected.label}: edit label/icon, edit limit inline, or reset limit to default."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingMetaBudget = selected
                        selectedForActions = null
                    }
                ) {
                    Text("Edit label/icon")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        inlineEditingBudgetId = selected.id
                        inlineLimitDraft = String.format(Locale.US, "%.2f", selected.limit)
                        selectedForActions = null
                    }) {
                        Text("Edit limit")
                    }
                    TextButton(onClick = {
                        if (selected.isCustom) {
                            viewModel.deleteCustomCategory(selected.id)
                        } else {
                            selected.category?.let(viewModel::deleteBudget)
                        }
                        selectedForActions = null
                    }) {
                        Text(if (selected.isCustom) "Delete" else "Reset")
                    }
                    TextButton(onClick = { selectedForActions = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    editingMetaBudget?.let { budget ->
        CategoryMetaEditorDialog(
            budget = budget,
            onDismiss = { editingMetaBudget = null },
            onSave = { label, emoji ->
                viewModel.updateBudgetCategoryMeta(budget.id, label, emoji)
                editingMetaBudget = null
                scope.launch {
                    snackbarHostState.showSnackbar("Updated ${budget.label}")
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
    isEditing: Boolean,
    limitDraft: String,
    onLimitDraftChange: (String) -> Unit,
    onSaveLimit: (Double) -> Unit,
    onCancelEdit: () -> Unit,
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
                if (!isEditing) {
                    Text("$${String.format(Locale.US, "%.2f", budget.spent)} / $${String.format(Locale.US, "%.2f", budget.limit)}")
                } else {
                    Text("Editing limit", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (isEditing) {
                val parsedLimit = limitDraft.toDoubleOrNull()
                val isValid = parsedLimit != null && parsedLimit > 0.0
                OutlinedTextField(
                    value = limitDraft,
                    onValueChange = onLimitDraftChange,
                    label = { Text("Budget limit") },
                    supportingText = {
                        Text(if (isValid) "Enter a positive dollar amount." else "Limit must be a number greater than zero.")
                    },
                    isError = limitDraft.isNotBlank() && !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCancelEdit) { Text("Cancel") }
                    TextButton(onClick = { parsedLimit?.let(onSaveLimit) }, enabled = isValid) { Text("Save") }
                }
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = animatedColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = if (budget.isCustom) {
                        "Custom category. Tap amount to edit limit. Long-press to rename or delete."
                    } else {
                        "Built-in category. Tap amount to edit limit. Long-press for label/icon edits."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryMetaEditorDialog(
    budget: Budget,
    onDismiss: () -> Unit,
    onSave: (label: String, emoji: String) -> Unit
) {
    var labelInput by remember(budget.id) { mutableStateOf(budget.label) }
    var emojiInput by remember(budget.id) { mutableStateOf(budget.emoji) }
    val canSave = labelInput.trim().isNotBlank() && emojiInput.trim().isNotBlank()
    val labelHint = "Max $CATEGORY_LABEL_MAX_LENGTH chars. Allowed: letters, numbers, spaces, and !@#\$%^&*()_+-:;.,/?"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                OutlinedTextField(
                    value = emojiInput,
                    onValueChange = { emojiInput = it },
                    label = { Text("Icon (emoji)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(labelInput.trim(), emojiInput.trim()) }, enabled = canSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

