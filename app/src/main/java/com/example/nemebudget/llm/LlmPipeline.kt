package com.example.nemebudget.llm

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import android.content.Context
import android.util.Log
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.Category
import com.example.nemebudget.model.toDefinition
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
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
Extract: Merchant, Amount, Category.
Output only valid JSONS
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
    suspend fun extractWithRetry(rawNotification: String, maxAttempts: Int = 2): ExtractionResult = withContext(Dispatchers.IO) {
        var currentPrompt = "Notification: \"$rawNotification\""
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
                currentPrompt = """
                    Original Notification: "$rawNotification"
                    Your previous output: $jsonStr
                    Validation Error: ${tx.verificationNotes}
                    Valid categories (exact spelling): ${allowedCategoryText()}
                    Return JSON only with keys: merchant, amount, category.
                    Do not abbreviate category names.
                """.trimIndent()
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

            var isVerified = true
            var notes = "Verified successfully."
            val rawLower = rawNotification.lowercase(Locale.ROOT)

            if (merchant != "Unknown" && merchant != "Error" && !merchantAppearsInRaw(rawLower, merchant)) {
                isVerified = false
                notes = "FAILED: Merchant '$merchant' not found in original text."
            }

            val amountString = amount.toString()
            if (amount > 0.0 && !rawLower.contains(amountString)) {
                isVerified = false
                notes = "FAILED: Amount '$amountString' not found in original text."
            }

            if (!allowedCategories().contains(category)) {
                isVerified = false
                notes = "FAILED: Category '$category' is not a valid option."
            }

            ExtractedTransaction(merchant, amount, category, isVerified, notes)

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
                    if (depth == 0 && start >= 0) {
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
            for (index in 0 until customArray.length()) {
                val item = customArray.optJSONObject(index) ?: continue
                val id = item.optString("id").ifBlank { continue }
                val label = item.optString("label").ifBlank { continue }
                val emoji = item.optString("emoji").ifBlank { "📁" }
                list += CategoryDefinition(id = id, label = label, emoji = emoji, isCustom = true)
            }

            if (list.isEmpty()) Category.entries.map { it.toDefinition() } else list
        } catch (_: Throwable) {
            Category.entries.map { it.toDefinition() }
        }
    }

    fun getBestLlmDevice(): String {
        return if (mlcEngine != null) "MLC Loaded" else "Unknown (Engine not loaded)"
    }
}