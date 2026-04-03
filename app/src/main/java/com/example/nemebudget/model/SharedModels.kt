package com.example.nemebudget.model

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

enum class Category(val label: String, val emoji: String) {
    DINING("Dining", "\uD83C\uDF54"),
    GROCERIES("Groceries", "\uD83D\uDED2"),
    GAS("Gas", "\u26FD"),
    BILLS("Bills", "\uD83D\uDCA1"),
    SUBSCRIPTIONS("Subscriptions", "\uD83D\uDCFA"),
    ENTERTAINMENT("Entertainment", "\uD83C\uDFAC"),
    SHOPPING("Shopping", "\uD83D\uDED2"),
    TRANSPORT("Transport", "\uD83D\uDE97"),
    HEALTH("Health", "\uD83D\uDC8A"),
    TRAVEL("Travel", "\u2708"),
    OTHER("Other", "\uD83D\uDCB3")
}

data class Budget(
    val id: String,
    val category: Category?,
    val label: String,
    val emoji: String,
    val spent: Double,
    val limit: Double,
    val isCustom: Boolean
)

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

data class RuleDefinition(
    val id: String,
    val matchField: RuleField,
    val query: String,
    val targetCategory: String
) {
    fun displayLabel(): String = "${matchField.label}: $query → $targetCategory"
}

fun Category.toDefinition(): CategoryDefinition = CategoryDefinition(
    id = name,
    label = label,
    emoji = emoji,
    isCustom = false
)

fun AppSettings.allCategoryOptions(): List<CategoryDefinition> {
    val customCategories = customBudgetCategories.map {
        CategoryDefinition(id = it.id, label = it.label, emoji = it.emoji, isCustom = true)
    }
    val builtInWithPresentations = Category.entries.map { category ->
        val override = categoryPresentation[category.name]
        if (override != null) {
            CategoryDefinition(id = category.name, label = override.label, emoji = override.emoji, isCustom = false)
        } else {
            category.toDefinition()
        }
    }
    return builtInWithPresentations + customCategories
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
    val primaryBank: String = "",
    val ignoredApps: Set<String> = emptySet(),
    val customRules: List<RuleDefinition> = emptyList(),
    val budgetLimits: Map<Category, Double> = emptyMap(),
    val categoryPresentation: Map<String, CategoryPresentation> = emptyMap(),
    val customBudgetCategories: List<CustomBudgetCategory> = emptyList(),
    val hiddenCategoryIds: Set<String> = emptySet()
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
    val isGpuOptimized: Boolean = false
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
