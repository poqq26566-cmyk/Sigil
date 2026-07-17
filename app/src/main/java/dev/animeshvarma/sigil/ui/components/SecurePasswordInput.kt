@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.data.VaultEntry

@Composable
fun SecurePasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSaveRequested: (String) -> Unit,
    vaultEntries: List<VaultEntry>,
    onEntrySelected: (VaultEntry) -> Unit,
    modifier: Modifier = Modifier,
    forceDropdownExpanded: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    var showNameDialog by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var newKeyName by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(forceDropdownExpanded) {
        if (forceDropdownExpanded) showMenu = true
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("密码 / 密钥") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Key, "密钥库", tint = MaterialTheme.colorScheme.primary)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = DpOffset(x = 10.dp, y = 0.dp),
                            modifier = Modifier
                                .width(260.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "印记 · 密钥库",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            DropdownMenuItem(
                                text = { Text("保存当前密钥") },
                                leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    newKeyName = ""
                                    showNameDialog = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            if (vaultEntries.isEmpty()) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "密钥库为空。",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                vaultEntries.forEach { entry ->
                                    val strengthColor = when {
                                        entry.strengthScore < 40 -> Color(0xFFCF6679)
                                        entry.strengthScore < 75 -> Color(0xFFFFD54F)
                                        entry.strengthScore < 95 -> Color(0xFF81C784)
                                        else -> Color(0xFF00E676)
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Column(verticalArrangement = Arrangement.Center) {
                                                Text(
                                                    text = entry.alias,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        Modifier
                                                            .size(8.dp)
                                                            .background(strengthColor, CircleShape)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = entry.strengthLabel,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            passwordVisible = false
                                            onEntrySelected(entry)
                                            showMenu = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            icon = { Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("保存到密钥库") },
            text = {
                Column {
                    Text("为这个密钥设置一个唯一别名：")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { newKeyName = it },
                        singleLine = true,
                        placeholder = { Text("输入唯一名称...") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyName.isNotBlank()) {
                            val exists = vaultEntries.any { it.alias.equals(newKeyName, ignoreCase = true) }
                            if (exists) {
                                showNameDialog = false
                                showOverwriteDialog = true
                            } else {
                                onSaveRequested(newKeyName)
                                showNameDialog = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text
