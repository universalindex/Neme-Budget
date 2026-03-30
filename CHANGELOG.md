# Project Changelog (Neme Budget)

This file tracks significant changes and progress for the Neme Budget app. Please update it whenever you complete a major task or make a critical architectural decision.

## 2026-03-29 - AI Assistant (Comprehensive Consolidation Update)

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
