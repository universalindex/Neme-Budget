# ⚙️ NOAH — Backend, AI & Data Pipeline Lead
### Zero-Cloud AI Budgeter | Weber State Hackathon | Due: April 3, 2026 @ 11:59 PM

---

## Your Role
You own the three invisible pillars of the app: the **notification interceptor**, the **local Qwen AI engine**,
and the **encrypted database**. Eli builds the entire UI against a FakeRepository while you build
the real one — no waiting, no blocking. When you push `RealRepository.kt`, he swaps it in with one line.

**AI choice: Qwen 2.5 1.5B-Instruct via MLC LLM.**
MLC LLM is the best Android runtime for Qwen because it ships prebuilt AAR packages with
hardware-accelerated inference AND natively supports **JSON schema-constrained generation** —
meaning the model is physically forced by the grammar sampler to output valid JSON.
No prompt hacks. No JSON cleanup. Zero malformed output.

---

## 🔴 Day 1 (March 22) — Contracts & Project Setup
> **Goal:** Database schema compiles, shared contracts committed, service skeleton registered in manifest.

### Step 1 — Add Your Dependencies to `build.gradle.kts`
```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// SQLCipher (encrypted DB)
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")

// DataStore (settings)
implementation("androidx.datastore:datastore-preferences:1.0.0")

// MLC LLM (local Qwen inference)
implementation("ai.mlc:mlc4j:0.1.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// OkHttp (model download)
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

Also add the MLC Maven repo to `settings.gradle.kts`:
```kotlin
maven { url = uri("https://repo.mlc.ai/repository/maven-public/") }
```

### Step 2 — Agree on and Commit Shared Contracts
`model/SharedModels.kt` is written by Eli. You review and approve.
The key types: `Transaction`, `Category`, `Budget`, `AppSettings`, `ModelStatus`, `AppRepository`.
**Do not write a single line of logic until this file is committed and both of you have approved it.**

### Step 3 — Define Room Entities
Create `database/Entities.kt`:
```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val merchant: String,
    val amount: Double,
    @ColumnInfo(name = "category") val category: String,  // Store enum name as String
    val date: Long,
    val isAiParsed: Int,        // 0 or 1 (Room doesn't store Boolean)
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

    @Query("SELECT * FROM transactions ORDER BY date DESC LIMIT 1")
    suspend fun getLatestTransaction(): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(t: TransactionEntity)

    @Update
    suspend fun updateTransaction(t: TransactionEntity)

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE categoryName = :name LIMIT 1")
    suspend fun getBudgetOnce(name: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryName = :name")
    suspend fun deleteBudget(name: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>
}
```

### Step 5 — Set Up the Encrypted Database
Create `database/AppDatabase.kt`:
```kotlin
@Database(entities = [TransactionEntity::class, BudgetEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                val passphrase = SQLiteDatabase.getBytes("zc-budget-key-2026".toCharArray())
                val factory = SupportFactory(passphrase)
                Room.databaseBuilder(context, AppDatabase::class.java, "budget.db")
                    .openHelperFactory(factory)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
```
> ⚠️ Hardcoded passphrase is fine for the hackathon. In production you'd derive it from Android Keystore.

### Step 6 — Register the Notification Service in AndroidManifest.xml
Do this now so the app structure is correct from Day 1:
```xml
<service
    android:name=".notification.BudgetNotificationListener"
    android:label="Zero Cloud Budget Listener"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### Step 7 — Implement RealRepository Skeleton
Create `repository/RealRepository.kt` implementing `AppRepository`.
Write all the mapper functions today (`TransactionEntity → Transaction` and back).
Leave the `getModelStatus()` flow returning a hardcoded "not downloaded" for now — you'll fill it in on Day 4.
**This skeleton must compile clean by end of Day 1.**

---

## 🟠 Days 2–3 (March 23–24) — Notification Listener Service
> **Goal:** The app intercepts any real notification. Proven in Logcat before touching AI.

### Step 1 — Create the Listener Service
Create `notification/BudgetNotificationListener.kt`:
```kotlin
class BudgetNotificationListener : NotificationListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title   = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                   ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                   ?: return

        Log.d("NOTIF_RAW", "pkg=${sbn.packageName} | title=$title | text=$bigText")
        // Filtering and AI routing added in next steps
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
```

### Step 2 — Apply the Ignore Filter
```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification) {
    val settings = runBlocking { settingsRepo.getSettingsOnce() }

    if (sbn.packageName in settings.ignoredApps) {
        Log.d("NOTIF_FILTER", "Skipped: ${sbn.packageName} (user excluded)")
        return
    }
    // ... continue to transaction detection
}
```

### Step 3 — Transaction Detection Heuristic
```kotlin
// Before spending compute time on AI inference, do a cheap pre-filter
val looksLikeTransaction = bigText.contains("$") ||
    bigText.contains("charge",   ignoreCase = true) ||
    bigText.contains("purchase", ignoreCase = true) ||
    bigText.contains("payment",  ignoreCase = true) ||
    bigText.contains("debit",    ignoreCase = true)  ||
    (settings.primaryBank.isNotEmpty() && title.contains(settings.primaryBank, ignoreCase = true))

if (!looksLikeTransaction) return
// Route to AI
```

### Step 4 — Test the Listener ✅ MILESTONE
1. Install on a real Android device
2. **Settings → Apps → Special App Access → Notification Access** → enable your app
3. Fire a test notification via ADB:
```bash
adb shell cmd notification post -S bigtext -t "Chase Bank" "TestTag" \
  "A charge of \$23.50 at Chipotle was made on your card ending in 4242"
```
4. Check Logcat filter `NOTIF_RAW` — you should see the title and text
5. ✅ **Do not write a single line of AI code until this passes.**

---

## 🟡 Days 4–6 (March 25–27) — Qwen 2.5 Local Inference + JSON-Locked Output
> **Goal:** A notification text string goes in. A perfectly structured `Transaction` object comes out.
> Guaranteed. Every time. Because the grammar sampler makes invalid JSON physically impossible.

### Step 1 — Get the Qwen Model
Qwen 2.5 1.5B-Instruct is prebuilt for MLC at HuggingFace:
1. Go to: `https://huggingface.co/mlc-ai/Qwen2.5-1.5B-Instruct-q4f16_1-MLC`
2. Download the model files (`.bin` shards + `mlc-chat-config.json` + `tokenizer` files)
3. Place everything in `app/src/main/assets/qwen2.5-1.5b/`
4. Total size: ~1.1 GB. This ships inside the APK for the hackathon.
   (For production you'd use `ModelDownloadManager` — wire that up on Day 5 if APK size is a problem)

> **If 1.1 GB APK is rejected by the test device:** Switch to `Qwen2.5-0.5B-Instruct-q4f16_1-MLC` (~400 MB).
> Accuracy is slightly lower but still excellent for transaction parsing.

### Step 2 — Initialize MLC LLM Engine
Create `ai/QwenEngine.kt`:
```kotlin
class QwenEngine(private val context: Context) {

    private var engine: MLCEngine? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        return@withContext try {
            engine = MLCEngine()
            engine!!.reload(
                modelPath = "${context.filesDir}/qwen2.5-1.5b",  // or assets path
                modelLib = "qwen2_5_1_5b_q4f16_1"
            )
            Log.d("QWEN", "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e("QWEN", "Model load failed: ${e.message}")
            false
        }
    }

    fun isReady() = engine != null

    fun release() { engine?.unload() }
}
```

### Step 3 — Define the JSON Schema (THE KEY STEP)
This is where Qwen gets locked to valid JSON output.
The grammar sampler in MLC LLM reads this schema and constrains every token it generates.
It is **physically impossible** for the model to produce output that doesn't match this schema.

Create `ai/TransactionSchema.kt`:
```kotlin
object TransactionSchema {

    // This JSON Schema is passed to MLC's response_format parameter
    val schema = """
    {
      "type": "object",
      "properties": {
        "is_transaction": {
          "type": "boolean"
        },
        "merchant": {
          "type": "string"
        },
        "amount": {
          "type": "number",
          "minimum": 0
        },
        "category": {
          "type": "string",
          "enum": ["DINING", "GROCERIES", "GAS", "BILLS", "ENTERTAINMENT", "SHOPPING", "TRANSPORT", "OTHER"]
        },
        "confidence": {
          "type": "number",
          "minimum": 0.0,
          "maximum": 1.0
        }
      },
      "required": ["is_transaction", "merchant", "amount", "category", "confidence"]
    }
    """.trimIndent()
}
```

When `is_transaction` is `false`, you ignore `merchant`/`amount`/`category` — they'll just be defaults.
This replaces the old `{"error": "not_a_transaction"}` pattern with a type-safe boolean.

### Step 4 — Write the Transaction Parser
Create `ai/TransactionParser.kt`:
```kotlin
class TransactionParser(private val engine: QwenEngine) {

    suspend fun parse(rawText: String, settings: AppSettings): Transaction? {
        if (!engine.isReady()) return null

        val prompt = buildPrompt(rawText, settings)

        val response = withContext(Dispatchers.Default) {
            engine.chatCompletion(
                messages = listOf(
                    ChatMessage(role = "user", content = prompt)
                ),
                responseFormat = ResponseFormat(
                    type = "json_schema",
                    jsonSchema = JsonSchema(
                        name = "transaction_extraction",
                        schema = TransactionSchema.schema
                    )
                ),
                temperature = 0.1f,   // Low = deterministic, consistent output
                maxTokens = 128       // Transaction JSON is small — cap it tight
            )
        } ?: return null

        // No JSON cleanup needed. Grammar sampler guarantees valid JSON.
        return try {
            val json = JSONObject(response)
            if (!json.getBoolean("is_transaction")) return null  // Not a financial notification

            Transaction(
                merchant   = json.getString("merchant"),
                amount     = json.getDouble("amount"),
                category   = Category.valueOf(json.getString("category")),
                date       = System.currentTimeMillis(),
                isAiParsed = true,
                confidence = json.getDouble("confidence").toFloat()
            )
        } catch (e: Exception) {
            // Should only happen if the schema itself has a bug — log and investigate
            Log.e("QWEN_PARSE", "Unexpected parse error: ${e.message} | raw=$response")
            null
        }
    }

    private fun buildPrompt(rawText: String, settings: AppSettings): String {
        val bankHint = if (settings.primaryBank.isNotEmpty())
            "The user's primary bank is ${settings.primaryBank}. " else ""

        val rulesHint = if (settings.customRules.isNotEmpty())
            "Apply these user rules: ${settings.customRules.joinToString("; ")}. " else ""

        return """
You are a financial notification parser.
${bankHint}${rulesHint}

Extract transaction data from the following push notification text.
If this is not a purchase or payment notification, set is_transaction to false.

Notification: "$rawText"
        """.trimIndent()
    }
}
```

### Step 5 — Model Download Manager (for Onboarding)
Create `ai/ModelDownloadManager.kt`:
```kotlin
class ModelDownloadManager(private val context: Context) {

    private val _status = MutableStateFlow(
        ModelStatus(isDownloaded = false, downloadProgress = 0f, modelSizeLabel = "1.1 GB")
    )
    val status: StateFlow<ModelStatus> = _status

    fun isModelReady(): Boolean = File(context.filesDir, "qwen2.5-1.5b/mlc-chat-config.json").exists()

    suspend fun downloadModel() = withContext(Dispatchers.IO) {
        // If bundling in assets: just copy from assets/ to filesDir on first launch
        // If downloading: use OkHttp, track bytes received / total, emit to _status
        // Example for assets copy:
        val configFile = File(context.filesDir, "qwen2.5-1.5b")
        if (!configFile.exists()) {
            configFile.mkdirs()
            // copyFromAssets(context, "qwen2.5-1.5b", configFile) — implement as util
        }
        _status.update { it.copy(isDownloaded = true, downloadProgress = 1f) }
    }
}
```
> **Note:** Expose `status: StateFlow<ModelStatus>` — Eli's Settings screen reads this directly
> through the RealRepository's `getModelStatus()` method.

### Step 6 — AI Isolation Test ✅ MILESTONE
Create a temporary `TestActivity` or use a Logcat-only approach:
```kotlin
// In a test coroutine (not a unit test, just a button in a debug screen):
val engine = QwenEngine(context)
engine.initialize()
val parser = TransactionParser(engine)
val fakeSettings = AppSettings(primaryBank = "Chase")

val result = parser.parse(
    rawText = "Chase: A purchase of \$34.99 at Chipotle Mexican Grill was made on card ending 4242",
    settings = fakeSettings
)
Log.d("AI_TEST", "Result: $result")
// Expected: Transaction(merchant="Chipotle Mexican Grill", amount=34.99, category=DINING, confidence~0.95)
```
**Do not wire the listener to the AI until this test passes at least 5 times in a row.**

---

## 🟢 Days 7–8 (March 28–29) — Full Pipeline Wiring
> **Goal:** Notification → Qwen → Database → Eli's UI updates automatically.

### Step 1 — Connect Listener → Parser → Database
In `BudgetNotificationListener.kt`:
```kotlin
// Inject or access: database, transactionParser, settingsRepo, wakeLock

override fun onNotificationPosted(sbn: StatusBarNotification) {
    // ... (filter logic from Days 2-3) ...

    coroutineScope.launch {
        wakeLock.acquire(15_000L)  // 15s max — Qwen 1.5B usually finishes in 2-4s
        try {
            val settings = settingsRepo.getSettingsOnce()
            val transaction = transactionParser.parse(bigText, settings) ?: return@launch

            val withText = transaction.copy(rawNotificationText = bigText)
            database.appDao().insertTransaction(withText.toEntity())

            // Auto-update the budget's spent amount
            val budget = database.appDao().getBudgetOnce(transaction.category.name)
            if (budget != null) {
                database.appDao().upsertBudget(
                    budget.copy(spent = budget.spent + transaction.amount)
                )
            }

            Log.d("PIPELINE", "✅ Saved: ${transaction.merchant} \$${transaction.amount} → ${transaction.category}")
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
```

### Step 2 — Service Lifecycle: Survive Battery Saver
```kotlin
// WakeLock setup in the service class:
private val wakeLock by lazy {
    (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZeroBudget::QwenInference")
}

// On Android 13+, if the service is killed, it restarts automatically IF
// the user hasn't manually revoked Notification Access.
// Add this to make re-init cleaner:
override fun onListenerConnected() {
    super.onListenerConnected()
    Log.d("PIPELINE", "NotificationListener connected")
    // Re-initialize Qwen engine if needed
}
```

### Step 3 — Full Pipeline Test ✅ MILESTONE
1. Fire ADB notification:
```bash
adb shell cmd notification post -S bigtext -t "America First CU" "Tag1" \
  "You made a \$67.50 purchase at Smith's Grocery on your Visa ending in 1234"
```
2. Wait ~3 seconds for Qwen inference
3. Check Logcat for: `✅ Saved: Smith's Grocery $67.50 → GROCERIES`
4. Open Android Studio → App Inspection → Database Inspector → verify row in `transactions` table
5. Eli will confirm his UI updated automatically (Room Flow emission)
- ✅ **This is the "Aha!" moment. The whole app is alive.**

---

## 🔵 March 30–31 — RealRepository Handoff + Polish

### Step 1 — Push RealRepository to Git
Complete `repository/RealRepository.kt`:
```kotlin
class RealRepository(
    private val db: AppDatabase,
    private val dataStore: DataStore<Preferences>,
    private val modelManager: ModelDownloadManager
) : AppRepository {

    override fun getAllTransactions() =
        db.appDao().getAllTransactions().map { it.map(TransactionEntity::toDomain) }

    override fun getModelStatus(): Flow<ModelStatus> = modelManager.status

    override suspend fun exportToCsv(): String = buildString {
        appendLine("Date,Merchant,Amount,Category,AI Parsed,Confidence")
        db.appDao().getAllTransactionsOnce().forEach { t ->
            appendLine("${Date(t.date)},\"${t.merchant}\",${t.amount},${t.category},${t.isAiParsed == 1},${t.confidence}")
        }
    }

    override suspend fun wipeAllData() {
        db.clearAllTables()
        dataStore.edit { it.clear() }
    }

    // ... implement all other AppRepository methods
}
```

### Step 2 — Integration with Eli
- Push to Git, message Eli
- **Do NOT touch his files.** He swaps `FakeRepository` → `RealRepository` himself
- If his UI breaks, it means the contract was violated — fix together, usually < 30 min

### Step 3 — Write the ADB Demo Script
Create `scripts/demo_trigger.sh` in the repo root:
```bash
#!/bin/bash
# Run from your laptop: bash scripts/demo_trigger.sh
# Triggers a realistic bank notification on the connected Android device

echo "Firing demo notification..."
adb shell cmd notification post -S bigtext \
  -t "America First CU" \
  "DemoTag" \
  "You made a purchase of \$34.99 at Chipotle Mexican Grill on your Visa ending 4242. Available balance: \$1,204.12"

echo "Done. Qwen should parse this in ~3 seconds."
```
Practice running this during the live demo so it's smooth on April 4th.

### Step 4 — Edge Cases
Handle these before April 1:
- Qwen not initialized yet → queue raw text, process after `engine.initialize()` completes
- `is_transaction = false` → silently discard, log at DEBUG level only (don't spam)
- Duplicate notification (same text within 5 seconds) → deduplicate by hash of rawText
- Battery saver killed the service → `onListenerConnected()` reinitializes Qwen

---

## 🏁 April 1–2 — Polish & Demo Prep

### Step 1 — Performance Audit
Run Qwen on the exact device you'll demo on:
- Target: inference < 4 seconds from notification received to DB insert
- If > 6 seconds: switch to `Qwen2.5-0.5B-Instruct-q4f16_1-MLC` (faster, ~400 MB)
- Check battery drain over 30 minutes of idle — it should be minimal since Qwen only runs on notification events

### Step 2 — Devpost Write-Up (Your Section)
Write the **Technical Complexity** section:
- MLC LLM runtime with Qwen 2.5 1.5B-Instruct — on-device, no internet after model load
- JSON schema-constrained generation — grammar sampler guarantees valid structured output
- SQLCipher AES-256 encrypted local database
- Android NotificationListenerService — zero-permission bank access, no Plaid, no OAuth
- End-to-end latency: notification received → chart updated in < 5 seconds

### Step 3 — README.md
Write `README.md` for the public GitHub repo:
- What the app does (1 paragraph)
- Architecture diagram (ASCII or a simple image)
- How to build it (Android Studio steps)
- How to grant notification access
- Model download instructions

---

## 📋 Noah's Daily Checklist

| Day | Date | Deliverable |
|-----|------|-------------|
| 1 | Mar 22 | Contracts approved ✅, Room DB compiles, manifest updated, RealRepository skeleton builds |
| 2-3 | Mar 23-24 | NotificationListener intercepts real notifications (Logcat proof) |
| 4 | Mar 25 | MLC LLM initialized, Qwen model loaded, engine.isReady() == true |
| 5 | Mar 26 | JSON schema defined, TransactionParser written |
| 6 | Mar 27 | AI isolation test passes 5x in a row ✅ |
| 7-8 | Mar 28-29 | Full pipeline: notification → DB insert → confirmed in DB Inspector ✅ |
| 9 | Mar 30 | RealRepository pushed to Git, Eli confirms swap works |
| 10 | Mar 31 | Edge cases handled, ADB demo script written and tested |
| 11 | Apr 1 | Performance verified on demo device, README drafted |
| 12 | Apr 2 | Demo video support, Devpost technical section written |
| 13 | Apr 3 | SUBMIT by 11:59 PM ✅ |

---

## 🚨 Anti-Bottleneck Rules
- **Eli never waits on you.** FakeRepository covers him. Your job is to deliver `RealRepository.kt` on March 30, not before.
- **Test each piece in isolation.** NotificationListener proven → AI parser proven → pipeline wired. Never combine unproven pieces.
- **JSON schema is the contract with Qwen.** If you change it, update `TransactionSchema.kt` and tell Eli immediately (the `Transaction` domain model may need to change too).
- **Git branches:** `feature/database`, `feature/notification-service`, `feature/qwen-engine`, `feature/pipeline`. Merge daily to `main`.
- **Keep a debug trigger button.** A single-tap button that fires a hardcoded string through `TransactionParser` is your fastest debugging tool. Keep it in a `DebugScreen.kt` throughout development.
- **The grammar sampler is your superpower.** When demoing, explicitly mention that Qwen is constrained by a JSON schema — judges will recognize this as real ML engineering, not just prompt engineering.