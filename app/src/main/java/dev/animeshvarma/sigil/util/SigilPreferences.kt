package dev.animeshvarma.sigil.util

import android.content.Context
import android.content.SharedPreferences
import dev.animeshvarma.sigil.model.LockMode

class SigilPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

        private const val KEY_LOCK_MODE = "lock_mode"
        private const val KEY_CUSTOM_PIN_HASH = "custom_pin_hash"
        private const val KEY_PIN_SALT = "custom_pin_salt"

        private const val KEY_GRACE_ENABLED = "grace_period_enabled"
        private const val KEY_GRACE_MINUTES = "grace_period_minutes"

        private const val KEY_SCREEN_SHIELD = "screen_shield_enabled"

        private const val KEY_LAST_BG_TIME = "last_background_timestamp"

        // Appearance Keys
        private const val KEY_DYNAMIC_COLORS = "dynamic_colors_enabled"
        private const val KEY_DARK_MODE = "dark_mode_enabled"
        private const val KEY_THEME_COLOR = "theme_color_int"
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    // --- Lock Configuration ---
    var lockMode: LockMode
        get() = LockMode.valueOf(prefs.getString(KEY_LOCK_MODE, LockMode.NONE.name) ?: LockMode.NONE.name)
        set(value) = prefs.edit().putString(KEY_LOCK_MODE, value.name).apply()

    var customPinHash: String?
        get() = prefs.getString(KEY_CUSTOM_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_PIN_HASH, value).apply()

    var customPinSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    // --- Grace Period ---
    var isGracePeriodEnabled: Boolean
        get() = prefs.getBoolean(KEY_GRACE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GRACE_ENABLED, value).apply()

    var graceDurationMinutes: Int
        get() = prefs.getInt(KEY_GRACE_MINUTES, 5)
        set(value) = prefs.edit().putInt(KEY_GRACE_MINUTES, value).apply()

    // --- Screen Shield ---
    var isScreenShieldEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_SHIELD, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_SHIELD, value).apply()

    // --- Timestamps ---
    var lastBackgroundTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BG_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BG_TIME, value).apply()

    // --- Appearance ---
    var isDynamicColorsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLORS, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLORS, value).apply()

    var isDarkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var selectedThemeColor: Int
        get() = prefs.getInt(KEY_THEME_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit().putInt(KEY_THEME_COLOR, value).apply()
}