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
* Troubleshoot TVM runtime crashes (`Cannot find system lib`) by iteratively mapping `model_lib` names against `mlc-chat-config.json` and compiled `.so` file hex dumps.
* Successfully matched the compiled kernel string (`qwen3_q4f16_1_1431bce2f7643ad37bb21ddc71153223`) and established a connection to the native library.
* **Current Blocker:** Encountered a hard OS-level restriction (`Cannot open libOpenCL!`) because the compiled `.aar` defaults to OpenCL, but the physical Android device (with a Mali GPU) enforces strict linker namespace security that blocks untrusted apps from accessing `/vendor/lib64/libOpenCL.so`.
* **Resolution Plan Required:** The `mlc4j.aar` must be recompiled on the host machine with explicitly forced Vulkan support (`set(USE_OPENCL OFF)`, `set(USE_VULKAN ON)`) to bypass Android's OpenCL namespace restrictions. Kotlin code is verified complete and awaiting the updated binary.

---

## 2026-03-25 - AI Assistant

### Frontend Implementation (Next Steps Through Mar 27)
* Rebuilt `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt` into a full ledger flow with:
  * date-grouped sections with sticky headers,
  * complete category chip filtering (not limited to the first four categories),
  * confidence bars and AI parse marker display,
  * transaction edit bottom sheet for merchant/category/amount corrections.
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt` to:
  * expose reactive `query` and `filter` state for Compose collection,
  * support `saveEdit(original, updated)` and append a deduplicated teaching rule (`"merchant = category"`) into settings when category corrections are made.
* Expanded `app/src/main/java/com/example/nemebudget/ui/screens/BudgetsScreen.kt` with Day 6 budget UX features:
  * animated budget progress cards,
  * over-budget warning banner,
  * add budget FAB,
  * long-press actions for edit/delete,
  * add/edit budget dialog with category + limit inputs.
* Rationale: this closes the main Mar 25-26 Transactions milestones and advances Mar 27 Budgets functionality while staying compatible with the existing shared repository contract.

---

## 2026-03-25 - AI Assistant

### Transactions Screen Milestone Checked Off
* Marked Days 4–5 (Transactions Screen) as complete in `DEV1_FRONTEND_PLAN.md` after verifying all required features are implemented in code:
  * Grouped list by date with sticky headers (`TransactionsScreen.kt`)
  * Transaction row with emoji, merchant, category, amount, AI badge, and confidence bar
  * Edit bottom sheet for transaction correction and AI teaching flow
  * Search/filter bar with chips for all categories
* Rationale: All checklist items for this milestone are present and functional in the current codebase.

---

## 2026-03-25 - AI Assistant

### Settings Milestone Implemented + Plan Progress Logged
* Completed Days 8–9 Settings scope in `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt` with:
  * primary bank input wired to debounced autosave,
  * ignore-app toggles from a predefined package list,
  * custom AI rule add/remove chip workflow,
  * model status card for downloading vs ready states,
  * data management controls for CSV export, Process Now status, and destructive wipe confirmation dialog.
* Extended `app/src/main/java/com/example/nemebudget/viewmodel/SettingsViewModel.kt` with:
  * 500ms debounce job for `updatePrimaryBank`,
  * predefined `knownSpamApps` list for UI toggles,
  * deduplicated custom rule insertion,
  * existing processing/export/wipe flows preserved for repository contract compatibility.
* Checked off completed pre-integration milestones in `DEV1_FRONTEND_PLAN.md`:
  * Days 6–7 Budgets goal + Steps 1–3,
  * Days 8–9 Settings goal + Steps 1–5.
* Validation: Ran `:app:testDebugUnitTest` successfully after these updates.
* Rationale: closes all planned frontend work up to (but not including) Integration Day while keeping `FakeRepository` wiring intact for the planned one-line swap later.

---

## 2026-03-25 - AI Assistant

### Settings UX Follow-Up: Ignore Apps Submenu
* Refined `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt` to move ignored app toggles into a submenu flow:
  * Main settings now shows `Ignore apps` as a dedicated entry row with selected-count summary.
  * Added an `IgnoreAppsScreen` subview (`Settings -> Ignore apps`) that contains the toggle list.
* Added helper copy clarifying behavior: ignored app selections are saved immediately and are intended to apply to notification ingest once the listener pipeline is connected.
* Validation: Ran `:app:testDebugUnitTest` successfully after the submenu refactor.

---

## 2026-03-25 - AI Assistant

### Privacy/Scale Upgrade: Ignore Any App (Not Just Presets)
* Extended ignore-app controls so users can filter far beyond the original fixed spam list:
  * `Ignore Apps` submenu now loads launchable apps from the device using `PackageManager`,
  * added app/package search,
  * added manual package entry (`com.example.app`) to support edge cases and non-launcher sources.
* Updated toggle behavior to explicit set/unset semantics via `setIgnoredApp(packageName, ignored)` in `app/src/main/java/com/example/nemebudget/viewmodel/SettingsViewModel.kt` so UI can safely drive dynamic lists.
* Preserved compatibility with current architecture: choices persist in `AppSettings.ignoredApps` now; they are intended to be enforced on live notification ingest once the NotificationListener/real pipeline is wired.
* Validation: Ran `:app:testDebugUnitTest` successfully after this update.

---

## 2026-03-25 - AI Assistant

### Plan Note Added: Auto-Suggest Ignore Apps From Notifications
* Updated `DEV1_FRONTEND_PLAN.md` (Days 8–9, Step 2) with a new unchecked follow-up item to auto-discover notification source packages after listener integration and surface them as suggested ignore toggles.
* Kept this item explicitly post-integration and user-confirmed to preserve privacy control and avoid implying the listener pipeline is already active.

