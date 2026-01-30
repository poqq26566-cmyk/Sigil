package dev.animeshvarma.sigil.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import android.util.Log
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.EncryptionProfile
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.model.LockType
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SigilPreferences"

class SigilPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_LOCK_MODE = "lock_mode"
        private const val KEY_LOCK_TYPE = "lock_type_enum"
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
        private const val KEY_SAVED_PROFILES = "saved_encryption_profiles"
        private const val KEY_ACTIVE_PROFILE_ID = "active_encryption_profile_id"
    }

    /**
     * Indicates whether the onboarding flow has been completed.
     *
     * @return `true` if onboarding has been completed, `false` otherwise.
     */
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

    var lockType: LockType
        get() = try {
            LockType.valueOf(
                prefs.getString(KEY_LOCK_TYPE, LockType.PIN.name)
                    ?: LockType.PIN.name
            )
        } catch (_: Exception) {
            LockType.PIN
        }
        set(value) = prefs.edit { putString(KEY_LOCK_TYPE, value.name) }

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

    // --- PROFILE PERSISTENCE ---
    var activeProfileId: String?
        get() = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        set(value) = prefs.edit { putString(KEY_ACTIVE_PROFILE_ID, value) }

    /**
     * Load custom encryption profiles persisted in shared preferences.
     *
     * Parses the JSON array stored under KEY_SAVED_PROFILES and reconstructs each saved EncryptionProfile.
     * Malformed entries are skipped; missing optional fields use these defaults: `description` = "Custom Profile",
     * `compress` = true, `raw` = false. Each profile's `layers` are parsed by algorithm name and an optional
     * `kdf` object (with `iter`, `mem`, `par`) is converted to a KdfConfig when present.
     *
     * @return List of reconstructed custom EncryptionProfile objects; empty if none are saved or parsing fails.
     */
    fun getCustomProfiles(): List<EncryptionProfile> {
        val jsonString = prefs.getString(KEY_SAVED_PROFILES, "[]") ?: "[]"
        val profiles = mutableListOf<EncryptionProfile>()

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                try {
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val desc = obj.optString("description", "Custom Profile")
                    val compress = obj.optBoolean("compress", true)
                    val isRaw = obj.optBoolean("raw", false)

                    // Parse Layers
                    val layersArray = obj.getJSONArray("layers")
                    val layers = mutableListOf<CryptoEngine.Algorithm>()
                    for (j in 0 until layersArray.length()) {
                        layers.add(CryptoEngine.Algorithm.valueOf(layersArray.getString(j)))
                    }
                    if (layers.isEmpty()) throw IllegalArgumentException("Profile has no layers")

                    // Parse Optional KDF
                    var kdfConfig: CryptoEngine.KdfConfig? = null
                    if (obj.has("kdf")) {
                        val kdfObj = obj.getJSONObject("kdf")
                        kdfConfig = CryptoEngine.KdfConfig(
                            iterations = kdfObj.getInt("iter"),
                            memoryPow2 = kdfObj.getInt("mem"),
                            parallelism = kdfObj.getInt("par")
                        )
                    }

                    profiles.add(EncryptionProfile(
                        id = id,
                        name = name,
                        description = desc,
                        layers = layers,
                        kdfConfig = kdfConfig,
                        isBuiltIn = false,
                        isCompressionEnabled = compress,
                        isRaw = isRaw
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse profile at index $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse saved profiles JSON", e)
        }
        return profiles
    }

    /**
     * Serializes the provided custom encryption profiles and saves them to shared preferences.
     *
     * Each profile is saved as a JSON object containing `id`, `name`, `description`, `compress`, `raw`,
     * `layers` (array of algorithm names) and an optional `kdf` object with `iter`, `mem`, and `par`.
     *
     * @param profiles List of profiles to persist; built-in profiles are ignored.
     */
    fun saveCustomProfiles(profiles: List<EncryptionProfile>) {
        val jsonArray = JSONArray()
        profiles.filter { !it.isBuiltIn }.forEach { profile ->
            val obj = JSONObject()
            obj.put("id", profile.id)
            obj.put("name", profile.name)
            obj.put("description", profile.description)
            obj.put("compress", profile.isCompressionEnabled)
            obj.put("raw", profile.isRaw)

            val layersArray = JSONArray()
            profile.layers.forEach { algo ->
                layersArray.put(algo.name)
            }
            obj.put("layers", layersArray)

            profile.kdfConfig?.let { kdf ->
                val kdfObj = JSONObject()
                kdfObj.put("iter", kdf.iterations)
                kdfObj.put("mem", kdf.memoryPow2)
                kdfObj.put("par", kdf.parallelism)
                obj.put("kdf", kdfObj)
            }

            jsonArray.put(obj)
        }
        prefs.edit { putString(KEY_SAVED_PROFILES, jsonArray.toString()) }
    }

    /**
     * Resets specific categories of user preferences.
     *
     * @param resetGeneral Security, Lock Mode, Onboarding, Screen Shield, Clipboard.
     * @param resetAppearance Dynamic Colors, Dark Mode, Theme Color.
     * @param resetKdf Iterations, Memory, Parallelism.
     * @param resetProfiles Saved Encryption Profiles and Active Profile ID.
     * @param synchronous If `true`, write changes synchronously.
     */
    fun resetSettings(
        resetGeneral: Boolean,
        resetAppearance: Boolean,
        resetKdf: Boolean,
        resetProfiles: Boolean,
        synchronous: Boolean
    ) {
        prefs.edit(commit = synchronous) {
            if (resetGeneral) {
                // Security & General
                putString(KEY_LOCK_MODE, LockMode.NONE.name)
                putString(KEY_LOCK_TYPE, LockType.PIN.name)
                putBoolean(KEY_GRACE_ENABLED, false)
                putInt(KEY_GRACE_MINUTES, 5)
                putBoolean(KEY_SCREEN_SHIELD, true)
                putInt(KEY_CLIPBOARD_TIMEOUT, 30)
                putBoolean(KEY_ONBOARDING_COMPLETED, false)
            }

            if (resetAppearance) {
                // Appearance
                putBoolean(KEY_DYNAMIC_COLORS, true)
                putBoolean(KEY_DARK_MODE, true)
                putInt(KEY_THEME_COLOR, 0xFFFFFFFF.toInt())
            }

            if (resetKdf) {
                // KDF Defaults
                putInt(KEY_KDF_ITERATIONS, 10)
                putInt(KEY_KDF_MEMORY, 16)
                putInt(KEY_KDF_PARALLELISM, 4)
            }

            if (resetProfiles) {
                // Profiles
                putString(KEY_SAVED_PROFILES, "[]")
                remove(KEY_ACTIVE_PROFILE_ID)
            }
        }
    }
}