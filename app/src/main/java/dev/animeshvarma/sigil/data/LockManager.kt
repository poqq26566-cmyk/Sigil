package dev.animeshvarma.sigil.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.util.BiometricHelper
import dev.animeshvarma.sigil.util.SecureMemory
import dev.animeshvarma.sigil.util.SigilPreferences
import java.security.MessageDigest
import java.security.SecureRandom

class LockManager(context: Context) {

    private val prefs = SigilPreferences(context)

    private val authPrefs = context.getSharedPreferences("sigil_auth", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "AUTH_PIN_HASH"
        private const val KEY_PIN_SALT = "AUTH_PIN_SALT"
        private const val SALT_SIZE = 16
    }

    // --- STATE CHECKS ---
    fun isAppLocked(): Boolean {
        if (prefs.lockMode == LockMode.NONE) return false

        if (BiometricHelper.hasBiometricChanged()) {
            prefs.lockMode = LockMode.CUSTOM
            resetGracePeriod()
            return true
        }

        val lastTime = prefs.lastBackgroundTimestamp
        if (lastTime == 0L) return true

        val currentTime = System.currentTimeMillis()
        val elapsedMillis = currentTime - lastTime

        return if (prefs.isGracePeriodEnabled) {
            val graceMillis = prefs.graceDurationMinutes * 60 * 1000L
            elapsedMillis > graceMillis
        } else {
            true
        }
    }

    fun recordBackgroundEvent() {
        prefs.lastBackgroundTimestamp = System.currentTimeMillis()
    }

    fun resetGracePeriod() {
        prefs.lastBackgroundTimestamp = 0L
    }

    // --- PIN MANAGEMENT (Argon2 Hashing) ---
    fun setAppLock(secret: String, type: dev.animeshvarma.sigil.model.LockType) {
        val secretChars = secret.toCharArray()

        // 1. Generate Random Salt
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)

        try {
            // 2. Calculate Argon2 Hash
            val hash = CryptoEngine.hashPin(secretChars, salt)

            // 3. Store Base64 encoded Salt and Hash
            authPrefs.edit {
                putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            }

            // 4. Update Preferences
            prefs.lockMode = LockMode.CUSTOM
            prefs.lockType = type

        } finally {
            // 5. Cleanup memory
            SecureMemory.wipe(secretChars)
        }
    }

    fun verifySecret(inputSecret: String): Boolean {
        // 1. Load Salt & Hash
        val saltString = authPrefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHashString = authPrefs.getString(KEY_PIN_HASH, null) ?: return false

        val salt = Base64.decode(saltString, Base64.NO_WRAP)
        val storedHash = Base64.decode(storedHashString, Base64.NO_WRAP)

        val inputChars = inputSecret.toCharArray()

        return try {
            // 2. Hash the Input using stored Salt
            val inputHash = CryptoEngine.hashPin(inputChars, salt)

            // 3. Constant-Time Comparison
            val isMatch = MessageDigest.isEqual(storedHash, inputHash)

            if (isMatch) {
                BiometricHelper.resetBiometrics()
            }

            isMatch

        } catch (_: Exception) {
            false
        } finally {
            // 4. Wipe Input Memory
            SecureMemory.wipe(inputChars)
        }
    }

    fun hasPinSet(): Boolean {
        return authPrefs.contains(KEY_PIN_HASH) && authPrefs.contains(KEY_PIN_SALT)
    }
}