package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val prefs = viewModel.getPrefs()
    val uiState by viewModel.uiState.collectAsState()
    val lockMode = prefs.lockMode

    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }

    // Auto-trigger Biometric on load
    LaunchedEffect(Unit) {
        if (lockMode == LockMode.DEVICE) {
            BiometricHelper.showPrompt(
                context as FragmentActivity,
                onSuccess = onUnlock,
                onFailure = { /* Stay locked */ }
            )
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
            modifier = Modifier.padding(32.dp)
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

            Text("Sigil is Locked", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Authentication required.", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(48.dp))

            if (lockMode == LockMode.CUSTOM) {
                // Custom PIN UI
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        showError = false
                    },
                    label = { Text("Enter PIN") },
                    singleLine = true,
                    isError = showError,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text("Incorrect PIN", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.verifyAppPin(pinInput) { isValid ->
                            if (isValid) {
                                onUnlock()
                            } else {
                                showError = true
                                pinInput = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    // DISABLE IF LOADING
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        // SHOW LOADER
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text("Unlock")
                    }
                }
            } else {
                // Device Biometric UI
                Button(
                    onClick = {
                        BiometricHelper.showPrompt(
                            context as FragmentActivity,
                            onSuccess = onUnlock,
                            onFailure = {}
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Emergency Reset
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
            text = { Text("This will delete ALL data, including your saved keys and settings. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeAllData() // You need to add this to VM
                        // Restart logic will handle the rest
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe Everything") }
            },
            dismissButton = { TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") } }
        )
    }
}