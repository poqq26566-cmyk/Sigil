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
    HOME("首页"),
    HEADERLESS("无头模式"),
    FILE_ENCRYPTION("文件/目录加密"),
    ASYMMETRIC("非对称加密"),
    STEGANOGRAPHY("隐写术"),
    PARTITIONS("分区加密"),
    KEYSTORE("密钥库"),
    DONATE("捐赠"),
    DOCS("文档/版本说明"),
    SETTINGS("设置")
}

enum class CipherType { BLOCK, STREAM }
enum class CipherMode { GCM, CBC, POLY1305 }

enum class LockType { PIN, PASSWORD }

data class SigilAlgorithm(
    val id: String,
    val name: String,
    val description: String,
    val type: CipherType,
    val defaultMode: CipherMode,
    val isWeak: Boolean = false,
    val securityWarning: String? = null
)

data class EncryptionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val layers: List<CryptoEngine.Algorithm>,
    val kdfConfig: CryptoEngine.KdfConfig? = null,
    val isBuiltIn: Boolean = false,
    val isCompressionEnabled: Boolean = true,
    val isRaw: Boolean = false
)

object ProfileRegistry {
    const val STANDARD_AES_ID = "sigil_standard_aes"

    val defaultProfile = EncryptionProfile(
        id = "sigil_default_chain",
        name = "印记链",
        description = "印记混合加密栈：XChaCha20 + Serpent + Twofish + AES。",
        layers = listOf(
            CryptoEngine.Algorithm.XCHACHA20_POLY1305,
            CryptoEngine.Algorithm.SERPENT_CBC,
            CryptoEngine.Algorithm.TWOFISH_CBC,
            CryptoEngine.Algorithm.AES_GCM
        ),
        isBuiltIn = true,
        isCompressionEnabled = true,
        isRaw = false
    )

    val standardProfile = EncryptionProfile(
        id = STANDARD_AES_ID,
        name = "标准 AES",
        description = "独立 AES-256-GCM。无级联、无头部、无元数据。其他原始算法请使用自定义标签页。不支持自动解密，需手动选择配置方案。",
        layers = listOf(CryptoEngine.Algorithm.AES_GCM),
        isBuiltIn = true,
        isCompressionEnabled = false,
        isRaw = true
    )

    val builtInProfiles = listOf(defaultProfile, standardProfile)
}

object AlgorithmRegistry {
    val supportedAlgorithms = listOf(
        SigilAlgorithm(
            id = "AES_GCM",
            name = "AES-256（GCM）",
            description = "全球标准算法。硬件加速，认证加密（AEAD）。快速且高度安全。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.GCM
        ),
        SigilAlgorithm(
            id = "ARIA_256_GCM",
            name = "ARIA-256（GCM）",
            description = "韩国国家标准（RFC 5794）。128位分组，256位密钥。独立于 NIST/AES 的高安全性 AEAD 算法。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.GCM
        ),
        SigilAlgorithm(
            id = "CHACHA20_POLY1305",
            name = "ChaCha20-Poly1305",
            description = "D. J. Bernstein 设计的高速流加密算法。抗填充预言攻击和时序攻击。",
            type = CipherType.STREAM,
            defaultMode = CipherMode.POLY1305
        ),
        SigilAlgorithm(
            id = "XCHACHA20_POLY1305",
            name = "XChaCha20-Poly1305",
            description = "扩展随机数变体（192位）。消除随机数碰撞风险。",
            type = CipherType.STREAM,
            defaultMode = CipherMode.POLY1305
        ),
        SigilAlgorithm(
            id = "SERPENT_CBC",
            name = "Serpent",
            description = "加密领域的「坦克」。AES 决赛入围算法，32轮（AES 仅14轮）。速度较慢，但提供最高安全裕度。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "TWOFISH_CBC",
            name = "Twofish",
            description = "复杂密钥调度使其极难被暴力破解。Bruce Schneier 设计。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "CAMELLIA_CBC",
            name = "Camellia",
            description = "欧盟（NESSIE）和日本（CRYPTREC）标准。安全性与 AES 相当。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "SM4_CBC",
            name = "SM4",
            description = "中国无线局域网国家标准（GB/T 32907）。中国政府数据安全强制标准。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "AES_CBC",
            name = "AES-256（CBC）",
            description = "经典 AES。兼容性好，但 GCM 模式具备内置完整性校验，更受推荐。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "CAST6_CBC",
            name = "CAST-256",
            description = "RFC 2612。AES 决赛入围算法，以抗线性和差分密码分析著称。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "RC6_CBC",
            name = "RC6",
            description = "Rivest（RSA）设计。简单快速，依赖数据相关旋转。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "SEED_CBC",
            name = "SEED",
            description = "韩国标准（KISA）。广泛应用于亚洲银行业安全领域。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC
        ),
        SigilAlgorithm(
            id = "BLOWFISH_CBC",
            name = "Blowfish",
            description = "Schneier 设计的经典算法。短文本加密速度快。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64位分组长度。大文件存在生日攻击漏洞。"
        ),
        SigilAlgorithm(
            id = "IDEA_CBC",
            name = "IDEA",
            description = "原始 PGP 加密算法。使用128位密钥。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64位分组长度。传统算法。"
        ),
        SigilAlgorithm(
            id = "CAST5_CBC",
            name = "CAST-128",
            description = "旧版 GPG 默认加密算法。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64位分组长度。传统算法。"
        ),
        SigilAlgorithm(
            id = "GOST_CBC",
            name = "GOST 28147",
            description = "苏联/俄罗斯标准。32轮 Feistel 网络结构。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64位分组长度。理论上存在高级分析攻击风险。"
        ),
        SigilAlgorithm(
            id = "TEA_CBC",
            name = "TEA",
            description = "微型加密算法。代码极其简洁。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "密钥调度较弱。存在等效密钥攻击漏洞。"
        ),
        SigilAlgorithm(
            id = "XTEA_CBC",
            name = "XTEA",
            description = "扩展 TEA。修复了 TEA 的部分弱点。",
            type = CipherType.BLOCK,
            defaultMode = CipherMode.CBC,
            isWeak = true,
            securityWarning = "64位分组长度。仅限教育/传统用途。"
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
    val availableProfiles: List<EncryptionProfile> = ProfileRegistry.builtInProfiles,
    val activeProfile: EncryptionProfile = ProfileRegistry.defaultProfile,
    val editingProfileId: String? = null,
    val isDemoDropdownExpanded: Boolean = false,
    val isDemoDrawerOpen: Boolean = false,
    val customLayers: List<LayerEntry> = listOf(LayerEntry(algorithm = CryptoEngine.Algorithm.AES_GCM)),
    val isCompressionEnabled: Boolean = true,

    // --- File / Directory Encryption ---
    val fileSourceUri: String? = null,
    val fileSourceName: String = "",
    val fileSourceIsDirectory: Boolean = false,
    val fileDestTreeUri: String? = null,
    val fileDestName: String = "",
    val filePassword: String = "",
    val fileStatusText: String = "请选择要加密/解密的文件或文件夹。",
    val fileLastResultName: String? = null
)
