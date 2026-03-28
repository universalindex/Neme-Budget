package com.example.nemebudget.llm

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import android.content.Context
import android.util.Log
import com.example.nemebudget.model.Category
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

    // Extract exactly the labels the enum uses
    private val allowedCategories = Category.entries.map { it.label }
    
    // Inject the allowed categories directly into the JSON Schema as an enum constraint
    private val jsonSchema = """
        {
          "type": "object",
          "properties": {
            "merchant": {"type": "string"},
            "amount": {"type": "number"},
            "category": {
              "type": "string",
              "enum": [${allowedCategories.joinToString(", ") { "\"$it\"" }}]
            }
          },
          "required": ["merchant", "amount", "category"]
        }
    """.trimIndent()

    private val systemPrompt = """
        You are a strict data extractor. Read the bank notification and extract the merchant name, amount, and a short category.
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
                    schema = jsonSchema
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
        var attempt = 1
        var currentPrompt = "Notification: \"$rawNotification\""
        
        while (true) {
            Log.d("LlmPipeline", "Extraction attempt $attempt for: $rawNotification")
            
            // 1. Generate JSON
            val jsonStr = generateJson(currentPrompt)
            
            // 2. Verify against the ORIGINAL text
            val tx = processAndVerify(rawNotification, jsonStr)
            
            // 3. If valid or out of retries, return
            if (tx.isVerified || attempt >= maxAttempts) {
                return@withContext ExtractionResult(jsonStr, tx, attempt - 1)
            }
            
            // 4. If invalid, prepare the correction prompt
            Log.d("LlmPipeline", "Validation failed: ${tx.verificationNotes}. Prompting LLM to correct itself...")
            currentPrompt = """
                Original Notification: "$rawNotification"
                Your previous output: $jsonStr
                Validation Error: ${tx.verificationNotes}
                Please carefully correct the JSON. The merchant and amount MUST exist in the original text exactly as written.
            """.trimIndent()
            
            attempt++
        }
        
        // Fallback (should never be reached due to loop break)
        ExtractionResult("""{"merchant": "Error", "amount": 0.0, "category": "Error"}""", ExtractedTransaction("Error", 0.0, "Error", false, "Unknown error"), 0)
    }

    /**
     * The core LLM generation call.
     */
    private suspend fun generateJson(userPrompt: String): String {
        val modelDir = File(modelPath)
        if (!modelDir.exists() || !modelDir.isDirectory) {
            return """{"merchant": "Error", "amount": 0.0, "category": "ModelNotFound"}"""
        }

        var llmJsonString = """{"merchant": "Error", "amount": 0.0, "category": "EngineFailure"}""" 
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

            // START TIMER
            val startTime = System.currentTimeMillis()

            val sb = StringBuilder()
            val channel = mlcEngine?.chat?.completions?.create(
                messages = messages,
                // RESTORED: Strict schema to force early termination!
                response_format = OpenAIProtocol.ResponseFormat(
                    type = "json_object",
                    schema = jsonSchema
                )
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
                llmJsonString = finalOutput
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
            val jsonObject = JSONObject(llmJsonString)
            val merchant = jsonObject.getString("merchant")
            val amount = jsonObject.getDouble("amount")
            val category = jsonObject.getString("category")

            var isVerified = true
            var notes = "Verified successfully."
            val rawLower = rawNotification.lowercase(Locale.ROOT)

            if (merchant != "Unknown" && merchant != "Error" && !rawLower.contains(merchant.lowercase(Locale.ROOT))) {
                isVerified = false
                notes = "FAILED: Merchant '$merchant' not found in original text."
            }

            val amountString = amount.toString()
            if (amount > 0.0 && !rawLower.contains(amountString)) { 
                isVerified = false
                notes = "FAILED: Amount '$amountString' not found in original text."
            }
            
            // NEW: Enforce exact enum matching!
            if (!allowedCategories.contains(category)) {
                isVerified = false
                notes = "FAILED: Category '$category' is not a valid option."
            }

            ExtractedTransaction(merchant, amount, category, isVerified, notes)

        } catch (e: Exception) {
            Log.e("LlmPipeline", "Failed to parse JSON", e)
            ExtractedTransaction("Error", 0.0, "Error", false, "JSON Parsing Failed: ${e.message}")
        }
    }

    fun getBestLlmDevice(): String {
        return if (mlcEngine != null) "MLC Loaded" else "Unknown (Engine not loaded)"
    }
}