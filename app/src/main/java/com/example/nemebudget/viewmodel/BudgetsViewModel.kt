package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.Budget
import com.example.nemebudget.model.Category
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BudgetsViewModel(private val repo: AppRepository) : ViewModel() {
    val budgets: StateFlow<List<Budget>> = repo.getBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun upsertBudget(budget: Budget) {
        viewModelScope.launch {
            repo.upsertBudget(budget)
        }
    }

    fun deleteBudget(category: Category) {
        viewModelScope.launch {
            repo.deleteBudget(category)
        }
    }

    fun addCustomCategory(label: String, emoji: String, limit: Double) {
        viewModelScope.launch {
            repo.addCustomBudgetCategory(label, emoji, limit)
        }
    }

    fun updateBudgetCategoryMeta(budgetId: String, label: String, emoji: String) {
        viewModelScope.launch {
            repo.updateBudgetCategoryMeta(budgetId, label, emoji)
        }
    }

    fun deleteCustomCategory(budgetId: String) {
        viewModelScope.launch {
            repo.deleteCustomBudgetCategory(budgetId)
        }
    }
}

