package com.example.nemebudget.pipeline

import android.content.Context
import android.util.Log
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.db.RawNotification
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.model.toDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Reads queued encrypted notifications from Room, runs a final transactional gate,
 * and converts verified LLM output into domain transactions.
 */
class NotificationBatchProcessor(
    context: Context,
    private val pipeline: LlmPipeline
) {
    private val dao = AppDatabase.getDatabase(context.applicationContext).rawNotificationDao()

    val pendingCount: Flow<Int> = dao.observePendingCount()

    suspend fun processPending(limit: Int): BatchProcessingResult {
        val pending = dao.getPendingBatch(limit)
        if (pending.isEmpty()) return BatchProcessingResult(0, emptyList(), 0, 0)

        var skippedCount = 0
        var failedCount = 0
        val createdTransactions = mutableListOf<Transaction>()

        pending.forEach { raw ->
            try {
                val gate = TransactionalNotificationGate.evaluate(raw.title, raw.text)
                if (!gate.passed) {
                    skippedCount++
                    Log.d("BatchProcessor", "Gate skipped rawId=${raw.id}: ${gate.reason}")
                    dao.delete(raw)
                    return@forEach
                }


                val extraction = pipeline.extractWithRetry(rawNotification = raw.text, maxAttempts = 2)
                val tx = extraction.transaction
                val rejectReason = when {
                    tx.merchant.equals("Error", ignoreCase = true) -> "merchant=Error"
                    tx.merchant.equals("Unknown", ignoreCase = true) || tx.merchant.isBlank() -> "merchant missing/unknown"
                    tx.amount <= 0.0 -> "amount <= 0"
                    else -> null
                }

                if (rejectReason != null) {
                    skippedCount++
                    Log.d("BatchProcessor", "Rejected extraction for rawId=${raw.id}: $rejectReason")
                } else if (tx.isVerified) {
                    val category = resolveCategory(tx.category)

                    createdTransactions += Transaction(
                        merchant = tx.merchant,
                        amount = tx.amount,
                        category = category,
                        date = raw.postTimeMillis,
                        isAiParsed = true,
                        confidence = 0.9f,
                        rawNotificationText = raw.text
                    )
                } else {
                    skippedCount++
                    Log.d("BatchProcessor", "Skipped unverified extraction for rawId=${raw.id}: ${tx.verificationNotes}")
                }

                dao.delete(raw)
            } catch (t: Throwable) {
                failedCount++
                Log.e("BatchProcessor", "Failed to process rawId=${raw.id}: ${t.message}", t)
            }
        }

        return BatchProcessingResult(
            processedRawCount = pending.size - failedCount,
            createdTransactions = createdTransactions,
            skippedCount = skippedCount,
            failedCount = failedCount
        )
    }

    suspend fun clearPendingQueue() {
        dao.deleteAll()
    }

    private fun resolveCategory(label: String): CategoryDefinition {
        return Category.entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
            ?.toDefinition()
            ?: CategoryDefinition(id = Category.OTHER.name, label = Category.OTHER.label, emoji = Category.OTHER.emoji)
    }

    data class BatchProcessingResult(
        val processedRawCount: Int,
        val createdTransactions: List<Transaction>,
        val skippedCount: Int,
        val failedCount: Int
    )

}


