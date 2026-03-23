package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.ProcessingState
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

    val pendingNotificationCount: StateFlow<Int> = repo.getPendingNotificationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount
    private var hasAttemptedStartupProcessing = false

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
            _processingState.value = ProcessingState.Idle
            refreshCount()
        }
    }

    fun processNow() {
        processPendingInternal(limit = 200, startup = false)
    }

    fun processOnAppOpenIfNeeded() {
        if (hasAttemptedStartupProcessing) return
        hasAttemptedStartupProcessing = true
        processPendingInternal(limit = 100, startup = true)
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

    private fun processPendingInternal(limit: Int, startup: Boolean) {
        viewModelScope.launch {
            val pending = pendingNotificationCount.value
            if (pending <= 0) {
                if (!startup) {
                    _processingState.value = ProcessingState.Success(
                        processedCount = 0,
                        completedAtMillis = System.currentTimeMillis()
                    )
                }
                return@launch
            }

            _processingState.value = ProcessingState.Processing(pendingCount = pending)
            try {
                val processed = repo.processPendingNotifications(limit)
                refreshCount()
                _processingState.value = ProcessingState.Success(
                    processedCount = processed,
                    completedAtMillis = System.currentTimeMillis()
                )
            } catch (t: Throwable) {
                _processingState.value = ProcessingState.Error(t.message ?: "Processing failed")
            }
        }
    }

    private fun refreshCount() {
        viewModelScope.launch {
            _totalCount.value = repo.getTotalTransactionCount()
        }
    }
}

