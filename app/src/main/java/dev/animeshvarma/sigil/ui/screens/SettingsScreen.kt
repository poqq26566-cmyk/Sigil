@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.util.BiometricHelper
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(viewModel: SigilViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.getPrefs()

    // --- STATE HOISTING ---
    var onboardingToggle by remember { mutableStateOf(viewModel.isOnboardingReset()) }

    // Security State
    var lockMode by remember { mutableStateOf(prefs.lockMode) }

    // Grace Period
    var graceEnabled by remember { mutableStateOf(prefs.isGracePeriodEnabled) }
    var graceMinutes by remember { mutableFloatStateOf(prefs.graceDurationMinutes.toFloat()) }

    // Cryptography (KDF)
    var kdfIterations by remember { mutableFloatStateOf(prefs.kdfIterations.toFloat()) }
    var kdfMemory by remember { mutableFloatStateOf(prefs.kdfMemoryPow2.toFloat()) }
    var kdfParallelism by remember { mutableFloatStateOf(prefs.kdfParallelism.toFloat()) }

    // Clipboard
    var clipTimeout by remember { mutableFloatStateOf(prefs.clipboardTimeoutSeconds.toFloat()) }

    // Privacy
    var screenShield by remember { mutableStateOf(prefs.isScreenShieldEnabled) }

    // Appearance
    var dynamicColors by remember { mutableStateOf(prefs.isDynamicColorsEnabled) }
    var darkMode by remember { mutableStateOf(prefs.isDarkModeEnabled) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.selectedThemeColor) }

    // Dialogs
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showVerifyPinDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showSecurityErrorDialog by remember { mutableStateOf(false) }

    // Danger Dialogs
    var showResetProfilesDialog by remember { mutableStateOf(false) }
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showWipeDataDialog by remember { mutableStateOf(false) }

    var isSavingPin by remember { mutableStateOf(false) }

    // Used to queue the target lock mode after PIN is set
    var pendingLockMode by remember { mutableStateOf<LockMode?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // --- GENERAL ---
        SettingsHeader("General")

        SettingsItem(
            title = "Show Onboarding",
            desc = "Trigger the introductory tour on the next app launch.",
            trailing = {
                Switch(
                    checked = onboardingToggle,
                    onCheckedChange = {
                        onboardingToggle = it
                        viewModel.setOnboardingReset(it)
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { restartApp(context) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Restart App")
        }

        Spacer(Modifier.height(24.dp))

        // --- CRYPTOGRAPHY ---
        SettingsHeader("Encryption Parameters (Argon2)")

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {

                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Global defaults. Profiles may override this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Iterations
                val iterVal = kdfIterations.roundToInt()
                val iterLabel = when (iterVal) {
                    10 -> "(Default)"
                    in 1..5 -> "(Fast/Weak)"
                    in 25..32 -> "(Max)"
                    else -> ""
                }
                Text("Iterations: $iterVal $iterLabel", fontWeight = FontWeight.Bold)
                Text("Higher = Harder to brute force, slower to decrypt.", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kdfIterations,
                    onValueChange = { kdfIterations = it },
                    onValueChangeFinished = { prefs.kdfIterations = kdfIterations.roundToInt() },
                    valueRange = 1f..32f,
                    steps = 30
                )

                val memoryMb = (1 shl kdfMemory.toInt()) / 1024
                val memLabel = when (memoryMb) {
                    64 -> "(Default)"
                    256 -> "(Max)"
                    else -> ""
                }

                Text("Memory Cost: ${memoryMb}MB $memLabel", fontWeight = FontWeight.Bold)

                // WARNING LOGIC
                if (kdfMemory >= 18f) {
                    Text(
                        "Warning: 256MB is extremely heavy. May crash older devices.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Slider(
                    value = kdfMemory,
                    onValueChange = { kdfMemory = it },
                    onValueChangeFinished = { prefs.kdfMemoryPow2 = kdfMemory.roundToInt() },
                    valueRange = 12f..18f,
                    steps = 5
                )

                // Parallelism
                val paraVal = kdfParallelism.roundToInt()
                val paraLabel = if (paraVal == 4) "(Default)" else ""
                Text("Parallelism: $paraVal Threads $paraLabel", fontWeight = FontWeight.Bold)
                Slider(
                    value = kdfParallelism,
                    onValueChange = { kdfParallelism = it },
                    onValueChangeFinished = { prefs.kdfParallelism = kdfParallelism.roundToInt() },
                    valueRange = 1f..8f,
                    steps = 6
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- CLIPBOARD & PRIVACY ---
        SettingsHeader("Privacy & Clipboard")

        SettingsItem(
            title = "Screen Shield",
            desc = "Block screenshots and hide content in Recents. (Requires Restart)",
            trailing = {
                Switch(checked = screenShield, onCheckedChange = {
                    screenShield = it
                    prefs.isScreenShieldEnabled = it
                })
            }
        )

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Clipboard Auto-Wipe: ${clipTimeout.toInt()}s", fontWeight = FontWeight.Bold)
                Slider(
                    value = clipTimeout,
                    onValueChange = { clipTimeout = it },
                    onValueChangeFinished = { prefs.clipboardTimeoutSeconds = clipTimeout.toInt() },
                    valueRange = 5f..120f,
                    steps = 22
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- APP LOCK ---
        SettingsHeader("App Lock")

        SettingsItem(
            title = "Require Authentication",
            desc = "Lock app on startup/resume.",
            trailing = {
                Switch(
                    checked = lockMode != LockMode.NONE,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (viewModel.hasSecurityPinSet()) {
                                lockMode = LockMode.CUSTOM
                                viewModel.setLockMode(LockMode.CUSTOM)
                            } else {
                                pendingLockMode = LockMode.CUSTOM
                                showSetPinDialog = true
                            }
                        } else {
                            lockMode = LockMode.NONE
                            viewModel.setLockMode(LockMode.NONE)
                        }
                    }
                )
            }
        )

        AnimatedVisibility(visible = lockMode != LockMode.NONE) {
            Column {
                Spacer(Modifier.height(12.dp))
                SigilSegmentedControl(
                    items = listOf("Device Biometrics", "Custom PIN"),
                    selectedIndex = if (lockMode == LockMode.CUSTOM) 1 else 0,
                    onItemSelection = { index ->
                        if (index == 0) {
                            if (!BiometricHelper.isDeviceSecure(context)) {
                                showSecurityErrorDialog = true
                            } else if (!viewModel.hasSecurityPinSet()) {
                                pendingLockMode = LockMode.DEVICE
                                showSetPinDialog = true
                            } else {
                                lockMode = LockMode.DEVICE
                                viewModel.setLockMode(LockMode.DEVICE)
                            }
                        } else {
                            if (viewModel.hasSecurityPinSet()) {
                                lockMode = LockMode.CUSTOM
                                viewModel.setLockMode(LockMode.CUSTOM)
                            } else {
                                pendingLockMode = LockMode.CUSTOM
                                showSetPinDialog = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showVerifyPinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verify My PIN")
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        pendingLockMode = lockMode
                        showSetPinDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change PIN")
                }

                Spacer(Modifier.height(12.dp))
            }
        }

        // Grace Period
        AnimatedVisibility(visible = lockMode != LockMode.NONE) {
            Column {
                SettingsItem(
                    title = "Keep Unlocked",
                    desc = "Don't lock immediately when switching apps.",
                    trailing = {
                        Switch(checked = graceEnabled, onCheckedChange = {
                            graceEnabled = it
                            prefs.isGracePeriodEnabled = it
                        })
                    }
                )

                AnimatedVisibility(visible = graceEnabled) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Timeout: ${graceMinutes.toInt()} minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = graceMinutes,
                            onValueChange = { graceMinutes = it },
                            onValueChangeFinished = { prefs.graceDurationMinutes = graceMinutes.toInt() },
                            valueRange = 1f..60f,
                            steps = 59
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- APPEARANCE ---
        SettingsHeader("Appearance")

        SettingsItem("Material You", "Use system wallpaper colors. (Requires Restart)") {
            Switch(checked = dynamicColors, onCheckedChange = {
                dynamicColors = it
                viewModel.setDynamicColors(it)
            })
        }

        AnimatedVisibility(visible = !dynamicColors) {
            Column {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable { showColorDialog = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Accent Color", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${Integer.toHexString(selectedColorInt).uppercase().takeLast(6)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(24.dp).clip(CircleShape).background(Color(selectedColorInt)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
                Spacer(Modifier.height(16.dp))

                SettingsItem("Dark Mode", "Force dark theme. (Requires Restart)") {
                    Switch(checked = darkMode, onCheckedChange = {
                        darkMode = it
                        viewModel.setDarkMode(it)
                    })
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- DATA & PROFILES ---
        SettingsHeader("Data Management")

        OutlinedButton(
            onClick = { showResetProfilesDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset Encryption Profiles")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showResetSettingsDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.SettingsBackupRestore, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset App Preferences")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showWipeDataDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Wipe All Data")
        }

        Spacer(Modifier.height(64.dp))
    }

    // --- DIALOGS ---

    if (showSecurityErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityErrorDialog = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Device Security Not Set") },
            text = { Text("Your device doesn't have a Screen Lock (PIN, Pattern, or Password) enabled.\n\nSigil requires this to secure your Biometrics.") },
            confirmButton = {
                Button(onClick = { showSecurityErrorDialog = false }) { Text("OK") }
            }
        )
    }

    if (showSetPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                if(!isSavingPin) {
                    showSetPinDialog = false
                    pendingLockMode = null
                }
            },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Set Custom PIN") },
            text = {
                Column {
                    Text("Enter a PIN to lock Sigil.\nThis will be required if Biometrics fail.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 12) pinInput = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        enabled = !isSavingPin,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.isNotEmpty()) {
                            isSavingPin = true
                            viewModel.setCustomPin(pinInput)

                            val newMode = pendingLockMode ?: LockMode.CUSTOM
                            lockMode = newMode
                            viewModel.setLockMode(newMode)

                            showSetPinDialog = false
                            isSavingPin = false
                            pendingLockMode = null
                        }
                    },
                    enabled = !isSavingPin
                ) {
                    if (isSavingPin) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Set PIN")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSetPinDialog = false
                        pendingLockMode = null
                        if (!viewModel.hasSecurityPinSet()) {
                            lockMode = LockMode.NONE
                            viewModel.setLockMode(LockMode.NONE)
                        }
                    },
                    enabled = !isSavingPin
                ) { Text("Cancel") }
            }
        )
    }

    if (showVerifyPinDialog) {
        var testInput by remember { mutableStateOf("") }
        var verificationResult by remember { mutableStateOf<Boolean?>(null) }
        var isVerifying by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showVerifyPinDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, null) },
            title = { Text("Verify PIN") },
            text = {
                Column {
                    Text("Type your PIN to check if it matches.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = {
                            testInput = it
                            verificationResult = null
                        },
                        label = { Text("Enter PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )

                    if (verificationResult != null) {
                        Spacer(Modifier.height(8.dp))
                        if (verificationResult == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Correct!", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Text("Incorrect", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isVerifying = true
                        viewModel.verifyAppPin(testInput) { isValid ->
                            isVerifying = false
                            verificationResult = isValid
                        }
                    },
                    enabled = !isVerifying && testInput.isNotEmpty()
                ) {
                    if (isVerifying) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Check")
                }
            },
            dismissButton = { TextButton(onClick = { showVerifyPinDialog = false }) { Text("Close") } }
        )
    }

    if (showColorDialog) {
        AdvancedColorPickerDialog(
            initialColor = Color(selectedColorInt),
            onDismiss = { showColorDialog = false },
            onColorSelected = {
                selectedColorInt = it.toArgb()
                viewModel.setThemeColor(it)
                showColorDialog = false
            }
        )
    }

    // CONFIRM RESET PROFILES
    if (showResetProfilesDialog) {
        AlertDialog(
            onDismissRequest = { showResetProfilesDialog = false },
            icon = { Icon(Icons.Default.Restore, null) },
            title = { Text("Reset Profiles") },
            text = { Text("Delete all custom encryption profiles and restore the default list?\n\nThis cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.resetProfiles()
                    showResetProfilesDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetProfilesDialog = false }) { Text("Cancel") }
            }
        )
    }

    // CONFIRM RESET SETTINGS
    if (showResetSettingsDialog) {
        var shouldRestart by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            icon = { Icon(Icons.Default.SettingsBackupRestore, null) },
            title = { Text("Reset Preferences") },
            text = {
                Column {
                    Text("Reset all application settings (Theme, Security, KDF) to defaults?\n\nThis will update the UI immediately, application of some changes will require a restart.")

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { shouldRestart = !shouldRestart },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = shouldRestart,
                            onCheckedChange = { shouldRestart = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart App immediately")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // 1. Save defaults to SharedPreferences (and Restart if checked)
                    viewModel.resetAppPreferences(shouldRestart)

                    // 2. IF NOT RESTARTING, MANUALLY UPDATE UI STATE
                    if (!shouldRestart) {
                        onboardingToggle = true

                        // Security
                        lockMode = LockMode.NONE
                        graceEnabled = false
                        graceMinutes = 5f

                        // KDF (Defaults based on your ViewModel/Prefs)
                        kdfIterations = 10f
                        kdfMemory = 16f
                        kdfParallelism = 4f

                        // Privacy
                        clipTimeout = 30f
                        screenShield = true

                        // Appearance
                        dynamicColors = true
                        darkMode = true
                        selectedColorInt = android.graphics.Color.WHITE // 0xFFFFFFFF
                    }

                    showResetSettingsDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // CONFIRM WIPE ALL
    if (showWipeDataDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDataDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Scorched Earth") },
            text = { Text("This will permanently delete ALL data, including:\n- Keys in Vault\n- Settings & Profiles\n- PINs & Biometrics\n\nThe app will restart.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.wipeAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("NUKE IT") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(title: String, desc: String, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(desc) },
        trailingContent = trailing,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
fun RowScope.RGBField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

@Composable
fun AdvancedColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var currentColor by remember { mutableStateOf(initialColor) }

    // HSV State
    val hsv = remember {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), arr)
        mutableStateOf(arr)
    }

    // Inputs
    var hexInput by remember { mutableStateOf(Integer.toHexString(initialColor.toArgb()).uppercase().takeLast(6)) }
    var rInput by remember { mutableStateOf((initialColor.red * 255).toInt().toString()) }
    var gInput by remember { mutableStateOf((initialColor.green * 255).toInt().toString()) }
    var bInput by remember { mutableStateOf((initialColor.blue * 255).toInt().toString()) }

    fun updateInputs(color: Color) {
        currentColor = color
        hexInput = Integer.toHexString(color.toArgb()).uppercase().takeLast(6)
        rInput = (color.red * 255).toInt().toString()
        gInput = (color.green * 255).toInt().toString()
        bInput = (color.blue * 255).toInt().toString()
    }

    fun updateFromHex(hex: String) {
        try {
            if (hex.length == 6) {
                // Fix: Use KTX toColorInt for cleaner logic
                val color = Color("#$hex".toColorInt())
                currentColor = color
                android.graphics.Color.colorToHSV(color.toArgb(), hsv.value)
                updateInputs(color)
            }
        } catch (_: Exception) { }
    }

    fun updateFromRGB(r: String, g: String, b: String) {
        try {
            val ri = r.toIntOrNull() ?: 0
            val gi = g.toIntOrNull() ?: 0
            val bi = b.toIntOrNull() ?: 0
            val color = Color(ri.coerceIn(0,255), gi.coerceIn(0,255), bi.coerceIn(0,255))
            currentColor = color
            hexInput = Integer.toHexString(color.toArgb()).uppercase().takeLast(6)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv.value)
        } catch (_: Exception) { }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Accent Color") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. CIRCULAR WHEEL
                @Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
                BoxWithConstraints(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val maxWidthPx = with(density) { maxWidth.toPx() }
                    val radiusPx = maxWidthPx / 2f

                    val angleRad = Math.toRadians(hsv.value[0].toDouble())
                    val dist = hsv.value[1] * (maxWidth.value / 2f)
                    val thumbX = (cos(angleRad) * dist).dp
                    val thumbY = (sin(angleRad) * dist).dp

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val delta = change.position - center
                                    val angle = (atan2(delta.y, delta.x) * (180 / Math.PI) + 360) % 360
                                    val distance = hypot(delta.x, delta.y)
                                    val saturation = (distance / radiusPx).coerceIn(0f, 1f)

                                    hsv.value[0] = angle.toFloat()
                                    hsv.value[1] = saturation
                                    val newColor = Color.hsv(hsv.value[0], hsv.value[1], hsv.value[2])
                                    updateInputs(newColor)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val delta = offset - center
                                    val angle = (atan2(delta.y, delta.x) * (180 / Math.PI) + 360) % 360
                                    val distance = hypot(delta.x, delta.y)
                                    val saturation = (distance / radiusPx).coerceIn(0f, 1f)

                                    hsv.value[0] = angle.toFloat()
                                    hsv.value[1] = saturation
                                    val newColor = Color.hsv(hsv.value[0], hsv.value[1], hsv.value[2])
                                    updateInputs(newColor)
                                }
                            }
                    ) {
                        drawCircle(brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent)))
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = thumbX, y = thumbY)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, Color.White, CircleShape)
                            .border(1.dp, Color.Black, CircleShape)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 2. BRIGHTNESS SLIDER
                Text("Brightness: ${(hsv.value[2] * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = hsv.value[2],
                    onValueChange = {
                        hsv.value[2] = it
                        val newColor = Color.hsv(hsv.value[0], hsv.value[1], hsv.value[2])
                        updateInputs(newColor)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // 3. HEX INPUT
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = {
                        if (it.length <= 6) {
                            hexInput = it.uppercase()
                            updateFromHex(it)
                        }
                    },
                    label = { Text("HEX") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                // 4. RGB INPUTS
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RGBField("R", rInput) { rInput = it; updateFromRGB(it, gInput, bInput) }
                    RGBField("G", gInput) { gInput = it; updateFromRGB(rInput, it, bInput) }
                    RGBField("B", bInput) { bInput = it; updateFromRGB(rInput, gInput, it) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    exitProcess(0)
}