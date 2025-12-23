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
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.toColorInt
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.system.exitProcess

@Composable
fun SettingsScreen(viewModel: SigilViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.getPrefs()

    // --- STATE HOISTING ---
    // General
    var onboardingToggle by remember { mutableStateOf(viewModel.isOnboardingReset()) }

    // Security
    var lockMode by remember { mutableStateOf(prefs.lockMode) }
    var graceEnabled by remember { mutableStateOf(prefs.isGracePeriodEnabled) }
    var graceMinutes by remember { mutableFloatStateOf(prefs.graceDurationMinutes.toFloat()) }

    // Privacy
    var screenShield by remember { mutableStateOf(prefs.isScreenShieldEnabled) }

    // Appearance
    var dynamicColors by remember { mutableStateOf(prefs.isDynamicColorsEnabled) }
    var darkMode by remember { mutableStateOf(prefs.isDarkModeEnabled) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.selectedThemeColor) }

    // Dialogs
    var showPinDialog by remember { mutableStateOf(false) }
    var showViewPinDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var revealedPin by remember { mutableStateOf("") }

    var isPinLoading by remember { mutableStateOf(false) }

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

        // Restart Button
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

        // --- SECURITY ---
        SettingsHeader("Security")

        // App Lock Master Switch
        SettingsItem(
            title = "App Lock",
            desc = "Require authentication to enter.",
            trailing = {
                Switch(
                    checked = lockMode != LockMode.NONE,
                    onCheckedChange = { enabled ->
                        lockMode = if (enabled) LockMode.DEVICE else LockMode.NONE
                        viewModel.setLockMode(lockMode)
                    }
                )
            }
        )

        // Lock Method Selector (Visible only if Lock is ON)
        AnimatedVisibility(visible = lockMode != LockMode.NONE) {
            Column {
                Spacer(Modifier.height(12.dp))
                SigilSegmentedControl(
                    items = listOf("Device Biometrics", "Custom PIN"),
                    selectedIndex = if (lockMode == LockMode.CUSTOM) 1 else 0,
                    onItemSelection = { index ->
                        if (index == 0) {
                            lockMode = LockMode.DEVICE
                            viewModel.setLockMode(LockMode.DEVICE)
                        } else {
                            // Trigger Dialog for PIN
                            showPinDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (lockMode == LockMode.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            isPinLoading = true
                            viewModel.retrieveAppPin { pin ->
                                isPinLoading = false
                                revealedPin = pin ?: "Not Set"
                                showViewPinDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isPinLoading
                    ) {
                        if (isPinLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Decrypting...")
                        } else {
                            Icon(Icons.Default.Visibility, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("View/Recover PIN")
                        }
                    }
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

        // --- PRIVACY ---
        SettingsHeader("Privacy")

        SettingsItem(
            title = "Screen Shield",
            desc = "Block screenshots and hide content in Recents.",
            trailing = {
                Switch(checked = screenShield, onCheckedChange = {
                    screenShield = it
                    prefs.isScreenShieldEnabled = it
                })
            }
        )

        Spacer(Modifier.height(24.dp))

        // --- APPEARANCE ---
        SettingsHeader("Appearance")

        SettingsItem(
            title = "Material You",
            desc = "Use system wallpaper colors.",
            trailing = {
                Switch(checked = dynamicColors, onCheckedChange = {
                    dynamicColors = it
                    viewModel.setDynamicColors(it)
                })
            }
        )

        AnimatedVisibility(visible = !dynamicColors) {
            Column {
                Spacer(Modifier.height(12.dp))

                // THEME COLOR SELECTOR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { showColorDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Accent Color", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#${Integer.toHexString(selectedColorInt).uppercase().takeLast(6)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(selectedColorInt))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SettingsItem("Dark Mode", "Use dark theme layout.") {
                    Switch(checked = darkMode, onCheckedChange = { darkMode = it; viewModel.setDarkMode(it) })
                }
            }
        }
        Spacer(Modifier.height(64.dp))
    }

    // --- DIALOGS ---

    // Set PIN Dialog
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false; if (prefs.lockMode != LockMode.CUSTOM) lockMode = prefs.lockMode },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Set Custom PIN") },
            text = {
                Column {
                    Text("Enter a PIN to lock Sigil. It will be encrypted in the Hardware Vault.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = pinInput, onValueChange = { if (it.length <= 12) pinInput = it }, label = { Text("PIN") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = { if (pinInput.isNotEmpty()) { viewModel.setCustomPin(pinInput); lockMode = LockMode.CUSTOM; viewModel.setLockMode(LockMode.CUSTOM); showPinDialog = false } }) { Text("Set PIN") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false; if (prefs.lockMode != LockMode.CUSTOM) lockMode = prefs.lockMode }) { Text("Cancel") } }
        )
    }

    // View PIN Dialog
    if (showViewPinDialog) {
        AlertDialog(
            onDismissRequest = { showViewPinDialog = false },
            title = { Text("Your PIN") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(revealedPin, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Stored securely in TEE.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { showViewPinDialog = false }) { Text("Close") } }
        )
    }

    // ADVANCED COLOR PICKER DIALOG
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
}

// --- COLOR PICKER COMPONENT ---
@Composable
fun AdvancedColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var currentColor by remember { mutableStateOf(initialColor) }

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
                val color = Color(android.graphics.Color.parseColor("#$hex"))
                currentColor = color
            }
        } catch (e: Exception) { }
    }

    fun updateFromRGB(r: String, g: String, b: String) {
        try {
            val ri = r.toIntOrNull() ?: 0
            val gi = g.toIntOrNull() ?: 0
            val bi = b.toIntOrNull() ?: 0
            val color = Color(ri.coerceIn(0,255), gi.coerceIn(0,255), bi.coerceIn(0,255))
            currentColor = color
            hexInput = Integer.toHexString(color.toArgb()).uppercase().takeLast(6)
        } catch (e: Exception) { }
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
                BoxWithConstraints(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val maxWidthPx = with(density) { maxWidth.toPx() }
                    val radiusPx = maxWidthPx / 2f

                    // Derive Thumb Position from Current Color
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(currentColor.toArgb(), hsv)

                    val angleRad = Math.toRadians(hsv[0].toDouble())
                    val dist = hsv[1] * (maxWidth.value / 2f)

                    // X = Center + (Cos(angle) * dist)
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

                                    val newColor = Color.hsv(angle.toFloat(), saturation, 1f)
                                    // Update inputs
                                    currentColor = newColor
                                    hexInput = Integer.toHexString(newColor.toArgb()).uppercase().takeLast(6)
                                    rInput = (newColor.red * 255).toInt().toString()
                                    gInput = (newColor.green * 255).toInt().toString()
                                    bInput = (newColor.blue * 255).toInt().toString()
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val delta = offset - center
                                    val angle = (atan2(delta.y, delta.x) * (180 / Math.PI) + 360) % 360
                                    val distance = hypot(delta.x, delta.y)
                                    val saturation = (distance / radiusPx).coerceIn(0f, 1f)

                                    val newColor = Color.hsv(angle.toFloat(), saturation, 1f)
                                    // Update inputs
                                    currentColor = newColor
                                    hexInput = Integer.toHexString(newColor.toArgb()).uppercase().takeLast(6)
                                    rInput = (newColor.red * 255).toInt().toString()
                                    gInput = (newColor.green * 255).toInt().toString()
                                    bInput = (newColor.blue * 255).toInt().toString()
                                }
                            }
                    ) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                            )
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White, Color.Transparent)
                            )
                        )
                    }

                    // THE THUMB
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

                Spacer(Modifier.height(24.dp))

                // 2. HEX INPUT
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

                // 3. RGB INPUTS
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

// --- HELPERS ---
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

private fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    exitProcess(0)
}