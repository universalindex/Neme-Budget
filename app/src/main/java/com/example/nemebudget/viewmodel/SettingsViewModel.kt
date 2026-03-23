package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: AppRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val modelStatus: StateFlow<ModelStatus> = repo.getModelStatus()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ModelStatus(isDownloaded = false, downloadProgress = 0f, modelSizeLabel = "Unknown")
        )

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount

    init {
        refreshCount()
    }

    fun updatePrimaryBank(bank: String) {
        saveSettings(settings.value.copy(primaryBank = bank))
    }

    fun toggleIgnoredApp(packageName: String) {
        val current = settings.value
        val updated = if (packageName in current.ignoredApps) {
            current.ignoredApps - packageName
        } else {
            current.ignoredApps + packageName
        }
        saveSettings(current.copy(ignoredApps = updated))
    }

    fun addCustomRule(rule: String) {
        if (rule.isBlank()) return
        val current = settings.value
        saveSettings(current.copy(customRules = current.customRules + rule.trim()))
    }

    fun removeCustomRule(rule: String) {
        val current = settings.value
        saveSettings(current.copy(customRules = current.customRules - rule))
    }

    fun wipeAll() {
        viewModelScope.launch {
            repo.wipeAllData()
            refreshCount()
        }
    }

    fun exportCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            onReady(repo.exportToCsv())
        }
    }

    private fun saveSettings(updated: AppSettings) {
        viewModelScope.launch {
            repo.saveSettings(updated)
        }
    }

    private fun refreshCount() {
        viewModelScope.launch {
            _totalCount.value = repo.getTotalTransactionCount()
        }
    }
}

