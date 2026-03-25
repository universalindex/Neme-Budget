package com.example.nemebudget.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.viewmodel.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selected by viewModel.filter.collectAsStateWithLifecycle()

    val sectionFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.US) }
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.US) }
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { sectionFormatter.format(Date(it.date)) }.toList()
    }

    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Transactions", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onSearchQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search merchant") }
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = { viewModel.onFilterChanged(null) },
                    label = { Text("All") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected == null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                )
            }
            itemsIndexed(Category.entries) { _, category ->
                AssistChip(
                    onClick = { viewModel.onFilterChanged(category) },
                    label = { Text(category.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected == category) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                )
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groupedTransactions.forEach { (dateLabel, items) ->
                stickyHeader {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 6.dp)
                    )
                }
                itemsIndexed(items) { _, txn ->
                    TransactionRow(
                        transaction = txn,
                        dateLabel = dateFormatter.format(Date(txn.date)),
                        onEdit = { editingTransaction = txn }
                    )
                }
            }
        }
    }

    editingTransaction?.let { original ->
        EditTransactionBottomSheet(
            original = original,
            onDismiss = { editingTransaction = null },
            onSave = { merchant, amount, category ->
                viewModel.saveEdit(
                    original = original,
                    updated = original.copy(
                        merchant = merchant,
                        amount = amount,
                        category = category
                    )
                )
                editingTransaction = null
            }
        )
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    dateLabel: String,
    onEdit: () -> Unit
) {
    val confidenceColor = when {
        transaction.confidence < 0.75f -> MaterialTheme.colorScheme.error
        transaction.confidence < 0.9f -> Color(0xFFFFB300)
        else -> Color(0xFF2E7D32)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = transaction.category.emoji,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = transaction.merchant,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${transaction.category.label} - $dateLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "-$${String.format(Locale.US, "%.2f", transaction.amount)}",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                if (transaction.isAiParsed) {
                    Spacer(Modifier.width(6.dp))
                    Text("\u2728")
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = onEdit) { Text("Edit") }
            }
        }

        LinearProgressIndicator(
            progress = { transaction.confidence.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = confidenceColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditTransactionBottomSheet(
    original: Transaction,
    onDismiss: () -> Unit,
    onSave: (merchant: String, amount: Double, category: Category) -> Unit
) {
    var merchant by remember(original.id) { mutableStateOf(original.merchant) }
    var amountInput by remember(original.id) { mutableStateOf(String.format(Locale.US, "%.2f", original.amount)) }
    var selectedCategory by remember(original.id) { mutableStateOf(original.category) }
    var menuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Edit Transaction", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(selectedCategory.label)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Category.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.label) },
                        onClick = {
                            selectedCategory = category
                            menuExpanded = false
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = {
                        val parsed = amountInput.toDoubleOrNull() ?: original.amount
                        val safeMerchant = merchant.trim().ifBlank { original.merchant }
                        onSave(safeMerchant, parsed, selectedCategory)
                    }
                ) {
                    Text("Save")
                }
            }

            Spacer(Modifier.size(6.dp))
            Text(
                text = "Correcting transactions teaches category preferences for future parses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(18.dp))
        }
    }
}
