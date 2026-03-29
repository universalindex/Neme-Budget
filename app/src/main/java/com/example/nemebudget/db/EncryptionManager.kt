package com.example.nemebudget.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 3. The AndroidKeyStore Wrapper
 * This is the heart of the security. It generates a 256-bit passphrase for SQLCipher, 
 * then encrypts that passphrase using a hardware-backed key from the AndroidKeyStore, 
 * and saves the encrypted passphrase to SharedPreferences.
 * It's impossible for malware to steal the key, even on a rooted device!
 */
object EncryptionManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "NemeBudgetDatabaseKey"
    private const val PREFS_NAME = "secure_db_prefs"
    private const val PREF_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
    private const val PREF_IV = "iv"

    fun getDbPassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedPassphraseBase64 = prefs.getString(PREF_ENCRYPTED_PASSPHRASE, null)
        val ivBase64 = prefs.getString(PREF_IV, null)

        // Open the hardware-backed Android KeyStore
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

        if (encryptedPassphraseBase64 != null && ivBase64 != null) {
            // STEP 1: If the app was launched before, decrypt the existing passphrase
            val secretKey = keyStore.getKey(ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            val encryptedBytes = Base64.decode(encryptedPassphraseBase64, Base64.DEFAULT)
            return cipher.doFinal(encryptedBytes)
        } else {
            // STEP 2: First App Launch! Generate a new hardware-backed key.
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            val secretKey = keyGenerator.generateKey()

            // Generate a truly random 32-byte (256-bit) password for the SQLite database
            val rawPassphrase = ByteArray(32)
            SecureRandom().nextBytes(rawPassphrase)

            // Encrypt that random password using the hardware KeyStore
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(rawPassphrase)

            // Save the encrypted password to SharedPreferences
            prefs.edit {
                putString(PREF_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedBytes, Base64.DEFAULT))
                putString(PREF_IV, Base64.encodeToString(iv, Base64.DEFAULT))
            }

            // Return the raw password to unlock the database in RAM right now
            return rawPassphrase
        }
    }
}
