package com.example.nemebudget.notifications

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.nemebudget.db.AppDatabase
import com.example.nemebudget.db.RawNotification
import com.example.nemebudget.pipeline.TransactionalNotificationGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.content.SharedPreferences
import android.content.Context
import org.json.JSONObject
import android.os.Build
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.nemebudget.NemeApp
import com.example.nemebudget.repository.AppRepository


/**
 * 1. The Interceptor
 * This Service runs constantly in the background (if the user grants permission).
 * Every single time ANY app on the phone posts a notification, Android calls `onNotificationPosted`.
 */
class BankNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo: AppRepository by lazy { (applicationContext as NemeApp).repository }
    // Written when settings change, read on every notification
    @Volatile private var ignoredApps: Set<String> = emptySet()

    private lateinit var prefs: SharedPreferences
    private companion object {
        const val CAPTURE_CHANNEL_ID = "capture_confirmations"
        const val CAPTURE_NOTIFICATION_ID = 4201
        const val TEST_CHANNEL_ID = "test_bank_channel"   // same string as in SettingsScreen
        const val DUPLICATE_WINDOW_MS = 2 * 60 * 1000L
    }


    // Must be kept in a field: SharedPreferences only holds listeners weakly,
    // so a listener created inline would get garbage collected and silently stop working.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == "app_settings_json") loadSettings()
    }
    private fun processNowInBackground() {
        serviceScope.launch {
            try {
                repo.processPendingNotifications(limit = 10)
            } catch (t: Throwable) {
                Log.e("BankListener", "Immediate processing failed", t)
            }
        }
    }
    private fun notifyCaptured() {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CAPTURE_CHANNEL_ID, "Captured transactions", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val notification = NotificationCompat.Builder(this, CAPTURE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Transaction captured")
            .setContentText("Saved for processing")
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(CAPTURE_NOTIFICATION_ID, notification)
    }
    @Volatile private var processNow = false

    private fun loadSettings() {
        val root = try {
            prefs.getString("app_settings_json", null)?.let { JSONObject(it) }
        } catch (t: Throwable) {
            null
        }
        val array = root?.optJSONArray("ignoredApps")
        ignoredApps = buildSet {
            if (array != null) {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        processNow = root?.optBoolean("processNow", false) ?: false
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        prefs = applicationContext.getSharedPreferences("nemebudget_prefs", Context.MODE_PRIVATE)
        loadSettings()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.d("BankListener", "Service Connected! Monitoring for bank alerts.")
    }


    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        super.onListenerConnected()
        prefs = applicationContext.getSharedPreferences("nemebudget_prefs", Context.MODE_PRIVATE)
        loadSettings()
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.w("BankListener", "Listener disconnected by system. Requesting rebind.")
            try {
                val component = ComponentName(applicationContext, BankNotificationListenerService::class.java)
                NotificationListenerService.requestRebind(component)
                Log.i("BankListener", "Rebind request submitted for notification listener.")
            } catch (t: Throwable) {
                Log.e("BankListener", "Failed to request listener rebind.", t)
            }
    }
    private fun wordsRegex(words: Set<String>): Regex {
        val alternation = words.joinToString("|") { Regex.escape(it) }
        return Regex("""(?<!\w)(?:$alternation)(?!\w)""")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (packageName in ignoredApps) return
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras
        if (packageName in ignoredApps) return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageName == applicationContext.packageName &&
                sbn.notification.channelId != TEST_CHANNEL_ID
            ) return
        }
        Log.d("BankListener", "Intercepted from: $packageName | Title: $title")

        // 2. Optimized SMS Filtering
        // If it's a messaging app, we only proceed if the sender (Title) looks like a bank code.

        // 3. Strict transactional filter
        // Shared strict gate: only persist high-confidence transaction candidates.
        val gate = TransactionalNotificationGate.evaluate(title = title, text = text)
        if (gate.passed) {
            Log.d("BankListener", "Match found! Saving to encrypted database.")
            saveToEncryptedVault(packageName, sbn.postTime, title, text)
            notifyCaptured()
            if (processNow) processNowInBackground()
        } else {
            Log.d("BankListener", "Skipped at scan-time gate: ${gate.reason}")
        }
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
