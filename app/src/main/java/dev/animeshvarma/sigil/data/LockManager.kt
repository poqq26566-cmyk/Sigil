package dev.animeshvarma.sigil.data

import android.content.Context
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.util.SigilPreferences

class LockManager(context: Context) {

    private val prefs = SigilPreferences(context)
    private val repository = KeystoreRepository(context)

    companion object {
        private const val APP_PIN_ALIAS = "_SIGIL_APP_ACCESS_PIN"
    }

    // --- STATE CHECKS ---
    fun isAppLocked(): Boolean {
        if (prefs.lockMode == LockMode.NONE) return false

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

    // --- PIN MANAGEMENT (TEE Encrypted) ---
    fun setCustomPin(pin: String) {
        // Score/Label are placeholders as this is internal
        repository.saveToVault(APP_PIN_ALIAS, pin, 100, "Internal")
        prefs.lockMode = LockMode.CUSTOM
    }

    fun verifyPin(inputPin: String): Boolean {
        // Load decrypted PIN from TEE
        val storedPin = repository.loadFromVault(APP_PIN_ALIAS)
        return storedPin == inputPin
    }

    fun getStoredPin(): String? {
        return repository.loadFromVault(APP_PIN_ALIAS)
    }
}