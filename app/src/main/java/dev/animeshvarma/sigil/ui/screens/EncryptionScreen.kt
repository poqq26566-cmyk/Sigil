@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AlgorithmRegistry
import dev.animeshvarma.sigil.model.EncryptionProfile
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.SecurePasswordInput
import dev.animeshvarma.sigil.ui.components.SigilButtonGroup

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EncryptionInterface(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current
    val vaultEntries by viewModel.vaultEntries.collectAsState()
    var showProfileSheet by remember { mutableStateOf(false) }

    // --- TOAST HELPER ---
    val currentToast = remember { mutableStateOf<Toast?>(null) }

    val showToast: (String) -> Unit = { message ->
        currentToast.value?.cancel()
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        currentToast.value = toast
        toast.show()
    }

    // Lifecycle safety for sheet
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) showProfileSheet = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxHeight()) {

        // 1. INPUT FIELD WITH OVERLAY
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.autoInput,
                onValueChange = { viewModel.onInputTextChanged(it) },
                label = { Text("Input Text") },
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )

            // BOOKMARK BUTTON
            SmallFloatingActionButton(
                onClick = { showProfileSheet = true },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 15.dp)
                    .size(36.dp),
                elevation = FloatingActionButtonDefaults.elevation(2.dp)
            ) {
                Icon(Icons.Default.Bookmarks, "Profiles", modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(11.dp))

        // 2. SECURE PASSWORD FIELD
        SecurePasswordInput(
            value = uiState.autoPassword,
            onValueChange = { viewModel.onPasswordChanged(it) },
            onSaveRequested = { name ->
                viewModel.saveToVault(alias = name, password = uiState.autoPassword)
            },
            vaultEntries = vaultEntries,
            onEntrySelected = { viewModel.loadFromVault(it) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            forceDropdownExpanded = uiState.isDemoDropdownExpanded
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Button Group
        SigilButtonGroup(
            onLogs = { viewModel.onLogsClicked() },
            onEncrypt = { viewModel.onEncrypt() },
            onDecrypt = { viewModel.onDecrypt() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Output Field
        OutlinedTextField(
            value = uiState.autoOutput,
            onValueChange = { },
            label = { Text("Output") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(144.dp),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                Column(modifier = Modifier.padding(end = 4.dp)) {
                    IconButton(onClick = {
                        if (uiState.autoOutput.isNotEmpty()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, uiState.autoOutput)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Encrypted Message")
                            context.startActivity(shareIntent)
                            viewModel.addLog("Share Sheet opened.")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        if (uiState.autoOutput.isNotEmpty()) {
                            viewModel.copyToClipboardSecurely(uiState.autoOutput, "Sigil Output")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // --- PROFILE SELECTION SHEET ---
    if (showProfileSheet) {
        ModalBottomSheet(onDismissRequest = { showProfileSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Encryption Profiles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Active: ${uiState.activeProfile.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalButton(onClick = {
                        showProfileSheet = false
                        viewModel.onModeSelected(SigilMode.CUSTOM)
                        showToast("Switched to Custom Mode")
                    }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    items(
                        items = uiState.availableProfiles,
                        key = { it.id }
                    ) { profile ->
                        Modifier.animateItem(placementSpec = tween(durationMillis = 300))

                        ExpandableProfileCard(
                            profile = profile,
                            isActive = profile.id == uiState.activeProfile.id,
                            onSelect = {
                                viewModel.selectProfile(it)
                                // UPDATED: Use helper
                                showToast("Activated: ${it.name}")
                            },
                            onEdit = {
                                viewModel.loadProfileToCustomMode(it)
                                showProfileSheet = false
                                // UPDATED: Use helper
                                showToast("Editing ${it.name}")
                            },
                            onDelete = {
                                viewModel.deleteProfile(it.id)
                                // UPDATED: Use helper
                                showToast("Profile deleted")
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpandableProfileCard(
    profile: EncryptionProfile,
    isActive: Boolean,
    onSelect: (EncryptionProfile) -> Unit,
    onEdit: (EncryptionProfile) -> Unit,
    onDelete: (EncryptionProfile) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var algoInfoDialog by remember { mutableStateOf<CryptoEngine.Algorithm?>(null) }

    // Weakness Check
    val weakCiphers = remember(profile) {
        profile.layers.mapNotNull { algo ->
            AlgorithmRegistry.supportedAlgorithms.find { it.id == algo.name }
        }.filter { it.isWeak }
    }
    val isWeak = weakCiphers.isNotEmpty()

    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // --- HEADER ROW (Always Visible) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left Side: Name + Badges
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (isActive) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Mini Badges Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (profile.isBuiltIn) {
                            BadgeText("Built-in", MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(6.dp))
                        }

                        // Algo Count
                        BadgeText("${profile.layers.size} Algos")

                        // Compression
                        if (profile.isCompressionEnabled && !profile.isRaw) {
                            Spacer(Modifier.width(6.dp))
                            BadgeText("CMP", MaterialTheme.colorScheme.tertiary)
                        }

                        // KDF
                        if (profile.kdfConfig != null) {
                            Spacer(Modifier.width(6.dp))
                            BadgeText("KDF+", MaterialTheme.colorScheme.tertiary)
                        }

                        // Weak Warning
                        if (isWeak) {
                            Spacer(Modifier.width(6.dp))
                            BadgeText("Weak", MaterialTheme.colorScheme.error)
                        }

                        // Raw Badge
                        if (profile.isRaw) {
                            Spacer(Modifier.width(6.dp))
                            BadgeText("RAW", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }

                // Right Side: Selection Action + Expand
                Row(verticalAlignment = Alignment.CenterVertically) {

                    // SWITCHING LOGIC: Use vs Active Badge
                    AnimatedVisibility(visible = !isActive) {
                        OutlinedButton(
                            onClick = { onSelect(profile) },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Use")
                        }
                    }

                    AnimatedVisibility(visible = isActive) {
                        // Badge style button for Active state
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = MaterialTheme.colorScheme.primary,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Active")
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "ExpandArrow")
                    Icon(
                        Icons.Default.ExpandMore,
                        null,
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- EXPANDED CONTENT ---
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))

                    // Description
                    if (profile.description.isNotBlank()) {
                        Text(
                            profile.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // Layer Chain Flow
                    Text("Algorithm Chain (Tap for info):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        profile.layers.forEachIndexed { index, algo ->
                            SuggestionChip(
                                onClick = { algoInfoDialog = algo },
                                label = { Text(algo.name.replace("_", "-")) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.height(24.dp)
                            )
                            if (index < profile.layers.size - 1) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(12.dp).align(Alignment.CenterVertically), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!profile.isBuiltIn) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onDelete(profile) },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(14.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { onEdit(profile) },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Edit, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Edit", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ALGO INFO DIALOG
    algoInfoDialog?.let { algo ->
        val details = AlgorithmRegistry.supportedAlgorithms.find { it.id == algo.name }
        AlertDialog(
            onDismissRequest = { algoInfoDialog = null },
            icon = { Icon(Icons.Default.Info, null) },
            title = { Text(details?.name ?: algo.name) },
            text = {
                Column {
                    Text(details?.description ?: "No description available.")
                    Spacer(Modifier.height(8.dp))
                    Text("Type: ${details?.type ?: "Unknown"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    if (details?.isWeak == true) {
                        Text("Warning: ${details.securityWarning}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { algoInfoDialog = null }) { Text("Close") }
            }
        )
    }
}

@Composable
fun BadgeText(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}