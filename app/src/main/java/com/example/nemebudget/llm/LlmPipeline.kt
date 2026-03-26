package com.example.nemebudget.llm

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import android.content.Context
import android.util.Log
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

class LlmPipeline(private val context: Context) {

    // 1. Pointing to the official weights folder you already have on your phone!
    private val modelPath: String
        get() = File(context.filesDir, "Qwen3-0.6B-q4f16_1-MLC").absolutePath

    // 2. The generic execution library hardcoded into the OFFICIAL mlc4j.aar from Maven
    private val modelLib = "qwen3_q4f16_1"

    private val jsonSchema = """
        {
          "type": "object",
          "properties": {
            "merchant": {"type": "string"},
            "amount": {"type": "number"},
            "category": {"type": "string"}
          },
          "required": ["merchant", "amount", "category"]
        }
    """.trimIndent()

    private val systemPrompt = """
        You are a strict data extractor. Read the bank notification and extract the merchant name, amount, and a short category (e.g., Dining, Groceries, Utility, Shopping). 
        Your output MUST be a JSON object that strictly conforms to the provided schema: $jsonSchema.
        No additional text, greetings, or explanations.
    """.trimIndent()

    private var mlcEngine: MLCEngine? = null

    /**
     * Loads the MLC LLM engine and model into RAM.
     */
    private fun loadEngine() {
        if (mlcEngine != null) {
            Log.d("LlmPipeline", "MLC Engine already loaded.")
            return
        }
        
        Log.d("LlmPipeline", "[VERSION 2] WAKING UP ENGINE: Attempting to load Qwen with lib '${modelLib}' from path '${modelPath}'")
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

    /**
     * Unloads the MLC LLM engine from RAM.
     */
    fun unloadEngine() {
        mlcEngine?.unload()
        mlcEngine = null
        Log.d("LlmPipeline", "MLC Engine unloaded from RAM.")
    }

    /**
     * Runs inference. Note: We now keep the engine loaded to avoid 2GB reload lag.
     */
    suspend fun simulateLlmInference(rawNotification: String): String = withContext(Dispatchers.IO) {
        val modelDir = File(modelPath)
        Log.d("LlmPipeline", "Verifying model path: $modelPath. Exists: ${modelDir.exists()}, Is Directory: ${modelDir.isDirectory()}")
        if (!modelDir.exists() || !modelDir.isDirectory) {
            Log.e("LlmPipeline", "Model not found at $modelPath! Please ensure the folder and its contents are copied correctly.")
            return@withContext """{"merchant": "Error", "amount": 0.0, "category": "ModelNotFound"}"""
        }

        var llmJsonString = """{"merchant": "Error", "amount": 0.0, "category": "EngineFailure"}""" 
        try {
            if (mlcEngine == null) {
                loadEngine()
            }

            val messages = listOf(
                OpenAIProtocol.ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.system,
                    content = systemPrompt
                ),
                OpenAIProtocol.ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.user,
                    content = "Notification: \"$rawNotification\""
                )
            )

            Log.d("LlmPipeline", "THINKING: Sending request to MLC Engine...")
            val sb = StringBuilder()
            val channel = mlcEngine?.chat?.completions?.create(
                messages = messages,
                response_format = OpenAIProtocol.ResponseFormat(type = "json_object")
            )

            // Consume the stream and rebuild the full JSON response.
            channel?.consumeEach { response ->
                val text = response.choices.firstOrNull()?.delta?.content
                if (text != null) {
                    sb.append(text)
                }
            }

            val finalOutput = sb.toString().trim()
            if (finalOutput.isNotEmpty()) {
                llmJsonString = finalOutput
            }
            Log.d("LlmPipeline", "MLC Engine raw output: $llmJsonString")

        } catch (e: Exception) {
            Log.e("LlmPipeline", "MLC Engine inference failed", e)
            unloadEngine()
        }
        return@withContext llmJsonString
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