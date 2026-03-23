# 🎨 DEV 1 — Frontend & UX Lead
### Zero-Cloud AI Budgeter | Weber State Hackathon | Due: April 3, 2026 @ 11:59 PM

---

## Your Role
You own everything the user sees and touches. You are building in **Kotlin + Jetpack Compose** (Android).
Dev 2 handles the backend pipeline. You two share a `Repository interface` and a set of `data classes`
defined on **Day 1** — after that, you never block each other.

---

## 🔴 Day 1 (March 22) — Contracts & Scaffolding
> **Goal:** Android project running, shared data contracts committed to Git, zero waiting.

### Step 1 — Create the Android Project
1. Open Android Studio → New Project → **Empty Activity (Jetpack Compose)**
2. Package name: `com.zerocloudbudget`
3. Min SDK: API 26 (Android 8.0) — required for NotificationListenerService reliability
4. Language: **Kotlin**, Build: **Gradle (KTS)**

### Step 2 — Add Dependencies to `build.gradle.kts`
```kotlin
// UI
implementation("androidx.compose.ui:ui:1.6.0")
implementation("androidx.compose.material3:material3:1.2.0")
implementation("androidx.navigation:navigation-compose:2.7.6")

// Charts
implementation("com.patrykandpatrick.vico:compose-m3:1.13.1") // Vico charts

// Database (for reading — Dev 2 writes to it)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// Settings persistence
implementation("androidx.datastore:datastore-preferences:1.0.0")

// Icons
implementation("androidx.compose.material:material-icons-extended:1.6.0")
```

### Step 3 — Define Shared Data Classes (COMMIT THESE FIRST)
Create `model/SharedModels.kt`. Dev 2 must agree on this before either of you writes any logic.

```kotlin
data class Transaction(
    val id: Int = 0,
    val merchant: String,
    val amount: Double,
    val category: Category,
    val date: Long,            // Unix timestamp
    val isAiParsed: Boolean,   // Show sparkle icon if true
    val confidence: Float,     // 0.0 to 1.0
    val rawNotificationText: String = ""
)

enum class Category(val label: String, val emoji: String) {
    DINING("Dining", "🍔"),
    GROCERIES("Groceries", "🛒"),
    GAS("Gas", "⛽"),
    BILLS("Bills", "💡"),
    ENTERTAINMENT("Entertainment", "🎬"),
    SHOPPING("Shopping", "🛍️"),
    TRANSPORT("Transport", "🚗"),
    OTHER("Other", "💳")
}

data class Budget(
    val category: Category,
    val spent: Double,
    val limit: Double
)

data class AppSettings(
    val primaryBank: String = "",
    val ignoredApps: Set<String> = emptySet(),   // e.g. "com.venmo"
    val customRules: List<String> = emptyList()  // e.g. "Chevron = Gas"
)
```

### Step 4 — Define the Repository Interface (ALSO COMMIT THIS)
Create `repository/AppRepository.kt`:
```kotlin
interface AppRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByDateRange(start: Long, end: Long): Flow<List<Transaction>>
    suspend fun updateTransaction(transaction: Transaction)
    fun getBudgets(): Flow<List<Budget>>
    suspend fun upsertBudget(budget: Budget)
    fun getSettings(): Flow<AppSettings>
    suspend fun saveSettings(settings: AppSettings)
}
```

### Step 5 — Create Your FakeRepository
Create `repository/FakeRepository.kt`. This lets you build the full UI today.
Generate 50 realistic fake transactions covering all categories and the past 30 days.
**You will swap this out for Dev 2's `RealRepository` on March 30th.**

### Step 6 — Navigation Shell
Set up bottom nav with 4 tabs: **Dashboard, Transactions, Budgets, Settings**
Use `NavHost` with a `Scaffold` and `NavigationBar` at the bottom.

---

## 🟠 Days 2–3 (March 23–24) — Dashboard Screen
> **Goal:** The home screen looks beautiful and works with fake data.

### Step 1 — "Safe to Spend" Hero Card
- Calculate: `safeToSpend = totalMonthlyIncome - totalSpentThisMonth`
- Display as a large bold number at top center
- Use a subtle gradient card background (dark green to teal for positive, red for negative)
- Animate the number counting up on first load using `animateFloatAsState`

### Step 2 — Spending Donut Chart
Using Vico:
```kotlin
// Pull this month's transactions, group by Category, sum amounts
// Feed into Vico's PieChart composable
// Color map each Category to a distinct color
```
- Show category labels around the chart with amounts
- Tap a slice → highlight it and show category total in center

### Step 3 — Recent Transactions Widget
- Show last 5 transactions as a mini-list
- Each row: `[Category Emoji] | Merchant Name | -$Amount | Date`
- If `isAiParsed == true`, show a small ✨ sparkle badge on the right
- Tap "See All" → navigates to Transactions tab

### Step 4 — Month Selector Header
- `<  March 2026  >` arrows to navigate months
- All data on screen filters to selected month

---

## 🟡 Days 4–5 (March 25–26) — Transactions Screen
> **Goal:** Full transaction ledger with AI confidence indicators and edit flow.

### Step 1 — Grouped List by Date
```kotlin
// Group List<Transaction> by date using:
transactions.groupBy { 
    SimpleDateFormat("MMMM d, yyyy").format(Date(it.date)) 
}
// Use LazyColumn with sticky headers for each date group
```

### Step 2 — Transaction Row Composable
Each row shows:
- Left: Category emoji in a colored circle
- Middle: Merchant name (bold) + Category label (muted)
- Right: Amount in red + ✨ icon if AI parsed
- Subtle confidence indicator: a tiny colored dot (green/yellow/red) based on `confidence`

### Step 3 — Edit Bottom Sheet
When user taps a transaction:
- `ModalBottomSheet` slides up
- Shows: Merchant text field, Category dropdown, Amount field
- "Save" button calls `repository.updateTransaction()`
- Add a note: *"Saving this will teach the AI for next time"* (for demo storytelling — the actual learning rule gets passed to Dev 2 as a custom rule string)

### Step 4 — Search & Filter Bar
- Search field at top filters by merchant name
- Filter chips: All | Dining | Groceries | Gas | etc.

---

## 🟢 Days 6–7 (March 27–28) — Budgets Screen
> **Goal:** Progress bars per category with visual warnings.

### Step 1 — Budget Card Composable
```kotlin
@Composable
fun BudgetCard(budget: Budget) {
    val progress = (budget.spent / budget.limit).coerceIn(0.0, 1.0)
    val color = when {
        progress < 0.6f -> Color(0xFF4CAF50)  // Green
        progress < 0.85f -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336)             // Red
    }
    // AnimatedLinearProgressIndicator with color transition
}
```

### Step 2 — Add/Edit Budget Flow
- FAB (+) button opens a dialog: select Category, enter limit amount
- Store budgets via `repository.upsertBudget()`
- Budget `spent` field is calculated live from the transactions in DB

### Step 3 — Over-Budget Alert Banner
- If ANY category is > 100%, show a red banner at the top of the screen
- Tapping it scrolls to the offending category

---

## 🔵 Days 8–9 (March 29–30) — Settings Screen
> **Goal:** All controls Dev 2 needs — ship this early so they can start reading from DataStore.

### Step 1 — Primary Bank Input
```kotlin
OutlinedTextField(
    value = settings.primaryBank,
    label = { Text("Primary Bank (e.g. America First)") },
    onValueChange = { /* update via ViewModel */ }
)
```
Auto-saves with a 500ms debounce using `LaunchedEffect + delay`.

### Step 2 — App Ignore Toggles
Hardcoded list of common apps that spam notifications:
- Venmo (`com.venmo`)
- Cash App (`com.squareup.cash`)  
- WhatsApp (`com.whatsapp`)
- PayPal (`com.paypal.android.p2pmobile`)

Each is a `Switch` composable. Toggled apps are added to `AppSettings.ignoredApps`.

### Step 3 — Custom AI Rules
```kotlin
// Text field where user types rules like:
// "Chevron = Gas"
// "Spotify = Entertainment"
// Stored as List<String> in AppSettings.customRules
// Shows as chips below the field with an X to remove
```

### Step 4 — Data Management Section
- **Export to CSV**: Button → (stub for now, wire up in Phase 3)
- **Wipe All Data**: Destructive button with confirmation dialog
- Shows total transaction count: *"123 transactions stored locally"*

### Step 5 — Model Status Card
- Shows LLM download status (read from a SharedFlow Dev 2 exposes)
- A progress bar if the model is downloading, green checkmark when ready
- Estimated model size: *"2.1 GB on device"*

---

## ⚡ March 30–31 — INTEGRATION DAY
> **Goal:** Swap FakeRepository for RealRepository. The pipeline goes end-to-end.

### Step 1 — Wire Up Real Repository
In your DI setup (or just in your ViewModels), replace:
```kotlin
val repo: AppRepository = FakeRepository()
// with:
val repo: AppRepository = RealRepository(database, dataStore)
```
This should be a 1-line change if contracts were followed. If anything breaks, it's a contract violation — fix the contract, not the UI.

### Step 2 — Test the Live Pipeline
1. On a real Android device, enable Notification Access for your app
2. Use your bank app or trigger a test notification
3. Watch the Dashboard update in real-time

### Step 3 — Smooth Any Rough Edges
- Add a `CircularProgressIndicator` while data loads
- Add an empty state (`"No transactions yet. Waiting for notifications..."`)
- Make sure rotation/recomposition doesn't cause flickers

---

## 🏁 April 1–2 — Polish & Demo Prep

### Step 1 — Onboarding Flow (2 screens)
Screen 1 — Welcome:
- Big headline: *"Your money. Your device. No cloud."*  
- 3 bullet points explaining local AI
- "Get Started" button

Screen 2 — Permission Gate:
- Explain why notification access is needed
- Big prominent button: "Grant Notification Access" → opens Android system settings
- Visual feedback once permission is granted (checkmark animation)

Screen 3 — Model Download:
- Progress bar with percentage
- Message: *"Downloading AI brain to your device (one-time setup)"*
- Reads progress from Dev 2's download status Flow

### Step 2 — Dark Mode Finalization
- Verify all colors work in both light and dark mode
- Test on a physical device (the judges will see a real phone)

### Step 3 — Demo Script Prep
Practice this flow for the 3-minute live demo:
1. Show the empty Dashboard (explain local-only privacy)
2. Open Settings, show the bank name field and ignore toggles
3. Trigger a fake bank notification from another device
4. Watch the transaction appear in real-time with a ✨ sparkle
5. Tap it → edit the category → show it updates the Budget bar
6. Show the Budgets screen turning red (set a low limit beforehand)

---

## 📋 Your Daily Checklist

| Day | Date | Deliverable |
|-----|------|-------------|
| 1 | Mar 22 | Contracts committed, project builds, navigation shell works |
| 2-3 | Mar 23-24 | Dashboard screen complete with fake data |
| 4-5 | Mar 25-26 | Transactions screen + edit sheet complete |
| 6-7 | Mar 27-28 | Budgets screen complete |
| 8-9 | Mar 29-30 | Settings screen complete + integration with real DB |
| 10 | Mar 31 | Full pipeline tested on real device |
| 11 | Apr 1 | Onboarding flow, polish, empty states |
| 12 | Apr 2 | Demo video recorded, Devpost written |
| 13 | Apr 3 | SUBMIT by 11:59 PM |

---

## 🚨 Anti-Bottleneck Rules
- **Never wait on Dev 2.** If a feature needs real data, stub it with fake data and move on.
- **Communicate contract changes immediately.** If you need a new field on `Transaction`, tell Dev 2 before adding it.
- **Use Git branches:** `feature/dashboard`, `feature/transactions`, etc. Merge to `main` daily.
- **Share a test APK every evening** via Discord/AirDrop so you can catch integration issues early.
