# ⚙️ DEV 2 — Backend, AI & Data Pipeline Lead
### Zero-Cloud AI Budgeter | Weber State Hackathon | Due: April 3, 2026 @ 11:59 PM

---

## Your Role
You own everything invisible: the notification interceptor, the local LLM, the encrypted database, and the
data pipeline. Dev 1 builds the UI against a fake version of your code from Day 1.
When you finish your `RealRepository`, they swap in a single line and the whole app works.

---

## 🔴 Day 1 (March 22) — Contracts & Project Setup
> **Goal:** Database schema running, shared contracts committed to Git, service skeleton created.

### Step 1 — Add Dependencies to `build.gradle.kts`
```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// SQLCipher (encrypted database)
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")

// DataStore (settings)
implementation("androidx.datastore:datastore-preferences:1.0.0")

// MediaPipe LLM (local AI)
implementation("com.google.mediapipe:tasks-genai:0.10.14")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

### Step 2 — Agree on and Commit Shared Contracts
These are defined in `model/SharedModels.kt`. Dev 1 creates the file — you agree on it together.
The key types are: `Transaction`, `Category`, `Budget`, `AppSettings`, `AppRepository` interface.
**Do not start Step 3 until this file is committed and you've both approved it.**

### Step 3 — Define Room Entities
Create `database/Entities.kt`:
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val merchant: String,
    val amount: Double,
    @ColumnInfo(name = "category") val category: String,   // Store enum name as String
    val date: Long,
    val isAiParsed: Int,      // 0 or 1 (Room doesn't support Boolean natively)
    val confidence: Float,
    val rawNotificationText: String
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryName: String,
    val spent: Double,
    val limit: Double
)
```

### Step 4 — Define Room DAOs
Create `database/AppDao.kt`:
```kotlin
@Dao
interface AppDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>
    
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<TransactionEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(t: TransactionEntity)
    
    @Update
    suspend fun updateTransaction(t: TransactionEntity)
    
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(b: BudgetEntity)
}
```

### Step 5 — Set Up Encrypted Database
Create `database/AppDatabase.kt`:
```kotlin
@Database(entities = [TransactionEntity::class, BudgetEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase {
            val passphrase = SQLiteDatabase.getBytes("your-secret-key".toCharArray())
            val factory = SupportFactory(passphrase)

            return Room.databaseBuilder(context, AppDatabase::class.java, "budget.db")
                .openHelperFactory(factory)
                .build()
        }
    }
}
```
> ⚠️ For the hackathon, a hardcoded passphrase is fine. In production you'd use Android Keystore.

### Step 6 — Implement RealRepository
Create `repository/RealRepository.kt` implementing the `AppRepository` interface.
Write mapper functions: `TransactionEntity → Transaction` and back.
This is the class Dev 1 will swap in on March 30th.

---

## 🟠 Days 2–3 (March 23–24) — Notification Listener Service
> **Goal:** The app can read any notification. Test it. Prove it works before touching AI.

### Step 1 — Register the Service in AndroidManifest.xml
```xml
<service
    android:name=".notification.BudgetNotificationListener"
    android:label="Budget Notification Listener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### Step 2 — Create the Service Class
Create `notification/BudgetNotificationListener.kt`:
```kotlin
class BudgetNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

        // Step 3 will filter ignored apps and route to AI
        Log.d("NOTIF_LISTENER", "Package: $packageName | Title: $title | Text: $bigText")
    }
}
```

### Step 3 — Apply the Ignore Filter from Settings
```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification) {
    // Read ignoredApps from DataStore (use runBlocking only here, or collect via coroutine)
    val settings = runBlocking { settingsRepository.getSettingsOnce() }

    if (sbn.packageName in settings.ignoredApps) {
        Log.d("NOTIF_LISTENER", "Ignoring ${sbn.packageName} (user excluded)")
        return
    }
    // Continue to AI routing...
}
```

### Step 4 — Primary Bank Filter
```kotlin
// Only route to AI if the notification looks like a bank transaction
// Simple heuristic: check if it contains $ or the word "charge", "purchase", "payment"
val looksLikeTransaction = bigText.contains("$") || 
    bigText.contains("charge", ignoreCase = true) ||
    bigText.contains("purchase", ignoreCase = true)

if (looksLikeTransaction) {
    aiPipeline.process(rawText = bigText, settings = settings)
}
```

### Step 5 — Test the Listener (MILESTONE)
- Install the app on a physical device
- Go to **Settings → Apps → Special App Access → Notification Access** → enable your app
- Send yourself a notification with a banking-style message using ADB:
```bash
adb shell am broadcast -a android.intent.action.NOTIFICATION_TEST \
  --es title "Chase Bank" --es text "A charge of \$23.50 at Chipotle was made"
```
- Check Logcat for: `Package: ... | Title: Chase Bank | Text: A charge of $23.50...`
- ✅ **This milestone must be done before touching the AI code.**

---

## 🟡 Days 4–6 (March 25–27) — Local LLM Integration
> **Goal:** A notification text goes in. A structured JSON Transaction object comes out.

### Step 1 — Download the Model File
Use Gemma 2B (or Phi-3 Mini if storage is a concern) from Hugging Face.
Convert it to the MediaPipe `.task` format:
1. Download `gemma-2b-it` from `https://huggingface.co/google/gemma-2b-it`
2. Use the MediaPipe Model Maker to convert to `.task` format
3. Place the `.task` file in your app's `/assets/` directory

For the hackathon, you can also ship it as a download (see Step 4).

### Step 2 — Create the AI Parser Class
Create `ai/TransactionParser.kt`:
```kotlin
class TransactionParser(private val context: Context) {

    private var llmInference: LlmInference? = null

    fun initialize(modelPath: String) {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(512)
            .setTopK(40)
            .setTemperature(0.1f)   // Low temperature = consistent output
            .build()
        llmInference = LlmInference.createFromOptions(context, options)
    }

    suspend fun parse(rawText: String, settings: AppSettings): Transaction? {
        val prompt = buildPrompt(rawText, settings)
        val response = withContext(Dispatchers.Default) {
            llmInference?.generateResponse(prompt) ?: return@withContext null
        }
        return parseJsonResponse(response)
    }

    fun release() { llmInference?.close() }
}
```

### Step 3 — Write the System Prompt (MOST IMPORTANT STEP)
This is the heart of the app. Spend real time on this.

```kotlin
private fun buildPrompt(rawText: String, settings: AppSettings): String {
    val rulesSection = if (settings.customRules.isNotEmpty()) {
        "Custom rules: ${settings.customRules.joinToString("; ")}"
    } else ""

    val bankHint = if (settings.primaryBank.isNotEmpty()) {
        "The user's primary bank is ${settings.primaryBank}."
    } else ""

    return """
You are a JSON transaction extractor. Extract financial data from a bank push notification.
$bankHint
$rulesSection

Categories: DINING, GROCERIES, GAS, BILLS, ENTERTAINMENT, SHOPPING, TRANSPORT, OTHER

Respond ONLY with a single valid JSON object. No markdown, no explanation.
Format:
{"merchant": "string", "amount": number, "category": "CATEGORY_ENUM", "confidence": number}

Confidence is 0.0 to 1.0 based on how certain you are.
If this is NOT a transaction notification, return: {"error": "not_a_transaction"}

Notification text: "$rawText"
    """.trimIndent()
}
```

### Step 4 — Parse the JSON Response
```kotlin
private fun parseJsonResponse(response: String): Transaction? {
    return try {
        val clean = response.trim().removePrefix("```json").removeSuffix("```").trim()
        val json = JSONObject(clean)
        
        if (json.has("error")) return null  // Not a transaction

        Transaction(
            merchant = json.getString("merchant"),
            amount = json.getDouble("amount"),
            category = Category.valueOf(json.getString("category")),
            date = System.currentTimeMillis(),
            isAiParsed = true,
            confidence = json.getDouble("confidence").toFloat(),
            rawNotificationText = ""  // Will be set by caller
        )
    } catch (e: Exception) {
        Log.e("AI_PARSER", "JSON parse failed: ${e.message}")
        null  // Return null, caller handles gracefully
    }
}
```

### Step 5 — Model Download Manager (for Onboarding)
Create `ai/ModelDownloadManager.kt`:
```kotlin
class ModelDownloadManager(private val context: Context) {
    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress

    suspend fun downloadModel(url: String, fileName: String): String {
        // Use OkHttp or built-in URL streams to download
        // Emit progress updates to _downloadProgress as 0.0 to 1.0
        // Save to context.filesDir/fileName
        // Return the absolute path
    }

    fun isModelDownloaded(fileName: String): Boolean {
        return File(context.filesDir, fileName).exists()
    }
}
```

### Step 6 — End-to-End AI Test (MILESTONE)
Write a simple unit test or a temp button in the app:
```kotlin
// Input: "Your Chase Sapphire card was charged $47.83 at Whole Foods Market"
// Expected output: {merchant: "Whole Foods Market", amount: 47.83, category: "GROCERIES", confidence: 0.92}
```
Do not move to Phase 3 until this milestone passes consistently.

---

## 🟢 Days 7–8 (March 28–29) — Full Pipeline Wiring
> **Goal:** Notification → AI → Database → UI update. The full chain.

### Step 1 — Connect the Listener to the Parser
In `BudgetNotificationListener.kt`:
```kotlin
// After the bank filter check:
coroutineScope.launch {
    val transaction = transactionParser.parse(
        rawText = bigText,
        settings = settingsRepository.getSettingsOnce()
    ) ?: return@launch  // AI returned null (not a transaction or parse failed)

    val finalTransaction = transaction.copy(rawNotificationText = bigText)
    database.appDao().insertTransaction(finalTransaction.toEntity())
    
    Log.d("PIPELINE", "Saved: ${finalTransaction.merchant} - $${finalTransaction.amount}")
}
```

### Step 2 — Update the Budget "Spent" on Each Insert
```kotlin
// After inserting transaction, update the budget's spent amount
val currentBudget = database.appDao().getBudgetOnce(transaction.category.name)
if (currentBudget != null) {
    database.appDao().upsertBudget(
        currentBudget.copy(spent = currentBudget.spent + transaction.amount)
    )
}
```

### Step 3 — Service Lifecycle: Prevent Battery Kill
```kotlin
// In the service, hold a partial WakeLock only during AI inference
// Release immediately after. This prevents Android from killing the service mid-inference.
private val wakeLock by lazy {
    (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BudgetApp::AIInferenceLock")
}

// In the AI processing block:
wakeLock.acquire(10_000)  // Max 10 seconds
try {
    val transaction = transactionParser.parse(rawText, settings)
    // ... save to DB
} finally {
    if (wakeLock.isHeld) wakeLock.release()
}
```

### Step 4 — Full Pipeline Test (MILESTONE)
1. Trigger a realistic bank-style ADB notification
2. Check Logcat for: `Saved: [Merchant] - $[Amount]`
3. Verify a new row appears in the SQLite database (use Database Inspector in Android Studio)
4. Watch Dev 1's UI update automatically (since Room emits via Flow)
- ✅ **This is the "Aha!" moment — the whole app works.**

---

## 🔵 March 30–31 — Integration & Handoff

### Step 1 — Give Dev 1 Your RealRepository
Push your completed `RealRepository.kt` to Git.
Dev 1 will replace `FakeRepository` with it. If anything breaks, the contract was violated —
fix it together by amending `AppRepository` interface and both implementations.

### Step 2 — CSV Export Feature
```kotlin
suspend fun exportToCsv(context: Context): Uri {
    val transactions = database.appDao().getAllTransactionsOnce()
    val csv = buildString {
        appendLine("Date,Merchant,Amount,Category,AI Parsed,Confidence")
        transactions.forEach { t ->
            appendLine("${Date(t.date)},${t.merchant},${t.amount},${t.category},${t.isAiParsed},${t.confidence}")
        }
    }
    // Write to context.getExternalFilesDir() and return a FileProvider URI
}
```

### Step 3 — Wipe All Data Feature
```kotlin
suspend fun wipeAllData() {
    database.clearAllTables()
    dataStore.edit { it.clear() }
}
```

### Step 4 — Custom Rules → Prompt Injection
When Dev 1's Settings screen saves a custom rule (e.g., `"Chevron = Gas"`), it flows via `AppSettings` 
into your `buildPrompt()` function automatically — no extra work needed since you're already reading `settings.customRules`.

---

## 🏁 April 1–2 — Polish, Edge Cases & Demo Prep

### Step 1 — Handle Edge Cases Gracefully
- AI returns malformed JSON → catch and log, do NOT crash
- AI returns `{"error": "not_a_transaction"}` → silently discard
- Model not yet downloaded → queue notifications, process after download
- Device battery saver mode killed the service → re-register on next app open

### Step 2 — Performance Check
- AI inference should complete in < 5 seconds on a mid-range device
- If using Gemma 2B and it's too slow, switch to **Phi-3 Mini (3.8B)** — it's faster despite being larger because of better quantization
- Test on the device you'll demo on specifically

### Step 3 — Demo Preparation: Fake Notification Script
Write an ADB script so you can trigger a perfect demo notification every time:
```bash
#!/bin/bash
# demo_trigger.sh
adb shell am start-foreground-service \
  -n com.zerocloudbudget/.notification.TestNotificationTrigger \
  --es notification_text "Chase: A purchase of \$34.99 at Chipotle was made on your card ending in 1234"
```
Alternatively, use a second phone to send a push notification to the demo device.

### Step 4 — Devpost Write-Up (Your Section)
Write the Technical Complexity section:
- MediaPipe LLM running Gemma 2B locally (no internet required after setup)
- SQLCipher encrypted database
- Android NotificationListenerService pipeline
- Zero external API calls: no Plaid, no OpenAI, no cloud backend

---

## 📋 Your Daily Checklist

| Day | Date | Deliverable |
|-----|------|-------------|
| 1 | Mar 22 | Contracts committed, Room DB builds, service skeleton registered |
| 2-3 | Mar 23-24 | NotificationListener intercepts notifications (Logcat proof) |
| 4-5 | Mar 25-26 | MediaPipe initialized, model loaded, test prompt runs |
| 6 | Mar 27 | AI parses a bank notification into correct JSON (test passes) |
| 7-8 | Mar 28-29 | Full pipeline: notification → DB insert → confirmed in DB Inspector |
| 9 | Mar 30 | RealRepository pushed to Git, Dev 1 integration complete |
| 10 | Mar 31 | CSV export + wipe working, edge cases handled |
| 11 | Apr 1 | Demo ADB script ready, performance verified on demo device |
| 12 | Apr 2 | Demo video recorded, Devpost written |
| 13 | Apr 3 | SUBMIT by 11:59 PM |

---

## 🚨 Anti-Bottleneck Rules
- **Expose everything as Flow.** Dev 1's UI updates automatically when you insert to DB — no callbacks needed.
- **Communicate contract changes immediately.** Adding a field to `Transaction`? Tell Dev 1 first.
- **Test in isolation before wiring up.** NotificationListener works alone. AI parser works alone. Connect them only after both are proven.
- **Use Git branches:** `feature/notification-service`, `feature/ai-parser`, `feature/repository`. Merge daily.
- **Keep a test harness.** A simple button in a debug screen that fires a hardcoded notification text through the parser is worth more than 10 unit tests for a 12-day hackathon.
