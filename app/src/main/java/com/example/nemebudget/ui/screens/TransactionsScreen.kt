package com.example.nemebudget.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.RejectedNotification
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.viewmodel.TransactionsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel, navController: NavController) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val rejectedItems by viewModel.rejectedNotifications.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selected by viewModel.filter.collectAsStateWithLifecycle()
    val categoryOptions by viewModel.categoryOptions.collectAsStateWithLifecycle()
    val sectionFormatter = remember { SimpleDateFormat("MMMM d, yyyy", Locale.US) }
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.US) }
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { sectionFormatter.format(Date(it.date)) }.toList()
    }
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun deleteWithUndo(txn: Transaction) {
        viewModel.deleteTransaction(txn.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted ${txn.merchant}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreTransaction(txn)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Transactions", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { showAddTransactionSheet = true }) {
                Text("Add transaction")
            }
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
                itemsIndexed(categoryOptions) { _, category ->
                    AssistChip(
                        onClick = { viewModel.onFilterChanged(category) },
                        label = { Text(category.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected?.id == category.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    )
                }
            }
            if (rejectedItems.isNotEmpty()) {
                HorizontalDivider()
                Text("Errors", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rejectedItems, key = { it.id }) { item ->
                        RejectedItemRow(
                            item = item,
                            onOpen = {
                                navController.navigate("resolve_error/${item.id}") {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
                HorizontalDivider()
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedTransactions.forEach { (dateLabel, items) ->
                    item {
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
        FloatingActionButton(
            onClick = { showAddTransactionSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(104.dp)
                .padding(16.dp)
        ) {
            Text("+", style = MaterialTheme.typography.displaySmall)
        }
    }
    }
    if (showAddTransactionSheet) {
        AddTransactionBottomSheet(
            categoryOptions = categoryOptions,
            onDismiss = { showAddTransactionSheet = false },
            onSave = { merchant, amount, category ->
                viewModel.addManualTransaction(merchant, amount, category)
                showAddTransactionSheet = false
            }
        )
    }
    editingTransaction?.let { original ->
        EditTransactionBottomSheet(
            original = original,
            categoryOptions = categoryOptions,
            onDismiss = { editingTransaction = null },
            onDelete = {
                deleteWithUndo(original)
                editingTransaction = null
            },
            onSave = { merchant, amount, category ->
                val updated = original.copy(
                    merchant = merchant,
                    amount = amount,
                    category = category
                )
                viewModel.saveEdit(original, updated)
                editingTransaction = null
            }
        )
    }
}
@Composable
private fun RejectedItemRow(item: RejectedNotification, onOpen: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(item.title.ifBlank { "(No title)" }, fontWeight = FontWeight.SemiBold)
        Text(item.text, style = MaterialTheme.typography.bodySmall)
        Text(
            "Reason: ${item.errorMessage}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatter.format(Date(item.postTimeMillis)), style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = onOpen) { Text("Open") }
        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
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
            Text(
                text = "-$${String.format(Locale.US, "%.2f", transaction.amount)}",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
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
    categoryOptions: List<CategoryDefinition>,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (merchant: String, amount: Double, category: CategoryDefinition) -> Unit
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
                categoryOptions.forEach { category ->
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
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
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
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AddTransactionBottomSheet(
    categoryOptions: List<CategoryDefinition>,
    onDismiss: () -> Unit,
    onSave: (merchant: String, amount: Double, category: CategoryDefinition) -> Unit
) {
    var merchant by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categoryOptions.firstOrNull { it.label == "Other" } ?: categoryOptions.first()) }
    var menuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add Transaction", style = MaterialTheme.typography.titleLarge)
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
                categoryOptions.forEach { category ->
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
                        val parsedAmount = amountInput.toDoubleOrNull() ?: return@Button
                        onSave(merchant, parsedAmount, selectedCategory)
                    }
                ) {
                    Text("Add")
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}
