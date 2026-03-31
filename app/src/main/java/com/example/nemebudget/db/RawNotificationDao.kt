package com.example.nemebudget.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

/**
 * 2. @Dao (Data Access Object)
 * Think of this interface as the translator between Kotlin functions and raw SQL commands.
 * You just define the Kotlin function, and Room writes the complex SQLite for you!
 */
@Dao
interface RawNotificationDao {

    /**
     * @Insert tells Room to generate a SQL "INSERT INTO raw_notifications..." statement.
     * When the NotificationListener gets an alert, it calls this to securely save it.
     */
    @Insert
    suspend fun insert(notification: RawNotification)

    /**
     * @Query lets us write our own SQL. 
     * This retrieves a specific number of unread notifications, ordered from oldest to newest.
     * The WorkManager (Batch processor) will call this to grab the next batch to process.
     */
    @Query("SELECT * FROM raw_notifications ORDER BY postTimeMillis ASC LIMIT :limit")
    suspend fun getPendingBatch(limit: Int): List<RawNotification>

    /**
     * @Query can also just count things! 
     * Because it returns a Flow<Int>, your Settings Screen UI will automatically 
     * update in real-time the instant a new notification hits the database!
     */
    @Query("SELECT COUNT(*) FROM raw_notifications")
    fun observePendingCount(): Flow<Int>

    /**
     * @Delete generates a SQL "DELETE FROM..." statement.
     * Once the AI successfully extracts the JSON, we call this function 
     * to permanently wipe the raw sensitive text from the phone.
     */
    @Delete
    suspend fun delete(notification: RawNotification)
    
    /**
     * Wipes the entire table. Used if the user clicks "Wipe All Data" in settings.
     */
    @Query("DELETE FROM raw_notifications")
    suspend fun deleteAll()
    
    // ===== ADDITIONAL METHODS FOR BATCH PROCESSING =====
    
    /**
     * Get unprocessed notifications. Returns a list (not a Flow) for batch processing.
     * RealRepository uses this to grab a batch of raw notifications to send to the LLM.
     */
    @Query("SELECT * FROM raw_notifications WHERE processed = 0 ORDER BY postTimeMillis ASC LIMIT :limit")
    suspend fun getUnprocessedRawNotifications(limit: Int): List<RawNotification>
    
    /**
     * Mark a notification as successfully processed.
     * This prevents the batch processor from re-processing the same notification.
     */
    @Query("UPDATE raw_notifications SET processed = 1 WHERE id = :id")
    suspend fun markAsProcessed(id: Int)
    
    /**
     * Mark a notification as failed (with an error message).
     * Useful for debugging - we can see what went wrong processing each notification.
     */
    @Query("UPDATE raw_notifications SET processed = 1, errorMessage = :errorMessage WHERE id = :id")
    suspend fun markAsFailed(id: Int, errorMessage: String)
    
    /**
     * Count unprocessed notifications.
     * Returns a Flow so the UI can show a real-time counter.
     */
    @Query("SELECT COUNT(*) FROM raw_notifications WHERE processed = 0")
    fun getUnprocessedCount(): Flow<Int>
}
