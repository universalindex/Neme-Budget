package com.example.nemebudget.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.model.resolveCategoryById
import com.example.nemebudget.model.resolveCategoryByLabel

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val merchant: String,
    val amount: Double,
    val categoryId: String,
    val categoryLabel: String,
    val categoryEmoji: String,
    val date: Long,
    val isAiParsed: Int,
    val confidence: Float,
    val rawNotificationText: String
) {
    fun toDomain(settings: AppSettings? = null): Transaction {
        val snapshotCategory = CategoryDefinition(
            id = categoryId.ifBlank { categoryLabel },
            label = categoryLabel,
            emoji = categoryEmoji.ifBlank {
                Category.entries.firstOrNull { it.label.equals(categoryLabel, ignoreCase = true) }?.emoji ?: ""
            },
            isCustom = !Category.entries.any { it.name.equals(categoryId, ignoreCase = true) }
        )

        val resolvedCategory = settings?.resolveCategoryById(categoryId)
            ?: settings?.resolveCategoryByLabel(categoryLabel)
            ?: snapshotCategory

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
