package dev.animeshvarma.sigil.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.animeshvarma.sigil.model.LockMode

class SigilPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LOCK_MODE = "lock_mode"
        private const val KEY_KDF_ITERATIONS = "kdf_iterations"
        private const val KEY_KDF_MEMORY = "kdf_memory_pow2"
        private const val KEY_KDF_PARALLELISM = "kdf_parallelism"
        private const val KEY_GRACE_ENABLED = "grace_period_enabled"
        private const val KEY_GRACE_MINUTES = "grace_period_minutes"
        private const val KEY_CLIPBOARD_TIMEOUT = "clipboard_timeout_seconds"
        private const val KEY_SCREEN_SHIELD = "screen_shield_enabled"
        private const val KEY_LAST_BG_TIME = "last_background_timestamp"
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors_enabled"
        private const val KEY_DARK_MODE = "dark_mode_enabled"
        private const val KEY_THEME_COLOR = "theme_color_int"
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, completed) }
    }

    // --- Lock Configuration ---
    var lockMode: LockMode
        get() = try {
            LockMode.valueOf(prefs.getString(KEY_LOCK_MODE, LockMode.NONE.name) ?: LockMode.NONE.name)
        } catch (_: Exception) {
            LockMode.NONE
        }
        set(value) = prefs.edit { putString(KEY_LOCK_MODE, value.name) }

    // --- Grace Period ---
    var isGracePeriodEnabled: Boolean
        get() = prefs.getBoolean(KEY_GRACE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_GRACE_ENABLED, value) }

    var graceDurationMinutes: Int
        get() = prefs.getInt(KEY_GRACE_MINUTES, 5)
        set(value) = prefs.edit { putInt(KEY_GRACE_MINUTES, value) }

    // --- Screen Shield ---
    var isScreenShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_SHIELD, true)
        set(value) = prefs.edit { putBoolean(KEY_SCREEN_SHIELD, value) }

    // --- Timestamps ---
    var lastBackgroundTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BG_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_BG_TIME, value) }

    // --- Appearance ---
    var isDynamicColorsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLORS, true)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_COLORS, value) }

    var isDarkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit { putBoolean(KEY_DARK_MODE, value) }

    var selectedThemeColor: Int
        get() = prefs.getInt(KEY_THEME_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit { putInt(KEY_THEME_COLOR, value) }

    var clipboardTimeoutSeconds: Int
        get() = prefs.getInt(KEY_CLIPBOARD_TIMEOUT, 30)
        set(value) = prefs.edit { putInt(KEY_CLIPBOARD_TIMEOUT, value) }

    // --- CRYPTOGRAPHY SETTINGS ---
    var kdfIterations: Int
        get() = prefs.getInt(KEY_KDF_ITERATIONS, 10)
        set(value) = prefs.edit { putInt(KEY_KDF_ITERATIONS, value) }


    var kdfMemoryPow2: Int
        get() = prefs.getInt(KEY_KDF_MEMORY, 16)
        set(value) = prefs.edit { putInt(KEY_KDF_MEMORY, value) }

    var kdfParallelism: Int
        get() = prefs.getInt(KEY_KDF_PARALLELISM, 4)
        set(value) = prefs.edit { putInt(KEY_KDF_PARALLELISM, value) }
}