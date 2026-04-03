package com.example.nemebudget.repository

import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.CategoryPresentation
import com.example.nemebudget.model.CustomBudgetCategory
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.RuleField
import com.example.nemebudget.model.RejectedNotification
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.model.activeCategoryOptions
import com.example.nemebudget.model.toDefinition
import com.example.nemebudget.model.resolveCategoryByLabel
import com.example.nemebudget.model.resolveCategoryById
import com.example.nemebudget.pipeline.NotificationBatchProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

class FakeRepository(
    private val batchProcessor: NotificationBatchProcessor? = null
) : AppRepository {

    private val transactionsFlow = MutableStateFlow(generateTransactions())
    private val budgetsFlow = MutableStateFlow(generateBudgets(transactionsFlow.value, AppSettings()))
    private val settingsFlow = MutableStateFlow(
        AppSettings(
            primaryBank = "Chase",
            ignoredApps = setOf("com.venmo"),
            customRules = listOf(
                RuleDefinition(
                    id = "seed_rule_1",
                    matchField = RuleField.MERCHANT,
                    query = "Chevron",
                    targetCategory = "Gas"
                )
            )
        )
    )
    private val modelStatusFlow = MutableStateFlow(
        ModelStatus(
            isDownloaded = true,
            downloadProgress = 1f,
            modelSizeLabel = "0.6 GB",
            isGpuOptimized = false // Start as false so we can test the optimization button
        )
    )
    private val pendingNotificationCountFlow = MutableStateFlow(3)
    private val rejectedFlow = MutableStateFlow<List<RejectedNotification>>(emptyList())
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        batchProcessor?.let { processor ->
            repoScope.launch {
                processor.pendingCount.collect { count ->
                    pendingNotificationCountFlow.value = count
                }
            }
        }
    }

    override fun getAllTransactions(): Flow<List<Transaction>> = combine(transactionsFlow, settingsFlow) { txns, settings ->
        txns.map { it.withResolvedCategory(settings) }
    }

    override fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>> {
        return combine(transactionsFlow, settingsFlow) { txns, settings ->
            txns.filter { it.date in start..end }.map { it.withResolvedCategory(settings) }
        }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        val currentMaxId = transactionsFlow.value.maxOfOrNull { it.id } ?: 0
        val row = transaction.copy(id = if (transaction.id == 0) currentMaxId + 1 else transaction.id)
        transactionsFlow.value = (listOf(row) + transactionsFlow.value).sortedByDescending { it.date }
        budgetsFlow.value = generateBudgets(transactionsFlow.value, settingsFlow.value)
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactionsFlow.value = transactionsFlow.value.map { current ->
            if (current.id == transaction.id) transaction else current
        }
        budgetsFlow.value = generateBudgets(transactionsFlow.value, settingsFlow.value)
    }

    override suspend fun deleteTransaction(id: Int) {
        transactionsFlow.value = transactionsFlow.value.filterNot { it.id == id }
        budgetsFlow.value = generateBudgets(transactionsFlow.value, settingsFlow.value)
    }


    override fun getBudgets(): Flow<List<Budget>> = budgetsFlow

    override suspend fun upsertBudget(budget: Budget) {
        val updatedSettings = if (budget.isCustom) {
            settingsFlow.value.copy(
                customBudgetCategories = settingsFlow.value.customBudgetCategories.map { current ->
                    if (current.id == budget.id) current.copy(limit = budget.limit) else current
                }
            )
        } else {
            val category = budget.category ?: return
            settingsFlow.value.copy(
                budgetLimits = settingsFlow.value.budgetLimits + (category to budget.limit)
            )
        }
        settingsFlow.value = updatedSettings
        budgetsFlow.value = generateBudgets(transactionsFlow.value, updatedSettings)
    }

    override suspend fun deleteBudget(category: Category) {
        softDeleteBudgetCategory(category.name)
    }

    override suspend fun addCustomBudgetCategory(label: String, emoji: String, limit: Double) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return
        val cleanedEmoji = emoji.trim().ifBlank { "📁" }
        val nextCustom = CustomBudgetCategory(
            id = "custom_${System.currentTimeMillis()}",
            label = trimmedLabel,
            emoji = cleanedEmoji,
            limit = limit
        )
        val updatedSettings = settingsFlow.value.copy(
            customBudgetCategories = settingsFlow.value.customBudgetCategories + nextCustom
        )
        settingsFlow.value = updatedSettings
        budgetsFlow.value = generateBudgets(transactionsFlow.value, updatedSettings)
    }

    override suspend fun updateBudgetCategoryMeta(budgetId: String, label: String, emoji: String) {
        val trimmedLabel = label.trim()
        val cleanedEmoji = emoji.trim().ifBlank { "📁" }
        if (trimmedLabel.isBlank()) return

        val updatedSettings = if (budgetId.startsWith(BUILTIN_PREFIX)) {
            val categoryName = budgetId.removePrefix(BUILTIN_PREFIX)
            settingsFlow.value.copy(
                categoryPresentation = settingsFlow.value.categoryPresentation +
                    (categoryName to CategoryPresentation(label = trimmedLabel, emoji = cleanedEmoji))
            )
        } else {
            settingsFlow.value.copy(
                customBudgetCategories = settingsFlow.value.customBudgetCategories.map { current ->
                    if (current.id == budgetId) current.copy(label = trimmedLabel, emoji = cleanedEmoji) else current
                }
            )
        }

        settingsFlow.value = updatedSettings
        budgetsFlow.value = generateBudgets(transactionsFlow.value, updatedSettings)
    }

    override suspend fun deleteCustomBudgetCategory(budgetId: String) {
        softDeleteBudgetCategory(budgetId)
    }

    override suspend fun softDeleteBudgetCategory(budgetId: String) {
        val normalizedId = if (budgetId.startsWith(BUILTIN_PREFIX)) budgetId.removePrefix(BUILTIN_PREFIX) else budgetId
        val updatedSettings = settingsFlow.value.copy(
            hiddenCategoryIds = settingsFlow.value.hiddenCategoryIds + normalizedId
        )
        settingsFlow.value = updatedSettings
        budgetsFlow.value = generateBudgets(transactionsFlow.value, updatedSettings)
    }

    override fun getSettings(): Flow<AppSettings> = settingsFlow

    override suspend fun saveSettings(settings: AppSettings) {
        settingsFlow.value = settings
        budgetsFlow.value = generateBudgets(transactionsFlow.value, settings)
    }

    override fun getPendingNotificationCount(): Flow<Int> = pendingNotificationCountFlow

    override suspend fun processPendingNotifications(
        limit: Int,
        onProgress: ((processed: Int, total: Int, currentItemLabel: String?) -> Unit)?
    ): Int {
        if (batchProcessor != null) {
            val total = min(limit, pendingNotificationCountFlow.value)
            onProgress?.invoke(0, total, null)
            val result = batchProcessor.processPending(limit)
            if (result.createdTransactions.isNotEmpty()) {
                val currentMaxId = transactionsFlow.value.maxOfOrNull { it.id } ?: 0
                val withIds = result.createdTransactions.mapIndexed { index, tx ->
                    tx.copy(id = currentMaxId + index + 1)
                }
                transactionsFlow.value = (withIds + transactionsFlow.value).sortedByDescending { it.date }
                budgetsFlow.value = generateBudgets(transactionsFlow.value, settingsFlow.value)
            }
            onProgress?.invoke(result.processedRawCount.coerceAtMost(total), total, null)
            return result.processedRawCount
        }

        val pending = pendingNotificationCountFlow.value
        if (pending <= 0) return 0

        val processedCount = min(limit, pending)
        onProgress?.invoke(0, processedCount, null)

        repeat(processedCount) { idx ->
            delay(120)
            onProgress?.invoke(idx + 1, processedCount, "Sample notification ${idx + 1}")
        }

        pendingNotificationCountFlow.value = pending - processedCount

        if (processedCount > 0) {
            val newTransactions = createProcessedNotificationTransactions(processedCount)
            transactionsFlow.value = (newTransactions + transactionsFlow.value).sortedByDescending { it.date }
            budgetsFlow.value = generateBudgets(transactionsFlow.value, settingsFlow.value)
        }

        return processedCount
    }

    override fun getRejectedNotifications(): Flow<List<RejectedNotification>> = rejectedFlow

    override suspend fun addRejectedNotification(title: String, text: String, reason: String) {
        val nextId = (rejectedFlow.value.maxOfOrNull { it.id } ?: 0) + 1
        val newItem = RejectedNotification(
            id = nextId,
            title = title,
            text = text,
            errorMessage = reason,
            postTimeMillis = System.currentTimeMillis()
        )
        rejectedFlow.value = listOf(newItem) + rejectedFlow.value
    }

    override suspend fun updateRejectedNotification(id: Int, title: String, text: String, reason: String) {
        rejectedFlow.value = rejectedFlow.value.map { current ->
            if (current.id == id) {
                current.copy(
                    title = title.ifBlank { current.title },
                    text = text,
                    errorMessage = reason.ifBlank { current.errorMessage }
                )
            } else {
                current
            }
        }
    }

    override suspend fun deleteRejectedNotification(id: Int) {
        rejectedFlow.value = rejectedFlow.value.filterNot { it.id == id }
    }

    override fun getModelStatus(): Flow<ModelStatus> = modelStatusFlow

    override suspend fun markGpuOptimized() {
        modelStatusFlow.value = modelStatusFlow.value.copy(isGpuOptimized = true)
    }

    override suspend fun refreshModelStatus() {
        modelStatusFlow.value = modelStatusFlow.value
    }

    override suspend fun getTotalTransactionCount(): Int = transactionsFlow.value.size

    override suspend fun wipeAllData() {
        transactionsFlow.value = emptyList()
        budgetsFlow.value = emptyList()
        settingsFlow.value = AppSettings()
        pendingNotificationCountFlow.value = 0
        rejectedFlow.value = emptyList()
        batchProcessor?.clearPendingQueue()
    }

    override suspend fun exportToCsv(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val header = "Date,Merchant,Amount,Category,AI Parsed,Confidence"
        val rows = transactionsFlow.value.map { it.withResolvedCategory(settingsFlow.value) }.joinToString("\n") { txn ->
            val date = formatter.format(Date(txn.date))
            "$date,\"${txn.merchant}\",${txn.amount},${txn.category.label},${txn.isAiParsed},${txn.confidence}"
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
                category = category.toDefinition(),
                date = timestamp,
                isAiParsed = id % 3 != 0,
                confidence = (0.6f + (id % 40) * 0.01f).coerceAtMost(0.99f),
                rawNotificationText = "Sample notification text #$id"
            )
        }.sortedByDescending { it.date }
    }

    private fun generateBudgets(transactions: List<Transaction>, settings: AppSettings): List<Budget> {
        val spentByCategory = transactions.groupBy { it.category.id }
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

        val activeCategories = settings.activeCategoryOptions()
        val builtInBudgets = activeCategories.filter { !it.isCustom && Category.entries.any { category -> category.name == it.id } }.map { categoryDef ->
            val category = Category.entries.first { it.name == categoryDef.id }
            val override = settings.categoryPresentation[category.name]
            Budget(
                id = "$BUILTIN_PREFIX${category.name}",
                category = category,
                label = categoryDef.label,
                emoji = categoryDef.emoji,
                spent = spentByCategory[category.name] ?: 0.0,
                limit = settings.budgetLimits[category] ?: limits.getValue(category),
                isCustom = false
            )
        }

        val customBudgets = settings.customBudgetCategories.filterNot { settings.hiddenCategoryIds.contains(it.id) }.map { custom ->
            Budget(
                id = custom.id,
                category = null,
                label = custom.label,
                emoji = custom.emoji,
                spent = spentByCategory[custom.id] ?: 0.0,
                limit = custom.limit,
                isCustom = true
            )
        }

        return builtInBudgets + customBudgets
    }

    private companion object {
        const val BUILTIN_PREFIX = "builtin:"
    }

    private fun createProcessedNotificationTransactions(count: Int): List<Transaction> {
        val now = System.currentTimeMillis()
        val currentMaxId = transactionsFlow.value.maxOfOrNull { it.id } ?: 0
        val merchants = listOf("Starbucks", "Chipotle", "Smiths", "Chevron", "Target")
        val categories = listOf(Category.DINING, Category.GROCERIES, Category.GAS, Category.SHOPPING, Category.BILLS)

        return (1..count).map { index ->
            val category = categories[(currentMaxId + index) % categories.size]
            val merchant = merchants[(currentMaxId + index) % merchants.size]
            Transaction(
                id = currentMaxId + index,
                merchant = merchant,
                amount = String.format(Locale.US, "%.2f", 8.0 + index * 5.75).toDouble(),
                category = category.toDefinition(),
                date = now - (index * 60_000L),
                isAiParsed = true,
                confidence = 0.93f,
                rawNotificationText = "Processed queued notification #$index"
            )
        }
    }

    private fun Transaction.withResolvedCategory(settings: AppSettings): Transaction {
        val resolved = settings.resolveCategoryById(category.id)
            ?: settings.resolveCategoryByLabel(category.label)
            ?: category
        return copy(category = resolved)
    }
}