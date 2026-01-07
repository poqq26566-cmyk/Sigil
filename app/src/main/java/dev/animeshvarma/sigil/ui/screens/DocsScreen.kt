@file:Suppress("unused")

package dev.animeshvarma.sigil.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.theme.AnimationConfig
import dev.animeshvarma.sigil.SigilViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(
    viewModel: SigilViewModel = viewModel()
) {
    val demoTab by viewModel.demoDocsTabIndex.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(demoTab) {
        if (demoTab != selectedTabIndex) selectedTabIndex = demoTab
    }
    val tabs = listOf("Docs", "Releases")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SigilSegmentedControl(
            items = tabs,
            selectedIndex = selectedTabIndex,
            onItemSelection = { selectedTabIndex = it },
            modifier = Modifier.fillMaxWidth(0.65f)
        )

        Spacer(modifier = Modifier.height(15.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            val slideSpring = spring<IntOffset>(
                stiffness = AnimationConfig.STIFFNESS,
                dampingRatio = AnimationConfig.DAMPING
            )

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = slideSpring) { it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { -it } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = slideSpring) { -it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { it } + fadeOut()
                    }
                },
                label = "DocsTabTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> DocsContent()
                    1 -> ReleasesContent()
                }
            }
        }
    }
}

@Composable
fun DocsContent() {
    UnderConstructionView()
}

// --- RELEASES TAB IMPLEMENTATION ---
data class ReleaseData(
    val version: String,
    val title: String,
    val tag: String,
    val hasBreakingChanges: Boolean = false,
    val categories: List<ReleaseCategory>
)

data class ReleaseCategory(
    val title: String,
    val points: List<String>
)

@Composable
fun ReleasesContent() {
    val releases = listOf(
        // --- v0.4 (SECURITY CORE & ONBOARDING) ---
        ReleaseData(
            version = "v0.4",
            title = "Onboarding & Access Control",
            tag = "Current Build",
            hasBreakingChanges = true,
            categories = listOf(
                ReleaseCategory(
                    "BREAKING CHANGES",
                    listOf(
                        "Incompatible Ciphertexts: Data encrypted with previous versions cannot be decrypted. The Key Derivation Function (KDF) logic has changed to support dynamic tuning.",
                        "Vault Wipe: The internal Keystore format has been hardened. Old saved keys are incompatible and have been cleared.",
                        "Auth Reset: PIN storage has moved to a Salted Argon2 Hash architecture. You must set a new PIN."
                    )
                ),
                ReleaseCategory(
                    "Core Cryptography (Engine v0.10.0)",
                    listOf(
                        "Quad-Layer Chain: 'Auto Mode' now utilizes a hybrid cascade of AES-256 + ChaCha20 + Twofish + Serpent.",
                        "ChaCha20-Poly1305: Added support for the high-performance ARX stream cipher (IETF standard).",
                        "Performance Tuning: Optimized Argon2id configuration for interactive UI responsiveness without compromising memory hardness.",
                        "Padding Oracle Mitigation: Unified exception handling to prevent side-channel leaks."
                    )
                ),
                ReleaseCategory(
                    "Architecture",
                    listOf(
                        "Zero-Knowledge Auth: App PINs are now stored as Salted Argon2 Hashes. It is mathematically impossible for the app to retrieve/reveal your PIN.",
                        "Anti-Tamper Biometrics: Fingerprint unlock now binds to a TEE CryptoObject. Root/Frida hooks cannot bypass authentication.",
                        "Secure Clipboard: Added 'Sensitive Content' flags (Android 13+) and an auto-wipe timer to clear copied secrets."
                    )
                ),
                ReleaseCategory(
                    "Onboarding & Discovery",
                    listOf(
                        "Interactive Tour: A comprehensive, guided simulation for new users demonstrating live encryption cycles and safe key management.",
                        "Advanced Mode: A specialized path for power users detailing the inner workings of the Layer Manager and Hardware Vault."
                    )
                ),
                ReleaseCategory(
                    "Access Control",
                    listOf(
                        "App Lock: Secure Sigil using Device Biometrics or a Custom PIN which is secured via the Hardware TEE.",
                        "Grace Period: 'Keep Unlocked' setting allows quick app switching without immediate re-authentication."
                    )
                ),
                ReleaseCategory(
                    "Privacy & Volatility",
                    listOf(
                        "Amnesia Protocol: Immediate sanitation of sensitive input data from volatile memory (RAM) upon backgrounding the application.",
                        "Screen Shield: Implementation of FLAG_SECURE blocks screenshots and hides content in the 'Recent Apps' overview.",
                        "Manifest Hardening: Explicitly disabled ADB and Cloud backups to prevent data extraction."
                    )
                ),
                ReleaseCategory(
                    "Visual Customization",
                    listOf(
                        "Advanced Theme Engine: Full support for Material You (Dynamic Colors), Dark/Light modes, and a custom HSV Color Picker.",
                        "Settings Overhaul: A complete redesign of the Settings tab to accommodate new KDF tuning and appearance controls."
                    )
                )
            )
        ),
        // --- v0.3 (THE Keystore UPDATE) ---
        ReleaseData(
            version = "v0.3",
            title = "Keystore Implementation",
            tag = "Dec 9, 2025",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "Security & Storage (Engine v0.9.0)",
                    listOf(
                        "Hardware Keystore: Keys are now encrypted via the Android Trusted Execution Environment (TEE).",
                        "Secure Memory: Implemented aggressive RAM wiping (zeroing out CharArrays) to prevent memory dump attacks.",
                        "Vault Architecture: Saved keys use a hybrid 'Hardware -> AES -> Twofish -> Serpent' chain.",
                        "Key Management: View, rename, and delete keys via the new dedicated Keystore tab."
                    )
                ),
                ReleaseCategory(
                    "User Experience",
                    listOf(
                        "Intent Handling: Sigil now accepts text shared directly from external apps (WhatsApp, Signal, etc.).",
                        "Smart Inputs: Password fields replaced with Secure Vault Dropdowns for one-tap access.",
                        "Visual Feedback: Output logs now display precise timing and detailed failure diagnosis (HMAC vs Format errors).",
                        "Safety First: Added confirmation dialogs for deleting or revealing sensitive keys."
                    )
                ),
                ReleaseCategory(
                    "System & Structure",
                    listOf(
                        "Headerless Mode: Added navigation entry for the upcoming raw binary mode.",
                        "Orientation Lock: Disabled landscape mode to ensure consistent physics stability.",
                        "Share Integration: Added direct 'Share' button to output fields."
                    )
                )
            )
        ),
        // --- v0.2 (Custom Implementation) ---
        ReleaseData(
            version = "v0.2",
            title = "Custom Implementation",
            tag = "Nov 30, 2025",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "User Interface",
                    listOf(
                        "Custom Encryption Tab: Full manual control over encryption layers.",
                        "Interactive Physics: Implemented spring-based pill buttons and transitions.",
                        "Movable Layers: Reorder algorithms using Up/Down arrows with smooth animations.",
                        "Polished Lists: Added fading edges and custom animated scrollbars for long lists.",
                        "Search: Added filterable algorithm list with descriptions."
                    )
                ),
                ReleaseCategory(
                    "Core Cryptography (Engine v0.8.0)",
                    listOf(
                        "Engine Fixes: Corrected Block Size logic for Blowfish/RC6.",
                        "Compression: Added ZLIB compression toggle.",
                        "Expanded Registry: Added Camellia, SM4, GOST, CAST6, and more.",
                        "Binary Packing: Optimized internal container format."
                    )
                )
            )
        ),
        // --- v0.1 (FOUNDATION) ---
        ReleaseData(
            version = "v0.1",
            title = "The Foundation",
            tag = "Nov 26, 2025",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "Core Cryptography (Engine v0.7.0)",
                    listOf(
                        "Initial Encryption implementation: Randomized triple-layer chain.",
                        "Blob Container: Opaque Base64 output with hidden metadata.",
                        "HMAC Integrity: Encrypt-then-MAC architecture.",
                        "Memory Hardening: Argon2id (64MB)."
                    )
                ),
                ReleaseCategory(
                    "User Interface",
                    listOf(
                        "Material 3 Design: Initial UI with dynamic theming.",
                        "System Console: Dedicated Logs window."
                    )
                )
            )
        )
    )

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(releases) { _, release ->
            ReleaseCard(release, defaultExpanded = false)
        }
    }
}

@Composable
fun ReleaseCard(release: ReleaseData, defaultExpanded: Boolean) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "CardBounce"
    )

    val shape = RoundedCornerShape(12.dp)

    // Manual Box Stack
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { expanded = !expanded }
            )
            .padding(16.dp)
            .animateContentSize()
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = release.version,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        if (release.hasBreakingChanges) {
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(20.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = "BREAKING CHANGES",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onError,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = release.tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Collapsible Content
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                release.categories.forEach { category ->
                    val isBreakingSection = category.title.contains("BREAKING", ignoreCase = true)

                    if (isBreakingSection) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )

                                category.points.forEach { point ->
                                    Row(
                                        modifier = Modifier.padding(bottom = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "• ",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = point,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        category.points.forEach { point ->
                            Row(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = point,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}