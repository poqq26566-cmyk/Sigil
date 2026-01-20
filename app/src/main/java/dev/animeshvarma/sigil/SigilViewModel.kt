package dev.animeshvarma.sigil

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.data.KeystoreRepository
import dev.animeshvarma.sigil.data.LockManager
import dev.animeshvarma.sigil.data.VaultEntry
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.EncryptionProfile
import dev.animeshvarma.sigil.model.LayerEntry
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.model.ProfileRegistry
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.util.SecureMemory
import dev.animeshvarma.sigil.util.SigilPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.system.exitProcess

class SigilViewModel(application: Application) : AndroidViewModel(application) {

    private val repository by lazy { KeystoreRepository(application) }
    private val prefs = SigilPreferences(application)
    private val _uiState = MutableStateFlow(UiState())
    private val lockManager = LockManager(application)

    val uiState: StateFlow<UiState> = _uiState
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Vault State
    private val _vaultEntries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val vaultEntries: StateFlow<List<VaultEntry>> = _vaultEntries

    private var pendingSharedText: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            refreshVault()
            loadProfiles()
        }
    }

    // --- PROFILE MANAGEMENT ---

    private fun loadProfiles() {
        val customProfiles = prefs.getCustomProfiles()
        val allProfiles = ProfileRegistry.builtInProfiles + customProfiles
        _uiState.update {
            it.copy(availableProfiles = allProfiles)
        }
    }

    fun selectProfile(profile: EncryptionProfile) {
        _uiState.update { it.copy(activeProfile = profile) }
        addLog("Profile Switched: ${profile.name}")
    }

    fun saveProfile(
        name: String,
        description: String,
        layers: List<CryptoEngine.Algorithm>,
        kdfOverride: CryptoEngine.KdfConfig?,
        compress: Boolean,
        onSuccess: () -> Unit,
        onDuplicateName: (EncryptionProfile) -> Unit
    ) {
        if (name.isBlank()) {
            addLog("Error: Profile name required.")
            return
        }
        if (layers.isEmpty()) {
            addLog("Error: Cannot save empty chain.")
            return
        }

        val currentCustom = prefs.getCustomProfiles().toMutableList()

        // Check for Name Duplication
        val duplicate = currentCustom.find { it.name.equals(name, ignoreCase = true) }
        if (duplicate != null) {
            onDuplicateName(duplicate)
            return
        }

        val newProfile = EncryptionProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            layers = layers,
            kdfConfig = kdfOverride,
            isCompressionEnabled = compress,
            isBuiltIn = false
        )

        currentCustom.add(newProfile)
        prefs.saveCustomProfiles(currentCustom)
        loadProfiles()
        selectProfile(newProfile)
        onSuccess()
        addLog("Profile Saved: $name")
    }

    fun overwriteProfile(profile: EncryptionProfile) {
        val currentCustom = prefs.getCustomProfiles().toMutableList()
        val index = currentCustom.indexOfFirst { it.name.equals(profile.name, ignoreCase = true) }
        if (index != -1) {
            currentCustom[index] = profile
            prefs.saveCustomProfiles(currentCustom)
            loadProfiles()
            selectProfile(profile)
            addLog("Profile Updated: ${profile.name}")
        }
    }

    fun deleteProfile(profileId: String) {
        val currentCustom = prefs.getCustomProfiles().toMutableList()
        if (currentCustom.removeIf { it.id == profileId }) {
            prefs.saveCustomProfiles(currentCustom)
            loadProfiles()
            // Fallback to default if deleted was active
            if (_uiState.value.activeProfile.id == profileId) {
                selectProfile(ProfileRegistry.defaultProfile)
            }
            addLog("Profile Deleted.")
        }
    }

    fun resetProfiles() {
        prefs.saveCustomProfiles(emptyList())
        loadProfiles()
        selectProfile(ProfileRegistry.defaultProfile)
        addLog("All custom profiles cleared. Reset to defaults.")
    }

    fun loadProfileToCustomMode(profile: EncryptionProfile) {
        _uiState.update {
            it.copy(
                customLayers = profile.layers.map { algo -> LayerEntry(algorithm = algo) },
                isCompressionEnabled = profile.isCompressionEnabled,
                editingProfileId = profile.id,
                selectedMode = SigilMode.CUSTOM
            )
        }
        addLog("Editing Profile: '${profile.name}'")
    }

    fun getProfileById(id: String): EncryptionProfile? {
        return _uiState.value.availableProfiles.find { it.id == id }
    }

    // --- HELPER: KDF CONFIG INJECTION ---
    private fun getActiveKdfConfig(): CryptoEngine.KdfConfig {
        // 1. Check if ACTIVE PROFILE has an override
        if (_uiState.value.selectedMode == SigilMode.AUTO) {
            val profileKdf = _uiState.value.activeProfile.kdfConfig
            if (profileKdf != null) return profileKdf
        }

        // 2. Fallback to Global Settings
        return CryptoEngine.KdfConfig(
            iterations = prefs.kdfIterations,
            memoryPow2 = prefs.kdfMemoryPow2,
            parallelism = prefs.kdfParallelism
        )
    }

    fun cachePendingIntent(text: String) {
        pendingSharedText = text
        addLog("Incoming data cached (Waiting for unlock).")
    }

    fun consumePendingIntent() {
        pendingSharedText?.let { text ->
            handleIncomingSharedText(text)
            pendingSharedText = null
        }
    }

    private fun refreshVault() {
        _vaultEntries.value = repository.getEntries()
    }

    // --- INPUT HANDLERS ---
    fun onInputTextChanged(newText: String) {
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoInput = newText)
            else it.copy(customInput = newText)
        }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = newPassword)
            else it.copy(customPassword = newPassword)
        }
    }

    // --- NAVIGATION ---
    fun onModeSelected(mode: SigilMode) {
        _uiState.update {
            it.copy(
                selectedMode = mode,
                editingProfileId = if (mode == SigilMode.CUSTOM) null else it.editingProfileId
            )
        }
    }

    fun onScreenSelected(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    // --- LOGS ---
    fun onLogsClicked() {
        _uiState.update { it.copy(showLogsDialog = !it.showLogsDialog) }
    }

    fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] $message"
        _uiState.update {
            val newLogs = (it.logs + formattedLog).takeLast(100)
            it.copy(logs = newLogs)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun clearSensitiveData() {
        _uiState.update {
            it.copy(
                autoPassword = "", autoInput = "", autoOutput = "",
                customPassword = "", customInput = "", customOutput = "",
                logs = emptyList()
            )
        }
    }

    // --- CUSTOM LAYER MANAGEMENT ---
    fun addLayers(algos: List<CryptoEngine.Algorithm>) {
        _uiState.update { state ->
            state.copy(
                customLayers = state.customLayers + algos.map { algo ->
                    LayerEntry(algorithm = algo)
                }
            )
        }
    }

    fun removeLayer(index: Int) {
        val mutable = _uiState.value.customLayers.toMutableList()
        if (index in mutable.indices) {
            mutable.removeAt(index)
            _uiState.update { it.copy(customLayers = mutable) }
        }
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.customLayers.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _uiState.update { it.copy(customLayers = list) }
        }
    }

    fun toggleCompression(enabled: Boolean) {
        _uiState.update { it.copy(isCompressionEnabled = enabled) }
    }

    // --- INTENT HANDLING ---
    fun handleIncomingSharedText(text: String) {
        if (text.isNotBlank()) {
            _uiState.update { it.copy(autoInput = text, customInput = text) }
            addLog("Received shared text from external app.")
        }
    }

    // --- CRYPTO OPERATIONS ---
    fun onEncrypt() {
        val state = _uiState.value
        val pwdString =
            if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwdString.isEmpty()) {
            addLog("Error: Encryption aborted. Password is required.")
            return
        }

        // TRIGGER LOADING
        _uiState.update { it.copy(isLoading = true) }

        // AGGRESSIVE WIPE: Clear password from UI immediately so it doesn't linger in View State
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = "")
            else it.copy(customPassword = "")
        }

        addLog("Encryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = pwdString.toCharArray()
            try {
                val chain: List<CryptoEngine.Algorithm>
                val compress: Boolean
                val kdfConfig = getActiveKdfConfig()

                if (state.selectedMode == SigilMode.AUTO) {
                    val profile = state.activeProfile
                    chain = profile.layers
                    compress = profile.isCompressionEnabled
                    addLog("Using Profile: ${profile.name}")
                } else {
                    chain = state.customLayers.map { it.algorithm }
                    compress = state.isCompressionEnabled
                }

                if (chain.isEmpty()) throw Exception("No encryption layers selected.")

                val result = CryptoEngine.encrypt(
                    data = input.toByteArray(StandardCharsets.UTF_8),
                    password = pwdChars,
                    algorithms = chain,
                    kdfConfig = kdfConfig,
                    compress = compress,
                    logCallback = { addLog(it) }
                )

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(
                        autoOutput = result,
                        isLoading = false
                    )
                    else it.copy(customOutput = result, isLoading = false)
                }
            } catch (_: Exception) {
                addLog("Error: Encryption failed.")
                _uiState.update { it.copy(isLoading = false) }
            } finally {
                SecureMemory.wipe(pwdChars)
            }
        }
    }

    fun onDecrypt() {
        val state = _uiState.value
        val pwdString =
            if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwdString.isEmpty()) {
            addLog("Error: Password required for decryption.")
            return
        }
        if (input.isEmpty()) {
            addLog("Warning: No input text found.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        // AGGRESSIVE WIPE: Clear password from UI immediately
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = "")
            else it.copy(customPassword = "")
        }

        addLog("Decryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = pwdString.toCharArray()
            try {
                val kdfConfig = getActiveKdfConfig()

                val decryptedBytes = CryptoEngine.decrypt(
                    encryptedData = input,
                    password = pwdChars,
                    kdfConfig = kdfConfig,
                    logCallback = { addLog(it) }
                )

                val decryptedString = String(decryptedBytes, StandardCharsets.UTF_8)
                SecureMemory.wipe(decryptedBytes)

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(
                        autoOutput = decryptedString,
                        isLoading = false
                    )
                    else it.copy(customOutput = decryptedString, isLoading = false)
                }

            } catch (_: Exception) {
                val errorReport = StringBuilder()
                errorReport.append("DECRYPTION FAILED\n\n")
                errorReport.append("POSSIBLE CAUSES:\n")
                errorReport.append("1. Wrong password.\n")
                errorReport.append("2. Corrupted data.\n")
                errorReport.append("3. Security Settings Mismatch (Check KDF Iterations/Memory).\n")
                errorReport.append("4. Profile Mismatch (If using custom KDF override).\n")

                val finalMessage = errorReport.toString()

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(
                        autoOutput = finalMessage,
                        isLoading = false
                    )
                    else it.copy(customOutput = finalMessage, isLoading = false)
                }

                addLog("Decryption Failed: Integrity check or Password mismatch.")
            } finally {
                SecureMemory.wipe(pwdChars)
            }
        }
    }

    // --- VAULT OPERATIONS ---
    fun saveToVault(alias: String, password: String) {
        if (alias.isBlank()) {
            addLog("Error: Key name cannot be empty.")
            return
        }
        if (password.isEmpty()) {
            addLog("Error: Cannot save empty key.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = password.toCharArray()
            try {
                val entropy = SecureMemory.calculateEntropy(password)
                repository.saveToVault(alias, pwdChars, entropy.score, entropy.label)
                refreshVault()
                addLog("Key saved to Vault as '$alias'.")
            } finally {
                SecureMemory.wipe(pwdChars)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadFromVault(entry: VaultEntry) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val secret = repository.loadFromVault(entry.alias)
            if (secret != null) {
                onPasswordChanged(secret)
                addLog("Key '${entry.alias}' loaded.")
            } else {
                addLog("Error: Failed to decrypt key.")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteFromVault(alias: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(alias)
            refreshVault()
            addLog("Key '$alias' deleted.")
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun renameVaultEntry(oldAlias: String, newAlias: String) {
        if (newAlias.isBlank()) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.renameEntry(oldAlias, newAlias)) {
                refreshVault()
                addLog("Renamed '$oldAlias' to '$newAlias'.")
            } else {
                addLog("Error: Rename failed.")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun viewKey(alias: String, onResult: (String?) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val secret = repository.loadFromVault(alias)
            _uiState.update { it.copy(isLoading = false) }
            withContext(Dispatchers.Main) {
                onResult(secret)
            }
        }
    }

    // --- DEMO / ONBOARDING CONTROL ---
    // 1. Control the Docs Tab programmatically
    private val _demoDocsTabIndex = MutableStateFlow(0)
    val demoDocsTabIndex: StateFlow<Int> = _demoDocsTabIndex

    fun setDemoMode(active: Boolean) {
        if (!active) {
            clearSensitiveData()
            refreshVault()
            toggleDemoDropdown(false)
        } else {
            _demoDocsTabIndex.value = 0
        }
    }

    fun toggleDemoDropdown(show: Boolean) {
        _uiState.update { it.copy(isDemoDropdownExpanded = show) }
    }

    fun setDocsTab(index: Int) {
        _demoDocsTabIndex.value = index
    }

    // 2. Simulate Layer Reordering for Advanced Demo
    fun demoSwapLayers() {
        val current = _uiState.value.customLayers.toMutableList()
        if (current.size >= 2) {
            val temp = current[0]
            current[0] = current[1]
            current[1] = temp
            _uiState.update { it.copy(customLayers = current) }
        }
    }

    // 3. Inject Fake Data
    fun injectDemoData(input: String, pass: String, output: String = "") {
        _uiState.update {
            it.copy(
                autoInput = input,
                autoPassword = pass,
                autoOutput = output,
                customInput = input,
                customPassword = pass
            )
        }
    }

    fun toggleDemoDrawer(open: Boolean) {
        _uiState.update { it.copy(isDemoDrawerOpen = open) }
    }

    fun injectDemoVault() {
        _vaultEntries.value = listOf(
            VaultEntry("Key 1", System.currentTimeMillis(), 100, "Unbreakable"),
            VaultEntry("Key 2", System.currentTimeMillis() - 1000000, 75, "Strong"),
            VaultEntry("Key 3", System.currentTimeMillis() - 5000000, 40, "Moderate")
        )
    }

    // --- SETTINGS OPERATIONS ---

    fun isOnboardingReset(): Boolean {
        return !prefs.hasCompletedOnboarding()
    }

    fun setOnboardingReset(reset: Boolean) {
        prefs.setOnboardingCompleted(!reset)
        if (reset) {
            addLog("Onboarding enabled for next session.")
        } else {
            addLog("Onboarding disabled.")
        }
    }
    // --- SECURITY SETTINGS ---
    fun setLockMode(mode: LockMode) {
        prefs.lockMode = mode
    }

    fun setCustomPin(pin: String) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            lockManager.setCustomPin(pin)
            delay(500)

            withContext(Dispatchers.Main) {
                addLog("Custom Security PIN set (TEE Encrypted).")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun verifyAppPin(input: String, onResult: (Boolean) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val isValid = lockManager.verifyPin(input)

            if (!isValid) delay(1000)

            _uiState.update { it.copy(isLoading = false) }

            withContext(Dispatchers.Main) {
                onResult(isValid)
            }
        }
    }


    fun wipeAllData() {
        // TRIGGER LOADING STATE
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            // 1. WIPE ALL PREFERENCES (Using delete for complete removal)
            try {
                context.deleteSharedPreferences("sigil_prefs")
                context.deleteSharedPreferences("sigil_vault_v3")
                context.deleteSharedPreferences("sigil_auth")
            } catch (_: Exception) {
                // Fallback for older APIs or weird file locks
                context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE).edit { clear() }
                context.getSharedPreferences("sigil_vault_v3", Context.MODE_PRIVATE).edit { clear() }
                context.getSharedPreferences("sigil_auth", Context.MODE_PRIVATE).edit { clear() }
            }

            // 2. WIPE HARDWARE KEYSTORE (TEE)
            try {
                val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                val aliases = ks.aliases()
                while (aliases.hasMoreElements()) {
                    val alias = aliases.nextElement()
                    ks.deleteEntry(alias)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. WIPE INTERNAL STORAGE
            try {
                context.cacheDir.deleteRecursively()
                context.filesDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. RESTART APP
            withContext(Dispatchers.Main) {
                val packageManager = context.packageManager
                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                val componentName = intent?.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(mainIntent)
                exitProcess(0)
            }
        }
    }

    fun resetAppPreferences() {
        // 1. Security & General
        prefs.lockMode = LockMode.NONE
        prefs.isGracePeriodEnabled = false
        prefs.graceDurationMinutes = 5
        prefs.isScreenShieldEnabled = true
        prefs.clipboardTimeoutSeconds = 30
        prefs.setOnboardingCompleted(false) // Reset Onboarding

        // 2. Appearance
        prefs.isDynamicColorsEnabled = true
        prefs.isDarkModeEnabled = true
        prefs.selectedThemeColor = 0xFFFFFFFF.toInt()

        // 3. KDF Defaults
        prefs.kdfIterations = 10
        prefs.kdfMemoryPow2 = 16
        prefs.kdfParallelism = 4

        addLog("Preferences reset to defaults (Vault/Profiles preserved).")

        val context = getApplication<Application>()
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        val mainIntent = Intent.makeRestartActivityTask(componentName)
        context.startActivity(mainIntent)
        exitProcess(0)
    }

    fun setDynamicColors(enabled: Boolean) { prefs.isDynamicColorsEnabled = enabled }
    fun setDarkMode(enabled: Boolean) { prefs.isDarkModeEnabled = enabled }
    fun setThemeColor(color: Color) {
        prefs.selectedThemeColor = color.toArgb()
    }

    fun getPrefs() = prefs

    fun hasSecurityPinSet(): Boolean {
        return lockManager.hasPinSet()
    }

    // --- CLIPBOARD SECURITY ---
    fun copyToClipboardSecurely(text: String, label: String = "Sigil Content") {
        if (text.isBlank()) return

        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 1. Create ClipData
        val clip = ClipData.newPlainText(label, text)

        // 2. Android 13+ Sensitive Flag (Prevents screenshot/preview of the clipboard overlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        clipboard.setPrimaryClip(clip)

        // 3. User Feedback & Timer
        val timeout = prefs.clipboardTimeoutSeconds
        Toast.makeText(context, "Copied! Wiping in ${timeout}s", Toast.LENGTH_SHORT).show()
        addLog("Sensitive data copied to clipboard.")

        // 4. Schedule Auto-Wipe
        viewModelScope.launch(Dispatchers.Main) {
            delay(timeout * 1000L)

            try {

                if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == label) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }

                    Toast.makeText(context, "Sigil: Clipboard auto-wiped.", Toast.LENGTH_SHORT).show()
                    addLog("Clipboard auto-wiped.")
                }
            } catch (_: Exception) {
            }
        }
    }
}