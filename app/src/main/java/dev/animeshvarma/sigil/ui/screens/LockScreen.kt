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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.model.LockType
import dev.animeshvarma.sigil.util.BiometricHelper

@Composable
fun LockScreen(
    viewModel: SigilViewModel,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val prefs = viewModel.getPrefs()
    val uiState by viewModel.uiState.collectAsState()

    val lockMode = prefs.lockMode
    val lockType = prefs.lockType

    val incorrectAuthText = if (lockType == LockType.PIN) "PIN 码错误" else "密码错误"

    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isPinFallback by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showBiometricInvalidatedDialog by remember { mutableStateOf(false) }

    fun triggerBiometric() {
        if (context is FragmentActivity) {
            val currentLockType = prefs.lockType
            val negativeText = if (currentLockType == LockType.PIN) "使用印记 PIN" else "使用印记密码"

            BiometricHelper.showPrompt(
                activity = context,
                negativeButtonText = negativeText,
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

    var biometricInvalidated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (BiometricHelper.hasBiometricChanged()) {
            biometricInvalidated = true
            showBiometricInvalidatedDialog = true
            isPinFallback = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (BiometricHelper.hasBiometricChanged()) {
                    biometricInvalidated = true
                    showBiometricInvalidatedDialog = true
                    isPinFallback = true
                    return@LifecycleEventObserver
                }

                if (lockMode == LockMode.DEVICE &&
                    !showBiometricInvalidatedDialog &&
                    !isPinFallback
                ) {
                    triggerBiometric()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                "印记已锁定",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "需要验证身份。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            if (lockMode == LockMode.CUSTOM || isPinFallback || showBiometricInvalidatedDialog) {
                val kbType = if (lockType == LockType.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                val labelText = if (lockType == LockType.PIN) "输入印记 PIN" else "输入密码"

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        if (showError) {
                            showError = false
                            errorMessage = ""
                        }
                    },
                    label = { Text(labelText) },
                    singleLine = true,
                    isError = showError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = kbType,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            keyboardController?.hide()
                            if (uiState.isLoading) return@KeyboardActions
                            if (pinInput.isNotEmpty()) {
                                viewModel.verifyAppSecret(pinInput) { isValid ->
                                    if (isValid) {
                                        onUnlock()
                                    } else {
                                        showError = true
                                        errorMessage = incorrectAuthText
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
                        text = errorMessage.ifEmpty { incorrectAuthText },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (pinInput.isEmpty()) return@Button
                        viewModel.verifyAppSecret(pinInput) { isValid ->
                            if (isValid) {
                                onUnlock()
                            } else {
                                showError = true
                                errorMessage = incorrectAuthText
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pinInput = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading && pinInput.isNotEmpty()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text("解锁")
                    }
                }

                if (lockMode == LockMode.DEVICE && isPinFallback && !biometricInvalidated) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { triggerBiometric() }) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重新尝试生物识别")
                    }
                }

            } else {
                Button(
                    onClick = { triggerBiometric() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("使用生物识别解锁")
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { showWipeDialog = true }) {
                val forgotText = if (lockType == LockType.PIN) "忘记 PIN？重置应用" else "忘记密码？重置应用"
                Text(forgotText, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showBiometricInvalidatedDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text("安全提醒")
            },
            text = {
                val authType = if (lockType == LockType.PIN) "PIN" else "密码"
                Text("检测到设备生物识别设置发生变化。\n\n为确保安全，印记已回退到 $authType 认证。登录后需在印记设置中重新启用生物识别解锁。")
            },
            confirmButton = {
                TextButton(
                    onClick = { showBiometricInvalidatedDialog = false }
                ) { Text("我知道了") }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("紧急重置") },
            text = { Text("此操作将删除所有数据，包括密钥库、设置和 PIN。此操作不可逆。") },
            confirmButton = {
                Button(
                    onClick = { viewModel.wipeAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("清除所有数据") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("取消") }
            }
        )
    }
}
