package com.example.nemebudget.model

import com.example.nemebudget.viewmodel.BudgetPeriodConfig

enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income")
}

data class Transaction(
    val id: Int = 0,
    val merchant: String,
    val amount: Double,
    val category: CategoryDefinition,
    val date: Long,
    val isAiParsed: Boolean,
    val confidence: Float,
    val rawNotificationText: String = "",
    val type: TransactionType = TransactionType.EXPENSE
)

data class CategoryDefinition(
    val id: String,
    val label: String,
    val emoji: String,
    val isCustom: Boolean = false
    )



data class Budget(
    val id: String,
    val label: String,
    val emoji: String,
    val spent: Double,
    val limit: Double,
    val isCustom: Boolean
)
val UNCATEGORIZED = CategoryDefinition(id = "uncategorized", label = "Uncategorized", emoji = "❓")
data class CategoryPresentation(
    val label: String,
    val emoji: String
)

data class CustomBudgetCategory(
    val id: String,
    val label: String,
    val emoji: String,
    val limit: Double
)

enum class RuleField(val label: String) {
    MERCHANT("Merchant"),
    RAW_TEXT("Notification text")
}

enum class RuleAction(val label: String) {
    SET_CATEGORY("Set category"),
    SET_MERCHANT("Set merchant"),
    IGNORE("Ignore rule")
}

data class RuleDefinition(
    val id: String,
    val matchField: RuleField,
    val query: String,
    val targetCategory: String,
    val action: RuleAction = RuleAction.SET_CATEGORY,
    val forcedMerchant: String? = null
) {
    fun displayLabel(): String = when (action) {
        RuleAction.SET_CATEGORY -> "${matchField.label}: $query → $targetCategory"
        RuleAction.SET_MERCHANT -> "${matchField.label}: $query → merchant '$forcedMerchant'"
        RuleAction.IGNORE -> "${matchField.label}: $query → ignore"
    }
}


fun AppSettings.allCategoryOptions(): List<CategoryDefinition> =
    customBudgetCategories.map {
        CategoryDefinition(id = it.id, label = it.label, emoji = it.emoji, isCustom = true)
    }

fun AppSettings.activeCategoryOptions(): List<CategoryDefinition> {
    return allCategoryOptions().filterNot { hiddenCategoryIds.contains(it.id) }
}

fun AppSettings.transactionCategoryOptions(): List<CategoryDefinition> = activeCategoryOptions()

fun AppSettings.resolveCategoryById(categoryId: String): CategoryDefinition? {
    return allCategoryOptions().firstOrNull { it.id.equals(categoryId, ignoreCase = true) }
}

fun AppSettings.resolveCategoryByLabel(categoryLabel: String): CategoryDefinition? {
    return allCategoryOptions().firstOrNull { it.label.equals(categoryLabel, ignoreCase = true) }
}

fun AppSettings.resolveActiveCategoryById(categoryId: String): CategoryDefinition? {
    return activeCategoryOptions().firstOrNull { it.id.equals(categoryId, ignoreCase = true) }
}

fun AppSettings.resolveActiveCategoryByLabel(categoryLabel: String): CategoryDefinition? {
    return activeCategoryOptions().firstOrNull { it.label.equals(categoryLabel, ignoreCase = true) }
}

data class AppSettings(
    val ignoredApps: Set<String> = emptySet(),
    val customRules: List<RuleDefinition> = emptyList(),
    val customBudgetCategories: List<CustomBudgetCategory> = emptyList(),
    val hiddenCategoryIds: Set<String> = emptySet(),
    val periodConfig: BudgetPeriodConfig = BudgetPeriodConfig.Monthly,
    val processNow: Boolean = false
)

data class RejectedNotification(
    val id: Int,
    val title: String,
    val text: String,
    val errorMessage: String,
    val postTimeMillis: Long
)

data class ModelStatus(
    val isDownloaded: Boolean,
    val downloadProgress: Float,
    val modelSizeLabel: String,
)

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Processing(
        val processedCount: Int,
        val totalCount: Int,
        val currentItemLabel: String? = null
    ) : ProcessingState
    data class Success(val processedCount: Int, val completedAtMillis: Long) : ProcessingState
    data class Error(val message: String) : ProcessingState
}
