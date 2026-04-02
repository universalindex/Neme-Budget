package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.RejectedNotification
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TransactionsViewModel(private val repo: AppRepository) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow<Category?>(null)

    val query: StateFlow<String> = searchQuery
    val filter: StateFlow<Category?> = selectedFilter

    val transactions: StateFlow<List<Transaction>> = repo.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        transactions,
        searchQuery,
        selectedFilter
    ) { txns, query, filter ->
        txns.filter { t ->
            (query.isBlank() || t.merchant.contains(query, ignoreCase = true)) &&
                (filter == null || t.category == filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rejectedNotifications: StateFlow<List<RejectedNotification>> = repo.getRejectedNotifications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterChanged(category: Category?) {
        selectedFilter.value = category
    }

    fun saveEdit(original: Transaction, updated: Transaction) {
        viewModelScope.launch {
            repo.updateTransaction(updated)

            if (original.category != updated.category) {
                val newRule = "${original.merchant.trim()} = ${updated.category.label}"
                val currentSettings = repo.getSettings().first()
                val hasRule = currentSettings.customRules.any { it.equals(newRule, ignoreCase = true) }
                if (!hasRule) {
                    repo.saveSettings(currentSettings.copy(customRules = currentSettings.customRules + newRule))
                }
            }
        }
    }

    fun addManualTransaction(merchant: String, amount: Double, category: Category) {
        val safeMerchant = merchant.trim().ifBlank { "Manual Entry" }
        val safeAmount = amount.coerceAtLeast(0.01)
        viewModelScope.launch {
            repo.addTransaction(
                Transaction(
                    merchant = safeMerchant,
                    amount = safeAmount,
                    category = category,
                    date = System.currentTimeMillis(),
                    isAiParsed = false,
                    confidence = 1.0f,
                    rawNotificationText = "Manual transaction"
                )
            )
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch {
            repo.deleteTransaction(id)
        }
    }

    fun updateRejectedItem(id: Int, title: String, text: String, reason: String) {
        viewModelScope.launch {
            repo.updateRejectedNotification(id, title, text, reason)
        }
    }

    fun resolveRejectedAsTransaction(
        errorId: Int,
        merchant: String,
        amount: Double,
        category: Category,
        rawNotificationText: String
    ) {
        val safeMerchant = merchant.trim().ifBlank { "Manual Entry" }
        val safeAmount = amount.coerceAtLeast(0.01)
        viewModelScope.launch {
            repo.addTransaction(
                Transaction(
                    merchant = safeMerchant,
                    amount = safeAmount,
                    category = category,
                    date = System.currentTimeMillis(),
                    isAiParsed = false,
                    confidence = 0.85f,
                    rawNotificationText = rawNotificationText
                )
            )
            repo.deleteRejectedNotification(errorId)
        }
    }

    fun deleteRejectedItem(id: Int) {
        viewModelScope.launch {
            repo.deleteRejectedNotification(id)
        }
    }
}

