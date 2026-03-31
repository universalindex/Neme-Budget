package com.example.nemebudget.db

import android.content.Context
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * 4. RoomDatabase (The Vault Manager)
 * This class brings everything together. It takes the Entity (RawNotification),
 * the Dao (RawNotificationDao), the Encryption Manager, and SQLCipher, 
 * and builds the actual encrypted database file on the phone.
 *
 * @Database tells Room this is the master file, listing all the tables (entities) inside it.
 */
@Database(entities = [RawNotification::class, TransactionEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Expose the DAO so your AppRepository can call its functions
    abstract fun rawNotificationDao(): RawNotificationDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        private const val TAG = "AppDatabase"

        // v1 -> v2 schema change:
        // 1) add processing state columns to raw_notifications
        // 2) create transactions table for parsed records
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE raw_notifications ADD COLUMN processed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE raw_notifications ADD COLUMN errorMessage TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchant TEXT NOT NULL,
                        amount REAL NOT NULL,
                        categoryLabel TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        isAiParsed INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        rawNotificationText TEXT NOT NULL,
                        type TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // v2 -> v3 schema change:
        // revert transactions table to old shape without the 'type' column.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchant TEXT NOT NULL,
                        amount REAL NOT NULL,
                        categoryLabel TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        isAiParsed INTEGER NOT NULL,
                        confidence REAL NOT NULL,
                        rawNotificationText TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO transactions_new (
                        id, merchant, amount, categoryLabel, date, isAiParsed, confidence, rawNotificationText
                    )
                    SELECT id, merchant, amount, categoryLabel, date, isAiParsed, confidence, rawNotificationText
                    FROM transactions
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE transactions")
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null
        @Volatile
        private var nativeLoaded: Boolean = false

        /**
         * The singleton pattern ensures we only ever open the encrypted file once in memory.
         * If two parts of your app try to open it at the same time, this blocks them and 
         * makes them share the same open connection to save battery and RAM.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                ensureSqlCipherNativeLoaded()

                // Get the raw 32-byte secure passphrase
                val passphrase = EncryptionManager.getDbPassphrase(context)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nemebudget_secure.db" // This is the actual file name on the Android disk
                )
                    // THIS IS WHERE SQLCIPHER STEPS IN!
                    // We pass the hardware-encrypted key from the AndroidKeyStore wrapper.
                    // If anyone tries to open the "nemebudget_secure.db" file without this key,
                    // it just looks like randomized static binary data!
                    .openHelperFactory(SupportOpenHelperFactory(passphrase))
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun ensureSqlCipherNativeLoaded() {
            if (nativeLoaded) return
            try {
                // sqlcipher-android ships libsqlcipher.so; it must be loaded before JNI calls.
                System.loadLibrary("sqlcipher")
                nativeLoaded = true
                Log.d(TAG, "SQLCipher native library loaded.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load SQLCipher native library.", t)
                throw IllegalStateException(
                    "SQLCipher native library failed to load. " +
                        "Check ABI packaging and dependency setup for net.zetetic:sqlcipher-android.",
                    t
                )
            }
        }
    }
}
