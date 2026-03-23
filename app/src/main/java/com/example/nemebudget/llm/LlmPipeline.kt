package com.example.nemebudget.llm

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.util.Locale

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

    /**
     * This is a placeholder for the actual C++/MLC/MediaPipe LLM call.
     * Right now, it simulates the LLM taking text and returning a JSON string.
     */
    suspend fun simulateLlmInference(rawNotification: String): String {
        // Simulate a 2-second delay for loading the model into RAM and thinking
        kotlinx.coroutines.delay(2000)
        
        // In reality, the LLM with grammar flags would generate this perfectly formatted string.
        // For testing, we are mocking what the LLM *would* say.
        return if (rawNotification.contains("Starbucks", ignoreCase = true)) {
            """{"merchant": "Starbucks", "amount": 5.40, "category": "Dining"}"""
        } else if (rawNotification.contains("Amazon", ignoreCase = true)) {
            // Let's mock an LLM hallucination where it gets the amount wrong!
            """{"merchant": "Amazon", "amount": 999.99, "category": "Shopping"}"""
        } else {
            """{"merchant": "Unknown", "amount": 0.0, "category": "Misc"}"""
        }
    }

    /**
     * The Anti-Hallucination "Ctrl+F" Verifier.
     * Takes the LLM's JSON string, parses it, and verifies it against the original text.
     */
    fun processAndVerify(rawNotification: String, llmJsonString: String): ExtractedTransaction {
        return try {
            // 1. Parse the guaranteed JSON (thanks to Grammar Flags)
            val jsonObject = JSONObject(llmJsonString)
            val merchant = jsonObject.getString("merchant")
            val amount = jsonObject.getDouble("amount")
            val category = jsonObject.getString("category")

            // 2. The "Ctrl+F" Verification Check
            var isVerified = true
            var notes = "Verified successfully."
            val rawLower = rawNotification.lowercase(Locale.ROOT)

            // Check Merchant
            if (merchant != "Unknown" && !rawLower.contains(merchant.lowercase(Locale.ROOT))) {
                isVerified = false
                notes = "FAILED: Merchant '$merchant' not found in original text."
            }

            // Check Amount (Checking if the raw string contains the number)
            // Note: In real life, amounts might have commas or currency symbols, so this needs to be robust.
            val amountString = amount.toString()
            if (!rawNotification.contains(amountString)) {
                isVerified = false
                notes = "FAILED: Amount '$amountString' not found in original text."
            }

            ExtractedTransaction(merchant, amount, category, isVerified, notes)

        } catch (e: Exception) {
            Log.e("LlmPipeline", "Failed to parse JSON", e)
            ExtractedTransaction("Error", 0.0, "Error", false, "JSON Parsing Failed: ${e.message}")
        }
    }

    /**
     * Attempts to determine the best LLM device (GPU, NPU, or CPU) available on the device.
     * This is a conceptual check. Actual MLC LLM initialization will provide concrete feedback.
     */
    fun getBestLlmDevice(): String {
        // We'll try to find the "best" device for MLC LLM.
        // MLC LLM uses strings like "vulkan" (for GPU/NPU via Vulkan), "cpu".

        // 1. Prioritize NPU/GPU via Vulkan (most modern Android devices)
        // This is a heuristic. MLC LLM's internal logic will decide if it uses NNAPI under Vulkan if it's the best backend.
        val vulkanSupported = checkVulkanSupport()
        if (vulkanSupported) {
            Log.d("LlmPipeline", "Vulkan (GPU/NPU) appears supported. Will try to use 'vulkan'.")
            return "vulkan"
        }

        // 2. Fallback to CPU
        Log.d("LlmPipeline", "Vulkan not fully supported or suitable. Falling back to 'cpu'.")
        return "cpu"
    }

    /**
     * A basic check for Vulkan API support on the device.
     * Vulkan is often the underlying API MLC LLM uses for GPU/NPU acceleration.
     */
    private fun checkVulkanSupport(): Boolean {
        // We need at least API 24 (Android 7.0) for basic Vulkan support features
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.d("LlmPipeline", "Vulkan not supported on API < 24.")
            return false
        }

        // Check for general Vulkan hardware support at a reasonable level
        val hasVulkanHardwareLevel = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val hasVulkanHardwareVersion = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        
        Log.d("LlmPipeline", "Vulkan Hardware Level: $hasVulkanHardwareLevel, Vulkan Hardware Version: $hasVulkanHardwareVersion")

        // For simplicity, we'll consider Vulkan supported if either a hardware level or version feature is present.
        // A real app might check for a specific hardware level (e.g., FEATURE_VULKAN_HARDWARE_LEVEL_1_1)
        return hasVulkanHardwareLevel || hasVulkanHardwareVersion
    }
}