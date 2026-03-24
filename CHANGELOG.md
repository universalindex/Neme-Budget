# Project Changelog (Neme Budget)

This file tracks significant changes and progress for the Neme Budget app. Please update it whenever you complete a major task or make a critical architectural decision.

## 2024-03-22 - Dev 2 (Backend / AI)

### LLM / Backend Updates
*   Successfully downloaded and ran the MLC LLM compiler/tools locally on the laptop to obtain the necessary Android C++ bindings (`mlc4j.aar`).
*   The compiled `.aar` library is prepped and ready for local integration. We have not actually hooked the engine up to the Android UI or pipeline yet (that is the next step).

---

## 2024-03-22 - AI Assistant

### Architectural Decisions
*   **The Final Decision (MLC LLM - Corrected Path!):** Confirmed commitment to **MLC LLM**. Resolved dependency issues by adding MLC LLM's *official custom Maven repository* and specifying correct artifact versions.

### Build / Infrastructure Updates
*   **README.md**: Initial project `README.md` created.
*   **settings.gradle.kts**: Cleaned up unnecessary repository links, and **added MLC LLM's custom Maven repository (`https://repo.mlc.ai/`)**.
*   **app/build.gradle.kts**: Configured dependencies to use `org.apache.tvm:tvm4j-core:0.1.0-alpha0` and `ai.mlc:mlc-llm-android:0.1.0-alpha0`.

### LLM / Backend Updates
*   **LlmPipeline.kt**: Created initial `LlmPipeline` class for LLM integration (placeholder `simulateLlmInference` and `processAndVerify` methods).

### Frontend / UI Updates
*   **MainActivity.kt**: Updated to include `LlmTestingScreen` for manual LLM input and verification display.

---

## 2026-03-23 - AI Assistant

### Repository Hygiene Updates
* Hardened `.gitignore` with a clean Android-focused baseline and full `.idea/` ignore policy to prevent IDE-specific environment files from being committed.
* Removed already-tracked `.idea` files from git index using `git rm -r --cached .idea` so they remain local but stop affecting teammates.
* Rationale: reduce machine-specific config churn and avoid unintended IDE environment drift across collaborators.

---

## 2026-03-23 - AI Assistant

### Frontend Implementation (Eli Scope Through Mar 24)
* Added shared frontend contracts for the repo handoff model:
  * `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`
  * `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt`
* Implemented `FakeRepository` with seeded transaction/budget/settings/model-status data and CSV export support:
  * `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt`
* Implemented all four Day 2 ViewModels:
  * `DashboardViewModel`, `TransactionsViewModel`, `BudgetsViewModel`, `SettingsViewModel`
* Reworked app shell to bottom navigation with four tabs (`Dashboard`, `Transactions`, `Budgets`, `Settings`) and kept the prior `LlmTestingScreen` reachable via a dedicated `Lab` route:
  * `app/src/main/java/com/example/nemebudget/MainActivity.kt`
  * `app/src/main/java/com/example/nemebudget/ui/navigation/AppDestination.kt`
* Built Day 3 dashboard features:
  * month selector,
  * safe-to-spend hero card with animated value,
  * category spending donut visualization,
  * recent transactions widget with AI sparkle marker.
  * `app/src/main/java/com/example/nemebudget/ui/dashboard/DashboardScreen.kt`
* Added scaffold screens for transactions, budgets, and settings wired to their ViewModels:
  * `app/src/main/java/com/example/nemebudget/ui/screens/*.kt`
* Updated `app/build.gradle.kts` with compose navigation and lifecycle dependencies required by the new app shell and state collection.

### Validation Notes
* Ran `:app:assembleDebug`.
* Kotlin compilation passed for new UI/repository/viewmodel code.
* Build currently fails at AAR metadata check due existing project mismatch: `androidx.core:1.18.0` requires `compileSdk 36`, while project is set to `compileSdk 35`.

### Follow-up Build Compatibility Fixes
* Updated `gradle/libs.versions.toml` to pin `coreKtx` to `1.15.0` so the project remains compatible with current `compileSdk = 35` and does not require API 36.
* Replaced deprecated icon usages with `Icons.AutoMirrored` in:
  * `app/src/main/java/com/example/nemebudget/ui/dashboard/DashboardScreen.kt`
  * `app/src/main/java/com/example/nemebudget/ui/navigation/AppDestination.kt`
* Re-ran `:app:testDebugUnitTest` successfully after compatibility and warning cleanup updates.

---

## 2026-03-23 - AI Assistant

### Build Compatibility Decision Update
* Updated `app/build.gradle.kts` to set `compileSdk = 36` and `targetSdk = 36` so the project aligns with `androidx.core:core-ktx:1.18.0` requirements.
* Chose SDK-level upgrade over dependency downgrade to avoid pinning older AndroidX versions and to keep the project on a forward-compatible path.
* Rationale: this resolves the `:app:checkDebugAarMetadata` blocker seen in both `:app:assembleDebug` and `:app:testDebugUnitTest`.

---

## 2026-03-23 - AI Assistant

### Navigation Launch Fix
* Updated `app/src/main/java/com/example/nemebudget/MainActivity.kt` to use a `NavHost` app shell instead of launching `LlmTestingScreen` directly.
* Set the start destination to `AppDestination.Dashboard.route` so the app opens on the dashboard by default.
* Wired the existing tabs (`Dashboard`, `Transactions`, `Budgets`, `Settings`) through bottom navigation and kept the lab accessible via `Settings -> Open LLM Lab` (`AppDestination.Lab`).
* Rationale: the dashboard and tab screens were implemented but unreachable because the previous root `Scaffold` always rendered the lab screen only.

---

## 2026-03-23 - AI Assistant

### Plan Alignment: Hybrid Notification Processing
* Updated `DEV2_BACKEND_AI_PLAN.md` to reflect the agreed architecture: listener writes to encrypted `raw_notifications`, with AI processing done in batches.
* Added backend plan details for `raw_notifications` entity/DAO methods, `NotificationProcessor`, and three batch triggers (app open, hourly `WorkManager`, and manual `Process Now`).
* Updated backend milestones/checklists/performance notes to validate ingest-first durability before batch parse/update behavior.
* Updated `DEV1_FRONTEND_PLAN.md` to include UI-facing batch controls: `processingState`, `Process Now` UX feedback, and integration/demo steps that explicitly show manual/app-open processing.
* Added WorkManager dependency guidance in both plan docs so scheduled batch behavior is part of implementation scope.

---

## 2026-03-23 - AI Assistant

### March 24 Frontend Alignment Implemented
* Added shared `ProcessingState` contract in `app/src/main/java/com/example/nemebudget/model/SharedModels.kt` (`Idle`, `Processing`, `Success`, `Error`).
* Extended `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt` with pending queue APIs: `getPendingNotificationCount()` and `processPendingNotifications(limit)`.
* Implemented fake queue processing in `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt` to simulate pending notifications and add AI-parsed transactions after processing.
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/SettingsViewModel.kt` with `processingState`, `processNow()`, and one-time `processOnAppOpenIfNeeded()` behavior.
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt` to show queued count, processing status text, and a disabled/loading `Process Now` button.
* Wired app-open processing trigger in `app/src/main/java/com/example/nemebudget/MainActivity.kt` via `LaunchedEffect` so queued work starts once at launch.

---

## 2026-03-24 - AI Assistant

### LLM Pipeline Hardening & API Resolution
* Decompiled `mlc4j.aar` to resolve `MLCEngine` and `Chat` API signatures, identifying correct instance-based `reload` and streaming `completions` patterns.
* Implemented real-time inference streaming in `LlmPipeline.kt` using `ChatCompletionMessage` and `consumeEach` on the response channel.
* Troubleshoot TVM runtime crashes (`Cannot find system lib`) by verifying `model_lib` name expectations against the C++ layer via Logcat.
* Optimized engine persistence: transitioned to keeping the model loaded across inference calls to eliminate 2GB reload latency.
* Verified local model weights path and configuration (`mlc-chat-config.json`) via Device File Explorer inspection to align code with on-device filesystem.
