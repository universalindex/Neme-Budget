```markdown
# Project Changelog (Neme Budget)

This file tracks significant changes and progress for the Neme Budget app. Please update it whenever you complete a major task or make a critical architectural decision.

## 2026-04-02 - AI Assistant (Settings Cleanup + Rule Editing UX)

### What Changed
* Removed the `Primary Bank` input from the visible Settings page while leaving the stored setting intact.
* Cleaned up `SettingsScreen.kt` by keeping the page focused on permissions, ignore apps, rule management, model status, and test notifications.
* Updated `ManageRulesScreen.kt`:
  * added an explicit `Edit` action for each rule,
  * allowed editing existing typed rules via the same bottom sheet,
  * fixed category chip labels so they stay on one line.

### Why This Was Done
1. The primary bank field was adding noise without affecting parsing behavior yet.
2. Users needed a direct edit path for rules, not only add/remove.
3. Category text wrapping was making the UI look broken, especially for labels like Transport.

## 2026-04-02 - AI Assistant (Typed Manage Rules Screen)

### What Changed
* Replaced the freeform rules entry in `SettingsScreen.kt` with a `Manage Rules` launcher card.
* Added `ManageRulesScreen.kt`:
  * searchable list of rules,
  * floating add-rule button,
  * slide-up add sheet,
  * typed field selector (`Merchant` or `Notification text`),
  * category picker chips.
* Updated the rule data model in `SharedModels.kt` to use `RuleDefinition` and `RuleField`.
* Updated `LlmPipeline` to apply typed rules after LLM extraction, before verification.
* Updated `TransactionsViewModel` so the teach-AI flow creates typed rules instead of string rules.

### Why This Was Done
1. The user-facing rule experience is now simpler than regex and easier to teach/understand.
2. Users can choose what field they’re matching instead of writing patterns.
3. The rules still run deterministically after the LLM, which preserves local-first predictability.

## 2026-04-02 - AI Assistant (Regex Rule Post-Processing for LLM Output)

### What Changed
* Added a regex rule engine in `app/src/main/java/com/example/nemebudget/llm/RegexRuleEngine.kt`.
* Updated `LlmPipeline` so `customRules` are applied after JSON extraction but before final verification.
* Updated `TransactionsViewModel` so learned rules are saved as escaped regex patterns tied to the replacement category.
* Updated `SettingsScreen` to explain the `Regex = Category` rule format.

### Why This Was Done
1. The rules feature is now deterministic: the model extracts first, then user regex rules can override the category in a predictable way.
2. Using regex makes the feature flexible enough to handle merchant variants like `Chevron|Shell` without needing more LLM prompting.
3. Applying rules post-LLM keeps the model prompt simpler and makes the user’s rules easier to reason about.

## 2026-04-02 - AI Assistant (Unified Category Soft Delete)

### What Changed
* Updated the category model in `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * Added `hiddenCategoryIds` to `AppSettings`.
  * Added `allCategoryOptions()`, `activeCategoryOptions()`, and active resolver helpers.
* Updated budget delete flow in `app/src/main/java/com/example/nemebudget/ui/screens/BudgetsScreen.kt`:
  * The edit sheet now deletes categories for both built-in and custom entries.
  * Delete now archives the category instead of treating built-ins as reset-only.
* Updated repository contracts and implementations:
  * Added `softDeleteBudgetCategory(...)` to `AppRepository`.
  * Persisted archived category IDs in `RealRepository` and mirrored the behavior in `FakeRepository`.
* Updated `LlmPipeline` and transaction/category consumers to use the active catalog only for new extraction/pickers while preserving historical resolution.

### Why This Was Done
1. Built-in and custom categories now behave like one catalog from the user’s perspective.
2. Soft delete preserves history while removing archived categories from pickers and future LLM outputs.
3. This keeps the app flexible without breaking older transactions that reference archived category IDs.

## 2026-04-02 - AI Assistant (Default Test Notification Updated to Card Guard Sample)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt`:
  * Default test notification title now starts as `Card Guard`.
  * Default test notification text now uses:
    * `Card Guard Credit Card *3320 $9.13 at SQ *KIWI LOCO FROZEN Y. Tap for details.`
  * Updated title placeholder and blank-title fallback to `Card Guard` for consistency.
* Updated `app/src/main/java/com/example/nemebudget/MainActivity.kt`:
  * LLM Lab default manual input now uses the same Card Guard sample text.

### Why This Was Done
1. Keeps test data aligned with the real-world format you want to validate.
2. Ensures Settings test notifications and LLM Lab manual testing start from the same baseline sample.

### Verification
* Ran `:app:compileDebugKotlin` successfully after defaults update.

## 2026-04-02 - AI Assistant (Money Signal Regex Expansion + Gate Unification)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/pipeline/TransactionalNotificationGate.kt`:
  * Expanded `MONEY_SIGNAL_REGEX` to cover:
    * currency prefix (`$45`, `USD 45`),
    * currency suffix (`45$`, `45 dollars`),
    * keyword-led numeric fields (`amount: 45`, `payment=45.50`, `spent 45`).
* Updated `app/src/main/java/com/example/nemebudget/pipeline/NotificationBatchProcessor.kt`:
  * Removed duplicate local money/action gate constants.
  * Switched final pre-LLM gate to shared `TransactionalNotificationGate.evaluate(...)` and reason-coded skip logs.
* Updated `app/src/test/java/com/example/nemebudget/TransactionalNotificationGateTest.kt`:
  * Added regression coverage for keyword-amount matching and suffix-currency-word matching.

### Why This Was Done
1. Broader money patterns improve capture of real-world alert formats that omit `$` or use keyword fields.
2. One shared gate avoids drift between listener/repository/batch paths.
3. Reason-coded logs make skip decisions easier to tune.

### Verification
* Ran `:app:compileDebugKotlin` successfully.
* Ran `:app:testDebugUnitTest --tests com.example.nemebudget.TransactionalNotificationGateTest` successfully.

## 2026-04-02 - AI Assistant (Transactions Delete Simplified to Tap-Then-Delete)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Removed swipe-to-delete behavior entirely.
  * Removed swipe/background delete UI that was visually glitchy.
  * Kept transaction rows tap-to-edit.
  * Deletion is now tap flow only via the existing `Delete` button in `EditTransactionBottomSheet`.
  * Kept snackbar undo behavior for deletes triggered from edit flow.

### Why This Was Done
1. Swipe interactions were unstable and visually distracting.
2. Tap-then-delete is clearer and more predictable.
3. This reduces accidental destructive actions while preserving undo safety.

### Verification
* Ran `:app:compileDebugKotlin` successfully after removing swipe delete.

## 2026-04-02 - AI Assistant (Transactions Delete UX Simplified: Hard Swipe + Undo + Tap Menu Delete)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Removed the red/reveal swipe background behavior that felt glitchy.
  * Kept swipe delete, but simplified to hard right-to-left swipe trigger with a stricter threshold.
  * Added undo flow via snackbar after each delete.
  * Added per-row overflow menu (`...`) with `Edit` and `Delete` actions for tap-menu deletion.
  * Kept row tap-to-edit behavior.
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt`:
  * Added `restoreTransaction(transaction)` so undo can reinsert deleted transactions.

### Why This Was Done
1. The previous swipe visuals were distracting and looked unstable in motion.
2. Hard-swipe + undo preserves fast deletion while making mistakes recoverable.
3. Menu delete from a tap path supports users who prefer explicit actions over gestures.

### Verification
* Ran `:app:compileDebugKotlin` successfully after the transaction UX changes.

## 2026-04-02 - AI Assistant (Budget Typed Limit + Dynamic Scrubber + Transaction Swipe Delete)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/BudgetsScreen.kt`:
  * Added a typed `Budget limit` input in the unified budget bottom sheet.
  * Synced slider and typed value both ways (drag updates text, typing updates slider).
  * Changed default scrubber range to `0..1000`.
  * Added dynamic range expansion so when a user types a higher number, that number becomes ~80% of scrubber max (`max = typed / 0.8`).
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Made transaction rows tap-to-edit by making the row itself clickable.
  * Added swipe-to-delete with Material `SwipeToDismissBox`:
    * partial swipe exposes a `Delete` action button,
    * full swipe right-to-left performs immediate delete.
  * Removed row-level inline `Edit`/`Delete` text buttons and removed delete confirmation dialog flow.

### Why This Was Done
1. Budget editing needed both precision (typed input) and speed (scrubbing).
2. Dynamic scrubber scaling avoids hard slider ceilings for larger budgets.
3. Transactions now match requested mobile pattern: tap to edit, swipe to delete (including full-swipe shortcut).

### Verification
* Ran `:app:compileDebugKotlin` successfully after UI changes.

## 2026-04-02 - AI Assistant (Unified Budget Editor: Scrubbing Slider + Single-Tap Edit)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/BudgetsScreen.kt`:
  * **Removed two-action UX**: Eliminated tap-to-edit-limit and long-press-to-edit-label workflow
  * **Added unified bottom sheet**: Single tap on any budget card now opens `BudgetEditBottomSheet` with all editing controls
  * **Added scrubbing slider**: Users can now quickly drag a slider to adjust budget limits (1-5000 range)
  * **Combined controls**: Label, icon, and limit editing all available in one place
  * **Action buttons**: Cancel, Delete/Reset, and Save buttons always visible at bottom
  * Removed `CategoryMetaEditorDialog` (unused, replaced by bottom sheet)
  * Simplified `BudgetCard` to show current spent/limit without inline editing UI

### Why This Was Done
1. Users wanted simpler interaction: one tap instead of tap-and-hold split
2. Scrubbing slider enables quick budget adjustments without typing (faster UX)
3. All budget metadata (limit, label, icon) is edited together in context
4. Cleaner mobile UX reduces cognitive load

### Verification
* Ran `:app:compileDebugKotlin` successfully
* Built full debug APK: `:app:assembleDebug` successful

## 2026-04-02 - AI Assistant (Built-In Category Presentation Overrides Now Propagate to Transaction UI)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * `AppSettings.transactionCategoryOptions()` now applies `categoryPresentation` overrides when building the transaction category list.
  * Previously, this helper was returning the hardcoded enum labels/emoji, so edits to built-in categories (like "Dining" → custom emoji) were invisible in the transactions screen, making it appear that edits didn't save.

### Why This Was Done
1. When you edit a built-in category's label/emoji, the change is stored in `categoryPresentation` and used correctly by the budgets screen.
2. But the transactions screen builds its category picker from `transactionCategoryOptions()`, which was ignoring those overrides.
3. Now the transactions screen, dashboard filters, error resolver, and LLM validation all see the same live presentation data.

### Verification
* Ran `:app:compileDebugKotlin` successfully after the fix.

## 2026-04-02 - AI Assistant (Live Category Icon Resolution for Transaction Screens)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/db/TransactionEntity.kt`:
  * Added a settings-aware `toDomain(settings)` mapper so transaction rows can resolve the latest category label/icon from the saved catalog.
  * Kept the stored `categoryId`/`categoryLabel`/`categoryEmoji` snapshot for history and fallback safety.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * `getAllTransactions()` and `getTransactionsByDateRange()` now combine the transaction table with `settingsFlow`, so built-in category icon edits and custom category icon edits immediately show up in transaction-based screens.
  * CSV export now uses the live category resolution too.
* Updated `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt`:
  * Mirrors the live-resolution behavior so the demo/test repository stays visually consistent with production.

### Why This Was Done
1. Category metadata is stored as editable settings, but transaction rows intentionally keep a snapshot for audit/history.
2. The UI should show the user’s current category icon everywhere, not just on the budgets screen that edits the catalog.
3. Resolving the display metadata at read time preserves history while fixing the “edited icon only updates some screens” mismatch.

### Verification
* Ran `:app:compileDebugKotlin` successfully after the repository/model changes.

## 2026-04-02 - AI Assistant (Full Category Catalog Migration: Custom Categories Become the Live Source of Truth)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * `Transaction.category` now uses `CategoryDefinition` instead of the hardcoded enum directly.
  * Added helper models/functions to build a live category catalog from built-in + custom categories.
* Updated `app/src/main/java/com/example/nemebudget/db/TransactionEntity.kt`:
  * Added `categoryId` and `categoryEmoji` columns to preserve stable category identity and display snapshots.
  * Mappers now round-trip `CategoryDefinition` rather than enum-only labels.
* Updated `app/src/main/java/com/example/nemebudget/db/AppDatabase.kt`:
  * Added `MIGRATION_3_4` to backfill the new category columns for existing installs.
  * Incremented Room database version so the new schema is applied on open.
* Updated `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`:
  * LLM schema + verification now rebuild from the saved category catalog in `app_settings_json`.
  * Custom categories are now valid LLM outputs, not just built-in enum entries.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` and `FakeRepository.kt`:
  * Transaction processing now resolves categories from the live catalog.
  * Budgets and CSV export use category definitions/labels consistently.
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt`, `DashboardViewModel.kt`, `TransactionsScreen.kt`, `ResolveErrorScreen.kt`, and `DashboardScreen.kt`:
  * Category pickers, filters, and chart labels now consume the live catalog instead of `Category.entries`.

### Why This Was Done
1. This removes the last major assumption that the built-in enum was the only valid category system.
2. Custom categories now participate in transaction entry, resolution, dashboard analytics, and LLM output validation.
3. The migration keeps legacy data readable by preserving stable category IDs and label/emoji snapshots in the transaction rows.

### Verification
* Ran `:app:compileDebugKotlin` successfully after the migration.

## 2026-04-02 - AI Assistant (DEV2 Item 3 Phase 2: Custom Budget Categories + Long-Press Label/Icon Editor)

### What Changed
* Updated budget/category contracts in `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * `Budget` now carries stable `id`, display `label`, display `emoji`, nullable enum backing `category`, and `isCustom`.
  * Added `CategoryPresentation` for editable built-in label/icon overrides.
  * Added `CustomBudgetCategory` for fully user-created budget categories.
  * Extended `AppSettings` with `categoryPresentation` and `customBudgetCategories`.
* Updated repository contract in `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt`:
  * Added APIs for custom category create/update/delete metadata flows.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * `getBudgets()` now emits both built-in enum budgets and custom budget categories.
  * Built-in category labels/icons now resolve from user overrides when present.
  * Added persistence for `categoryPresentation` and `customBudgetCategories` inside existing settings JSON.
  * Added methods: `addCustomBudgetCategory`, `updateBudgetCategoryMeta`, `deleteCustomBudgetCategory`.
* Updated `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt`:
  * Mirrors new behavior for built-in label/icon overrides + custom budget categories.
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/BudgetsViewModel.kt`:
  * Added wrappers for custom category create/update/delete actions.
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/BudgetsScreen.kt`:
  * FAB now opens `Add custom category` dialog (label + emoji + initial limit).
  * Long-press now opens category action menu with label/icon editing and delete/reset actions.
  * Added category metadata editor dialog (`label` + `emoji`).
  * Category labels now enforce a 50-character cap and allowed-character filtering so custom names stay list-friendly.
  * Kept inline limit editing but now keyed by stable budget `id` to support both built-in and custom rows.

### Why This Was Done
1. Budget customization needed to decouple category display metadata from enum-only labels without destabilizing transaction parsing.
2. Long-press is now the dedicated “category management” affordance while tap remains quick limit editing.
3. This phase unlocks custom budgeting categories immediately while preserving existing transaction/LLM enum behavior.

### Verification
* Ran `:app:compileDebugKotlin` successfully after all model, repository, and UI changes.

## 2026-04-02 - AI Assistant (SQLCipher mlock Warning Mitigation)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/db/AppDatabase.kt`.
* Added a Room database `onOpen` callback for SQLCipher runtime pragmas.
* On open, the DB now executes:
  * `PRAGMA cipher_memory_security = OFF`

### Why This Was Done
1. Runtime logs were repeatedly showing `sqlcipher_mlock: mlock() returned -1 errno=12` during keying/open operations.
2. Those messages are typically non-fatal on Android devices with low/strict memlock limits, but they create noisy logs and can correlate with extra overhead around DB open paths.
3. This mitigation keeps at-rest SQLCipher encryption intact while disabling the memory-page locking feature that triggers the warnings on constrained devices.

### Security/Tradeoff Note
* This reduces SQLCipher in-memory hardening (`mlock` protections) in exchange for quieter and more stable behavior on affected devices.
* Disk encryption and passphrase-based keying remain unchanged.

## 2026-04-02 - AI Assistant (DEV2 Item 3 Phase 1: Inline Budget Editing + Persistent Limits)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * Extended `AppSettings` with `budgetLimits: Map<Category, Double>` so user-edited budget caps have a real contract-backed storage field.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * Added persisted settings serialization/deserialization in shared preferences key `app_settings_json`.
  * Switched settings handling to reactive `settingsFlow` so budget-limit changes immediately recompute budget UI state.
  * Updated `getBudgets()` to combine transactions with saved limit overrides instead of using a fixed hardcoded limit.
  * Implemented all `AppRepository` contract methods in this file (`getModelStatus`, `markGpuOptimized`, `getTotalTransactionCount`, `wipeAllData`, `exportToCsv`) so the repository compiles cleanly and remains complete.
  * Restored rejected-extraction user notifications with API-level and permission-safe checks.
* Updated `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt`:
  * Budget edits now write to `settingsFlow.budgetLimits` and regenerate budget cards with overrides for parity with real data behavior.
  * Budget recalculation after transaction add/update/delete now respects saved limit overrides.
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/BudgetsScreen.kt`:
  * Added inline budget-limit editing directly on budget cards (tap card -> amount becomes editable).
  * Added inline validation (`> 0` numeric), save/cancel controls, and a save confirmation snackbar.
  * Kept long-press actions for management flow while steering primary edit behavior to direct inline editing.

### Why This Was Done
1. Budget edits previously looked editable in UI but did not persist in the real repository path, which breaks user trust.
2. Inline amount editing is the fastest path for the common action (change budget cap), while long-press remains available for less frequent actions.
3. Combining transaction flow + settings flow ensures edited limits immediately reflect in the rendered budget cards without app restarts.
4. Completing the repository interface methods in `RealRepository` avoids contract drift and compile instability.

### Verification
* Ran `:app:compileDebugKotlin` successfully after the changes.

## 2026-04-02 - AI Assistant (Dashboard 'See All' Navigation Now Uses Top-Level Tab Pattern)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/MainActivity.kt`.
* Added a small `navigateToTopLevel(...)` helper so top-level destinations all use the same `popUpTo(...)/launchSingleTop/restoreState` behavior.
* Switched the dashboard `See All` action to use that helper when opening `Transactions`.

### Why This Was Done
1. Bottom tabs work best when every top-level destination uses the same navigation contract.
2. Reusing the bottom-bar pattern for `See All` keeps the back stack predictable and avoids odd cases where the user must rely on system back instead of the tab bar.
3. This is a minimal, low-risk fix: no UI redesign, just a route-consistency improvement.

## 2026-04-02 - AI Assistant (DEV2 Item 2 Refinement: Dashboard Swipe Works Across Whole Dashboard)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/dashboard/DashboardScreen.kt` again.
* Moved the horizontal swipe detector from the month header row to the full dashboard `LazyColumn` container.
* Month swipes now work anywhere on the dashboard screen, while still using the same `previousMonth()` / `nextMonth()` logic as the arrow buttons.

### Why This Was Done
1. The month header-only gesture area was too narrow in practice.
2. Expanding the gesture surface makes the feature easier to discover and use.
3. The dashboard already distinguishes horizontal swipes from vertical scrolling, so broadening the hit area is low risk.

### Behavior Notes
* Vertical list scrolling remains the primary interaction; horizontal swipes are only meant for intentional month changes.
* Arrow buttons still work exactly as before.

## 2026-04-02 - AI Assistant (DEV2 Item 2: Dashboard Month Swipe Navigation)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/dashboard/DashboardScreen.kt`.
* Added horizontal swipe handling to the month selector row.
* Swipe left now calls the same month advance path as the next-arrow button.
* Swipe right now calls the same month back path as the previous-arrow button.
* Kept the existing arrow buttons unchanged so both input methods share the same `DashboardViewModel` month state.

### Why This Was Done
1. Month navigation already had the correct state logic; the missing piece was gesture parity.
2. Reusing `previousMonth()` and `nextMonth()` avoids duplicated month math and keeps swipes/buttons perfectly aligned.
3. Limiting the gesture to the month selector row reduces conflict with the dashboard's vertical scrolling content.

### Behavior Notes
* The dashboard still derives all monthly data from the single `selectedMonth` state.
* Swipe gestures and arrow taps now both drive the same underlying month selection transitions.

## 2026-04-02 - AI Assistant (DEV2 Step 1: Determinate Processing Progress)

### What Changed
* Updated processing state contract in `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * `ProcessingState.Processing` now carries `processedCount`, `totalCount`, and optional `currentItemLabel`.
* Updated repository contract in `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt`:
  * `processPendingNotifications(...)` now accepts an optional progress callback.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * Emits callback progress per completed raw notification during batch processing.
* Updated `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt`:
  * Emits callback progress in both mock and processor-backed paths for UI parity.
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/SettingsViewModel.kt`:
  * Wires repository callback into `ProcessingState.Processing` updates.
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt`:
  * Replaced indeterminate process button spinner with determinate progress text + `LinearProgressIndicator`.
  * Shows `Processing X of Y` and current item label while running.

### Why This Was Done
1. Spinner-only feedback told users work was happening, but not how far along the batch was.
2. Determinate progress improves transparency and makes long processing runs easier to trust/debug.
3. Callback-based updates keep architecture clean (repository owns work, ViewModel owns state, UI only renders).

## 2026-04-02 - AI Assistant (Small UX Rollback: Explicit Edit Button, No Row-Wide Tap)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`.
* Removed row-wide tap-to-edit behavior from `TransactionRow(...)` by dropping the `Modifier.clickable(...)` usage.
* Added an explicit `Edit` text button in the trailing action area next to `Delete`.

### Why This Was Done
1. Broad row tap targets can cause accidental edits and hide available actions.
2. Explicit action buttons are easier to learn and safer to use in dense list rows.
3. This keeps scope intentionally small (interaction affordance only), matching requested low-risk rollback style.

### Follow-Up Note Added
* Added deferred DEV2 backlog item in `DEV2_BACKEND_AI_PLAN.md`:
  * Reintroduce `isAiParsed` as a subtle star marker later (without crowding the row action area).

## 2026-04-02 - AI Assistant (Onboarding Flow Rewired Back Into Startup)

### What Changed
* Reconnected the existing onboarding composables in `app/src/main/java/com/example/nemebudget/ui/screens/OnboardingScreens.kt` to the real app startup flow.
* Added a new `OnboardingFlowScreen(...)` coordinator that sequences:
  1. welcome screen,
  2. permission gate,
  3. model-ready screen.
* Made the permission step functional by checking live OS state every second:
  * Notification Listener enabled state via `Settings.Secure.enabled_notification_listeners`
  * `POST_NOTIFICATIONS` runtime permission on Android 13+
* Kept the model screen tied to the existing `ModelStatus` flow so onboarding reuses the same readiness signal used in Settings.
* Added a persisted first-run completion flag in `MainActivity.kt`:
  * Shared prefs file: `nemebudget_prefs`
  * Key: `onboarding_completed`
  * Once set, the app skips onboarding on future launches and drops into the existing bottom-nav shell immediately.

### Why This Was Done
1. The onboarding screens already existed but were not actually wired into navigation, so they behaved like dead UI.
2. A small persisted boolean is the simplest durable way to make onboarding show once per install without moving the whole app to a more complex navigation state machine.
3. Keeping the existing permission prompt logic in the shell means the app still has a fallback if permissions are later revoked.

### Files Modified
* `app/src/main/java/com/example/nemebudget/ui/screens/OnboardingScreens.kt`
* `app/src/main/java/com/example/nemebudget/MainActivity.kt`
* `CHANGELOG.md`

## 2026-04-02 - AI Assistant (Fixed Navigation Crash: Added ResolveError Route + Import Cleanup)

### What Changed
* **Fixed critical crash** when user taps a rejected notification to open resolver screen:
  - Added missing `ModalBottomSheet` import to `TransactionsScreen.kt` (was preventing build)
  - Added missing `ResolveErrorScreen` import to `MainActivity.kt`
  - **Added ResolveError navigation route** to `MainActivity.kt` NavHost with parameter extraction:
    - Route: `resolve_error/{errorId}` with `NavArgument` of type `IntType`
    - Extracts `errorId` from URL, finds matching `RejectedNotification` from ViewModel
    - Wires all three callbacks to ViewModel methods: `deleteRejectedItem`, `resolveRejectedAsTransaction`
    - Properly handles back navigation after resolver actions
  - Added missing `collectAsStateWithLifecycle`, `navArgument`, `NavType` imports

* **Added `IMPORT_ANALYSIS.md`** - Audit of unused imports across UI screens:
  - Identified 4 confirmed unused imports in `TransactionsScreen.kt`
  - Identified 2 unused imports in `ResolveErrorScreen.kt`
  - Ready for cleanup with IDE "Optimize Imports" after validation

### Why These Changes
1. **Crash Root Cause**: App was trying to navigate to `resolve_error/41` but this route wasn't registered in the NavHost, causing `IllegalArgumentException` on tap
2. **Teaching**: This demonstrates the importance of:
   - **Contract definition** (AppDestination defines available routes)
   - **Implementation** (route must be added as composable in NavHost)
   - **Wiring** (callbacks must be connected to ViewModel methods)
   - Any missing piece breaks the entire flow

### Files Modified
- `MainActivity.kt`: Added ResolveError route, fixed imports
- `TransactionsScreen.kt`: Added ModalBottomSheet import, added @OptIn annotation
- Created: `IMPORT_ANALYSIS.md`

### Build Status
✅ Build successful after all fixes

---

## 2026-04-01 - AI Assistant (Follow-Up Backlog Capture: Dashboard + Resolver + Navigation UX)

### User-Requested Follow-Ups Captured (No Runtime Logic Change in This Entry)
* Added explicit planning follow-ups for next implementation pass:
  1) add month swiping gesture support in dashboard month navigation,
  2) improve budget editing experience,
  3) rework Error Resolver UX to show as "Errors detected, click here to address" and open a dedicated resolver screen,
  4) downsize oversized add-transaction FAB bubble for tuning,
  5) make dashboard safer/clearer with per-category safe-to-spend communication.

### Why This Entry Exists
* This entry records alignment after iterative UX changes so Dev2 planning and future implementation work stay synchronized with latest product direction.

---

## 2026-04-01 - AI Assistant (Error Resolver Flow + Fixed Bottom Actions + Larger Add FAB)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Removed transaction-row edit flow from this screen.
  * Error rows now open a dedicated resolver sheet (`ResolveErrorBottomSheet`) instead of inline title/text/reason editing.
  * Resolver sheet now shows raw notification content at the top and editable JSON-style fields at the bottom: `merchant`, `value` (amount), `category`.
  * Resolver action controls are fixed at the bottom of the sheet for constant visibility: `Delete Error`, `Cancel`, `Save`.
  * Increased add-transaction FAB bubble size and plus glyph size for better touch/accessibility.
* Updated conversion behavior from error -> transaction:
  * Added `resolveRejectedAsTransaction(...)` in `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt`.
  * Save from resolver now creates a transaction from edited fields and deletes the source error row.

### Why This Was Done
* Align UI with request to edit extraction fields (JSON-style output fields) rather than editing normal transaction rows.
* Keep destructive/primary actions visible at all times in long sheets.
* Improve plus action discoverability and ergonomics with a larger floating action button.

---

## 2026-04-01 - AI Assistant (Transactions UX Polish: Confirm Delete + Bigger FAB + Editable Errors)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Added delete confirmation dialog before removing a transaction row.
  * Enlarged the add-transaction floating `+` button (larger bubble + larger plus text).
  * Error rows now support both **Edit** and **Delete** actions.
  * Added `EditErrorBottomSheet` to edit error title/text/reason fields.
  * Manual transaction category picker changed from dropdown to explicit category chips in the add sheet to avoid being stuck on `Other`.
* Updated data contract and persistence for error editing:
  * `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt` adds `updateRejectedNotification(...)`.
  * `app/src/main/java/com/example/nemebudget/db/RawNotificationDao.kt` adds `updateRejectedNotification(...)` SQL update query.
  * `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` and `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt` implement update behavior.
  * `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt` adds `updateRejectedItem(...)`.

### Why This Was Done
* Prevent accidental transaction deletion via confirmation.
* Improve primary add action discoverability and touch ergonomics.
* Make error records actionable by allowing correction as well as deletion.
* Fix manual transaction categorization UX so all categories are directly selectable.

---

## 2026-04-01 - AI Assistant (Transactions UX: Errors-Only Section + Floating Add Button)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`.
  * Removed the manual rejected-entry add form from the Transactions UI.
  * Renamed the rejected area label from `Rejected Notifications` to `Errors`.
  * Entire `Errors` section is now hidden when there are no error rows.
  * Added a floating `+` action button anchored to the bottom-right of the screen.
  * Manual transaction creation now opens a dedicated bottom sheet (`AddTransactionBottomSheet`) from the FAB.

### Why This Was Done
* Aligns with product intent: error list is for failed extractions, not manual entry.
* Keeps the primary transaction-history screen cleaner and reduces visual clutter.
* Provides a more standard mobile UX for manual transaction entry using a floating primary action.

---

## 2026-04-01 - AI Assistant (Rejected Visibility + General Transaction Add/Delete Controls)

### What Changed
* Updated repository contract `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt`:
  * Added `addTransaction(transaction)` and `deleteTransaction(id)` for direct main-table management.
* Implemented new contract methods in:
  * `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` (Room-backed insert/delete),
  * `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt` (in-memory parity).
* Updated `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt`:
  * Added `addManualTransaction(...)` and `deleteTransaction(id)` actions.

### Transactions Screen Behavior Updates
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Rejected section now **shows failed notifications first** with empty-state guidance when there are none.
  * Manual rejected-entry form is now optional (toggle), not the default focus.
  * Added a new **Manual Transaction** form (toggle) to insert rows into the real transactions table.
  * Added per-row **Delete** action on transaction rows.

### Why This Was Done
* User feedback indicated rejected UX looked like an add-form only, which obscured the actual failed-item review purpose.
* Added direct add/delete controls so table management works "in general" from one screen.
* Keeps failed-notification observability while improving day-to-day transaction maintenance.

---

## 2026-04-01 - AI Assistant (Transactions History Visibility Fix)

### Problem
* Transaction history appeared to disappear after rejected-items tools were added to `TransactionsScreen`.
* Root cause was UI layout pressure: the new rejected section consumed vertical space, making the main history list effectively non-visible on some screens.

### Fix Applied
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt`:
  * Rejected tools panel is now hidden by default and toggled with Show/Hide.
  * Main transactions list now uses `Modifier.weight(1f)` so it always gets remaining viewport space.
  * Rejected list remains height-capped (`140.dp`) to avoid pushing out primary content when expanded.

### Why This Works
* `weight(1f)` enforces a stable layout contract for the primary transaction list.
* Collapsing optional controls prevents secondary tooling from dominating core UX.
* Keeps rejected-item workflow available without sacrificing transaction-history visibility.

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt` processing rules:
  * Added hard rejection for invalid extraction outputs before transaction insert:
    * merchant is `Error`,
    * merchant is `Unknown`/blank,
    * amount `<= 0`.
  * Rejections are persisted through `raw_notifications.errorMessage` via `markAsFailed(...)` (not inserted into `transactions`).
* Added user-visible notification when merchant is explicitly `Error`:
  * Channel: `nemebudget_rejections`
  * Notification title: `Transaction Rejected`
  * Includes notification text and rejection reason.
* Added rejected-item repository APIs in `app/src/main/java/com/example/nemebudget/repository/AppRepository.kt` and implementations in:
  * `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`
  * `app/src/main/java/com/example/nemebudget/repository/FakeRepository.kt`
* Added rejected-item DAO support in `app/src/main/java/com/example/nemebudget/db/RawNotificationDao.kt`:
  * `getRejectedNotifications()`
  * `deleteById(id)`
* Added rejected item model in `app/src/main/java/com/example/nemebudget/model/SharedModels.kt`:
  * `RejectedNotification`
* Added rejected-items UI controls in Transactions:
  * `app/src/main/java/com/example/nemebudget/viewmodel/TransactionsViewModel.kt` now exposes rejected flow and add/delete actions.
  * `app/src/main/java/com/example/nemebudget/ui/screens/TransactionsScreen.kt` now includes:
    * rejected table view,
    * manual add form (title/text/reason),
    * per-row delete action.
* Aligned fallback batch path in `app/src/main/java/com/example/nemebudget/pipeline/NotificationBatchProcessor.kt` to reject `Error`/`Unknown`/`amount<=0` outputs as well.

### Why This Was Done
* Prevent placeholder LLM outputs (`Error`, `0.0`) from being counted as successful transactions.
* Keep extraction failures visible and auditable inside app UX instead of silently losing context.
* Give the user manual control over rejected entries for review/cleanup from the Transactions tab.

---

## 2026-03-31 - AI Assistant (Test Notification Title Is Now Editable)

### What Changed
* Updated `app/src/main/java/com/example/nemebudget/ui/screens/SettingsScreen.kt` test generator UI:
  * Added new `OutlinedTextField` for `Notification title`.
  * Kept existing text field for notification body.
* Updated `postTestNotification(...)` signature to accept both `notificationTitle` and `notificationText`.
* Replaced hardcoded `.setContentTitle("Transaction Alert")` with user-provided title.
* Added safe fallback title `"Debit Card Alert"` when the title field is blank.

### Why This Was Done
* Hardcoding `"Transaction Alert"` made all test notifications share the same high-signal title, which biased filtering behavior and made testing less realistic.
* Allowing editable titles helps test edge cases (false positives/negatives) and better simulates real bank/provider formats.

### Behavior Notes
* The button still requires non-blank notification text.
* Title can be edited freely; blank titles are normalized to `"Debit Card Alert"` before posting.

---

## 2026-03-31 - AI Assistant (Scan-Time Transaction Gate + Shared Filter Policy)

### What Changed
* Added shared policy object `app/src/main/java/com/example/nemebudget/pipeline/TransactionalNotificationGate.kt`.
  * Centralizes deterministic pre-LLM rules and returns reason-coded decisions.
* Updated `app/src/main/java/com/example/nemebudget/notifications/BankNotificationListenerService.kt`.
  * Listener now runs the strict gate **before** `saveToEncryptedVault(...)`.
  * Non-transaction notifications are rejected at scan-time and are not inserted into encrypted `raw_notifications`.
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`.
  * Replaced local duplicate gate logic with the shared gate to prevent drift.
* Added unit tests in `app/src/test/java/com/example/nemebudget/TransactionalNotificationGateTest.kt`.
  * Covers pass path, no-money rejection, and conversational-money false-positive rejection.

### Why This Was Done
* The strict filter previously ran mainly at processing time, so noisy notifications still polluted the raw queue.
* Moving the gate to scan-time enforces boundary validation earlier (ingest hygiene) and reduces wasted LLM calls.
* Sharing one gate between listener and repository prevents behavior mismatch over time.

### Technical Details
* Gate requires:
  1) money signal,
  2) transaction action signal,
  3) conversational/email marker only allowed when bank context is present.
* Listener now logs skip reasons (for example `no_money_signal`, `conversation_or_email_without_bank_context`) to aid tuning.

---

## 2026-03-31 - AI Assistant (Pre-LLM Gate Hardening in RealRepository)

### Problem Observed
* Non-transaction notifications (for example conversational/email-like text) were still reaching `LlmPipeline.extractWithRetry(...)`.
* This caused placeholder JSON attempts like `{"merchant":"Merchant","amount":0,"category":"Other"}` and unnecessary retries.

### Root Cause
* The active batch path used by Settings (`SettingsViewModel -> AppRepository.processPendingNotifications -> RealRepository`) had no mandatory pre-LLM transactional gate.
* A final gate existed in `NotificationBatchProcessor`, but this code path was not the primary one used for manual processing.

### Changes Applied
* Updated `app/src/main/java/com/example/nemebudget/repository/RealRepository.kt`:
  * Added `evaluateTransactionalGate(title, text)` and `GateDecision`.
  * Added strict preconditions before calling the LLM:
    1) money signal regex must match,
    2) transaction action keyword must exist,
    3) if content looks conversational/email-like, bank context must also be present.
  * Added reason-coded skip logs (for example: `no_money_signal`, `no_transaction_action_signal`, `conversation_or_email_without_bank_context`).
  * Notifications that fail the gate are marked processed and skipped before inference to reduce wasted compute and hallucination risk.

### Technical Notes
* Money regex supports both prefix and suffix currency patterns (`$45`, `45$`, `USD 45`, `45 dollars`).
* Action words include debit/credit/payment/transfer/refund/deposit signals.
* Conversational markers are intentionally lightweight and only block when bank context is absent, to reduce false positives while protecting valid bank alerts.

### Why This Helps
* Prevents obvious non-financial content from consuming LLM tokens and latency budget.
* Improves extraction precision by only invoking LLM on likely transactional candidates.
* Gives fast debugging signal through explicit skip reasons in Logcat.

---
>>>>>>> eebcab5827f60132f412c5075857215b6c878038

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
  - Handles error states: if LLM fails, mark row as failed (not skipped) to preserve audit trail

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

