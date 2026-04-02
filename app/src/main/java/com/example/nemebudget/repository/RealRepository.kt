package com.example.nemebudget.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.db.RawNotification
import com.example.nemebudget.db.toEntity
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.CategoryPresentation
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.CustomBudgetCategory
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.RejectedNotification
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.model.resolveCategoryByLabel
import com.example.nemebudget.model.toDefinition
import com.example.nemebudget.pipeline.TransactionalNotificationGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * RealRepository - The TRUE data source that connects the UI to the database.
 * 
 * This is what I meant when I said the UI was using FakeRepository—you now have the real one!
 * 
 * Here's the architecture:
 * 1. UI calls methods on AppRepository interface (defined in AppRepository.kt)
 * 2. RealRepository implements those methods by querying AppDatabase
 * 3. AppDatabase uses SQLCipher to encrypt/decrypt data on disk
 * 4. Data flows back to the UI through Kotlin Flow (reactive streams)
 * 
 * This is also where we coordinate the LLM pipeline for processing notifications.
 */
class RealRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val llmPipeline: LlmPipeline
) : AppRepository {

    private val TAG = "RealRepository"
    private val prefs = context.applicationContext.getSharedPreferences("nemebudget_prefs", Context.MODE_PRIVATE)
    private val gpuOptimizedKey = "gpu_optimized"
    private val settingsKey = "app_settings_json"
    private val modelStatusFlow = kotlinx.coroutines.flow.MutableStateFlow(
        ModelStatus(
            isDownloaded = true,
            downloadProgress = 1f,
            modelSizeLabel = "0.6 GB",
            isGpuOptimized = prefs.getBoolean(gpuOptimizedKey, false)
        )
    )

    // Persisted in SharedPreferences for now so budget edits survive app restarts without a schema migration.
    private val settingsFlow = MutableStateFlow(loadAppSettingsFromPrefs())

    /**
     * Get all transactions from the database as a Flow.
     * The UI observes this Flow and automatically updates when transactions change.
     * 
     * Flow is "reactive"—whenever a new transaction is inserted, the UI sees it immediately.
     */
    override fun getAllTransactions(): Flow<List<Transaction>> {
        return combine(db.transactionDao().getAllTransactions(), settingsFlow) { entities, settings ->
            entities.map { it.toDomain(settings) }
        }
    }

    /**
     * Get transactions within a date range (e.g., for the current month).
     * Same reactive pattern as above.
     */
    override fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>> {
        return combine(db.transactionDao().getTransactionsByDateRange(start, end), settingsFlow) { entities, settings ->
            entities.map { it.toDomain(settings) }
        }
    }

    override suspend fun addTransaction(transaction: Transaction) {
        db.transactionDao().insertTransaction(transaction.toEntity())
    }

    /**
     * User manually edited a transaction (e.g., corrected a category).
     * We update it in the database.
     */
    override suspend fun updateTransaction(transaction: Transaction) {
        db.transactionDao().updateTransaction(transaction.toEntity())
    }

    override suspend fun deleteTransaction(id: Int) {
        db.transactionDao().deleteTransaction(id)
    }

    /**
     * Calculate budgets by summing transactions by category and type.
     * The UI calls this to show "Spent $X of $Y in Dining".
     */
    override fun getBudgets(): Flow<List<Budget>> {
        return combine(getAllTransactions(), settingsFlow) { transactions, settings ->
            val expensesByCategory = transactions
                 .groupBy { it.category.id }
                 .mapValues { (_, txns) -> txns.sumOf { it.amount } }

            val defaultLimits = defaultBudgetLimits()
            val builtInBudgets = Category.entries.map { category ->
                val presentation = settings.categoryPresentation[category.name]
                Budget(
                    id = "$BUILTIN_PREFIX${category.name}",
                    category = category,
                    label = presentation?.label ?: category.label,
                    emoji = presentation?.emoji ?: category.emoji,
                    spent = expensesByCategory[category.name] ?: 0.0,
                    limit = settings.budgetLimits[category] ?: defaultLimits.getValue(category),
                    isCustom = false
                )
            }

            val customBudgets = settings.customBudgetCategories.map { custom ->
                Budget(
                    id = custom.id,
                    category = null,
                    label = custom.label,
                    emoji = custom.emoji,
                    spent = expensesByCategory[custom.id] ?: 0.0,
                    limit = custom.limit,
                    isCustom = true
                )
            }

            builtInBudgets + customBudgets
        }
    }

    /**
     * Upsert = "Update or Insert": add budget if it doesn't exist, update if it does.
     * For now, we're calculating budgets from transactions, so this stores the user's chosen limit.
     */
    override suspend fun upsertBudget(budget: Budget) {
        val updated = if (budget.isCustom) {
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
        settingsFlow.value = updated
        persistSettings(updated)
        Log.d(TAG, "Budget upsert requested for ${budget.id}: $${budget.limit}")
    }

    /**
     * Delete a budget by category.
     * This removes the user's custom override and falls back to the default limit.
     */
    override suspend fun deleteBudget(category: Category) {
        val updated = settingsFlow.value.copy(
            budgetLimits = settingsFlow.value.budgetLimits - category
        )
        settingsFlow.value = updated
        persistSettings(updated)
        Log.d(TAG, "Budget delete requested for $category")
    }

    override suspend fun addCustomBudgetCategory(label: String, emoji: String, limit: Double) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return
        val cleanedEmoji = emoji.trim().ifBlank { "📁" }
        val next = CustomBudgetCategory(
            id = "custom_${System.currentTimeMillis()}",
            label = trimmedLabel,
            emoji = cleanedEmoji,
            limit = limit
        )
        val updated = settingsFlow.value.copy(
            customBudgetCategories = settingsFlow.value.customBudgetCategories + next
        )
        settingsFlow.value = updated
        persistSettings(updated)
    }

    override suspend fun updateBudgetCategoryMeta(budgetId: String, label: String, emoji: String) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return
        val cleanedEmoji = emoji.trim().ifBlank { "📁" }

        val updated = if (budgetId.startsWith(BUILTIN_PREFIX)) {
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

        settingsFlow.value = updated
        persistSettings(updated)
    }

    override suspend fun deleteCustomBudgetCategory(budgetId: String) {
        val updated = settingsFlow.value.copy(
            customBudgetCategories = settingsFlow.value.customBudgetCategories.filterNot { it.id == budgetId }
        )
        settingsFlow.value = updated
        persistSettings(updated)
    }

    /**
     * Get app settings (primary bank, ignored apps, custom rules).
     */
    override fun getSettings(): Flow<AppSettings> {
        return settingsFlow
    }

    /**
     * Save app settings.
     */
    override suspend fun saveSettings(settings: AppSettings) {
        settingsFlow.value = settings
        persistSettings(settings)
        Log.d(TAG, "Settings saved: primaryBank=${settings.primaryBank}")
        // TODO: Persist to DataStore/Room
    }

    /**
     * Count how many raw notifications are waiting to be processed.
     */
    override fun getPendingNotificationCount(): Flow<Int> {
        return db.rawNotificationDao().getUnprocessedCount()
    }

    /**
     * Process pending notifications:
     * 1. Fetch up to `limit` raw notifications from raw_notifications table
     * 2. For each one, run the LLM pipeline
     * 3. If successful, insert the parsed Transaction into transactions table
     * 4. Mark the raw notification as processed
     * 
     * This is the core of the AI pipeline!
     */
    override suspend fun processPendingNotifications(
        limit: Int,
        onProgress: ((processed: Int, total: Int, currentItemLabel: String?) -> Unit)?
    ): Int {
        return withContext(Dispatchers.IO) {
            val pending = db.rawNotificationDao().getUnprocessedRawNotifications(limit)
            val total = pending.size
            Log.d(TAG, "Processing $total pending notifications...")

            var processedCount = 0
            var completedCount = 0
            onProgress?.invoke(0, total, null)

            pending.forEachIndexed { index, raw ->
                val label = raw.title.ifBlank { raw.text.take(48) }
                try {
                    val gate = TransactionalNotificationGate.evaluate(raw.title, raw.text)
                    if (!gate.passed) {
                        Log.d(TAG, "Skipped rawId=${raw.id} before LLM: ${gate.reason}")
                        db.rawNotificationDao().markAsProcessed(raw.id)
                        completedCount++
                        onProgress?.invoke(completedCount, total, label)
                        if ((index + 1) % 5 == 0) yield()
                        return@forEachIndexed
                    }

                    val result = llmPipeline.extractWithRetry(raw.text)

                    val rejectReason = rejectionReasonFor(result.transaction.merchant, result.transaction.amount)
                    if (rejectReason != null) {
                        db.rawNotificationDao().markAsFailed(raw.id, rejectReason)
                        if (result.transaction.merchant.equals("Error", ignoreCase = true)) {
                            notifyRejectedExtraction(raw, rejectReason)
                        }
                        Log.w(TAG, "✗ Rejected extraction for rawId=${raw.id}: $rejectReason")
                        completedCount++
                        onProgress?.invoke(completedCount, total, label)
                        if ((index + 1) % 5 == 0) yield()
                        return@forEachIndexed
                    }

                    if (result.transaction.isVerified) {
                        val txn = Transaction(
                            merchant = result.transaction.merchant,
                            amount = result.transaction.amount,
                            category = settingsFlow.value.resolveCategoryByLabel(result.transaction.category)
                                ?: CategoryDefinition(
                                    id = Category.OTHER.name,
                                    label = Category.OTHER.label,
                                    emoji = Category.OTHER.emoji
                                ),
                            date = raw.postTimeMillis,
                            isAiParsed = true,
                            confidence = 0.95f,
                            rawNotificationText = raw.text
                        )

                        db.transactionDao().insertTransaction(txn.toEntity())
                        Log.d(TAG, "✓ Processed: ${txn.merchant} $${txn.amount}")
                        processedCount++
                    } else {
                        val merchantOk = result.transaction.merchant != "Error" &&
                            result.transaction.merchant != "Unknown" &&
                            merchantAppearsInRaw(result.transaction.merchant, raw.text)
                        val amountOk = result.transaction.amount > 0.0

                        if (merchantOk && amountOk) {
                            val rescued = Transaction(
                                merchant = result.transaction.merchant,
                                amount = result.transaction.amount,
                                category = CategoryDefinition(
                                    id = Category.OTHER.name,
                                    label = Category.OTHER.label,
                                    emoji = Category.OTHER.emoji
                                ),
                                date = raw.postTimeMillis,
                                isAiParsed = true,
                                confidence = 0.45f,
                                rawNotificationText = raw.text
                            )
                            db.transactionDao().insertTransaction(rescued.toEntity())
                            processedCount++
                            Log.w(TAG, "~ Rescued with OTHER category: ${result.transaction.verificationNotes}")
                        } else {
                            Log.w(TAG, "✗ Verification failed and not recoverable: ${result.transaction.verificationNotes}")
                        }
                    }

                    db.rawNotificationDao().markAsProcessed(raw.id)
                    completedCount++
                    onProgress?.invoke(completedCount, total, label)
                    if ((index + 1) % 5 == 0) yield()

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification ${raw.id}", e)
                    db.rawNotificationDao().markAsFailed(raw.id, e.message ?: "Unknown error")
                    completedCount++
                    onProgress?.invoke(completedCount, total, label)
                    if ((index + 1) % 5 == 0) yield()
                }
            }

            Log.d(TAG, "Processing complete: $processedCount/$total successful")
            processedCount
        }
    }

    override fun getRejectedNotifications(): Flow<List<RejectedNotification>> {
        return db.rawNotificationDao().getRejectedNotifications().map { rows ->
            rows.mapNotNull { row ->
                row.errorMessage?.let { reason ->
                    RejectedNotification(
                        id = row.id,
                        title = row.title,
                        text = row.text,
                        errorMessage = reason,
                        postTimeMillis = row.postTimeMillis
                    )
                }
            }
        }
    }

    override suspend fun addRejectedNotification(title: String, text: String, reason: String) {
        db.rawNotificationDao().insert(
            RawNotification(
                packageName = context.packageName,
                postTimeMillis = System.currentTimeMillis(),
                title = title.ifBlank { "Manual Rejection" },
                text = text,
                processed = 1,
                errorMessage = reason.ifBlank { "Manually added" }
            )
        )
    }

    override suspend fun updateRejectedNotification(id: Int, title: String, text: String, reason: String) {
        db.rawNotificationDao().updateRejectedNotification(
            id = id,
            title = title.ifBlank { "Error" },
            text = text,
            reason = reason.ifBlank { "Edited" }
        )
    }

    override suspend fun deleteRejectedNotification(id: Int) {
        db.rawNotificationDao().deleteById(id)
    }

    override fun getModelStatus(): Flow<ModelStatus> = modelStatusFlow

    override suspend fun markGpuOptimized() {
        prefs.edit().putBoolean(gpuOptimizedKey, true).apply()
        modelStatusFlow.value = modelStatusFlow.value.copy(isGpuOptimized = true)
    }

    override suspend fun getTotalTransactionCount(): Int {
        return db.transactionDao().getTransactionCount()
    }

    override suspend fun wipeAllData() {
        db.clearAllTables()
        prefs.edit().clear().apply()
        settingsFlow.value = AppSettings()
        modelStatusFlow.value = modelStatusFlow.value.copy(isGpuOptimized = false)
    }

    override suspend fun exportToCsv(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val header = "Date,Merchant,Amount,Category,AI Parsed,Confidence"
        val rows = db.transactionDao().getAllTransactionsOnce().joinToString("\n") { entity ->
            val tx = entity.toDomain(settingsFlow.value)
            val date = formatter.format(Date(tx.date))
            "$date,\"${tx.merchant}\",${tx.amount},${tx.category.label},${tx.isAiParsed},${tx.confidence}"
        }
        return if (rows.isBlank()) header else "$header\n$rows"
    }

    private fun notifyRejectedExtraction(raw: RawNotification, reason: String) {
        val channelId = "nemebudget_errors"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NemeBudget Errors",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val textPreview = raw.text.take(96)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Parsing error detected")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$reason\n$textPreview"))
            .setAutoCancel(true)
            .build()

        val hasPostPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasPostPermission) return

        try {
            NotificationManagerCompat.from(context).notify(raw.id + 10_000, notification)
        } catch (security: SecurityException) {
            Log.w(TAG, "Skipping rejected-extraction notification due to missing notification permission.", security)
        }
    }

    private fun merchantAppearsInRaw(merchant: String, rawText: String): Boolean {
        val rawLower = rawText.lowercase(Locale.ROOT)
        val merchantLower = merchant.lowercase(Locale.ROOT)
        if (rawLower.contains(merchantLower)) return true

        val normalizedRaw = rawLower.replace(Regex("[^a-z0-9]"), "")
        val normalizedMerchant = merchantLower.replace(Regex("[^a-z0-9]"), "")
        return normalizedMerchant.isNotBlank() && normalizedRaw.contains(normalizedMerchant)
    }

    private fun rejectionReasonFor(merchant: String, amount: Double): String? {
        if (merchant.equals("Error", ignoreCase = true)) return "Rejected: merchant was Error"
        if (amount <= 0.0) return "Rejected: amount was not positive"
        return null
    }

    private fun defaultBudgetLimits(): Map<Category, Double> = mapOf(
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

    private fun loadAppSettingsFromPrefs(): AppSettings {
        val json = prefs.getString(settingsKey, null) ?: return appSettingsFallback()
        return try {
            val root = JSONObject(json)
            AppSettings(
                primaryBank = root.optString("primaryBank", ""),
                ignoredApps = root.optJSONArray("ignoredApps")?.let { array ->
                    buildSet {
                        for (index in 0 until array.length()) {
                            array.optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                } ?: emptySet(),
                customRules = root.optJSONArray("customRules")?.let { array ->
                    buildList {
                        for (index in 0 until array.length()) {
                            array.optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                } ?: emptyList(),
                budgetLimits = root.optJSONObject("budgetLimits")?.let { budgetJson ->
                    buildMap {
                        Category.entries.forEach { category ->
                            if (budgetJson.has(category.name)) {
                                put(category, budgetJson.optDouble(category.name, defaultBudgetLimits()[category] ?: 0.0))
                            }
                        }
                    }
                } ?: emptyMap(),
                categoryPresentation = root.optJSONObject("categoryPresentation")?.let { presentationJson ->
                    buildMap {
                        presentationJson.keys().forEach { key ->
                            val item = presentationJson.optJSONObject(key) ?: return@forEach
                            val label = item.optString("label").ifBlank { return@forEach }
                            val emoji = item.optString("emoji").ifBlank { "📁" }
                            put(key, CategoryPresentation(label = label, emoji = emoji))
                        }
                    }
                } ?: emptyMap(),
                customBudgetCategories = root.optJSONArray("customBudgetCategories")?.let { customArray ->
                    buildList {
                        for (index in 0 until customArray.length()) {
                            val item = customArray.optJSONObject(index) ?: continue
                            val id = item.optString("id").ifBlank { continue }
                            val label = item.optString("label").ifBlank { continue }
                            val emoji = item.optString("emoji").ifBlank { "📁" }
                            val limit = item.optDouble("limit", 100.0)
                            add(CustomBudgetCategory(id = id, label = label, emoji = emoji, limit = limit))
                        }
                    }
                } ?: emptyList()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read saved settings, using defaults", t)
            appSettingsFallback()
        }
    }

    private fun appSettingsFallback(): AppSettings = AppSettings(
        primaryBank = "Chase",
        ignoredApps = setOf(),
        customRules = listOf()
    )

    private fun persistSettings(settings: AppSettings) {
        val root = JSONObject().apply {
            put("primaryBank", settings.primaryBank)
            put("ignoredApps", org.json.JSONArray(settings.ignoredApps.toList()))
            put("customRules", org.json.JSONArray(settings.customRules))
            put("budgetLimits", JSONObject().apply {
                settings.budgetLimits.forEach { (category, limit) ->
                    put(category.name, limit)
                }
            })
            put("categoryPresentation", JSONObject().apply {
                settings.categoryPresentation.forEach { (key, presentation) ->
                    put(key, JSONObject().apply {
                        put("label", presentation.label)
                        put("emoji", presentation.emoji)
                    })
                }
            })
            put("customBudgetCategories", org.json.JSONArray().apply {
                settings.customBudgetCategories.forEach { custom ->
                    put(JSONObject().apply {
                        put("id", custom.id)
                        put("label", custom.label)
                        put("emoji", custom.emoji)
                        put("limit", custom.limit)
                    })
                }
            })
        }
        prefs.edit().putString(settingsKey, root.toString()).apply()
    }

    private companion object {
        const val BUILTIN_PREFIX = "builtin:"
    }
}





