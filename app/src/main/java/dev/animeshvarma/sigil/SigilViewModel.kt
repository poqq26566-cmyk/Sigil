package dev.animeshvarma.sigil

import android.app.Application
import android.content.Context
import android.content.Intent
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
import dev.animeshvarma.sigil.model.LayerEntry
import dev.animeshvarma.sigil.model.LockMode
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
import kotlin.system.exitProcess

class SigilViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KeystoreRepository(application)
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
        refreshVault()
    }

    // --- HELPER: KDF CONFIG INJECTION ---
    private fun getUserKdfConfig(): CryptoEngine.KdfConfig {
        return CryptoEngine.KdfConfig(
            iterations = 10,
            memoryPow2 = 16,      // 64MB
            parallelism = 4
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
        _uiState.update { it.copy(selectedMode = mode) }
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

                if (state.selectedMode == SigilMode.AUTO) {
                    chain = listOf(
                        CryptoEngine.Algorithm.AES_GCM,
                        CryptoEngine.Algorithm.CHACHA20_POLY1305,
                        CryptoEngine.Algorithm.TWOFISH_CBC,
                        CryptoEngine.Algorithm.SERPENT_CBC
                    ).shuffled()

                    compress = true
                    addLog("Auto Mode: 4-Layer Hybrid Chain (AES+ChaCha+Twofish+Serpent).")
                } else {
                    chain = state.customLayers.map { it.algorithm }
                    compress = state.isCompressionEnabled
                }

                if (chain.isEmpty()) throw Exception("No encryption layers selected.")

                val result = CryptoEngine.encrypt(
                    data = input.toByteArray(StandardCharsets.UTF_8),
                    password = pwdChars,
                    algorithms = chain,
                    kdfConfig = getUserKdfConfig(),
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
                val decryptedBytes = CryptoEngine.decrypt(
                    encryptedData = input,
                    password = pwdChars,
                    kdfConfig = getUserKdfConfig(),
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
                errorReport.append("3. Different Security Settings.\n")

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
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE).edit {
                this.clear()
            }

            context.getSharedPreferences("sigil_vault_v3", Context.MODE_PRIVATE).edit {
                this.clear()
            }
            withContext(Dispatchers.Main) {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                exitProcess(0)
            }
        }
    }

    fun setDynamicColors(enabled: Boolean) { prefs.isDynamicColorsEnabled = enabled }
    fun setDarkMode(enabled: Boolean) { prefs.isDarkModeEnabled = enabled }
    fun setThemeColor(color: Color) {
        prefs.selectedThemeColor = color.toArgb()
    }

    fun getPrefs() = prefs

    fun lockAppImmediately() {
        lockManager.resetGracePeriod()
        _uiState.update { it.copy(isLoading = false) } // Safety reset
    }

    fun hasSecurityPinSet(): Boolean {
        return lockManager.hasPinSet()
    }
}