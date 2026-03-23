package com.example.nemebudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.* // Added for remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // Added for LocalContext
import androidx.compose.ui.unit.dp
import com.example.nemebudget.ui.theme.NemeBudgetTheme
import com.example.nemebudget.llm.ExtractedTransaction
import com.example.nemebudget.llm.LlmPipeline
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NemeBudgetTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LlmTestingScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun LlmTestingScreen(modifier: Modifier = Modifier) {
    var rawNotificationInput by remember { mutableStateOf("Chase: You spent $5.40 at Starbucks today.") }
    var outputTransaction by remember { mutableStateOf<ExtractedTransaction?>(null) }
    var rawLlmJsonOutput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var detectedDevice by remember { mutableStateOf("Detecting...") } // New state for device detection

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current // Get the current Android context
    val pipeline = remember { LlmPipeline(context) } // Pass context to LlmPipeline

    // Run device detection once when the Composable enters the composition
    LaunchedEffect(Unit) {
        detectedDevice = pipeline.getBestLlmDevice()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "The Brain Testing Lab", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Detected LLM Device: $detectedDevice", style = MaterialTheme.typography.bodySmall) // Display detected device

        OutlinedTextField(
            value = rawNotificationInput,
            onValueChange = { rawNotificationInput = it },
            label = { Text("Paste Fake Bank Notification Here") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                coroutineScope.launch {
                    isProcessing = true
                    val jsonResponse = pipeline.simulateLlmInference(rawNotificationInput)
                    rawLlmJsonOutput = jsonResponse
                    val verifiedTransaction = pipeline.processAndVerify(rawNotificationInput, jsonResponse)
                    outputTransaction = verifiedTransaction
                    isProcessing = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isProcessing
        ) {
            Text(if (isProcessing) "LLM Thinking..." else "Process with Grammar & Verifier")
        }

        HorizontalDivider()

        Text(text = "Raw LLM JSON Output:", style = MaterialTheme.typography.titleMedium)
        Text(text = rawLlmJsonOutput, style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Verification Result:", style = MaterialTheme.typography.titleMedium)
        outputTransaction?.let { transaction ->
            val resultColor = if (transaction.isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

            Surface(color = resultColor.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Verified: ${transaction.isVerified}", color = resultColor, style = MaterialTheme.typography.titleSmall)
                    Text("Merchant: ${transaction.merchant}")
                    Text("Amount: $${transaction.amount}")
                    Text("Category: ${transaction.category}")
                    Text("Notes: ${transaction.verificationNotes}", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
