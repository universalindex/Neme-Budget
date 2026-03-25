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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.nemebudget.model.Category
import com.example.nemebudget.viewmodel.BudgetsViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val overBudget = budgets.filter { it.limit > 0 && it.spent > it.limit }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var editingBudget by remember { mutableStateOf<Budget?>(null) }
    var selectedForActions by remember { mutableStateOf<Budget?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { editingBudget = Budget(Category.DINING, spent = 0.0, limit = 100.0) }) {
                Icon(Icons.Default.Add, contentDescription = "Add budget")
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
                            text = "Warning: ${offender?.category?.label ?: "A category"} is over budget",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .clickable {
                                    val category = offender?.category ?: return@clickable
                                    val budgetIndex = budgets.indexOfFirst { it.category == category }
                                    if (budgetIndex >= 0) {
                                        // Header + banner occupy the first two list items.
                                        scope.launch { listState.animateScrollToItem(budgetIndex + 2) }
                                    }
                                }
                                .padding(12.dp)
                        )
                    }
                }
            }

            items(budgets) { budget ->
                BudgetCard(
                    budget = budget,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { selectedForActions = budget }
                    )
                )
            }
        }
    }

    selectedForActions?.let { selected ->
        AlertDialog(
            onDismissRequest = { selectedForActions = null },
            title = { Text("Budget Actions") },
            text = { Text("${selected.category.label}: choose edit or delete") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingBudget = selected
                        selectedForActions = null
                    }
                ) {
                    Text("Edit")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.deleteBudget(selected.category)
                        selectedForActions = null
                    }) {
                        Text("Delete")
                    }
                    TextButton(onClick = { selectedForActions = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    editingBudget?.let { draft ->
        BudgetEditorDialog(
            initial = draft,
            onDismiss = { editingBudget = null },
            onSave = { category, limit ->
                val existingSpent = budgets.firstOrNull { it.category == category }?.spent ?: 0.0
                viewModel.upsertBudget(
                    Budget(
                        category = category,
                        spent = existingSpent,
                        limit = limit
                    )
                )
                editingBudget = null
            }
        )
    }
}

@Composable
private fun BudgetCard(
    budget: Budget,
    modifier: Modifier = Modifier
) {
    val progress = (budget.spent / budget.limit).toFloat().coerceIn(0f, 1f)
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
                Text("${budget.category.emoji} ${budget.category.label}")
                Text("$${String.format(Locale.US, "%.2f", budget.spent)} / $${String.format(Locale.US, "%.2f", budget.limit)}")
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = animatedColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    initial: Budget,
    onDismiss: () -> Unit,
    onSave: (Category, Double) -> Unit
) {
    var selectedCategory by remember(initial.category) { mutableStateOf(initial.category) }
    var limitInput by remember(initial.limit) { mutableStateOf(String.format(Locale.US, "%.2f", initial.limit)) }
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add or Edit Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { categoryExpanded = true }) {
                    Text(selectedCategory.label)
                }
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    Category.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.label) },
                            onClick = {
                                selectedCategory = category
                                categoryExpanded = false
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { limitInput = it },
                    label = { Text("Limit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedLimit = limitInput.toDoubleOrNull()
                if (parsedLimit != null && parsedLimit > 0.0) {
                    onSave(selectedCategory, parsedLimit)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

