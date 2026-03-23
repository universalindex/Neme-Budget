package com.example.nemebudget.model

data class Transaction(
    val id: Int = 0,
    val merchant: String,
    val amount: Double,
    val category: Category,
    val date: Long,
    val isAiParsed: Boolean,
    val confidence: Float,
    val rawNotificationText: String = ""
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
    val category: Category,
    val spent: Double,
    val limit: Double
)

data class AppSettings(
    val primaryBank: String = "",
    val ignoredApps: Set<String> = emptySet(),
    val customRules: List<String> = emptyList()
)

data class ModelStatus(
    val isDownloaded: Boolean,
    val downloadProgress: Float,
    val modelSizeLabel: String
)

sealed interface ProcessingState {
    data object Idle : ProcessingState
    data class Processing(val pendingCount: Int) : ProcessingState
    data class Success(val processedCount: Int, val completedAtMillis: Long) : ProcessingState
    data class Error(val message: String) : ProcessingState
}


