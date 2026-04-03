package com.example.nemebudget.llm

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import android.content.Context
import android.util.Log
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.RuleField
import com.example.nemebudget.model.toDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.consumeEach

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

    private val modelPath: String
        get() = File(context.filesDir, "Qwen3-0.6B-q4f16_1-MLC").absolutePath

    private val modelLib = "qwen3_q4f16_1"

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

    private fun jsonSchema(): String {
        val categories = allowedCategories()
        return """
        {
          "type": "object",
          "properties": {
            "merchant": {"type": "string"},
            "amount": {"type": "number"},
             "category": {
               "type": "string",
               "enum": [${categories.joinToString(", ") { "\"$it\"" }}]
             }
           },
          "required": ["merchant", "amount", "category"]
        }
        """.trimIndent()
    }

    private val systemPrompt = """
You extract a single financial transaction from a raw mobile notification.

Output requirements:
- Return exactly one JSON object with keys: merchant, amount, category.
Extraction rules for confusing notifications:
1) Merchant
   - Prefer the payee/store/counterparty near words like "at", "from", "to", "merchant", or "payee".
   - Ignore card masks, OTP codes, balances, and reference IDs.
2) Amount
   - Extract the transaction amount that was charged/spent/debited/paid/credited.
   - Ignore available balance, remaining limit, rewards, and account-ending digits.
   - If multiple amounts exist, choose the one tied to the transaction action.
3) Category
   - Map to the closest allowed category; use "Other" if uncertain.

If the notification is not clearly a transaction, return:
{"merchant":"Error","amount":0.0,"category":"Other"}
""".trimIndent()

    private var mlcEngine: MLCEngine? = null

    private fun loadEngine() {
        if (mlcEngine != null) {
            Log.d("LlmPipeline", "MLC Engine already loaded.")
            return
        }
        
        Log.d("LlmPipeline", "WAKING UP ENGINE: Attempting to load Qwen with lib '${modelLib}' from path '${modelPath}'")
        try {
            val engine = MLCEngine()
            engine.reload(modelPath, modelLib)
            mlcEngine = engine
            Log.d("LlmPipeline", "MLC Engine loaded successfully.")
        } catch (e: Exception) {
            Log.e("LlmPipeline", "CRITICAL: Failed to load MLC Engine with lib '${modelLib}'", e)
            mlcEngine = null 
            throw e 
        }
    }

    fun unloadEngine() {
        mlcEngine?.unload()
        mlcEngine = null
        Log.d("LlmPipeline", "MLC Engine unloaded from RAM.")
    }

    /**
     * Warms up the engine to force AOT (Ahead-of-Time) shader compilation.
     */
    suspend fun warmUpEngine(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (mlcEngine == null) loadEngine()
            Log.d("LlmPipeline", "Warming up engine to compile shaders...")
            
            val messages = listOf(
                OpenAIProtocol.ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.user,
                    content = "Warmup."
                )
            )
            
            val channel = mlcEngine?.chat?.completions?.create(
                messages = messages,
                response_format = OpenAIProtocol.ResponseFormat(
                    type = "json_object",
                    schema = jsonSchema()
                ),
                max_tokens = 1 
            )
            
            channel?.consumeEach { /* discard */ }
            Log.d("LlmPipeline", "Engine warmup complete! Shaders cached.")
            true
        } catch (e: Exception) {
            Log.e("LlmPipeline", "Warmup failed", e)
            false
        }
    }

    /**
     * Handles the full flow of generating, verifying, and retrying if the LLM hallucinated.
     */
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
    private suspend fun generateJson(userPrompt: String): String {
        val modelDir = File(modelPath)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return """{"merchant": "Error", "amount": 0.0, "category": "Other"}"""
        }

        var llmJsonString = """{"merchant": "Error", "amount": 0.0, "category": "Other"}"""
        try {
            if (mlcEngine == null) loadEngine()

            val messages = listOf(
                OpenAIProtocol.ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.system,
                    content = systemPrompt
                ),
                OpenAIProtocol.ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.user,
                    content = userPrompt
                )
            )

            val startTime = System.currentTimeMillis()

            val sb = StringBuilder()
            val channel = mlcEngine?.chat?.completions?.create(
                messages = messages,
                response_format = OpenAIProtocol.ResponseFormat(
                    type = "json_object",
                    schema = jsonSchema()
                ),
                max_tokens = 220
            )

            channel?.consumeEach { response ->
                val contentObj = response.choices.firstOrNull()?.delta?.content
                val textToken = contentObj?.text
                if (textToken != null) {
                    sb.append(textToken)
                }
            }

            val finalOutput = sb.toString().trim()
            if (finalOutput.isNotEmpty()) {
                llmJsonString = extractFirstCompleteJsonObject(finalOutput) ?: llmJsonString
            }
            
            // END TIMER
            val timeTaken = System.currentTimeMillis() - startTime
            Log.d("LlmPipeline", "SPEED TEST: Generation took ${timeTaken}ms")
            Log.d("LlmPipeline", "MLC Engine raw output: $llmJsonString")

        } catch (e: Exception) {
            Log.e("LlmPipeline", "MLC Engine inference failed", e)
            unloadEngine()
        }
        return llmJsonString
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

            if (candidate.merchant != "Unknown" && candidate.merchant != "Error" && !merchantAppearsInRaw(rawLower, candidate.merchant)) {
                isVerified = false
                notes += "FAILED: Merchant '${candidate.merchant}' not found in original text."
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
                        val targetCategory = item.optString("targetCategory").trim()
                        if (query.isBlank() || targetCategory.isBlank()) continue
                        add(RuleDefinition(id = id, matchField = field, query = query, targetCategory = targetCategory))
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
            targetCategory = targetCategory
        )
    }

    fun getBestLlmDevice(): String {
        return if (mlcEngine != null) "MLC Loaded" else "Unknown (Engine not loaded)"
    }
}