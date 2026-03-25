# 🎨 ELI — Frontend, UX & ViewModel Lead
### Zero-Cloud AI Budgeter | Weber State Hackathon | Due: April 3, 2026 @ 11:59 PM

---

## Your Role
You own everything the user sees and touches, **plus the ViewModel layer** that bridges Noah's data to your UI.
You are building in **Kotlin + Jetpack Compose** (Android).
Noah handles the notification service, Qwen AI engine, and encrypted database.
You two share a `Repository interface` and a set of `data classes` defined on **Day 1** —
after that, you never block each other.

**Architecture note (hybrid pipeline):**
- Notifications are ingested into encrypted `raw_notifications` first.
- AI parsing happens in batches (app open, hourly worker, and manual **Process Now**).
- Your UI job is to make processing state visible and controllable.

---

## [x] 🔴 Day 1 (March 22) — Contracts & Scaffolding 
> **Goal:** Android project running, shared data contracts committed to Git, zero waiting.
- [x] Day 1 goal

### [x] Step 1 — Create the Android Project
1. Open Android Studio → New Project → **Empty Activity (Jetpack Compose)**
2. Package name: `com.zerocloudbudget`
3. Min SDK: **API 26** (Android 8.0) — required for NotificationListenerService reliability
4. Language: **Kotlin**, Build: **Gradle (KTS)**

### [x] Step 2 — Add Your Dependencies to `build.gradle.kts`
```kotlin
// UI & Navigation
implementation("androidx.compose.ui:ui:1.6.0")
implementation("androidx.compose.material3:material3:1.2.0")
implementation("androidx.navigation:navigation-compose:2.7.6")
implementation("androidx.compose.material:material-icons-extended:1.6.0")

// ViewModel
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

// Charts
implementation("com.patrykandpatrick.vico:compose-m3:1.13.1")

// Room (read-only from your side — Noah writes to it)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// Settings persistence
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Observe/manual trigger batch processing jobs
implementation("androidx.work:work-runtime-ktx:2.10.1")
```

### [x] Step 3 — Define Shared Data Classes (COMMIT THESE FIRST — call Noah before writing a single line of logic)
Create `model/SharedModels.kt`. **Both of you must approve this before either of you writes any logic.**

```kotlin
data class Transaction(
    val id: Int = 0,
    val merchant: String,
    val amount: Double,
    val category: Category,
    val date: Long,               // Unix timestamp
    val isAiParsed: Boolean,      // Show sparkle ✨ icon if true
    val confidence: Float,        // 0.0 to 1.0 — from Qwen's JSON output
    val rawNotificationText: String = ""
)

enum class Category(val label: String, val emoji: String) {
  DINING("Dining", "🍔"),
  GROCERIES("Groceries", "🛒"),
  GAS("Gas", "⛽"),
  BILLS("Bills", "💡"),
  SUBSCRIPTIONS("Subscriptions", "📺"),
  ENTERTAINMENT("Entertainment", "🎬"),
  SHOPPING("Shopping", "🛍️"),
  TRANSPORT("Transport", "🚗"),
  HEALTH("Health", "💊"),
  TRAVEL("Travel", "✈️"),
  OTHER("Other", "💳")
}

data class Budget(
    val category: Category,
    val spent: Double,
    val limit: Double
)

data class AppSettings(
    val primaryBank: String = "",
    val ignoredApps: Set<String> = emptySet(),    // e.g. "com.venmo"
    val customRules: List<String> = emptyList()   // e.g. "Chevron = Gas"
)

// Noah exposes this from his ModelDownloadManager
data class ModelStatus(
    val isDownloaded: Boolean,
    val downloadProgress: Float,  // 0.0 to 1.0
    val modelSizeLabel: String    // e.g. "1.1 GB"
)
```

### [x] Step 4 — Define the Repository Interface (ALSO COMMIT THIS)
Create `repository/AppRepository.kt`:
```kotlin
interface AppRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    suspend fun updateTransaction(transaction: Transaction)
    fun getBudgets(): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget)
    suspend fun deleteBudget(category: Category)
    fun getSettings(): Flow<AppSettings>
    suspend fun saveSettings(settings: AppSettings)
    fun getModelStatus(): Flow<ModelStatus>
    suspend fun getTotalTransactionCount(): Int
    suspend fun wipeAllData()
    suspend fun exportToCsv(): String   // Returns CSV string — you handle the file write/share UI
}
```

### [x] Step 5 — Create Your FakeRepository
Create `repository/FakeRepository.kt`. This lets you build the full UI immediately.
```kotlin
class FakeRepository : AppRepository {
    // Generate 50 realistic fake transactions across all 8 categories
    // Spread across the past 30 days
    // Mix isAiParsed = true and false, vary confidence between 0.6 and 0.99
    // Pre-set budgets with some near-limit, some over
    // Override getModelStatus() → emit ModelStatus(isDownloaded = true, downloadProgress = 1f, modelSizeLabel = "1.1 GB")
}
```
**You will swap this for Noah's `RealRepository` on March 30. It should be a 1-line change.**

### [x] Step 6 — Navigation Shell
Set up bottom nav with 4 tabs: **Dashboard, Transactions, Budgets, Settings**
```kotlin
// Use NavHost + Scaffold + NavigationBar
// Each tab has its own navBackStackEntry so scroll position is preserved
// Bottom bar shows badge on Transactions tab for new AI-parsed items (stretch goal)
```

---

## [x] 🟠 Days 2–3 (March 23–24) — ViewModels + Dashboard Screen
> **Goal:** ViewModel layer complete for all 4 screens, Dashboard looks beautiful with fake data.
- [x] Days 2–3 goal

### [x] Step 1 — Write All 4 ViewModels First
This is your Day 2 priority. ViewModels are what connect Noah's repository to your composables.
Create `viewmodel/DashboardViewModel.kt`:
```kotlin
@HiltViewModel // or just use viewModel() without Hilt for the hackathon
class DashboardViewModel(private val repo: AppRepository) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth = _selectedMonth.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = _selectedMonth
        .flatMapLatest { month ->
            repo.getTransactionsByDateRange(
                start = month.atDay(1).toEpochMilli(),
                end = month.atEndOfMonth().toEpochMilli()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val spendingByCategory: StateFlow<Map<Category, Double>> = transactions
        .map { list -> list.groupBy { it.category }.mapValues { (_, txns) -> txns.sumOf { it.amount } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    val safeToSpend: StateFlow<Double> = transactions
        .map { list -> 2000.0 - list.sumOf { it.amount } } // 2000 = configurable monthly income
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    fun previousMonth() { _selectedMonth.update { it.minusMonths(1) } }
    fun nextMonth() { _selectedMonth.update { it.plusMonths(1) } }
}
```

Create `viewmodel/TransactionsViewModel.kt`:
```kotlin
class TransactionsViewModel(private val repo: AppRepository) : ViewModel() {
    val transactions = repo.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    var searchQuery by mutableStateOf("")
    var selectedFilter by mutableStateOf<Category?>(null)

    val filteredTransactions = combine(transactions, snapshotFlow { searchQuery }, snapshotFlow { selectedFilter }) { txns, query, filter ->
        txns.filter { t ->
            (query.isEmpty() || t.merchant.contains(query, ignoreCase = true)) &&
            (filter == null || t.category == filter)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    fun saveEdit(transaction: Transaction) = viewModelScope.launch {
        repo.updateTransaction(transaction)
        // Also push an updated rule to settings so Noah's AI learns:
        // "merchant_name = CATEGORY" added to AppSettings.customRules
    }
}
```

Create `viewmodel/BudgetsViewModel.kt` and `viewmodel/SettingsViewModel.kt` similarly —
each just collects the relevant flows from the repository and exposes `suspend fun` wrappers for actions.

Also add a lightweight processing state in `SettingsViewModel`:
```kotlin
val processingState: StateFlow<ProcessingState> // Idle, Processing(count), Success(lastRun), Error(message)
fun processNow() // calls repository/pipeline trigger
fun processOnAppOpenIfNeeded() // called once from app shell
```

### [x] Step 2 — Dashboard: "Safe to Spend" Hero Card
- Collect `safeToSpend` from `DashboardViewModel`
- Display as a large bold number at top center
- Gradient card background: dark green → teal for positive, dark red for negative
- Animate the number counting up on first composition using `animateFloatAsState`

### [x] Step 3 — Dashboard: Spending Donut Chart
```kotlin
// Collect spendingByCategory from ViewModel
// Feed into Vico PieChart composable
// Color-map each Category to a distinct color constant
// Tap a slice → highlight it + show category total in center
```

### [x] Step 4 — Dashboard: Recent Transactions Widget
- Show last 5 transactions as a mini-list
- Each row: `[Emoji] | Merchant | -$Amount | Date | ✨ if isAiParsed`
- Confidence dot: tiny colored circle (green/yellow/red) from `confidence` float
- "See All" → navigate to Transactions tab

### [x] Step 5 — Dashboard: Month Selector Header
- `< March 2026 >` arrows call `viewModel.previousMonth()` / `viewModel.nextMonth()`
- All data on screen re-filters automatically via the StateFlow chain

---

## [x] 🟡 Days 4–5 (March 25–26) — Transactions Screen
> **Goal:** Full ledger with AI confidence indicators and the edit-to-teach flow.
- [x] Days 4–5 goal

### [x] Step 1 — Grouped List by Date
```kotlin
val grouped = filteredTransactions.groupBy { txn ->
    SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(txn.date))
}
// LazyColumn with stickyHeader {} for each date group
```

### [x] Step 2 — Transaction Row Composable
- Left: Category emoji in a colored filled circle
- Middle: Merchant (bold, 1 line) + Category label (muted, small)
- Right: `-$XX.XX` in red/coral + ✨ badge if `isAiParsed`
- Bottom edge: 2px confidence bar (full width, color = green/yellow/red based on `confidence`)

### [x] Step 3 — Edit Bottom Sheet (The "Teach AI" Flow)
```kotlin
// ModalBottomSheet slides up on tap
// Fields: Merchant (OutlinedTextField), Category (dropdown ExposedDropdownMenu), Amount
// Footer note: "Correcting this teaches the AI your preferences"
// On Save → viewModel.saveEdit(transaction)
//   which also appends "${oldMerchant} = ${newCategory}" to AppSettings.customRules
//   Noah's AI will pick this up on the next notification automatically
```

### [x] Step 4 — Search & Filter Bar
- `OutlinedTextField` at top for merchant name search
- `LazyRow` of filter chips below: All | Dining | Groceries | Gas | etc.
- Chips update `viewModel.selectedFilter` which flows through to `filteredTransactions`

---

## [x] 🟢 Days 6–7 (March 27–28) — Budgets Screen
> **Goal:** Progress bars per category with live color warnings.
- [x] Days 6–7 goal

### [x] Step 1 — Budget Card Composable
```kotlin
@Composable
fun BudgetCard(budget: Budget) {
    val progress = (budget.spent / budget.limit).toFloat().coerceIn(0f, 1f)
    val targetColor = when {
        progress < 0.60f -> Color(0xFF4CAF50)   // Green
        progress < 0.85f -> Color(0xFFFFC107)   // Yellow
        else             -> Color(0xFFF44336)   // Red
    }
    val animatedColor by animateColorAsState(targetColor, tween(600))
    val animatedProgress by animateFloatAsState(progress, tween(800))
    // LinearProgressIndicator with animatedColor and animatedProgress
    // Row below: "${budget.category.emoji} ${budget.category.label}" left, "$spent / $limit" right
}
```

### [x] Step 2 — Add / Edit Budget Dialog
- FAB (+) button opens `AlertDialog`: category picker dropdown + amount text field
- "Save" calls `viewModel.upsertBudget()`
- Long-press a budget card → shows edit/delete options

### [x] Step 3 — Over-Budget Alert Banner
- `AnimatedVisibility` banner at top of screen whenever any category > 100%
- Red background, white text: "⚠️ Dining is over budget"
- Tapping it animates the `LazyColumn` scroll to the offending card

---

## [x] 🔵 Days 8–9 (March 29–30) — Settings Screen + CSV Export + Batch Controls
> **Goal:** All controls Noah needs, shipped early. CSV export fully wired.
- [x] Days 8–9 goal

### [x] Step 1 — Primary Bank Input
```kotlin
OutlinedTextField(
    value = settings.primaryBank,
    label = { Text("Primary Bank (e.g. America First)") },
    onValueChange = { viewModel.updatePrimaryBank(it) }
)
// Auto-save with 500ms debounce in ViewModel using:
// viewModelScope.launch { delay(500); repo.saveSettings(current) }
```

### [x] Step 2 — App Ignore Toggles
Pre-filled list in your ViewModel:
```kotlin
val knownSpamApps = listOf(
    "Venmo"   to "com.venmo",
    "Cash App" to "com.squareup.cash",
    "WhatsApp" to "com.whatsapp",
    "PayPal"  to "com.paypal.android.p2pmobile",
    "Gmail"   to "com.google.android.gm"
)
// Each → Switch composable, toggled = added to settings.ignoredApps
```
- [ ] **Post-integration follow-up:** when notification listener ingest is live, auto-discover source package names from received notifications and surface new packages in `Settings -> Ignore apps` as suggested toggles (user-confirmed before adding to `settings.ignoredApps`).

### [x] Step 3 — Custom AI Rules Input
```kotlin
// OutlinedTextField: user types "Chevron = Gas"
// On submit (Enter or button): appends to settings.customRules, saves
// Rules display as removable chips below the field
// These flow directly into Noah's Qwen prompt — no extra wiring needed
```

### [x] Step 4 — Qwen Model Status Card
```kotlin
// Collect repo.getModelStatus() → ModelStatus from Noah's ModelDownloadManager
val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()

// If modelStatus.isDownloaded == false:
//   Show LinearProgressIndicator with modelStatus.downloadProgress
//   Label: "Downloading Qwen AI (one-time, ${modelStatus.modelSizeLabel})"
// If downloaded:
//   Green checkmark + "Qwen 2.5 1.5B · Ready · ${modelStatus.modelSizeLabel} on device"
```

### [x] Step 5 — Data Management Section
```kotlin
// Transaction count row: "📦 143 transactions stored locally"
// [Export to CSV] button -> calls viewModel.exportCsv()
//   which calls repo.exportToCsv() -> gets CSV string back from Noah
//   then YOU handle the Android share sheet:
val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/csv"
    putExtra(Intent.EXTRA_TEXT, csvString)
}
startActivity(Intent.createChooser(shareIntent, "Export transactions"))

// [Process Now] button -> viewModel.processNow()
// While processing: show inline spinner + "Processing 3 new notifications..."
// After complete: "Last processed 12:41 PM"

// [Wipe All Data] — red destructive button
// AlertDialog confirmation -> viewModel.wipeAll() -> repo.wipeAllData()
```

---

## [ ] ⚡ March 30–31 — INTEGRATION DAY
> **Goal:** Swap FakeRepository for Noah's RealRepository. Hybrid pipeline verified end-to-end.
- [ ] Integration day goal

### [ ] Step 1 — Wire Up Real Repository
In your Application class or ViewModel factory:
```kotlin
// Before:
val repo: AppRepository = FakeRepository()
// After:
val repo: AppRepository = RealRepository(database, dataStore, modelDownloadManager)
```
This must be a 1-line change. If anything breaks, the contract was violated somewhere — fix it together.

### [ ] Step 2 — Do NOT Touch Noah's Code
Your job today is verification, not debugging his pipeline. Test:
1. Grant notification access on the physical device
2. Fire a test ADB notification (Noah has the script)
3. Confirm the raw notification is captured first (Noah validates in DB Inspector)
4. Trigger batch processing by app open or tapping **Process Now**
5. Confirm transaction appears in your Transactions tab with ✨
6. Confirm the relevant Budget bar updates
7. Open Settings -> verify model status and processing state timestamps

---

## [ ] 🏁 April 1–2 — Onboarding, Polish & Demo Prep
- [ ] April 1–2 goal

### [ ] Step 1 — Onboarding Flow (3 screens, all Eli)
**Screen 1 — Welcome:**
- Big headline: *"Your money. Your device. No cloud."*
- 3 short bullets: "Local AI", "Zero bank logins", "Encrypted on your phone"
- "Get Started" button

**Screen 2 — Permission Gate:**
- Explain why notification access is needed in plain English
- Big button: "Grant Notification Access" → `startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))`
- `LaunchedEffect` polls every second whether permission was granted → animate checkmark when it is
- "Next" button only enabled after permission granted

**Screen 3 — Model Download:**
- Reads `modelStatus` from the ViewModel
- `LinearProgressIndicator` with percentage
- *"Downloading Qwen AI to your device — this only happens once"*
- Auto-advances to Dashboard when `isDownloaded == true`

### [ ] Step 2 — Dark Mode Finalization
- Audit every screen in both light and dark system themes
- Test on the physical device you will demo on (judges see a real phone, not an emulator)
- Make sure chart colors have enough contrast in both modes

### [ ] Step 3 — Demo Script (Practice This 5+ Times)
This is your 3-minute live demo script for April 4th:
```
1. Open app -> show Welcome screen, explain local-only privacy
2. Show Dashboard with pre-loaded fake data (you loaded these earlier)
3. Open Settings -> type "America First" in Primary Bank, show Ignore toggle for Venmo
4. [Noah fires ADB notification from his laptop]
5. Tap "Process Now" and show "Processing new notifications..."
6. You watch — transaction appears in Dashboard's Recent list with ✨ sparkle
7. Tap the transaction -> show Edit sheet -> change category -> "teaches AI"
8. Navigate to Budgets -> show a bar turning red (pre-set a low limit)
9. Show Transactions tab -> filter by Dining -> show confidence dots
```

### [ ] Step 4 — Demo Video (1–2 min, due April 2)
Record on a physical device using screen recording + narration:
- Clip 1 (15s): Welcome screen, explain the concept
- Clip 2 (30s): Notification captured -> manual/app-open batch process -> chart update
- Clip 3 (20s): Edit transaction -> budget bar responds
- Clip 4 (15s): Settings screen, explain ignore toggles, AI rules, and Process Now control
  Edit in CapCut or DaVinci Resolve. Upload to YouTube/Vimeo for Devpost.

### [ ] Step 5 — Devpost (Your Section)
Write the **User Experience** and **Innovation** sections:
- UX: clean Compose UI, real-time updates, edit-to-teach flow
- Innovation: zero bank API, zero cloud, local Qwen AI on-device

---

## 📋 Eli's Daily Checklist

| Day | Date | Deliverable |
|-----|------|-------------|
| 1 | Mar 22 | Contracts committed ✅, project builds, nav shell works |
| 2 | Mar 23 | All 4 ViewModels written and wired to FakeRepository |
| 3 | Mar 24 | Dashboard screen complete with charts + month selector |
| 4-5 | Mar 25-26 | Transactions screen + edit sheet complete |
| 6-7 | Mar 27-28 | Budgets screen + over-budget banner complete |
| 8-9 | Mar 29-30 | Settings screen + CSV export + processing controls + model status card |
| 10 | Mar 31 | Integration day — RealRepository swapped, hybrid pipeline verified |
| 11 | Apr 1 | Onboarding flow, empty states, dark mode audit |
| 12 | Apr 2 | Demo video recorded + uploaded, Devpost draft written |
| 13 | Apr 3 | Final Devpost submission by 11:59 PM ✅ |

---

## 🚨 Anti-Bottleneck Rules
- **Never wait on Noah.** FakeRepository exists so you can build everything independently.
- **ViewModel is your responsibility.** Never put business logic in a Composable — it belongs in the ViewModel.
- **Communicate contract changes immediately.** Need a new field on `Transaction`? Call Noah before adding it.
- **Git branches:** `feature/viewmodels`, `feature/dashboard`, `feature/transactions`, etc. Merge to `main` every evening.
- **Nightly APK share.** Build a debug APK and send it to Noah so he can verify UI wiring to pipeline states.
- **Make processing visible.** The `Process Now` state feedback is part of the demo story, not just a debug tool.
