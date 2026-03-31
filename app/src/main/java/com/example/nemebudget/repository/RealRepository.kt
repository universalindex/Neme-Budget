package com.example.nemebudget.repository

import android.content.Context
import android.util.Log
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.db.toEntity
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.StringWriter

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
    private val modelStatusFlow = kotlinx.coroutines.flow.MutableStateFlow(
        ModelStatus(
            isDownloaded = true,
            downloadProgress = 1f,
            modelSizeLabel = "0.6 GB",
            isGpuOptimized = prefs.getBoolean(gpuOptimizedKey, false)
        )
    )

    // For now, in-memory settings. Later, persist to DataStore or Room.
    private var appSettings = AppSettings(
        primaryBank = "Chase",
        ignoredApps = setOf(),
        customRules = listOf()
    )

    /**
     * Get all transactions from the database as a Flow.
     * The UI observes this Flow and automatically updates when transactions change.
     * 
     * Flow is "reactive"—whenever a new transaction is inserted, the UI sees it immediately.
     */
    override fun getAllTransactions(): Flow<List<Transaction>> {
        return db.transactionDao().getAllTransactions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get transactions within a date range (e.g., for the current month).
     * Same reactive pattern as above.
     */
    override fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>> {
        return db.transactionDao().getTransactionsByDateRange(start, end).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * User manually edited a transaction (e.g., corrected a category).
     * We update it in the database.
     */
    override suspend fun updateTransaction(transaction: Transaction) {
        db.transactionDao().updateTransaction(transaction.toEntity())
    }

    /**
     * Calculate budgets by summing transactions by category and type.
     * The UI calls this to show "Spent $X of $Y in Dining".
     */
    override fun getBudgets(): Flow<List<Budget>> {
        return getAllTransactions().map { transactions ->
            // Group by category and sum all persisted transactions (legacy no-type schema behavior)
            val expensesByCategory = transactions
                 .groupBy { it.category }
                 .mapValues { (_, txns) -> txns.sumOf { it.amount } }

            Category.entries.map { category ->
                Budget(
                    category = category,
                    spent = expensesByCategory[category] ?: 0.0,
                    limit = 500.0  // TODO: Make this configurable in AppSettings
                )
            }
        }
    }

    /**
     * Upsert = "Update or Insert": add budget if it doesn't exist, update if it does.
     * For now, we're calculating budgets from transactions, so this is a no-op.
     * In the future, you might store custom budget limits.
     */
    override suspend fun upsertBudget(budget: Budget) {
        // TODO: Store custom budget limits in Settings or a separate table
        Log.d(TAG, "Budget upsert requested for ${budget.category}: $${budget.limit}")
    }

    /**
     * Delete a budget by category.
     * Similar to upsert, this would be a settings store in the future.
     */
    override suspend fun deleteBudget(category: Category) {
        // TODO: Remove custom budget limit for this category
        Log.d(TAG, "Budget delete requested for $category")
    }

    /**
     * Get app settings (primary bank, ignored apps, custom rules).
     */
    override fun getSettings(): Flow<AppSettings> {
        // For now, just return the in-memory settings
        // TODO: Persist to DataStore or Room
        return kotlinx.coroutines.flow.flowOf(appSettings)
    }

    /**
     * Save app settings.
     */
    override suspend fun saveSettings(settings: AppSettings) {
        appSettings = settings
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
    override suspend fun processPendingNotifications(limit: Int): Int {
        return withContext(Dispatchers.IO) {
            val pending = db.rawNotificationDao().getUnprocessedRawNotifications(limit)
            Log.d(TAG, "Processing ${pending.size} pending notifications...")

            var processedCount = 0
            pending.forEachIndexed { index, raw ->
                try {
                    val result = llmPipeline.extractWithRetry(raw.text)

                    if (result.transaction.isVerified) {
                        val txn = Transaction(
                            merchant = result.transaction.merchant,
                            amount = result.transaction.amount,
                            category = Category.entries.first { it.label == result.transaction.category },
                            date = raw.postTimeMillis,
                            isAiParsed = true,
                            confidence = 0.95f,
                            rawNotificationText = raw.text
                        )

                        db.transactionDao().insertTransaction(txn.toEntity())
                        Log.d(TAG, "✓ Processed: ${txn.merchant} $${txn.amount}")
                        processedCount++
                    } else {
                        // Verification failed. Only rescue when merchant text still appears in raw notification.
                        val merchantOk = result.transaction.merchant != "Error" &&
                            result.transaction.merchant != "Unknown" &&
                            merchantAppearsInRaw(result.transaction.merchant, raw.text)
                        val amountOk = result.transaction.amount > 0.0

                        if (merchantOk && amountOk) {
                            val rescued = Transaction(
                                merchant = result.transaction.merchant,
                                amount = result.transaction.amount,
                                category = Category.OTHER,
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
                    if ((index + 1) % 5 == 0) yield()

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing notification ${raw.id}", e)
                    db.rawNotificationDao().markAsFailed(raw.id, e.message ?: "Unknown error")
                    if ((index + 1) % 5 == 0) yield()
                }
            }

            Log.d(TAG, "Processing complete: $processedCount/${pending.size} successful")
            processedCount
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

    /**
     * Model status - whether the Qwen model is downloaded and ready.
     * For now, returns a hardcoded "ready" status since the model ships with the APK.
     */
    override fun getModelStatus(): Flow<ModelStatus> {
        return modelStatusFlow
    }

    /**
     * Mark the GPU as optimized after shader compilation completes.
     * This can be used to skip warmup on subsequent app launches.
     */
    override suspend fun markGpuOptimized() {
        prefs.edit().putBoolean(gpuOptimizedKey, true).apply()
        modelStatusFlow.value = modelStatusFlow.value.copy(isGpuOptimized = true)
        Log.d(TAG, "GPU optimization marked. Shaders compiled and cached.")
    }

    /**
     * Get total transaction count (for stats/UI).
     */
    override suspend fun getTotalTransactionCount(): Int {
        return db.transactionDao().getTransactionCount()
    }

    /**
     * Wipe all user data - useful for logout or testing.
     * This clears the transactions, raw notifications, and settings.
     */
    override suspend fun wipeAllData() {
        db.clearAllTables()
        appSettings = AppSettings()
        prefs.edit().putBoolean(gpuOptimizedKey, false).apply()
        modelStatusFlow.value = modelStatusFlow.value.copy(isGpuOptimized = false)
        Log.d(TAG, "All data wiped.")
    }

    /**
     * Export all transactions to CSV for backup/download.
     * The format matches what a spreadsheet app expects.
     */
    override suspend fun exportToCsv(): String {
        return withContext(Dispatchers.IO) {
            val transactions = db.transactionDao().getAllTransactionsOnce()
            val csv = StringWriter()
            
            // Header row
            csv.append("Date,Merchant,Amount,Category,Confidence\n")
            
            // Data rows
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            transactions.forEach { txn ->
                val dateStr = dateFormat.format(Date(txn.date))
                csv.append("\"$dateStr\",\"${txn.merchant}\",${txn.amount},${txn.categoryLabel},${txn.confidence}\n")
            }
            
            csv.toString()
        }
    }
}
