package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.Transaction
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class MonthSelection(val year: Int, val monthZeroBased: Int) {
    fun label(): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    fun startMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun endMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }
}

class DashboardViewModel(private val repo: AppRepository) : ViewModel() {
    private val now = Calendar.getInstance()
    private val _selectedMonth = MutableStateFlow(
        MonthSelection(
            year = now.get(Calendar.YEAR),
            monthZeroBased = now.get(Calendar.MONTH)
        )
    )
    val selectedMonth = _selectedMonth.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<Transaction>> = _selectedMonth
        .flatMapLatest { month ->
            repo.getTransactionsByDateRange(month.startMillis(), month.endMillis())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val spendingByCategory: StateFlow<Map<Category, Double>> = transactions
        .map { list -> list.groupBy { it.category }.mapValues { (_, txns) -> txns.sumOf { it.amount } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val safeToSpend: StateFlow<Double> = transactions
        .map { list -> 2000.0 - list.sumOf { it.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun previousMonth() {
        _selectedMonth.update {
            if (it.monthZeroBased == 0) {
                MonthSelection(it.year - 1, 11)
            } else {
                MonthSelection(it.year, it.monthZeroBased - 1)
            }
        }
    }

    fun nextMonth() {
        _selectedMonth.update {
            if (it.monthZeroBased == 11) {
                MonthSelection(it.year + 1, 0)
            } else {
                MonthSelection(it.year, it.monthZeroBased + 1)
            }
        }
    }
}


