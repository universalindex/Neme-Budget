package com.example.nemebudget.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nemebudget.llm.LlmPipeline
import com.example.nemebudget.model.AppSettings
import com.example.nemebudget.model.ModelStatus
import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.ProcessingState
import com.example.nemebudget.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.min

class SettingsViewModel(private val repo: AppRepository) : ViewModel() {
    data class KnownApp(val label: String, val packageName: String)

    val knownSpamApps: List<KnownApp> = listOf(
        KnownApp("Venmo", "com.venmo"),
        KnownApp("Cash App", "com.squareup.cash"),
        KnownApp("WhatsApp", "com.whatsapp"),
        KnownApp("PayPal", "com.paypal.android.p2pmobile"),
        KnownApp("Gmail", "com.google.android.gm")
    )

    val settings: StateFlow<AppSettings> = repo.getSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val modelStatus: StateFlow<ModelStatus> = repo.getModelStatus()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ModelStatus(isDownloaded = false, downloadProgress = 0f, modelSizeLabel = "Unknown", isGpuOptimized = false)
        )

    val pendingNotificationCount: StateFlow<Int> = repo.getPendingNotificationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount
    private var hasAttemptedStartupProcessing = false
    private var primaryBankSaveJob: Job? = null

    init {
        refreshCount()
    }

    fun optimizeEngine(pipeline: LlmPipeline) {
        viewModelScope.launch {
            _isOptimizing.value = true
            val success = pipeline.warmUpEngine()
            if (success) {
                repo.markGpuOptimized()
            }
            _isOptimizing.value = false
        }
    }

    fun updatePrimaryBank(bank: String) {
        val updated = settings.value.copy(primaryBank = bank)
        primaryBankSaveJob?.cancel()
        primaryBankSaveJob = viewModelScope.launch {
            delay(500)
            repo.saveSettings(updated)
        }
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

    fun setIgnoredApp(packageName: String, ignored: Boolean) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return
        val current = settings.value
        val alreadyIgnored = normalized in current.ignoredApps
        if (alreadyIgnored == ignored) return

        val updated = if (ignored) {
            current.ignoredApps + normalized
        } else {
            current.ignoredApps - normalized
        }
        saveSettings(current.copy(ignoredApps = updated))
    }

    fun addCustomRule(rule: RuleDefinition) {
        val current = settings.value
        if (current.customRules.any { existing ->
                existing.matchField == rule.matchField &&
                    existing.query.equals(rule.query, ignoreCase = true) &&
                    existing.targetCategory.equals(rule.targetCategory, ignoreCase = true)
            }) return
        saveSettings(current.copy(customRules = current.customRules + rule))
    }

    fun removeCustomRule(rule: RuleDefinition) {
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
        processPendingInternal(limit = 80, startup = false)
    }

    fun processOnAppOpenIfNeeded() {
        if (hasAttemptedStartupProcessing) return
        hasAttemptedStartupProcessing = true
        processPendingInternal(limit = 20, startup = true)
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
            if (startup) {
                // Let first frame/layout settle before heavy model/database work starts.
                delay(1200)
            }

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

            val total = min(pending, limit)
            _processingState.value = ProcessingState.Processing(
                processedCount = 0,
                totalCount = total,
                currentItemLabel = null
            )
            try {
                val processed = repo.processPendingNotifications(limit) { done, all, current ->
                    _processingState.value = ProcessingState.Processing(
                        processedCount = done.coerceAtMost(all),
                        totalCount = all,
                        currentItemLabel = current
                    )
                }
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