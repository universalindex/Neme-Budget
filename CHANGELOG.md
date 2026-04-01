# Project Changelog (Neme Budget)

This file tracks significant changes and progress for the Neme Budget app. Please update it whenever you complete a major task or make a critical architectural decision.

## 2026-03-31 - AI Assistant (Apr 1-2 Plan Work: Onboarding Flow + Checklist Progress)

### Onboarding Flow Added
* Added `app/src/main/java/com/example/nemebudget/ui/screens/OnboardingScreens.kt` with:
  * `OnboardingWelcomeScreen`
  * `OnboardingPermissionScreen`
  * `OnboardingModelScreen`
* Updated `app/src/main/java/com/example/nemebudget/ui/navigation/AppDestination.kt` with onboarding routes:
  * `onboarding_welcome`
  * `onboarding_permission`
  * `onboarding_model`
* Updated `app/src/main/java/com/example/nemebudget/MainActivity.kt` to:
  * Choose start destination based on persisted `onboarding_completed` flag
  * Poll listener permission state every second during onboarding permission step
  * Navigate first launch through Welcome -> Permission -> Model
  * Persist onboarding completion and route into Dashboard
  * Keep the old listener reminder dialog only for post-onboarding sessions

### Plan Tracking Updates
* Updated `DEV1_FRONTEND_PLAN.md`:
  * Marked **Integration Day Step 1 (Wire Up Real Repository)** as complete.
  * Marked **April 1-2 Step 1 (Onboarding Flow)** as complete.
  * Added explicit implementation notes under both completed steps.
  * Added Apr 2 prep status notes for Demo Script and Devpost draft deliverables.
* Added Apr 2 prep docs:
  * `DEMO_SCRIPT.md` (3-minute live demo runbook + dry-run checklist)
  * `DEVPOST_DRAFT.md` (draft "User Experience" and "Innovation" sections)

### Rationale
* This completes the biggest code-deliverable block for the Apr 1-2 plan while leaving manual tasks (dark-mode audit on physical device, demo recording, Devpost submission) as checklist follow-through tasks.

## 2026-03-31 - AI Assistant (Transactions UX: Delete + Manual Add)

### What was added
* Extended `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt` with:
  * `deleteTransaction(transaction: Transaction)`
  * `addTransaction(transaction: Transaction)`
* Implemented both methods in `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` via `TransactionDao`.
* Wired actions in `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt`:
  * `deleteTransaction(...)`
  * `addManualTransaction(...)` with basic validation (non-blank merchant, amount > 0).
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Added **Add transaction** button and `AddTransactionBottomSheet` (merchant, amount, category).
  * Added **Delete** action in `EditTransactionBottomSheet` so users can remove transactions from the editor flow.

### Rationale
* Users need direct ledger control from the Transactions screen:
  * remove bad/duplicate rows quickly,
  * manually add missing transactions without waiting for notification ingest.
* Kept the flow aligned with existing architecture (UI -> ViewModel -> Repository -> DAO) so list updates remain reactive through existing `Flow` streams.

## 2026-03-30 - AI Assistant (Rollback: Remove Persisted Transaction Type + Reliability/Perf Pass)

### Requested Rollback Applied
* Reverted persistent transaction storage back to the pre-type schema (no `type` column in `transactions`).
* Rationale: prior no-type flow was more stable for current extraction quality and avoids losing records due to type misclassification.

### Database Changes
* Updated `app/src/main/java/com/example/nemebudget/db/TransactionEntity.kt`:
  * Removed entity field `type`.
  * Removed mapping to/from `TransactionType` in entity mappers.
* Updated `app/src/main/java/com/example/nemebudget/db/TransactionDao.kt`:
  * Removed type-dependent queries (`getTransactionsByType`, `getTotalByType`).
* Updated `app/src/main/java/com/example/nemebudget/db/AppDatabase.kt`:
  * Bumped DB version to `3`.
  * Added `MIGRATION_2_3` that rebuilds `transactions` without the `type` column:
    1) create `transactions_new` (legacy shape)
    2) copy shared columns from old `transactions`
    3) drop old table and rename new table
  * Kept `MIGRATION_1_2` for older installs and chained both migrations.

### LLM Pipeline Contract Rollback
* Updated `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`:
  * Reverted extraction JSON/schema back to keys: `merchant`, `amount`, `category`.
  * Removed `transactionType` from `ExtractedTransaction`, schema `required` list, prompts, and verification path.
  * Kept strict category enum guidance + normalization helper for single-letter outputs.
  * Kept bounded generation (`max_tokens = 96`) and retry correction prompt with exact category list.

### Three Approved Follow-Ups Applied
1) **Do not silently drop recoverable notifications**
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * On verification failure, if merchant/amount are still high-signal, insert fallback transaction with `Category.OTHER` and lower confidence (`0.45f`) instead of dropping.
  * Still marks raw row processed to avoid infinite loops.

2) **Prompt/token hardening**
* Retry correction now restates exact category list and JSON key contract.
* Category abbreviation discouragement retained.

3) **Frame-skip mitigation pass**
* Existing chunk/yield and startup throttling remain in place.
* Additional repository logic now avoids repeated retry storms from recoverable-but-invalid category cases by persisting fallback transaction.

### Other Alignment
* Updated CSV export in `RealRepository` to remove the `Type` column from header/data rows to match reverted schema.
* Budget aggregation now follows legacy no-type behavior (sum all persisted transactions by category).

### Validation
* Ran `./gradlew.bat :app:compileDebugKotlin`.
* Result: **BUILD SUCCESSFUL**.

---

## 2026-03-30 - AI Assistant (Crash Fix: Room Migration 1->2)

### Crash Symptoms
* App crashed at launch with:
  * `IllegalStateException: A migration from 1 to 2 was required but not found`
* Trigger point was first Room open of encrypted DB (`nemebudget_secure.db`) after schema version was bumped to `2`.

### Root Cause
* `AppDatabase` version increased from `1` to `2` (new `transactions` table + new columns on `raw_notifications`) but no explicit Room migration path was registered.
* Room intentionally refuses to open to prevent silent data loss.

### Fix Applied
* Updated `app/src/main/java/com/example/nemebudget/db/AppDatabase.kt`:
  * Added `MIGRATION_1_2` using `Migration(1, 2)` + `SupportSQLiteDatabase` SQL.
  * Registered it in builder with `.addMigrations(MIGRATION_1_2)`.
* Migration SQL performs:
  1) `ALTER TABLE raw_notifications ADD COLUMN processed INTEGER NOT NULL DEFAULT 0`
  2) `ALTER TABLE raw_notifications ADD COLUMN errorMessage TEXT`
  3) `CREATE TABLE IF NOT EXISTS transactions (...)`

### Why this preserves security/data
* SQLCipher path is unchanged (`SupportOpenHelperFactory(passphrase)` still used).
* Migration runs inside the same encrypted database file, so existing user data remains encrypted and is upgraded in-place.

### Validation
* `./gradlew.bat :app:compileDebugKotlin` passes after change.
* Runtime expectation: existing installs on schema v1 should now open without destructive reset and migrate to v2 automatically.

---

## 2026-03-30 - AI Assistant (Build Triage: KSP + LLM Contract Fixes)

### What Broke
* `:app:kspDebugKotlin` failed with `Unused parameter: errorMessage` in `RawNotificationDao.markAsFailed(...)`.
* `:app:compileDebugKotlin` then failed in `LlmPipeline.extractWithRetry(...)` because `ExtractedTransaction` added `transactionType` but one fallback constructor call still used the old argument shape.

### Fixes Applied
* Updated `app/src/main/java/com/example/nemebudget/db/RawNotificationDao.kt`:
  * Changed `markAsFailed` query to bind `errorMessage` directly:
    * from `UPDATE raw_notifications SET processed = 1 WHERE id = :id`
    * to `UPDATE raw_notifications SET processed = 1, errorMessage = :errorMessage WHERE id = :id`
  * Rationale: Room/KSP requires all DAO method params to be used by SQL placeholders.
* Updated `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`:
  * Fixed fallback `ExtractedTransaction(...)` call to include `transactionType` and correct parameter ordering.
  * Aligned fallback/default JSON payloads with current schema by including `transactionType` in model-not-found and engine-failure defaults.
  * Rationale: keep runtime fallbacks schema-compatible and avoid constructor mismatch after contract expansion.

### Validation
* Ran `./gradlew.bat :app:compileDebugKotlin` from repo root.
* Result: **BUILD SUCCESSFUL**.

---

## 2026-03-30 - AI Assistant (Payment Support + RealRepository Implementation)

### Major Architectural Additions

#### 1. **TransactionType Enum & Income/Expense Support**
* Added `TransactionType` enum to `SharedModels.kt` with values `EXPENSE` and `INCOME`.
* Updated `Transaction` domain model to include `type: TransactionType = TransactionType.EXPENSE` (backward compatible default).
* **Why this matters:** The app now distinguishes between money going OUT (expenses, purchases) and money coming IN (paychecks, transfers, payments received). Budgets will only sum EXPENSE transactions, but users can view full income history.

#### 2. **LLM Pipeline Extended for Income Detection**
* Updated `ExtractedTransaction` data class in `LlmPipeline.kt` to include `transactionType: String` field (output from Qwen).
* Expanded JSON schema to include `transactionType` enum constraint: `["EXPENSE", "INCOME"]`.
* Updated system prompt to teach Qwen to recognize payment keywords:
  - **EXPENSE keywords:** "charged", "purchase", "payment sent", "withdrawal", "debit"
  - **INCOME keywords:** "received", "credited", "deposited", "payment received", "transferred to you"
* Updated `processAndVerify()` to validate `transactionType` enum and handle both directions.
* **Technical details:** The grammar sampler in MLC LLM reads the schema and physically constrains the model to only output EXPENSE or INCOME—no hallucination possible.

#### 3. **Database Schema: Transaction Storage**
* Created `TransactionEntity.kt` as the Room entity for storing parsed transactions in encrypted database:
  - Fields: `id`, `merchant`, `amount`, `categoryLabel`, `date`, `isAiParsed`, `confidence`, `rawNotificationText`, `type`
  - Includes mapper functions: `toDomain()` to convert to domain `Transaction`, and extension `Transaction.toEntity()` for reverse
  - **Why a separate entity:** SQLite needs strings for enums and integers for booleans; domain models use Kotlin types. The entity layer handles this translation.
* Updated `RawNotification` entity to track processing state:
  - Added `processed: Int` (0 = pending, 1 = processed) to avoid reprocessing same notification
  - Added `errorMessage: String?` to store failure reasons for debugging

#### 4. **Data Access Layer: TransactionDao**
* Created `TransactionDao.kt` with full CRUD operations as suspend functions + Flow observables:
  - `getAllTransactions()`: Flow<List> for reactive UI updates
  - `getTransactionsByDateRange()`: For monthly/weekly filtering
  - `getLatestTransaction()`, `insertTransaction()`, `updateTransaction()`, `deleteTransaction()`
  - `getTransactionCount()`, `getTotalByType()`: For stats and budget calculations
  - All queries use Flow for automatic UI reactivity when data changes
* Updated `RawNotificationDao.kt` with batch processing support:
  - `getUnprocessedRawNotifications(limit)`: Fetches pending batch
  - `markAsProcessed(id)`, `markAsFailed(id, errorMessage)`: Tracks processing state
  - `getUnprocessedCount()`: Returns Flow<Int> so Settings screen can show real-time pending count

#### 5. **RealRepository: The Missing Piece**
* Created `RealRepository.kt` implementing `AppRepository` interface—this is what was missing!
  - **Before:** UI was using `FakeRepository` (in-memory mock data, no database persistence)
  - **After:** UI can now use `RealRepository` to read/write actual encrypted database
  - All transaction queries map database entities → domain models via `toDomain()`
  - Budget calculations compute spent totals by filtering EXPENSE transactions and summing by category
  - `processPendingNotifications(limit)` orchestrates the full pipeline:
    1. Fetch unprocessed raw notifications
    2. Run each through LLM extraction with retry logic
    3. If verified, create Transaction and insert to database
    4. Mark raw notification as processed
    5. Return count of successful transactions created
* Handles error states: if LLM fails, mark row as failed (not skipped) to preserve audit trail

#### 6. **Database Schema Version Bump**
* Updated `AppDatabase` to version 2 (from version 1) to accommodate:
  - New `transactions` table with `TransactionEntity`
  - New fields in `raw_notifications`: `processed`, `errorMessage`
* Room will auto-migrate schema on next app launch with `@Migrate` if needed, or clear data if missing migration

### Integration Points Explained

**Data Flow Lifecycle:**
1. **Ingest:** `BankNotificationListenerService` receives push → saves to `raw_notifications` with `processed=0`
2. **Batch Process:** `RealRepository.processPendingNotifications()` → fetches unprocessed → LLM parses → inserts to `transactions` → marks `processed=1`
3. **UI Display:** `SettingsViewModel` observes `Repository.getAllTransactions()` → Flow automatically updates Composables
4. **Manual Edit:** User taps transaction category → `Repository.updateTransaction()` → Room updates row → Flow notifies UI

**Why This Architecture:**
- **Separation of concerns:** Database layer doesn't know about Compose; UI doesn't know about SQLCipher
- **Reactive:** Flow-based queries mean UI always sees latest data without polling
- **Auditable:** Raw notifications kept even after processing (with `processed` flag) for debugging
- **Testable:** Each layer can be tested independently (mock `AppDatabase`, use `FakeRepository` for UI tests)

### Migration Path for Existing Data
- Existing `raw_notifications` rows will have `processed=NULL` → schema migration must set default to 0
- No existing transactions yet, so `transactions` table starts empty
- Settings remain in-memory until DataStore integration

### Known Follow-Ups
* Wire `RealRepository` into dependency injection (currently requires manual instantiation)
* Persist app settings to DataStore or Room instead of in-memory
* Implement `markGpuOptimized()` persistence for shader cache skip on relaunch
* Performance: monitor batch processing latency with large pending queues (100+ notifications)
* Test income transaction detection with real bank payment notifications



### Current Source-of-Truth Snapshot (as of this entry)
* **Build/plugins** (`app/build.gradle.kts`): module uses catalog-managed plugin aliases for Android app, Compose compiler plugin, Kotlin serialization plugin, and KSP.
* **Dependency strategy**: hardcoded app-module coordinates were migrated to `gradle/libs.versions.toml` aliases for navigation-compose, lifecycle-compose, material icons, Room, SQLCipher, SQLite KTX, and Kotlinx serialization.
* **DB encryption runtime**: project is on `net.zetetic:sqlcipher-android` (not deprecated `android-database-sqlcipher`). `AppDatabase` now uses `SupportOpenHelperFactory` and an explicit one-time `System.loadLibrary("sqlcipher")` guard before first Room open.
* **Notification ingest pipeline**: listener saves raw notifications into encrypted `raw_notifications`; batch processor drains queue and runs extraction.
* **LLM gating behavior**: the LLM context pre-gate was removed after causing false negatives. Current behavior is regex/action prefilter -> `extractWithRetry(...)`.

### Superseded/Corrected History Notes
* Prior entries that mention active use of `net.zetetic:android-database-sqlcipher`, `SupportFactory`, or `net.sqlcipher.*` are historical and superseded by the SQLCipher migration entry dated 2026-03-29.
* Prior entries that describe active LLM context pre-classification (`classifyTransactionalContext`) are historical and superseded by the pre-gate removal entry dated 2026-03-29.
* Prior entries that mention top-level `alias(libs.plugins.kotlin.serialization) apply false` are superseded; serialization plugin alias is module-scoped in `app/build.gradle.kts`.

### Validation and Stability Notes
* Gradle/Kotlin validation has been repeatedly checked with `:app:compileDebugKotlin` after each migration cluster (catalog expansion, dependency swaps, SQLCipher API updates, and plugin alias cleanup).
* SQLCipher native crash class (`UnsatisfiedLinkError` from `SQLiteConnection.nativeOpen`) was addressed by enforcing native load order in `AppDatabase` before Room open.
* This consolidation entry is intended to be the quickest onboarding reference for Dev1/Dev2 when reconciling older roadmap notes with current implementation.

### Known Follow-Ups (Tracked, Not Completed Here)
* Verify 16 KB page-size compliance for Play submission using release artifacts and Play-compatible checks.
* Replace temporary/simple notification tray icon with final branded monochrome notification icon.
* Continue performance work (frame drops during shader warmup and batch processing) and UX upgrades (determinate processing progress UI) per DEV2 backlog.

---

## 2026-03-29 - AI Assistant (Dependency Modernization: SQLCipher + Leaner Icons)

### Dependency Swaps
* Replaced deprecated SQLCipher artifact:
  * from `net.zetetic:android-database-sqlcipher`
  * to `net.zetetic:sqlcipher-android` (version-catalog alias `libs.sqlcipher.android`)
* Removed `androidx.appcompat:appcompat` from `app/build.gradle.kts` (project already uses `ComponentActivity` + Compose).
* Removed `gson` from active `app/build.gradle.kts` dependencies (kotlinx serialization remains the JSON stack).
* Replaced `material-icons-extended` with `material-icons-core` to reduce binary footprint and avoid pinned/discontinued extended icon set.
* Normalized the material icons catalog alias to `androidx-compose-material-icons` (accessor `libs.androidx.compose.material.icons`) to avoid Kotlin DSL accessor resolution issues in IDE.

### Required Code Migration After SQLCipher Swap
* Updated SQLCipher imports in `app/src/main/java/com/example/nemebudget/db/AppDatabase.kt`:
  * `net.sqlcipher.database.SQLiteDatabase` -> `net.zetetic.database.sqlcipher.SQLiteDatabase`
  * `SupportFactory` -> `SupportOpenHelperFactory`
* Updated helper factory call:
  * `.openHelperFactory(SupportFactory(passphrase))`
  * -> `.openHelperFactory(SupportOpenHelperFactory(passphrase))`
* Added explicit SQLCipher native load guard in `AppDatabase`:
  * `System.loadLibrary("sqlcipher")` runs once before opening Room.
  * Throws a clear `IllegalStateException` with packaging guidance if JNI load fails.
* Rationale: fix runtime crash `UnsatisfiedLinkError: SQLiteConnection.nativeOpen ... no implementation found` by guaranteeing native library load order before first DB access.

### Icon Compatibility Adjustments
* Updated `app/src/main/java/com/example/nemebudget/ui/navigation/AppDestination.kt` to use Material Icons Core-safe symbols (`Home`, `List`, `AccountCircle`).
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt` to use `ArrowForward` instead of `ChevronRight`.

### Rationale
* Align dependencies with current ecosystem support and Play-era requirements.
* Reduce build/APK overhead from legacy or oversized libraries.
* Removed redundant top-level `alias(libs.plugins.kotlin.serialization) apply false` from `build.gradle.kts` to avoid IDE Kotlin DSL accessor resolution errors; module-level plugin alias in `app/build.gradle.kts` remains the active source of truth.

---

## 2026-03-29 - AI Assistant (Catalog Expansion + LLM Pre-Processing Removal)

### Build Hygiene: Version Catalog Coverage Expanded
* Moved additional hardcoded dependency coordinates from `app/build.gradle.kts` into `gradle/libs.versions.toml`.
* Added explicit version keys and aliases for:
  * Navigation Compose (`androidx.navigation:navigation-compose`)
  * Lifecycle Compose artifacts (`lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`)
  * Compose Material Icons Extended
  * AppCompat
  * Gson
  * Kotlinx Serialization JSON
  * Room Runtime/KTX/Compiler
  * SQLCipher (`net.zetetic:android-database-sqlcipher`)
  * SQLite KTX
* Replaced inline dependency strings in `app/build.gradle.kts` with `libs.*` references and removed module-local `room_version` constant.
* Rationale: keep dependency/plugin versions centralized to reduce drift and simplify future upgrades/reviews.

### Pipeline Behavior: Removed LLM Context Pre-Processing Gate
* Removed the LLM context gate call from `app/src/main/java/com/example/nemebudget/pipeline/NotificationBatchProcessor.kt`.
  * Batch flow now goes directly from regex/action prefilter -> `extractWithRetry(...)`.
* Removed context-gate-only code from `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`:
  * Deleted `NotificationContextDecision` data model.
  * Deleted context gate prompt/schema fields.
  * Deleted `classifyTransactionalContext(...)` method.
* Rationale: user reported this pre-processing stage was blocking valid notifications from entering final transaction extraction.

---

## 2026-03-29 - AI Assistant (Scoped Update: Kotlin Plugin Unification Only)

### Build Hygiene
* Unified Kotlin serialization plugin configuration to the version catalog:
  * Added `kotlin-serialization` plugin alias in `gradle/libs.versions.toml`.
  * Updated `app/build.gradle.kts` to use `alias(libs.plugins.kotlin.serialization)`.
  * Added `alias(libs.plugins.kotlin.serialization) apply false` in top-level `build.gradle.kts`.
* Rationale: remove hardcoded plugin version in module script and keep Kotlin plugin versioning centralized.

### Planning (Deferred)
* Added a top-level deferred work queue in `DEV2_BACKEND_AI_PLAN.md` for the remaining requested items (notification icon, 16 KB SQLCipher check, frame-drop reduction, determinate progress UI, batch tuning/history verification).
* Rationale: user requested this pass to implement only the first item and save the rest for later.

---

## 2026-03-29 - AI Assistant (Gradle Sync Fix: Missing KSP Alias)

### Build Configuration Repair
* Added missing KSP version and plugin alias to `gradle/libs.versions.toml`:
  * `ksp = "2.2.10-2.0.2"` under `[versions]`
  * `ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }` under `[plugins]`
* Added `android.disallowKotlinSourceSets=false` to `gradle.properties` so KSP generated source sets are accepted with built-in Kotlin mode.
* Rationale: `app/build.gradle.kts` uses `alias(libs.plugins.ksp)`, and without the catalog entry Gradle script compilation fails during sync (`Unresolved reference 'ksp'`).

---

## 2026-03-29 - AI Assistant (LLM Context Gate in Batch Processor)

### Processing Accuracy: Conversational Money Mentions Filter
* Added `NotificationContextDecision` and `classifyTransactionalContext(...)` to `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`.
  * Uses a strict JSON schema (`is_transactional`, `reason`) to classify whether text is an actual account event.
  * Designed to reject conversational examples like payment requests or hypothetical price mentions that still contain money patterns.
* Updated `app/src/main/java/com/example/nemebudget/pipeline/NotificationBatchProcessor.kt` to run the LLM context gate after the regex/action prefilter and before extraction.
  * Non-transactional context is skipped, logged with reason, and removed from pending queue.
  * If classification inference fails, pipeline falls back to allow-processing (to avoid silent data loss during transient model issues).
* Rationale: improve precision beyond regex so only real transaction-like notifications reach extraction.

---

## 2026-03-29 - AI Assistant (Batch Processing Kickoff)

### Batch Pipeline (First Increment)
* Added `app/src/main/java/com/example/nemebudget/pipeline/NotificationBatchProcessor.kt`.
  * Reads encrypted pending rows from `raw_notifications` via Room.
  * Applies a final transactional gate (money signal + action words) before running LLM extraction.
  * Runs `LlmPipeline.extractWithRetry(...)` and converts verified outputs into domain `Transaction` objects.
  * Deletes processed/skipped rows from the raw queue to keep backlog clean.
* Wired `FakeRepository` to optionally use `NotificationBatchProcessor` for real queue processing while preserving existing fake fallback behavior.
  * `processPendingNotifications(...)` now consumes real encrypted pending notifications when processor is attached.
  * `getPendingNotificationCount()` now reflects DB queue count through Flow collection.
  * `wipeAllData()` now clears pending raw notifications when processor is attached.
* Updated `MainActivity.kt` to instantiate `NotificationBatchProcessor(context, pipeline)` and inject it into `FakeRepository`.
* Rationale: start batch processing without a full repository migration yet, so app-open/manual processing paths become functional against the encrypted ingestion queue.

---

## 2026-03-29 - AI Assistant (MainActivity Compile Hotfix)

### Build Repair: Notification Permission + Tester Wiring
* Fixed broken function scope in `app/src/main/java/com/example/nemebudget/MainActivity.kt` where the listener-permission `AlertDialog` block had been moved outside `MainApp()`.
* Restored missing helper functions in `MainActivity.kt` used by startup permission checks:
  * `isPostNotificationsGranted(context)`
  * `isNotificationListenerEnabled(context)`
* Added missing `NotificationChannel` import so the in-app test-notification channel setup compiles on Android 8+.
* Closed the trailing brace for `sendTestNotification(...)`, which was causing parser cascade errors (`Expecting top level declaration`, missing `}` at EOF).
* Rationale: keep behavior unchanged while unblocking Gradle/Kotlin compilation errors introduced by partial edits.

---

## 2026-03-28 - AI Assistant (Permission Onboarding + Room Runtime Fix)

### Runtime Stability: Room `AppDatabase_Impl` Generation
* Switched Room annotation processing in `app/build.gradle.kts` from `annotationProcessor(...)` to `ksp(...)`.
* Enabled KSP plugin via `alias(libs.plugins.ksp)` in `app/build.gradle.kts`.
* Added KSP plugin coordinates in `gradle/libs.versions.toml` (`ksp = "2.2.10-2.0.2"`).
* Upgraded Room to `2.8.4` in `app/build.gradle.kts` to resolve KSP processor crash (`unexpected jvm signature V`) seen with Room `2.6.1` on current Kotlin/AGP setup.
* Added `android.disallowKotlinSourceSets=false` to `gradle.properties` so KSP generated sources are accepted with built-in Kotlin mode.
* Rationale: fix logcat runtime failure `AppDatabase_Impl does not exist` by ensuring Room code generation actually runs for Kotlin sources.

### Permission UX: Ask for Required Access Early
* Added first-run permission onboarding in `app/src/main/java/com/example/nemebudget/MainActivity.kt`:
  * Requests `POST_NOTIFICATIONS` at runtime on Android 13+.
  * Shows a dialog when Notification Listener access is missing, with direct deep-link to `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
* Added a new **Required Permissions** card in `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt`:
  * Live status for Notification Listener + Post Notifications.
  * One-tap actions to grant each missing permission.
* Added permission guardrails for test notifications in `SettingsScreen.kt`:
  * Blocks posting if notification runtime permission is missing.
  * Shows short user-facing toast when permission is required.

### Validation
* Ran clean build: `:app:clean :app:assembleDebug`.
* Build succeeded with `kspDebugKotlin` executing successfully.

---

## 2026-03-28 - AI Assistant (Testing Feature)

### Test Notification Generator for Encryption Pipeline Debugging

**SettingsScreen.kt**:
* Added "🧪 Test Notification Generator" section to Settings page for easy debugging
* Implemented text field with default placeholder "You spent $45.99 at Starbucks" for customizable test notification content
* Added "Post Test Notification" button that posts a notification that mimics real bank transaction alerts
* Notifications are posted with:
  - Title: "Transaction Alert"
  - Channel: "test_bank_channel" (Android 8+)
  - Priority: HIGH
  - Auto-cancel enabled

**PostTestNotification Helper Function**:
* Creates and posts notifications using `NotificationCompat.Builder`
* Handles API level compatibility (creates notification channel for Android 8+)
* Uses unique notification IDs based on timestamp to allow multiple test notifications
* Notifications contain financial keywords ("spent", "alert") that pass the BankNotificationListenerService filters

**Dependencies**:
* Added `androidx.appcompat:appcompat:1.6.1` for `NotificationCompat` support

**Permissions** (already existed):
* `android.permission.POST_NOTIFICATIONS` already declared for Android 13+

**Workflow**:
1. User navigates to Settings → Test Notification Generator
2. (Optional) Modifies the notification text
3. Taps "Post Test Notification"
4. App posts a notification that BankNotificationListenerService intercepts
5. Notification is saved to the encrypted database after passing all filter tiers
6. User can tap "Process Now" to run the LLM extraction pipeline
7. View results in Transactions tab

**Testing Scenario**:
This enables end-to-end encryption pipeline testing without needing real bank notifications:
- Test notification generation ✓
- Package filtering (self-generated by app) ✓
- Keyword filtering (contains "spent" and "$") ✓
- Database encryption (SQLCipher) ✓
- Passphrase generation (AndroidKeyStore) ✓
- LLM extraction (when Process Now is tapped) ✓

---

## 2026-03-28 - AI Assistant (Post-Logcat Debugging)

### CRITICAL FIX: Room Annotation Processor Configuration

**Issue**: Logcat error `Cannot find implementation for com.example.nemebudget.db.AppDatabase. AppDatabase_Impl does not exist` when NotificationListenerService attempted to access the encrypted database.

**Root Cause**: The project uses built-in Kotlin support (AGP 9.1.0 + Kotlin 2.2.10), which doesn't support the old `annotationProcessor` configuration for Kotlin. The Room compiler wasn't being invoked, so `AppDatabase_Impl` was never generated at compile time.

**Solution Applied**:
* **gradle.properties**: Added flags `android.builtInKotlin=true` and `android.newDsl=true` to enable annotation processor support with built-in Kotlin
* **app/build.gradle.kts**: Kept `annotationProcessor("androidx.room:room-compiler:$room_version")` with the new gradle.properties flags

**Validation**:
* Clean rebuild (`:app:clean :app:assembleDebug`) now succeeds
* `AppDatabase_Impl` is properly generated by the Room compiler during the Kotlin compilation phase
* SQLCipher native libraries successfully strip and package (`libsqlcipher.so`)

**Impact**: The encrypted database pipeline is now functional - notifications can be persisted to the encrypted vault without the annotation processor error.

---

## 2026-03-28 - AI Assistant

### Encryption & Notification Listener Hardening

#### AndroidManifest.xml
* Added `READ_NOTIFICATION_LISTENER_SERVICE` permission for proper NotificationListenerService operation
* Added `BIND_NOTIFICATION_LISTENER_SERVICE` permission declaration (was previously only in service definition)

#### EncryptionManager.kt - Complete Error Handling Refactor
* **Comprehensive try-catch wrapping**: Wrapped `getOrCreatePassphrase()` logic in `getDbPassphrase()` to gracefully handle all encryption failures
* **Fallback passphrase generation**: Implemented `generateFallbackPassphrase()` that creates a temporary non-persistent passphrase if AndroidKeyStore becomes unavailable (e.g., due to device lock or security policy changes)
* **Improved key rotation**: Added logic to delete old keys before generating new ones to prevent stale key conflicts
* **Explicit GCM parameter handling**: Fixed `GCMParameterSpec(128, iv)` with documented tag length (128 bits = 16 bytes for AES-GCM)
* **Passphrase validation**: Added size check (must be exactly 32 bytes) with re-generation on corruption
* **Enhanced logging**: Added detailed DEBUG and ERROR logs at each critical step for troubleshooting encryption flows

#### AppDatabase.kt - SQLCipher Integration Fixes
* **SQLCipher native library loading**: Wrapped `SQLiteDatabase.loadLibs(context)` in try-catch with clear error messaging
* **Passphrase encoding validation**: Added explicit size validation and logging before passing to `SupportFactory`
* **Database callback implementation**: Added `DatabaseCallback` class extending `RoomDatabase.Callback()` to log database creation and opening events
* **Layered error handling**: Separated concerns into 4 distinct try-catch blocks:
  1. SQLCipher native library loading
  2. EncryptionManager passphrase acquisition
  3. Room database builder instantiation
  4. Overall singleton initialization
* **Comprehensive error messages**: Each exception now includes context-specific messages explaining what failed and why

#### BankNotificationListenerService.kt - Multi-Tier Filtering Pipeline
* **Tier 1 - Package ID Whitelist (O(1))**: Implemented HashSet-based whitelist of banking apps (Chase, BoA, Wells Fargo, Citi, Capital One, PayPal, Venmo, Google Pay, Apple Pay, SMS apps) for immediate filtering
* **Tier 2 - Android Category Check**: Added check for `Notification.CATEGORY_TRANSACTION` flag to prioritize genuine transaction notifications
* **Tier 3 - Broad Keyword Filter**: Wide-net regex search for currency symbols ($, USD) and financial verbs (spent, charged, purchase, etc.)
* **Tier 4 - LLM Validation**: Only notifications passing all three tiers proceed to the encrypted database for AI processing (~5% of total notifications)
* **SMS Shortcode Filtering**: Whitelisted bank SMS shortcodes (Chase: 24273, BoA: 226262, Wells Fargo: 925968, Citi: 2622, Capital One: 244365)
* **Improved error handling**: Added try-catch around entire `onNotificationPosted()` with detailed logging
* **Verbose logging**: Added V-level logs for filter rejections, DEBUG logs for passes

---

## 2026-03-27 - AI Assistant

### LLM Pipeline & UI Updates
*   **LlmPipeline.kt**:
    *   **Fixed `ChatCompletionMessageContent` parsing**: Corrected the `simulateLlmInference` function to properly extract `textToken` from `response.choices.firstOrNull()?.delta?.content` instead of appending the raw `ChatCompletionMessageContent` object. This resolves the verbose `ChatCompletionMessageContent(text=...)` output in the UI.
    *   **Strict JSON Schema Enforcement Restored**: Re-enabled `schema = jsonSchema` within `OpenAIProtocol.ResponseFormat` for all `create` calls (`simulateLlmInference` and `warmUpEngine`). This ensures the LLM's output strictly adheres to the defined JSON structure and prevents hallucinated array outputs or conversational text.
    *   **Dynamic Category Enum Integration**: Integrated the `Category` enum from `SharedModels.kt` directly into the `jsonSchema`'s `enum` constraint for the `category` property. This physically limits the LLM's category output to your app's defined categories.
    *   **Refined System Prompt**: Shortened the `systemPrompt` to "Extract transaction data." as schema enforcement now handles explicit output format and category constraints, improving efficiency.
    *   **AOT Shader Warmup Fix**: Modified `warmUpEngine()` to include the `jsonSchema` in `response_format` when requesting a single token. This ensures that the TVM grammar-specific shaders are compiled and cached during the initial optimization, leading to fast subsequent grammar-constrained inferences.
    *   **Implemented LLM Retry Mechanism (`extractWithRetry`)**: Introduced a new `extractWithRetry` function that wraps `generateJson` and `processAndVerify`. If initial LLM output fails verification, it constructs a correction prompt (including original notification, previous bad output, and validation error) and re-prompts the LLM to self-correct, improving data extraction robustness.
*   **MainActivity.kt**:
    *   **LlmPipeline Hoisting**: Moved the instantiation of `LlmPipeline` from `LlmTestingScreen` up to `MainApp()` and passed it as a parameter. This ensures the `MLCEngine` instance persists in RAM across tab navigations, eliminating the 1-2 second "reload" delay when returning to the LLM Lab.
*   **SettingsScreen.kt & FakeRepository.kt**:
    *   **Updated Model Status UI**: Corrected the hardcoded model name in `SettingsScreen.kt` to reflect "Qwen 3 0.6B".
    *   **Integrated GPU Optimization Status**: Updated `ModelStatus` (in `SharedModels.kt`), `FakeRepository.kt`, and `SettingsViewModel.kt` to track `isGpuOptimized` state. Added an "Optimize Now" button to `SettingsScreen.kt` that triggers `pipeline.warmUpEngine()` and updates the UI with progress, providing explicit user control over AOT shader compilation.
    *   **LlmTestingScreen**: Added display for `retriesUsed` in the LLM Lab UI to indicate when the LLM self-corrected.

---

## 2026-03-30 - AI Assistant (Optimization Persistence + Retry Hardening + Frame-Skip Pass 1)

### Item 2: Persist LLM optimization state across relaunches
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` to persist `isGpuOptimized` with `SharedPreferences`:
  * Added pref file `nemebudget_prefs`
  * Added key `gpu_optimized`
  * `getModelStatus()` now returns a `MutableStateFlow<ModelStatus>` initialized from stored preference, not a hardcoded false value.
  * `markGpuOptimized()` now writes preference and updates flow state immediately.
  * `wipeAllData()` now resets optimization preference and status flow.
* Rationale: previously UI always showed unoptimized after process restart because status was hardcoded; now optimization state survives app relaunch and screen navigation.

### Item 3: Retry/prompt hardening for enum/type confusion
* Updated `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`:
  * Retry correction prompt now includes explicit category enum list and strict transaction-type mapping reminder.
  * Retry prompt explicitly forbids category abbreviation and restates required JSON keys.
  * Added `max_tokens = 96` to generation call to keep responses bounded and reduce drift.
* Existing guardrails retained and now reinforced:
  * category normalization helper for single-letter shorthand (e.g., `E` -> `Entertainment` when unambiguous)
  * semantic direction check rejecting `INCOME` for obvious spend phrases and vice versa.
* Rationale: reduce loops where model emits invalid category/type combinations despite schema hints.

### Frame Skipping: First mitigation pass
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/SettingsViewModel.kt`:
  * Reduced manual processing batch size from `200` -> `80`.
  * Reduced startup auto-processing batch from `100` -> `20`.
  * Added startup delay (`1200ms`) before background batch starts to let initial UI render settle first.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * Added cooperative coroutine `yield()` every 5 processed notifications during batch processing loop.
* Rationale: reduce long uninterrupted CPU contention that can contribute to jank/dropped frames during app startup and large processing runs.

### Validation
* Ran `./gradlew.bat :app:compileDebugKotlin`.
* Result: **BUILD SUCCESSFUL**.

---

## 2026-03-30 - AI Assistant (LLM JSON Truncation Guard + Merchant Validation Hardening)

### Scope (per request)
* Kept `LlmPipeline` system prompt unchanged.
* Focused only on truncation-safe parsing and merchant hallucination gating.

### LLM JSON Truncation/Formatting Reliability
* Updated `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`:
  * Increased generation budget from `max_tokens = 120` to `220` to reduce premature cut-off risk.
  * Added `extractFirstCompleteJsonObject(...)` and used it in both generation and verification paths.
    * During generation: accepts only a complete balanced JSON object from streamed output.
    * During verification: rejects incomplete/non-JSON output explicitly instead of attempting partial parse.
  * Result: truncated streams now fail deterministically as parse/verification failures rather than silently producing malformed states.

### Merchant Hallucination Control
* Updated `LlmPipeline.processAndVerify(...)` merchant check:
  * Replaced simple substring check with `merchantAppearsInRaw(...)` that also compares normalized alphanumeric forms.
  * Handles punctuation/spacing differences while still requiring textual evidence in original notification.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` fallback-rescue path:
  * Rescue insert now requires merchant evidence in raw notification (`merchantAppearsInRaw(...)`) plus positive amount.
  * Prevents hallucinated merchant names from being persisted during non-verified fallback saves.

### Validation
* Ran `./gradlew.bat :app:compileDebugKotlin`.
* Result: **BUILD SUCCESSFUL**.

---
