package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.viewmodel.BudgetsViewModel
import java.util.Locale

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Budgets", style = MaterialTheme.typography.headlineSmall)
        }

        items(budgets) { budget ->
            val progress = (budget.spent / budget.limit).toFloat().coerceIn(0f, 1f)
            val color = when {
                progress < 0.6f -> Color(0xFF4CAF50)
                progress < 0.85f -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            }
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${budget.category.emoji} ${budget.category.label}")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = color)
                    Text("$${String.format(Locale.US, "%.2f", budget.spent)} / $${String.format(Locale.US, "%.2f", budget.limit)}")
                }
            }
        }
    }
}

