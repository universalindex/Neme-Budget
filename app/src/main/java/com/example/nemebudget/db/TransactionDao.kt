package com.example.nemebudget.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * TransactionDao - The interface that Room uses to generate database queries for transactions.
 * 
 * Think of this as a "query contract": you define what operations you need (get all transactions,
 * insert, update, filter by date, etc.), and Room automatically generates the SQL under the hood.
 * 
 * Using suspend functions means these operations won't block the UI thread.
 * Using Flow means the UI can automatically refresh when data changes.
 */
@Dao
interface TransactionDao {
    
    /**
     * Get all transactions ordered by most recent first.
     * Returns as a Flow so the UI automatically updates when transactions change.
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    /**
     * Get transactions within a date range.
     * Example: get all transactions for the current month.
     */
    @Query("SELECT * FROM transactions WHERE date BETWEEN :startMillis AND :endMillis ORDER BY date DESC")
    fun getTransactionsByDateRange(startMillis: Long, endMillis: Long): Flow<List<TransactionEntity>>
    
    /**
     * Get the most recent transaction (useful for quick checks).
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT 1")
    suspend fun getLatestTransaction(): TransactionEntity?
    
    /**
     * Insert a single transaction.
     * OnConflict.REPLACE means if we somehow insert a duplicate ID, replace the old one.
     */
    @Insert
    suspend fun insertTransaction(transaction: TransactionEntity)
    
    /**
     * Update an existing transaction (e.g., user manually corrects a category).
     */
    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)
    
    /**
     * Delete a transaction by ID.
     */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Int)
    
    /**
     * Count total transactions (useful for stats).
     */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int
    
    /**
     * Get all transactions at once (for export, no automatic updates).
     */
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>
}
