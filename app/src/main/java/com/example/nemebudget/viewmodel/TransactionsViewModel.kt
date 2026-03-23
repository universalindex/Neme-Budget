package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransactionsViewModel(private val repo: AppRepository) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow<Category?>(null)

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

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterChanged(category: Category?) {
        selectedFilter.value = category
    }

    fun saveEdit(transaction: Transaction) {
        viewModelScope.launch {
            repo.updateTransaction(transaction)
        }
    }

    fun currentQuery(): String = searchQuery.value

    fun currentFilter(): Category? = selectedFilter.value
}

