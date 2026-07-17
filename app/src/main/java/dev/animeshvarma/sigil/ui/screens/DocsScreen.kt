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
    val tabs = listOf("文档", "版本说明")

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
        ReleaseData(
            version = "v0.4.5",
            title = "配置方案与密码",
            tag = "当前版本",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "加密配置方案",
                    listOf(
                        "自定义加密链：用户现可在「自定义」标签页中创建、命名并保存自己的多算法加密链。",
                        "原始模式：新增「原始」兼容模式。此模式绕过印记元数据头部，直接输出纯密文/IV，兼容标准外部工具。",
                        "印记链：默认级联方案（XChaCha20 + Serpent + Twofish + AES）现已成为明确的内置配置方案。"
                    )
                ),
                ReleaseCategory(
                    "认证系统重构",
                    listOf(
                        "密码支持：在数字 PIN 之外，新增了对字母数字密码的支持。",
                        "动态锁屏：键盘现根据锁屏类型在数字键盘和 QWERTY 键盘之间自动切换。",
                        "设置向导：将简单开关替换为安全的多步设置流程（选择 → 输入 → 确认）。"
                    )
                ),
                ReleaseCategory(
                    "加密引擎更新（v0.11.0）",
                    listOf(
                        "新算法：新增 XChaCha20-Poly1305（扩展随机数）和 ARIA-256-GCM。",
                        "安全存储：强化了 LockManager 逻辑，防止硬件存储失败时的乐观状态更新。",
                        "严格内存清理：增强加密循环中的内存擦除，对中间缓冲区进行归零。"
                    )
                )
            )
        ),
        ReleaseData(
            version = "v0.4.1",
            title = "透明性修复",
            tag = "2026年1月15日",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "构建透明性",
                    listOf(
                        "移除 Google 元数据：明确禁用 'dependenciesInfo' 块，确保 APK 的二进制透明性。"
                    )
                ),
                ReleaseCategory(
                    "文档",
                    listOf(
                        "商店合规：为第三方客户端优化了描述。",
                        "资源：标准化截图命名以确保商店排序一致。"
                    )
                ),
                ReleaseCategory(
                    "认证",
                    listOf(
                        "通过别名轮换将生物识别密钥迁移至 AES-GCM。"
                    )
                )
            )
        ),
        ReleaseData(
            version = "v0.4",
            title = "引导教程与访问控制",
            tag = "2026年1月7日",
            hasBreakingChanges = true,
            categories = listOf(
                ReleaseCategory(
                    "破坏性变更",
                    listOf(
                        "不兼容密文：先前版本加密的数据无法解密。密钥派生函数（KDF）逻辑已变更以支持动态调整。",
                        "密钥库清除：内部密钥库格式已强化。旧保存的密钥不兼容且已被清除。",
                        "认证重置：PIN 存储已迁移至加盐 Argon2 哈希架构。您需要设置新 PIN。"
                    )
                ),
                ReleaseCategory(
                    "核心加密（v0.10.0）",
                    listOf(
                        "四层级联：「自动模式」现使用 AES-256 + ChaCha20 + Twofish + Serpent 混合级联。",
                        "ChaCha20-Poly1305：新增对高性能 ARX 流加密算法（IETF 标准）的支持。",
                        "性能优化：优化 Argon2id 配置，在保证内存硬度的同时实现交互式 UI 响应。",
                        "填充预言缓解：统一异常处理以防止侧信道泄露。"
                    )
                ),
                ReleaseCategory(
                    "架构",
                    listOf(
                        "零知识认证：应用 PIN 现以加盐 Argon2 哈希存储。应用在数学上无法检索/泄露您的 PIN。",
                        "防篡改生物识别：指纹解锁现在绑定到 TEE CryptoObject。Root/Frida 钩子无法绕过认证。",
                        "安全剪贴板：新增「敏感内容」标志（Android 13+）和自动清除计时器。"
                    )
                ),
                ReleaseCategory(
                    "引导与探索",
                    listOf(
                        "交互式引导：为新用户提供全面的引导模拟，演示实时加密周期和安全密钥管理。",
                        "高级模式：面向高级用户的专门路径，详细介绍层管理器和硬件密钥库的内部工作原理。"
                    )
                ),
                ReleaseCategory(
                    "访问控制",
                    listOf(
                        "应用锁：使用设备生物识别或自定义 PIN（由硬件 TEE 保护）保护印记。",
                        "宽松期：「保持解锁」设置允许快速切换应用而无需立即重新认证。"
                    )
                ),
                ReleaseCategory(
                    "隐私与易失性",
                    listOf(
                        "遗忘协议：应用进入后台时立即从易失性内存（RAM）中清除敏感输入数据。",
                        "屏幕保护：启用 FLAG_SECURE 阻止截屏并在「最近任务」概览中隐藏内容。",
                        "清单加固：明确禁用 ADB 和云备份以防止数据提取。"
                    )
                ),
                ReleaseCategory(
                    "视觉定制",
                    listOf(
                        "高级主题引擎：全面支持 Material You（动态颜色）、深色/浅色模式以及自定义 HSV 颜色选择器。",
                        "设置全面改版：重新设计了设置标签页以适应新的 KDF 调整和外观控制。"
                    )
                )
            )
        ),
        ReleaseData(
            version = "v0.3",
            title = "密钥库实现",
            tag = "2025年12月9日",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "安全与存储（v0.9.0）",
                    listOf(
                        "硬件密钥库：密钥现通过 Android 可信执行环境（TEE）加密。",
                        "安全内存：实现激进的内存擦除（将 CharArrays 归零）以防止内存转储攻击。",
                        "密钥库架构：保存的密钥使用混合链「硬件 → AES → Twofish → Serpent」。",
                        "密钥管理：通过专用密钥库标签页查看、重命名和删除密钥。"
                    )
                ),
                ReleaseCategory(
                    "用户体验",
                    listOf(
                        "意图处理：印记现接受直接从外部应用（WhatsApp、Signal 等）分享的文本。",
                        "智能输入：密码字段替换为安全密钥库下拉菜单，实现一键访问。",
                        "视觉反馈：输出日志现在显示精确时间和详细故障诊断（HMAC 与格式错误）。",
                        "安全优先：为删除或显示敏感密钥增加了确认对话框。"
                    )
                ),
                ReleaseCategory(
                    "系统与结构",
                    listOf(
                        "无头模式：为即将推出的原始二进制模式添加了导航入口。",
                        "方向锁定：禁用横屏模式以确保一致的物理稳定性。",
                        "分享集成：为输出字段添加了直接「分享」按钮。"
                    )
                )
            )
        ),
        ReleaseData(
            version = "v0.2",
            title = "自定义实现",
            tag = "2025年11月30日",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "用户界面",
                    listOf(
                        "自定义加密标签页：完全手动控制加密层。",
                        "交互式物理效果：实现了基于弹簧的按钮和过渡动画。",
                        "可移动层：使用上下箭头重新排序算法，带有平滑动画。",
                        "精致列表：为长列表添加了淡出边缘和自定义动画滚动条。",
                        "搜索：添加了带描述的可过滤算法列表。"
                    )
                ),
                ReleaseCategory(
                    "核心加密（v0.8.0）",
                    listOf(
                        "引擎修复：修正了 Blowfish/RC6 的分组大小逻辑。",
                        "压缩：添加了 ZLIB 压缩开关。",
                        "扩展注册表：新增 Camellia、SM4、GOST、CAST6 等。",
                        "二进制打包：优化了内部容器格式。"
                    )
                )
            )
        ),
        ReleaseData(
            version = "v0.1",
            title = "基础架构",
            tag = "2025年11月26日",
            hasBreakingChanges = false,
            categories = listOf(
                ReleaseCategory(
                    "核心加密（v0.7.0）",
                    listOf(
                        "初始加密实现：随机三层加密链。",
                        "二进制容器：带有隐藏元数据的不透明 Base64 输出。",
                        "HMAC 完整性：加密-然后-MAC 架构。",
                        "内存硬化：Argon2id（64MB）。"
                    )
                ),
                ReleaseCategory(
                    "用户界面",
                    listOf(
                        "Material 3 设计：具有动态主题的初始 UI。",
                        "系统控制台：专用日志窗口。"
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
                                        text = "破坏性变更",
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

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                release.categories.forEach { category ->
                    val isBreakingSection = category.title.contains("破坏性", ignoreCase = true)

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
