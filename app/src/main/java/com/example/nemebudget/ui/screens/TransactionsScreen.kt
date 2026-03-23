package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.model.Category
import com.example.nemebudget.viewmodel.TransactionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel) {
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val query = viewModel.currentQuery()
    val selected = viewModel.currentFilter()
    val formatter = SimpleDateFormat("MMM d", Locale.US)

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

        Row(modifier = Modifier.fillMaxWidth()) {
            AssistChip(
                onClick = { viewModel.onFilterChanged(null) },
                label = { Text("All") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            )
            Spacer(Modifier.size(8.dp))
            Category.entries.take(4).forEach { category ->
                AssistChip(
                    onClick = { viewModel.onFilterChanged(category) },
                    label = { Text(category.label) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected == category) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                )
                Spacer(Modifier.size(8.dp))
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(transactions) { txn ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${txn.category.emoji} ${txn.merchant}")
                        Text(formatter.format(Date(txn.date)), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("-$${String.format(Locale.US, "%.2f", txn.amount)}")
                }
            }
        }
    }
}

