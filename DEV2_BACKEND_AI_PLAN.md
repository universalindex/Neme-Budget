# ⚙️ NOAH — Backend, AI & Data Pipeline Lead
### Zero-Cloud AI Budgeter | Weber State Hackathon | Due: April 3, 2026 @ 11:59 PM

> **Deferred Work Queue (Do Later):**
> 1) [ ] Replace test notification tray icon (`setSmallIcon`) with a branded monochrome notification icon.
> 2) [ ] Investigate 16 KB page-size compatibility warning for `libsqlcipher.so` before Play submission.
> 3) [ ] Reduce frame drops during shader warmup and batch notification processing.
> 4) [ ] Replace indeterminate processing spinner with per-notification determinate progress bar.
> 5) [ ] Tune batch throughput (start with size 10) and verify transaction history/count updates correctly.
> 6) [ ] Reintroduce `isAiParsed` UI indicator later as a subtle star marker (not in the cramped action row).

> **New Follow-Up Queue (Added 2026-04-01):**
> 1) [ ] **Dashboard month swipe navigation**
>    - Add horizontal swipe gesture to change month in dashboard (parity with arrow buttons).
>    - Acceptance: swipe left/right updates month label + data consistently with button behavior.
> 2) [/] **Better budget editing UX**
>    - Improve budget limit editing flow (discoverability, validation, and save feedback).
>    - Acceptance: user can edit category budget limits without confusing state and with clear confirmation.
>    - Status update (2026-04-02): inline tap-to-edit limit + validation + save feedback shipped; persistent category limit overrides now stored in app settings for both real/fake repositories.
>    - Status update (2026-04-02, phase 2): custom budget category creation shipped (label + emoji + initial limit) and long-press label/icon editor shipped for both built-in and custom categories.
>    - Status update (2026-04-02, phase 3): category labels now enforce a 50-character cap and allowed-character filtering so custom categories stay list-friendly.
>    - Remaining for full completion: optional polish for icon picker UX and propagation of custom category aliases into future LLM validation/rule-mapping flows.
> 3) [ ] **Error resolver UX redesign + missing editing features**
>    - Replace inline sheet trigger with compact banner/row: `Errors detected - click here to address`.
>    - Open dedicated error-resolution screen (not the same swipe-up creator style as transaction add).
>    - Ensure editing includes practical correction fields (merchant/value/category) and source notification context.
>    - Acceptance: when errors exist, user can enter resolver screen, review raw context, edit correction fields, and resolve/delete.
> 4) [ ] **Tune add-transaction FAB sizing**
>    - Current plus bubble is oversized; reduce to a configurable size constant for quick UX tuning.
>    - Acceptance: final FAB size is visually balanced and easy to tap.
> 5) [ ] **Clearer dashboard safe-to-spend**
>    - Improve dashboard communication for safe-to-spend, including per-category clarity (not only global number).
>    - Acceptance: user can understand both overall safe-to-spend and category-level spending context at a glance.

---

## Your Role
You own the three invisible pillars of the app: the **notification interceptor**, the **local Qwen AI engine**,
and the **encrypted database**. Eli builds the entire UI against a FakeRepository while you build
the real one — no waiting, no blocking. When you push `RealRepository.kt`, he swaps it in with one line.

**Architecture update (hybrid, resilient):**
- Listener path is ingest-only: notification -> encrypted `raw_notifications` table.
- AI path is batch-only: unprocessed rows -> Qwen parse -> `transactions`/`budgets` updates.
- Processing triggers: app open, hourly `WorkManager`, and a manual "Process Now" action for demo control.

**AI choice: Qwen 2.5 1.5B-Instruct via MLC LLM.**
MLC LLM is the best Android runtime for Qwen because it ships prebuilt AAR packages with
hardware-accelerated inference AND natively supports **JSON schema-constrained generation** —
meaning the model is physically forced by the grammar sampler to output valid JSON.
No prompt hacks. No JSON cleanup. Zero malformed output.

---

## [/] 🔴 Day 1 (March 22) — Contracts & Project Setup
> **Goal:** Database schema compiles, shared contracts committed, service skeleton registered in manifest.

### [/] Step 1 — Add Your Dependencies to `build.gradle.kts`
```kotlin
// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// SQLCipher (encrypted DB)
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")

// [ ] DataStore (settings)
// implementation("androidx.datastore:datastore-preferences:1.0.0")

// [x] MLC LLM (local Qwen inference) - via libs/mlc4j.aar
// implementation("ai.mlc:mlc4j:0.1.1")

// [x] Coroutines
// implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// [ ] OkHttp (model download)
// implementation("com.squareup.okhttp3:okhttp:4.12.0")

// [ ] Background batch scheduling
// implementation("androidx.work:work-runtime-ktx:2.10.1")
```

Also add the MLC Maven repo to `settings.gradle.kts`:
```kotlin
maven { url = uri("https://repo.mlc.ai/repository/maven-public/") }
```

### [x] Step 2 — Agree on and Commit Shared Contracts
`model/SharedModels.kt` is written by Eli. You review and approve.
The key types: `Transaction`, `Category`, `Budget`, `AppSettings`, `ModelStatus`, `AppRepository`.
**Do not write a single line of logic until this file is committed and both of you have approved it.**

### [/] Step 3 — Define Room Entities
Create `database/Entities.kt`:
```kotlin
// [ ] TransactionEntity pending
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val merchant: String,
    val amount: Double,
    @ColumnInfo(name = "category") val category: String,
    val date: Long,
    val isAiParsed: Int,
    val confidence: Float,
    val rawNotificationText: String
)

// [ ] BudgetEntity pending
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryName: String,
    val spent: Double,
    val limit: Double
)

// [x] RawNotification implemented
@Entity(tableName = "raw_notifications")
data class RawNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val processed: Int = 0,
    val processAttempts: Int = 0,
    val lastError: String? = null
)
```

### [/] Step 4 — Define Room DAOs
Create `database/AppDao.kt`:
```kotlin
// [x] RawNotificationDao implemented (as RawNotificationDao.kt)
// [ ] AppDao for transactions/budgets pending
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawNotification(n: RawNotificationEntity)

    @Query("SELECT * FROM raw_notifications WHERE processed = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnprocessedRawNotifications(limit: Int): List<RawNotificationEntity>

    @Query("UPDATE raw_notifications SET processed = 1, lastError = NULL WHERE id = :id")
    suspend fun markRawProcessed(id: Int)

    @Query("UPDATE raw_notifications SET processAttempts = processAttempts + 1, lastError = :error WHERE id = :id")
    suspend fun markRawFailed(id: Int, error: String)

    @Query("SELECT COUNT(*) FROM raw_notifications WHERE processed = 0")
    fun getUnprocessedRawCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>
}
```

### [x] Step 5 — Set Up the Encrypted Database
Create `database/AppDatabase.kt`:
```kotlin
// [x] Implemented as AppDatabase.kt using SQLCipher
@Database(
    entities = [TransactionEntity::class, BudgetEntity::class, RawNotificationEntity::class],
    version = 1
)
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

### [x] Step 6 — Register the Notification Service in AndroidManifest.xml
Do this now so the app structure is correct from Day 1:
```xml
<!-- [x] Registered as .notifications.BankNotificationListenerService -->
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

### [ ] Step 7 — Implement RealRepository Skeleton
Create `repository/RealRepository.kt` implementing `AppRepository`.
Write all the mapper functions today (`TransactionEntity → Transaction` and back).
Leave the `getModelStatus()` flow returning a hardcoded "not downloaded" for now — you'll fill it in on Day 4.
**This skeleton must compile clean by end of Day 1.**

---

## [x] 🟠 Days 2–3 (March 23–24) — Notification Ingestion Service
> **Goal:** The app reliably captures notifications into encrypted storage. No AI in service path.

### [x] Step 1 — Create the Listener Service (Ingest-Only)
Create `notification/BudgetNotificationListener.kt`:
```kotlin
// [x] Implemented as BankNotificationListenerService.kt
class BudgetNotificationListener : NotificationListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        coroutineScope.launch {
            val settings = settingsRepo.getSettingsOnce()
            if (sbn.packageName in settings.ignoredApps) {
                Log.d("NOTIF_FILTER", "Skipped: ${sbn.packageName} (user excluded)")
                return@launch
            }

            appDao.insertRawNotification(
                RawNotificationEntity(
                    packageName = sbn.packageName,
                    title = title,
                    text = bigText,
                    timestamp = sbn.postTime
                )
            )
            Log.d("NOTIF_INGEST", "Saved raw notification: pkg=${sbn.packageName}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
```

### [ ] Step 2 — Add Duplicate Guard (Fast, Cheap)
```kotlin
// [ ] Pending (using keyword filtering for now)
// Optional but recommended inside listener write path
val fingerprint = sha256("${sbn.packageName}|$title|$bigText|${sbn.postTime / 5000}")
if (appDao.existsRecentRawFingerprint(fingerprint)) return
```

### [x] Step 3 — Test the Ingestion Service ✅ MILESTONE
1. Install on a real Android device.
2. **Settings -> Apps -> Special App Access -> Notification Access** -> enable your app.
3. Fire a test notification via ADB:
```bash
adb shell cmd notification post -S bigtext -t "Chase Bank" "TestTag" \
  "A charge of $23.50 at Chipotle was made on your card ending in 4242"
```
4. [x] Check Logcat filter `NOTIF_INGEST`.
5. [x] Verify row appears in `raw_notifications` table with `processed = 0`.
6. ✅ Do not wire listener to Qwen directly.

---

## [x] 🟡 Days 4–6 (March 25–27) — Qwen 2.5 Local Inference + JSON-Locked Output
> **Goal:** A notification text string goes in. A perfectly structured `Transaction` object comes out.
> Guaranteed. Every time. Because the grammar sampler makes invalid JSON physically impossible.

### [x] Step 1 — Get the Qwen Model
Qwen 2.5 1.5B-Instruct is prebuilt for MLC at HuggingFace:
1. Go to: `https://huggingface.co/mlc-ai/Qwen2.5-1.5B-Instruct-q4f16_1-MLC`
2. Download the model files (`.bin` shards + `mlc-chat-config.json` + `tokenizer` files)
3. Place everything in `app/src/main/assets/qwen2.5-1.5b/`
4. Total size: ~1.1 GB. This ships inside the APK for the hackathon.
   (For production you'd use `ModelDownloadManager` — wire that up on Day 5 if APK size is a problem)

> **If 1.1 GB APK is rejected by the test device:** Switch to `Qwen2.5-0.5B-Instruct-q4f16_1-MLC` (~400 MB).
> Accuracy is slightly lower but still excellent for transaction parsing.

### [x] Step 2 — Initialize MLC LLM Engine
Create `ai/QwenEngine.kt`:
```kotlin
// [x] Implemented as LlmPipeline.kt
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

### [x] Step 3 — Define the JSON Schema (THE KEY STEP)
This is where Qwen gets locked to valid JSON output.
The grammar sampler in MLC LLM reads this schema and constrains every token it generates.
It is **physically impossible** for the model to produce output that doesn't match this schema.

Create `ai/TransactionSchema.kt`:
```kotlin
// [x] Implemented in LlmPipeline.kt
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

### [x] Step 4 — Write the Transaction Parser
Create `ai/TransactionParser.kt`:
```kotlin
// [x] Implemented in LlmPipeline.kt with verification loop
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

### [x] Step 5 — Model Download Manager (for Onboarding)
Create `ai/ModelDownloadManager.kt`:
```kotlin
// [x] Implemented in SharedModels and FakeRepository (via ModelStatus)
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

### [x] Step 6 — AI Isolation Test ✅ MILESTONE
Create a temporary `TestActivity` or use a Logcat-only approach:
```kotlin
// [x] Implemented as LlmTestingScreen.kt in MainActivity
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

## [x] 🟢 Days 7–8 (March 28–29) — Batch Processing Pipeline
> **Goal:** Raw notifications are processed in controlled batches into transactions and budget updates.

### [x] Step 1 — Build `NotificationProcessor`
Create `pipeline/NotificationProcessor.kt`:
```kotlin
// [x] Implemented as NotificationBatchProcessor.kt
class NotificationProcessor(
    private val appDao: AppDao,
    private val parser: TransactionParser,
    private val settingsRepo: SettingsRepository
) {
    suspend fun processPending(limit: Int = 50) {
        val pending = appDao.getUnprocessedRawNotifications(limit)
        if (pending.isEmpty()) return

        val settings = settingsRepo.getSettingsOnce()
        pending.forEach { raw ->
            try {
                val parsed = parser.parse(raw.text, settings)
                if (parsed != null) {
                    val txn = parsed.copy(rawNotificationText = raw.text)
                    appDao.insertTransaction(txn.toEntity())
                    appDao.incrementBudgetSpent(txn.category.name, txn.amount)
                }
                appDao.markRawProcessed(raw.id)
            } catch (t: Throwable) {
                appDao.markRawFailed(raw.id, t.message ?: "unknown")
            }
        }
    }
}
```

### [/] Step 2 — Trigger Batch Processing in 3 Places
```kotlin
// [x] 1) App open (fast foreground catch-up) - implemented in MainActivity via SettingsViewModel
// viewModelScope.launch { notificationProcessor.processPending(limit = 100) }

// [ ] 2) Hourly background sweep - pending WorkManager registration
// val request = PeriodicWorkRequestBuilder<NotificationProcessWorker>(1, TimeUnit.HOURS).build()
// WorkManager.getInstance(context).enqueueUniquePeriodicWork(
//    "notif-batch-hourly",
//    ExistingPeriodicWorkPolicy.UPDATE,
//    request
// )

// [x] 3) Manual trigger (Settings "Process Now" button) - implemented in SettingsScreen
// viewModelScope.launch { notificationProcessor.processPending(limit = 200) }
```

### [x] Step 3 — Full Hybrid Test ✅ MILESTONE
1. Fire 2-3 ADB notifications.
2. [x] Confirm `raw_notifications` rows appear first.
3. [x] Trigger processing (open app or tap **Process Now**).
4. [x] Watch logs: `BATCH_PROCESS start/end` and per-row success/failure.
5. [x] Verify `transactions` rows, updated budgets, and `raw_notifications.processed = 1`.
6. ✅ This demonstrates durability + controllable AI processing.

---

## [ ] 🔵 March 30–31 — RealRepository Handoff + Polish

### [ ] Step 1 — Push RealRepository to Git
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

### [ ] Step 2 — Integration with Eli
- Push to Git, message Eli
- **Do NOT touch his files.** He swaps `FakeRepository` → `RealRepository` himself
- If his UI breaks, it means the contract was violated — fix together, usually < 30 min

### [ ] Step 3 — Write the ADB Demo Script
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

### [ ] Step 4 — Edge Cases
Handle these before April 1:
- Qwen not initialized -> leave rows unprocessed and retry next batch.
- `is_transaction = false` -> mark raw row processed without creating a transaction.
- Duplicate notification bursts -> dedupe using hash/fingerprint before insert.
- Worker throttled by battery saver -> app-open/manual triggers still guarantee catch-up.

---

## [ ] 🏁 April 1–2 — Polish & Demo Prep

### [ ] Step 1 — Performance Audit
Run Qwen on the exact device you'll demo on:
- Target ingestion latency: < 100ms from notification post to encrypted raw row.
- Target batch latency: < 5s for a 1-3 notification demo batch.
- If inference is slow, switch to `Qwen2.5-0.5B-Instruct-q4f16_1-MLC`.
- Check battery drain over 30 minutes of idle: listener should stay lightweight because AI is off service path.

### [ ] Step 2 — Devpost Write-Up (Your Section)
Write the **Technical Complexity** section:
- MLC LLM runtime with Qwen 2.5 1.5B-Instruct — on-device, no internet after model load
- JSON schema-constrained generation — grammar sampler guarantees valid structured output
- SQLCipher AES-256 encrypted local database
- Android NotificationListenerService — zero-permission bank access, no Plaid, no OAuth
- End-to-end latency: notification received → chart updated in < 5 seconds

### [ ] Step 3 — README.md
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
| 1 | Mar 22 | Contracts approved ✅, Room DB compiles ✅, manifest updated ✅, RealRepository skeleton builds |
| 2-3 | Mar 23-24 | NotificationListener saves raw notifications to encrypted DB ✅ |
| 4 | Mar 25 | MLC LLM initialized ✅, Qwen model loaded ✅ |
| 5 | Mar 26 | JSON schema defined ✅, TransactionParser written ✅ |
| 6 | Mar 27 | AI isolation test passes 5x in a row ✅ |
| 7-8 | Mar 28-29 | Hybrid pipeline: raw ingest -> batch process -> transaction/budget updates ✅ |
| 9 | Mar 30 | RealRepository pushed to Git |
| 10 | Mar 31 | Edge cases handled, ADB demo script written and tested |
| 11 | Apr 1 | Performance verified on demo device, README drafted |
| 12 | Apr 2 | Demo video support, Devpost technical section written |
| 13 | Apr 3 | SUBMIT by 11:59 PM ✅ |

---

## 🚨 Anti-Bottleneck Rules
- **Eli never waits on you.** FakeRepository covers him. Your job is to deliver `RealRepository.kt` on March 30, not before.
- **Test each piece in isolation.** Notification ingest proven -> AI parser proven -> batch processor proven. Never combine unproven pieces.
- **JSON schema is the contract with Qwen.** If you change it, update `TransactionSchema.kt` and tell Eli immediately (the `Transaction` domain model may need to change too).
- **Git branches:** `feature/database`, `feature/notification-service`, `feature/qwen-engine`, `feature/pipeline`. Merge daily to `main`.
- **Keep a debug trigger button.** A single-tap button that runs `NotificationProcessor.processPending()` is your fastest debugging tool.
- **The grammar sampler is your superpower.** When demoing, explicitly mention that Qwen is constrained by a JSON schema — judges will recognize this as real ML engineering, not just prompt engineering.
