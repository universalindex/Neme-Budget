package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.example.nemebudget.model.AppSettings
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine

class DashboardViewModel(private val repo: AppRepository) : ViewModel() {

    private val settings: StateFlow<AppSettings> = repo.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    // Tracks "which period, relative to right now" the user has navigated to.
    // Recomputed whenever periodConfig changes (e.g. user switches Weekly -> Monthly in settings).
    private val _referenceMillis = MutableStateFlow(System.currentTimeMillis())

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPeriod: StateFlow<BudgetPeriod> = combine(settings, _referenceMillis) { s, ref ->
        periodContaining(s.periodConfig, ref)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        periodContaining(BudgetPeriodConfig.Monthly, System.currentTimeMillis())
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = currentPeriod
        .flatMapLatest { period -> repo.getTransactionsByDateRange(period.startMillis, period.endMillis) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val spendingByCategory: StateFlow<Map<CategoryDefinition, Double>> = transactions
        .map { list -> list.groupBy { it.category }.mapValues { (_, txns) -> txns.sumOf { it.amount } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val safeToSpend: StateFlow<Double> = transactions
        .map { list -> 2000.0 - list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun previousPeriod() {
        _referenceMillis.value = currentPeriod.value.previous().startMillis
    }

    fun nextPeriod() {
        _referenceMillis.value = currentPeriod.value.next().startMillis
    }

    fun jumpToDate(millis: Long) {
        _referenceMillis.value = millis
    }
}


