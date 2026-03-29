package com.example.nemebudget.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.db.RawNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 1. The Interceptor
 * This Service runs constantly in the background (if the user grants permission).
 * Every single time ANY app on the phone posts a notification, Android calls `onNotificationPosted`.
 */
class BankNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Keywords used to identify potential financial transactions
    private val financialKeywords = listOf(
        "spent", "paid", "charge", "transaction", "payment", 
        "purchase", "receipt", "sent", "received", "transfer", "$"
    )

    // Known SMS shortcodes for banks (Add more as needed)
    private val bankShortcodes = listOf("24273", "73422", "23411")

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("BankListener", "Service Connected! Monitoring for bank alerts.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras

        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val fullContent = "$title $text".lowercase(Locale.ROOT)

        Log.d("BankListener", "Intercepted from: $packageName | Title: $title")

        // 2. Optimized SMS Filtering
        // If it's a messaging app, we only proceed if the sender (Title) looks like a bank code.
        val isSmsApp = packageName.contains("messaging") || packageName.contains("sms")
        if (isSmsApp) {
            val isFromBank = bankShortcodes.any { code -> title.contains(code) }
            if (!isFromBank) {
                // It's a text message from a human, skip it to save battery and privacy!
                return 
            }
        }

        // 3. General Keyword Filter
        if (isFinancialNotification(fullContent)) {
            Log.d("BankListener", "Match found! Saving to encrypted database.")
            saveToEncryptedVault(packageName, sbn.postTime, title, text)
        }
    }

    private fun isFinancialNotification(content: String): Boolean {
        if (content.isBlank()) return false
        return financialKeywords.any { keyword -> content.contains(keyword) }
    }

    private fun saveToEncryptedVault(packageName: String, postTime: Long, title: String, text: String) {
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.rawNotificationDao()

                val rawNotif = RawNotification(
                    packageName = packageName,
                    postTimeMillis = postTime,
                    title = title,
                    text = text
                )

                dao.insert(rawNotif)
                Log.d("BankListener", "Notification encrypted and secured.")
                
            } catch (e: Exception) {
                Log.e("BankListener", "Database Error: ${e.message}")
            }
        }
    }
}
