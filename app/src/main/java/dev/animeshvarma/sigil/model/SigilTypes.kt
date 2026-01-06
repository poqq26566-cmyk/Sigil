package dev.animeshvarma.sigil.model

import dev.animeshvarma.sigil.crypto.CryptoEngine
import java.util.UUID

enum class SigilMode {
    AUTO, CUSTOM
}

enum class LockMode {
    NONE,
    DEVICE,
    CUSTOM
}

enum class AppScreen(val title: String) {
    HOME("Home"),
    HEADERLESS("Headerless Mode"),
    FILE_ENCRYPTION("File/Dir Encryption"),
    ASYMMETRIC("Asymmetric"),
    STEGANOGRAPHY("Steganography"),
    PARTITIONS("Partitions"),
    KEYSTORE("Keystore"),
    DONATE("Donate"),
    DOCS("Docs/Release Notes"),
    SETTINGS("Settings")
}

enum class CipherType { BLOCK, STREAM }
enum class CipherMode { GCM, CBC, POLY1305 }

data class SigilAlgorithm(
    val id: String,
    val name: String,
    val description: String,
    val type: CipherType,
    val defaultMode: CipherMode,
    // SECURITY INDICATORS
    val isWeak: Boolean = false,
    val securityWarning: String? = null
)

object AlgorithmRegistry {
    val supportedAlgorithms = listOf(
        SigilAlgorithm(
            id = "AES_GCM",
            name = "AES-256 (GCM)",
            description = "The global standard. Hardware accelerated, authenticated encryption (AEAD). Fast and highly secure.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.GCM
        ),
        SigilAlgorithm(
            id = "CHACHA20_POLY1305",
            name = "ChaCha20-Poly1305",
            description = "High-speed stream cipher by D. J. Bernstein. Immune to padding oracle attacks and timing attacks. Faster than AES on older CPUs.",
            type = CipherType.STREAM,
            defaultMode = CipherMode.POLY1305
        ),

        SigilAlgorithm(
            id = "SERPENT_CBC",
            name = "Serpent",
            description = "The 'Tank'. AES runner-up with 32 rounds (vs AES's 14). Slower, but offers the highest theoretical security margin.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "TWOFISH_CBC",
            name = "Twofish",
            description = "Complex key schedule makes it exceptionally resistant to brute-force attacks. Designed by Bruce Schneier.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "CAMELLIA_CBC",
            name = "Camellia",
            description = "EU (NESSIE) and Japan (CRYPTREC) standard. Security profile comparable to AES.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "SM4_CBC",
            name = "SM4",
            description = "Chinese National Wireless LAN standard (GB/T 32907). Mandated for government data security in China.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "AES_CBC",
            name = "AES-256 (CBC)",
            description = "Classic AES. Good compatibility, but GCM is preferred for built-in integrity checks.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "CAST6_CBC",
            name = "CAST-256",
            description = "RFC 2612. An AES finalist known for resistance to linear and differential cryptanalysis.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "RC6_CBC",
            name = "RC6",
            description = "Rivest (RSA) design. Simple and fast, relies on data-dependent rotations.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "SEED_CBC",
            name = "SEED",
            description = "South Korean standard (KISA). Widely used in Asian banking security.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),

        SigilAlgorithm(
            id = "BLOWFISH_CBC",
            name = "Blowfish",
            description = "Legacy Schneier design. Fast for short text.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64-bit Block Size. Vulnerable to birthday attacks on large files."
        ),
        SigilAlgorithm(
            id = "IDEA_CBC",
            name = "IDEA",
            description = "The original PGP cipher. Uses 128-bit keys.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64-bit Block Size. Legacy algorithm."
        ),
        SigilAlgorithm(
            id = "CAST5_CBC",
            name = "CAST-128",
            description = "Default cipher for older GPG versions.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64-bit Block Size. Legacy algorithm."
        ),
        SigilAlgorithm(
            id = "GOST_CBC",
            name = "GOST 28147",
            description = "Soviet/Russian standard. 32-round Feistel network.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64-bit Block Size. Structure theoretically vulnerable to advanced analysis."
        ),
        SigilAlgorithm(
            id = "TEA_CBC",
            name = "TEA",
            description = "Tiny Encryption Algorithm. Extremely simple code.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "Weak Key Schedule. Vulnerable to equivalent key attacks."
        ),
        SigilAlgorithm(
            id = "XTEA_CBC",
            name = "XTEA",
            description = "Extended TEA. Fixes some TEA weaknesses.",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64-bit Block Size. Educational/Legacy use only."
        )
    )
}
data class LayerEntry(
    val id: String = UUID.randomUUID().toString(),
    val algorithm: CryptoEngine.Algorithm
)

data class UiState(
    val autoInput: String = "",
    val autoPassword: String = "",
    val autoOutput: String = "",

    val customInput: String = "",
    val customPassword: String = "",
    val customOutput: String = "",

    val selectedMode: SigilMode = SigilMode.AUTO,
    val currentScreen: AppScreen = AppScreen.HOME,
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val showLogsDialog: Boolean = false,

    // Demo Controls
    val isDemoDropdownExpanded: Boolean = false,
    val isDemoDrawerOpen: Boolean = false,

    val customLayers: List<LayerEntry> = listOf(
        LayerEntry(algorithm = CryptoEngine.Algorithm.AES_GCM)
    ),

    val isCompressionEnabled: Boolean = true
)