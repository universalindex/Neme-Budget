package com.example.nemebudget.repository

import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.RejectedNotification
import com.example.nemebudget.model.Transaction
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    suspend fun addTransaction(transaction: Transaction)
    suspend fun updateTransaction(transaction: Transaction)
    suspend fun deleteTransaction(id: Int)

    fun getBudgets(): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget)
    suspend fun deleteBudget(category: Category)
    suspend fun addCustomBudgetCategory(label: String, emoji: String, limit: Double)
    suspend fun updateBudgetCategoryMeta(budgetId: String, label: String, emoji: String)
    suspend fun deleteCustomBudgetCategory(budgetId: String)
    suspend fun softDeleteBudgetCategory(budgetId: String)

    fun getSettings(): Flow<AppSettings>
    suspend fun saveSettings(settings: AppSettings)
    fun getPendingNotificationCount(): Flow<Int>
    suspend fun processPendingNotifications(
        limit: Int,
        onProgress: ((processed: Int, total: Int, currentItemLabel: String?) -> Unit)? = null
    ): Int

    fun getRejectedNotifications(): Flow<List<RejectedNotification>>
    suspend fun addRejectedNotification(title: String, text: String, reason: String)
    suspend fun updateRejectedNotification(id: Int, title: String, text: String, reason: String)
    suspend fun deleteRejectedNotification(id: Int)

    fun getModelStatus(): Flow<ModelStatus>
    suspend fun markGpuOptimized() // Persists the optimization flag once AOT shader compilation completes
    suspend fun refreshModelStatus()

    suspend fun getTotalTransactionCount(): Int
    suspend fun wipeAllData()
    suspend fun exportToCsv(): String
}
