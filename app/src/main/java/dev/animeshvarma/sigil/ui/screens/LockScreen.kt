@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.util.BiometricHelper

@Composable
fun LockScreen(
    viewModel: SigilViewModel,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = viewModel.getPrefs()
    val uiState by viewModel.uiState.collectAsState()

    val lockMode = prefs.lockMode

    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    // STATE: Controls whether we show the PIN input even in Biometric Mode
    var isPinFallback by remember { mutableStateOf(false) }

    var showWipeDialog by remember { mutableStateOf(false) }

    fun triggerBiometric() {
        if (context is FragmentActivity) {
            BiometricHelper.showPrompt(
                activity = context,
                onSuccess = { _ -> onUnlock() },
                onFailure = {
                    isPinFallback = true
                },
                onError = { error ->
                    showError = true
                    errorMessage = error
                    isPinFallback = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (lockMode == LockMode.DEVICE) {
            triggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(32.dp))

            Text(
                "Sigil Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Identity verification required.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            if (lockMode == LockMode.CUSTOM || isPinFallback) {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        if (showError) {
                            showError = false
                            errorMessage = ""
                        }
                    },
                    label = { Text("Enter Sigil PIN") },
                    singleLine = true,
                    isError = showError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (pinInput.isNotEmpty()) {
                                viewModel.verifyAppPin(pinInput) { isValid ->
                                    if (isValid) {
                                        onUnlock()
                                    } else {
                                        showError = true
                                        errorMessage = "Incorrect PIN"
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pinInput = ""
                                    }
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text(
                        text = errorMessage.ifEmpty { "Incorrect PIN" },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.verifyAppPin(pinInput) { isValid ->
                            if (isValid) {
                                onUnlock()
                            } else {
                                showError = true
                                errorMessage = "Incorrect PIN"
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pinInput = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text("Unlock")
                    }
                }

                if (lockMode == LockMode.DEVICE && isPinFallback) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { triggerBiometric() }) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Try Biometrics Again")
                    }
                }

            } else {
                // Biometric Button State
                Button(
                    onClick = { triggerBiometric() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { showWipeDialog = true }) {
                Text("Forgot PIN? Reset App", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Emergency Reset") },
            text = { Text("This will delete ALL data, including your Vault keys, Settings, and PIN. This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.wipeAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }
}