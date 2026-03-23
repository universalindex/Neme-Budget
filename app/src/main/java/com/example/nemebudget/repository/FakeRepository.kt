package com.example.nemebudget.repository

import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class FakeRepository : AppRepository {

    private val transactionsFlow = MutableStateFlow(generateTransactions())
    private val budgetsFlow = MutableStateFlow(generateBudgets(transactionsFlow.value))
    private val settingsFlow = MutableStateFlow(
        AppSettings(
            primaryBank = "Chase",
            ignoredApps = setOf("com.venmo"),
            customRules = listOf("Chevron = Gas")
        )
    )
    private val modelStatusFlow = MutableStateFlow(
        ModelStatus(
            isDownloaded = true,
            downloadProgress = 1f,
            modelSizeLabel = "1.1 GB"
        )
    )

    override fun getAllTransactions(): Flow<List<Transaction>> = transactionsFlow

    override fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>> {
        return transactionsFlow.map { txns -> txns.filter { it.date in start..end } }
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionsFlow.value = transactionsFlow.value.map { current ->
            if (current.id == transaction.id) transaction else current
        }
        budgetsFlow.value = generateBudgets(transactionsFlow.value)
    }

    override fun getBudgets(): Flow<List<Budget>> = budgetsFlow

    override suspend fun upsertBudget(budget: Budget) {
        val current = budgetsFlow.value.toMutableList()
        val index = current.indexOfFirst { it.category == budget.category }
        if (index >= 0) {
            current[index] = budget.copy(spent = current[index].spent)
        } else {
            current.add(budget)
        }
        budgetsFlow.value = current.sortedBy { it.category.ordinal }
    }

    override suspend fun deleteBudget(category: Category) {
        budgetsFlow.value = budgetsFlow.value.filterNot { it.category == category }
    }

    override fun getSettings(): Flow<AppSettings> = settingsFlow

    override suspend fun saveSettings(settings: AppSettings) {
        settingsFlow.value = settings
    }

    override fun getModelStatus(): Flow<ModelStatus> = modelStatusFlow

    override suspend fun getTotalTransactionCount(): Int = transactionsFlow.value.size

    override suspend fun wipeAllData() {
        transactionsFlow.value = emptyList()
        budgetsFlow.value = emptyList()
        settingsFlow.value = AppSettings()
    }

    override suspend fun exportToCsv(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val header = "Date,Merchant,Amount,Category,AI Parsed,Confidence"
        val rows = transactionsFlow.value.joinToString("\n") { txn ->
            val date = formatter.format(Date(txn.date))
            "$date,\"${txn.merchant}\",${txn.amount},${txn.category.name},${txn.isAiParsed},${txn.confidence}"
        }
        return "$header\n$rows"
    }

    private fun generateTransactions(): List<Transaction> {
        val now = System.currentTimeMillis()
        val merchants = mapOf(
            Category.DINING to listOf("Chipotle", "Starbucks", "Cafe Rio", "Subway", "Chick-fil-A"),
            Category.GROCERIES to listOf("Walmart", "Smiths", "Whole Foods", "Costco", "Trader Joes"),
            Category.GAS to listOf("Chevron", "Shell", "Maverik", "Exxon", "Sinclair"),
            Category.BILLS to listOf("Rocky Mountain Power", "Comcast", "AT&T", "Spotify", "Netflix"),
            Category.SUBSCRIPTIONS to listOf("Netflix", "Hulu", "Disney+", "YouTube Premium", "Spotify"),
            Category.ENTERTAINMENT to listOf("Cinemark", "Steam", "Xbox", "AMC", "Topgolf"),
            Category.SHOPPING to listOf("Amazon", "Target", "Best Buy", "Old Navy", "Nike"),
            Category.TRANSPORT to listOf("Uber", "Lyft", "UTA", "Delta", "Southwest"),
            Category.HEALTH to listOf("Walgreens", "CVS", "Intermountain", "Vision Center", "Dental Care"),
            Category.TRAVEL to listOf("Airbnb", "Expedia", "Hilton", "Southwest", "Delta"),
            Category.OTHER to listOf("Venmo", "PayPal", "Apple", "Google", "Misc Store")
        )

        return (1..50).map { id ->
            val category = Category.entries[(id - 1) % Category.entries.size]
            val merchantList = merchants.getValue(category)
            val merchant = merchantList[Random(id).nextInt(merchantList.size)]
            val amount = when (category) {
                Category.GAS -> Random(id * 11).nextDouble(25.0, 75.0)
                Category.BILLS -> Random(id * 13).nextDouble(12.0, 180.0)
                Category.GROCERIES -> Random(id * 17).nextDouble(20.0, 150.0)
                Category.TRAVEL -> Random(id * 29).nextDouble(80.0, 420.0)
                else -> Random(id * 19).nextDouble(5.0, 120.0)
            }
            val daysAgo = Random(id * 7).nextInt(0, 30)
            val timestamp = now - (daysAgo * 24L * 60L * 60L * 1000L) - Random(id * 23).nextLong(0, 86_400_000L)

            Transaction(
                id = id,
                merchant = merchant,
                amount = String.format(Locale.US, "%.2f", amount).toDouble(),
                category = category,
                date = timestamp,
                isAiParsed = id % 3 != 0,
                confidence = (0.6f + (id % 40) * 0.01f).coerceAtMost(0.99f),
                rawNotificationText = "Sample notification text #$id"
            )
        }.sortedByDescending { it.date }
    }

    private fun generateBudgets(transactions: List<Transaction>): List<Budget> {
        val spentByCategory = transactions.groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount } }

        val limits = mapOf(
            Category.DINING to 350.0,
            Category.GROCERIES to 500.0,
            Category.GAS to 260.0,
            Category.BILLS to 450.0,
            Category.SUBSCRIPTIONS to 120.0,
            Category.ENTERTAINMENT to 200.0,
            Category.SHOPPING to 280.0,
            Category.TRANSPORT to 220.0,
            Category.HEALTH to 160.0,
            Category.TRAVEL to 350.0,
            Category.OTHER to 180.0
        )

        return Category.entries.map { category ->
            Budget(
                category = category,
                spent = spentByCategory[category] ?: 0.0,
                limit = limits.getValue(category)
            )
        }
    }
}


