package com.example.nemebudget.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * 4. RoomDatabase (The Vault Manager)
 * This class brings everything together. It takes the Entity (RawNotification),
 * the Dao (RawNotificationDao), the Encryption Manager, and SQLCipher, 
 * and builds the actual encrypted database file on the phone.
 *
 * @Database tells Room this is the master file, listing all the tables (entities) inside it.
 */
@Database(entities = [RawNotification::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // Expose the DAO so your AppRepository can call its functions
    abstract fun rawNotificationDao(): RawNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * The singleton pattern ensures we only ever open the encrypted file once in memory.
         * If two parts of your app try to open it at the same time, this blocks them and 
         * makes them share the same open connection to save battery and RAM.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // IMPORTANT: SQLCipher requires loading its native libraries before use!
                SQLiteDatabase.loadLibs(context)

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
                    .openHelperFactory(SupportFactory(passphrase))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


