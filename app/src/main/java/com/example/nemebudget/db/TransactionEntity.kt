package com.example.nemebudget.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.model.resolveCategoryById
import com.example.nemebudget.model.resolveCategoryByLabel

/**
 * TransactionEntity - The Room entity for storing parsed transactions in the encrypted database.
 * 
 * This is what gets saved AFTER the LLM successfully processes a raw notification.
 * It's the "final" transaction record that appears in the user's history and budgets.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    // The merchant/sender name (e.g., "Starbucks", "Employer Inc.")
    val merchant: String,
    
    // The transaction amount (always positive)
    val amount: Double,

    // Stable category identifier used by the new catalog-based UI / LLM path.
    val categoryId: String,
    
    // The category label (e.g., "Dining", "Gas", "Salary")
    val categoryLabel: String,

    // Emoji snapshot for direct display without needing a lookup.
    val categoryEmoji: String,
    
    // Timestamp in milliseconds
    val date: Long,
    
    // Was this parsed by the AI (1 = true, 0 = false)
    val isAiParsed: Int,
    
    // Confidence score from the LLM (0.0 to 1.0)
    val confidence: Float,
    
    // The original notification text (kept for audit trail)
    val rawNotificationText: String
) {
    /**
     * Convert this database entity to the domain model that the UI expects.
     * This is the mapper layer - translating from database format to business logic format.
     */
    fun toDomain(settings: AppSettings? = null): Transaction {
        val snapshotCategory = CategoryDefinition(
            id = categoryId.ifBlank { categoryLabel },
            label = categoryLabel,
            emoji = categoryEmoji.ifBlank { Category.entries.firstOrNull { it.label == categoryLabel }?.emoji ?: "" },
            isCustom = !Category.entries.any { it.name.equals(categoryId, ignoreCase = true) }
        )
        val liveCategory = settings?.resolveCategoryById(categoryId)
            ?: settings?.resolveCategoryByLabel(categoryLabel)
            ?: snapshotCategory
        val resolvedCategory = liveCategory.copy(
            isCustom = liveCategory.isCustom || Category.entries.none { it.name.equals(liveCategory.id, ignoreCase = true) }
        )
        return Transaction(
            id = id,
            merchant = merchant,
            amount = amount,
            category = resolvedCategory,
            date = date,
            isAiParsed = isAiParsed == 1,
            confidence = confidence,
            rawNotificationText = rawNotificationText
        )
    }
}

/**
 * Extension function to convert domain model to entity for database storage.
 */
fun Transaction.toEntity(): TransactionEntity {
    return TransactionEntity(
        id = id,
        merchant = merchant,
        amount = amount,
        categoryId = category.id,
        categoryLabel = category.label,
        categoryEmoji = category.emoji,
        date = date,
        isAiParsed = if (isAiParsed) 1 else 0,
        confidence = confidence,
        rawNotificationText = rawNotificationText
    )
}
