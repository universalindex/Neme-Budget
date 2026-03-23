package com.example.nemebudget.repository

import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.Transaction
import kotlinx.coroutines.flow.Flow

interface AppRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    suspend fun updateTransaction(transaction: Transaction)

    fun getBudgets(): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget)
    suspend fun deleteBudget(category: Category)

    fun getSettings(): Flow<AppSettings>
    suspend fun saveSettings(settings: AppSettings)
    fun getPendingNotificationCount(): Flow<Int>
    suspend fun processPendingNotifications(limit: Int): Int

    fun getModelStatus(): Flow<ModelStatus>
    suspend fun getTotalTransactionCount(): Int
    suspend fun wipeAllData()
    suspend fun exportToCsv(): String
}

