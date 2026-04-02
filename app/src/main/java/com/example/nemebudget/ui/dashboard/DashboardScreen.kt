package com.example.nemebudget.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.viewmodel.DashboardViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSeeAllTransactions: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val spendingByCategory by viewModel.spendingByCategory.collectAsStateWithLifecycle()
    val safeToSpend by viewModel.safeToSpend.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    val onPrevious = viewModel::previousMonth
    val onNext = viewModel::nextMonth

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(onPrevious, onNext) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            totalDrag <= -swipeThresholdPx -> onNext()
                            totalDrag >= swipeThresholdPx -> onPrevious()
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = {
                        totalDrag = 0f
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            MonthSelector(
                label = selectedMonth.label(),
                onPrevious = onPrevious,
                onNext = onNext
            )
        }

        item {
            SafeToSpendCard(safeToSpend = safeToSpend)
        }

        item {
            SpendingDonutCard(spendingByCategory = spendingByCategory)
        }

        item {
            RecentTransactionsCard(
                transactions = transactions.take(5),
                onSeeAllTransactions = onSeeAllTransactions
            )
        }
    }
}

@Composable
private fun MonthSelector(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous month")
        }
        Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next month")
        }
    }
}

@Composable
private fun SafeToSpendCard(safeToSpend: Double) {
    val animatedValue by animateFloatAsState(targetValue = safeToSpend.toFloat(), label = "safe_to_spend")
    val positive = safeToSpend >= 0
    val gradient = if (positive) {
        Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF00695C)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF6D1B1B), Color(0xFFB71C1C)))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Safe to Spend", color = Color.White.copy(alpha = 0.92f))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$${String.format(Locale.US, "%,.2f", animatedValue)}",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SpendingDonutCard(spendingByCategory: Map<CategoryDefinition, Double>) {
    val entries = spendingByCategory.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }

    var selectedIndex by remember { mutableIntStateOf(0) }
    if (selectedIndex > entries.lastIndex) selectedIndex = 0

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spending by Category", style = MaterialTheme.typography.titleMedium)

            if (entries.isEmpty()) {
                Text("No spending in this month yet.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }

            val total = entries.sumOf { it.value }.toFloat().coerceAtLeast(1f)
            val colors = listOf(
                Color(0xFF4FC3F7), Color(0xFF81C784), Color(0xFFFFB74D), Color(0xFFE57373),
                Color(0xFFBA68C8), Color(0xFFA1887F), Color(0xFF90A4AE), Color(0xFFFFD54F)
            )

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(220.dp)) {
                    var startAngle = -90f
                    entries.forEachIndexed { index, entry ->
                        val sweep = (entry.value.toFloat() / total) * 360f
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 36f, cap = StrokeCap.Butt),
                            size = Size(size.width, size.height)
                        )
                        startAngle += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val selected = entries[selectedIndex]
                    Text(selected.key.label, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$${String.format(Locale.US, "%.2f", selected.value)}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedIndex = index },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = colors[index % colors.size],
                            modifier = Modifier.size(10.dp),
                            shape = MaterialTheme.shapes.small
                        ) {}
                        Spacer(Modifier.size(8.dp))
                        Text("${entry.key.emoji} ${entry.key.label}")
                    }
                    Text("$${String.format(Locale.US, "%.2f", entry.value)}")
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionsCard(
    transactions: List<Transaction>,
    onSeeAllTransactions: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d", Locale.US) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onSeeAllTransactions)
                )
            }

            if (transactions.isEmpty()) {
                Text("No transactions yet.")
            } else {
                transactions.forEach { txn ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("${txn.category.emoji} ${txn.merchant}", fontWeight = FontWeight.Medium)
                            Text(dateFormatter.format(Date(txn.date)), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("-$${String.format(Locale.US, "%.2f", txn.amount)}")
                            if (txn.isAiParsed) {
                                Spacer(Modifier.size(6.dp))
                                Text("\u2728")
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
