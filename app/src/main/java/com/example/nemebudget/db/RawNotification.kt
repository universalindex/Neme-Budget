package com.example.nemebudget.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 1. @Entity tells Room: "Create a SQLite table out of this Kotlin class."
 * We named the table "raw_notifications".
 */
@Entity(tableName = "raw_notifications")
data class RawNotification(
    /**
     * @PrimaryKey(autoGenerate = true) tells the database to assign a unique ID 
     * automatically every time a new row is added (1, 2, 3, etc.).
     */
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // The name of the app that sent the notification (e.g., "com.chase.sig.android")
    val packageName: String,
    
    // Exactly when the notification arrived
    val postTimeMillis: Long,
    
    // The title of the notification (e.g., "Transaction Alert")
    val title: String,
    
    // The actual sensitive body text (e.g., "You spent $5.40 at Starbucks")
    // THIS is the text that SQLCipher will encrypt on the hard drive!
    val text: String
)
