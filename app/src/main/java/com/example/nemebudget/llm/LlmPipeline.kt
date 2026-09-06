package com.example.nemebudget.llm
import android.content.Context
import android.util.Log
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.RuleAction
import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.RuleField
import com.example.nemebudget.model.toDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.net.Uri
import android.app.ActivityManager
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.tool
import com.google.ai.edge.litertlm.ToolSet
/**
 * Data class representing the strict JSON structure we force the LLM to output.
 */





data class ExtractedTransaction(
    val merchant: String,
    val amount: Double,
    val category: String,
    val isVerified: Boolean,
    val verificationNotes: String
)


data class ExtractionResult(
    val rawJson: String,
    val transaction: ExtractedTransaction,
    val retries: Int
)

class LlmPipeline(private val context: Context) {
    class TransactionToolSet : ToolSet {
        var lastResult: ExtractedTransaction? = null

        @Tool(description = "Records the extracted transaction")
        fun recordTransaction(
            @ToolParam(description = "Merchant name — the place money was spent") merchant: String,
            @ToolParam(description = "Transaction amount") amount: Double,
            @ToolParam(description = "Category — must exactly match one of the allowed category names given in the prompt") category: String
        ): String {
            lastResult = ExtractedTransaction(merchant, amount, category, isVerified = false, verificationNotes = "")
            return "recorded"
        }
    }
    init {
        @OptIn(ExperimentalApi::class)
        ExperimentalFlags.enableSpeculativeDecoding = true //This line should be working right?
    }
    private val modelPath: String
        get() = File(context.filesDir, "model.litertlm").absolutePath
    companion object {
        private val engineLock = Any()
        private var sharedEngine: Engine? = null
        fun shutdown() {
            synchronized(engineLock) {
                sharedEngine?.close()
                sharedEngine = null
            }
        }
    }

    private fun engine(): Engine {
        synchronized(engineLock) {
            sharedEngine?.let { return it }
            if (!isModelInstalled()) {
                throw IllegalStateException("Missing model file at $modelPath")
            }
            val requiredBytes = File(modelPath).length()
            if (!hasEnoughAvailableMemory(requiredBytes)) {
                throw IllegalStateException("Not enough available memory to load the model. Try closing other apps.")
            }
            val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.GPU(), cacheDir = context.cacheDir.path))
            newEngine.initialize()
            sharedEngine = newEngine
            return newEngine
        }
    }

    private fun allowedCategories(): List<String> = loadCategoryCatalog().map { it.label }

    private fun allowedCategoryText(): String = allowedCategories().joinToString(", ")

    private fun loadCustomRules(): List<RuleDefinition> {
        val prefs = context.applicationContext.getSharedPreferences("nemebudget_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("app_settings_json", null) ?: return emptyList()

        return try {
            val root = JSONObject(json)
            parseRuleDefinitions(root.optJSONArray("customRules") ?: JSONArray())
        } catch (_: Throwable) {
            emptyList()
        }
    }


    private val systemPrompt = """
Extract the transaction from the notification using the transaction extractor tool.
You MUST USE THIS TOOL. DO NOTHING ELSE
Rules:
    Merchant: Prioritize store/payee names. Exclude card/reference numbers.
    Amount: Use the transaction value. Ignore balances or limits.
    Category: Select the most relevant category or "Other".
    Fallback: If no transaction is found, return "Error" for merchant and 0.0 for amount.
Example:
Notification: America First Alert: Card Guard Credit Card *3320 $9.13 at SQ *Kiwi Loco Frozen Y.
Output: {"merchant": "Kiwi Loco Frozen Y", "amount": 9.13, "category": "Dining"}
""".trimIndent()




    private fun installModelFromBundledAssets(targetFile: File): Boolean {
        val assetName = context.assets.list("")?.firstOrNull { it.endsWith(".litertlm") } ?: return false
        return try {
            targetFile.parentFile?.mkdirs()
            context.assets.open(assetName).use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            }
            targetFile.isFile && targetFile.length() > 0
        } catch (_: Exception) {
            false
        }
    }
    fun isModelInstalled(): Boolean {
        val modelFile = File(modelPath)
        return modelFile.isFile && modelFile.length() > 0
    }
private val transactionTool = TransactionToolSet()
  //This stuff is new and right but for the prompt stuff I'm not worrei
    val conversationConfig = ConversationConfig(
        systemInstruction = Contents.of(systemPrompt),
        initialMessages = listOf(
        ),
        tools = listOf(tool(transactionTool)), //Also not sure why this is bad
        samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8),
    )
    /**
     * Handles the full flow of generating, verifying, and retrying if the LLM hallucinated.
     */
    fun installModelFromUri(uri: Uri): Boolean {
        val targetFile = File(modelPath)
        return try {
            targetFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }

            }
            targetFile.isFile && targetFile.length() > 0
        }
        catch (_: Exception) {
        false
    }
}
    suspend fun getBestLlmDevice(): String = withContext(Dispatchers.IO) {
        // Try NPU first
        if (canLoadNativeLib("Placeholder")) {
            return@withContext "NPU"  //Todo NPU integration Figure out how to get AOT bianaries up for common phones
        }
        if (canLoadNativeLib("vndksupport")) {
            return@withContext "GPU"
        }
        else
            return@withContext "CPU"
    }
    private fun canLoadNativeLib(libName: String): Boolean {
        return try {
            System.loadLibrary(libName)
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }
    private fun hasEnoughAvailableMemory(requiredBytes: Long): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem > requiredBytes
    }

    suspend fun extractWithRetry(rawNotification: String, maxAttempts: Int = 3): ExtractionResult = withContext(Dispatchers.IO) {
        var currentPrompt = buildInitialPrompt(rawNotification)
        var lastJson = """{"merchant":"Error","amount":0.0,"category":"Other"}"""
        var lastTx = ExtractedTransaction("Error", 0.0, "Other", false, "Unknown error")

        for (attempt in 1..maxAttempts) {
            Log.d("LlmPipeline", "Extraction attempt $attempt for: $rawNotification")
            val jsonStr = generateJson(currentPrompt)
            val tx = processAndVerify(rawNotification, jsonStr)

            lastJson = jsonStr
            lastTx = tx

            if (tx.isVerified) {
                return@withContext ExtractionResult(jsonStr, tx, attempt - 1)
            }

            if (attempt < maxAttempts) {
                Log.d("LlmPipeline", "Validation failed: ${tx.verificationNotes}. Prompting LLM to correct itself...")
                currentPrompt = buildRetryPrompt(rawNotification, jsonStr, tx.verificationNotes)
            }
        }

        ExtractionResult(lastJson, lastTx, (maxAttempts - 1).coerceAtLeast(0))
    }


    /**
     * The core LLM generation call.
     */
    private suspend fun generateJson(userPrompt: String): String = withContext(Dispatchers.IO) {
        transactionTool.lastResult = null
        engine().createConversation(conversationConfig).use { conversation ->
            conversation.sendMessage(userPrompt)
        }
        val result = transactionTool.lastResult
        if (result != null) {
            """{"merchant": ${JSONObject.quote(result.merchant)}, "amount": ${result.amount}, "category": ${JSONObject.quote(result.category)}}"""
        } else {
            """{"merchant": "Error", "amount": 0.0, "category": "Other"}"""
        }

    }

    fun processAndVerify(rawNotification: String, llmJsonString: String): ExtractedTransaction {
        return try {
            val jsonPayload = extractFirstCompleteJsonObject(llmJsonString)
                ?: throw IllegalArgumentException("Incomplete or non-JSON model output")
            val jsonObject = JSONObject(jsonPayload)
            val merchant = jsonObject.getString("merchant")
            val amount = jsonObject.getDouble("amount")
            val category = normalizeCategory(jsonObject.getString("category"))

            val baseTransaction = ExtractedTransaction(
                merchant = merchant,
                amount = amount,
                category = category,
                isVerified = false,
                verificationNotes = ""
            )
            val savedRules = loadCustomRules()
            val ruleResult = applyRuleDefinitions(
                rawNotification = rawNotification,
                merchant = merchant,
                transaction = baseTransaction,
                rules = savedRules
            )
            val candidate = ruleResult.transaction

            var isVerified = true
            var notes = ruleResult.appliedRule?.let { "Rule matched: ${it.displayLabel()}. " } ?: ""
            val rawLower = rawNotification.lowercase(Locale.ROOT)

            val merchantForcedByRule = ruleResult.appliedRule?.action == RuleAction.SET_MERCHANT
            if (candidate.merchant == "Error") {
                isVerified = false
                notes = "FAILED: Model did not call the transaction tool."
            }
            if (!merchantForcedByRule && candidate.merchant != "Unknown" && candidate.merchant != "Error" && !merchantAppearsInRaw(rawLower, candidate.merchant)) {
                isVerified = false
                notes += "FAILED: Merchant '${candidate.merchant}' not found in original text."
            } else if (merchantForcedByRule) {
                notes += "Merchant forced by rule. "
            }

            val amountString = candidate.amount.toString()
            if (candidate.amount > 0.0 && !amountAppearsInRaw(rawLower, candidate.amount)) {
                isVerified = false
                notes = "FAILED: Amount '$amountString' not found in original text."
            }

            if (!allowedCategories().contains(candidate.category)) {
                isVerified = false
                notes = "FAILED: Category '${candidate.category}' is not a valid option."
            }

            if (isVerified) {
                if (notes.isBlank()) notes = "Verified successfully." else notes += " Verified successfully."
            }

            candidate.copy(isVerified = isVerified, verificationNotes = notes.trim())

        } catch (e: Exception) {
            Log.e("LlmPipeline", "Failed to parse JSON", e)
            ExtractedTransaction("Error", 0.0, "Error", false, "JSON Parsing Failed: ${e.message}")
        }
    }

    private fun merchantAppearsInRaw(rawLower: String, merchant: String): Boolean {
        val merchantLower = merchant.lowercase(Locale.ROOT)
        if (rawLower.contains(merchantLower)) return true

        val normalizedRaw = rawLower.replace(Regex("[^a-z0-9]"), "")
        val normalizedMerchant = merchantLower.replace(Regex("[^a-z0-9]"), "")
        return normalizedMerchant.isNotBlank() && normalizedRaw.contains(normalizedMerchant)
    }

    private fun amountAppearsInRaw(rawLower: String, amount: Double): Boolean {
        val normalizedRaw = rawLower.replace(",", "")
        val candidates = buildSet {
            add(String.format(Locale.US, "%.2f", amount))
            add(String.format(Locale.US, "%.1f", amount))
            add(String.format(Locale.US, "%.0f", amount))
        }

        if (candidates.any { normalizedRaw.contains(it) }) return true

        val numberRegex = Regex("""\d+(?:\.\d{1,2})?""")
        return numberRegex.findAll(normalizedRaw).any {
            val parsed = it.value.toDoubleOrNull() ?: return@any false
            abs(parsed - amount) < 0.01
        }
    }

    private fun buildInitialPrompt(rawNotification: String): String {
        return """
Allowed categories (exact spelling): ${allowedCategoryText()}

Notification text:
$rawNotification

Return JSON only with keys merchant, amount, category.
""".trimIndent()
    }

    private fun buildRetryPrompt(rawNotification: String, previousJson: String, validationError: String): String {
        return """
Re-evaluate this notification and fix the extraction.

Allowed categories (exact spelling): ${allowedCategoryText()}
Original notification:
$rawNotification

Previous JSON:
$previousJson

Validation error:
$validationError

Correction rules:
- Keep merchant text grounded in the original notification.
- Choose the transaction amount, not balance/available/reward/reference numbers.
- Do not abbreviate category names.
- Return JSON only with keys merchant, amount, category.
""".trimIndent()
    }

    private fun extractFirstCompleteJsonObject(text: String): String? {
        var start = -1
        var depth = 0
        var inString = false
        var escaping = false

        for (i in text.indices) {
            val c = text[i]

            if (escaping) {
                escaping = false
                continue
            }
            if (c == '\\' && inString) {
                escaping = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (inString) continue

            if (c == '{') {
                if (depth == 0) start = i
                depth++
            } else if (c == '}') {
                if (depth > 0) {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun normalizeCategory(rawCategory: String): String {
        val trimmed = rawCategory.trim()
        allowedCategories().firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }

        if (trimmed.length == 1) {
            val matches = allowedCategories().filter { it.startsWith(trimmed, ignoreCase = true) }
            if (matches.size == 1) return matches.first()
        }
        return trimmed
    }

    private fun loadCategoryCatalog(): List<CategoryDefinition> {
        val prefs = context.applicationContext.getSharedPreferences("nemebudget_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("app_settings_json", null) ?: return Category.entries.map { it.toDefinition() }

        return try {
            val root = JSONObject(json)
            val list = mutableListOf<CategoryDefinition>()

            Category.entries.forEach { category ->
                val presentation = root.optJSONObject("categoryPresentation")?.optJSONObject(category.name)
                list += CategoryDefinition(
                    id = category.name,
                    label = presentation?.optString("label").takeUnless { it.isNullOrBlank() } ?: category.label,
                    emoji = presentation?.optString("emoji").takeUnless { it.isNullOrBlank() } ?: category.emoji,
                    isCustom = false
                )
            }

            val customArray = root.optJSONArray("customBudgetCategories") ?: JSONArray()
            val hiddenIds = root.optJSONArray("hiddenCategoryIds")?.let { hiddenArray ->
                buildSet {
                    for (index in 0 until hiddenArray.length()) {
                        hiddenArray.optString(index).takeIf { it.isNotBlank() }?.let { add(it.removePrefix("builtin:")) }
                    }
                }
            } ?: emptySet()

            list.removeAll { hiddenIds.contains(it.id) }

            for (index in 0 until customArray.length()) {
                val item = customArray.optJSONObject(index) ?: continue
                val id = item.optString("id").ifBlank { continue }
                val label = item.optString("label").ifBlank { continue }
                val emoji = item.optString("emoji").ifBlank { "📁" }
                if (hiddenIds.contains(id)) continue
                list += CategoryDefinition(id = id, label = label, emoji = emoji, isCustom = true)
            }

            if (list.isEmpty()) Category.entries.map { it.toDefinition() } else list
        } catch (_: Throwable) {
            Category.entries.map { it.toDefinition() }
        }
    }

    private fun parseRuleDefinitions(array: JSONArray): List<RuleDefinition> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                when (item) {
                    is JSONObject -> {
                        val id = item.optString("id").ifBlank { continue }
                        val field = runCatching { RuleField.valueOf(item.optString("matchField")) }.getOrNull() ?: continue
                        val query = item.optString("query").trim()
                        val action = runCatching {
                            RuleAction.valueOf(item.optString("action", RuleAction.SET_CATEGORY.name))
                        }.getOrDefault(RuleAction.SET_CATEGORY)
                        val targetCategory = item.optString("targetCategory", "Other").trim().ifBlank { "Other" }
                        val forcedMerchant = item.optString("forcedMerchant").trim().ifBlank { null }
                        if (query.isBlank()) continue
                        if (action == RuleAction.SET_MERCHANT && forcedMerchant.isNullOrBlank()) continue
                        add(
                            RuleDefinition(
                                id = id,
                                matchField = field,
                                query = query,
                                targetCategory = targetCategory,
                                action = action,
                                forcedMerchant = forcedMerchant
                            )
                        )
                    }
                    is String -> {
                        parseLegacyRuleString(item)?.let { add(it) }
                    }
                }
            }
        }
    }

    private fun parseLegacyRuleString(raw: String): RuleDefinition? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(Regex("\\s*[=:>]\\s*"), limit = 2)
        if (parts.size != 2) return null
        val query = parts[0].trim()
        val targetCategory = parts[1].trim()
        if (query.isBlank() || targetCategory.isBlank()) return null
        return RuleDefinition(
            id = "legacy_${trimmed.hashCode()}",
            matchField = RuleField.MERCHANT,
            query = query,
            targetCategory = targetCategory,
            action = RuleAction.SET_CATEGORY,
            forcedMerchant = null
        )
    }

}