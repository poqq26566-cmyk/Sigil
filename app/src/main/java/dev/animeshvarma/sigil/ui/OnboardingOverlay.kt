package dev.animeshvarma.sigil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class OnboardingState {
    START_SCREEN,
    BASIC_INTRO,
    BASIC_PROFILE_EXPLAIN,
    BASIC_INPUT, BASIC_PASS, BASIC_ENCRYPT_WAIT, BASIC_ENCRYPT_DONE, BASIC_OUTPUT,
    DECRYPT_PREP, DECRYPT_WAIT, DECRYPT_DONE,
    DRAWER_SHOW,
    KEYSTORE_NAV, KEYSTORE_EXPLAIN, KEYSTORE_USAGE,
    SETTINGS_NAV, SETTINGS_EXPLAIN,
    FORK_SELECTION,
    ADV_CUSTOM_INTRO, ADV_CUSTOM_LAYERS, ADV_CUSTOM_REORDER, ADV_BLOB_EXPLAIN,
    ADV_LOGS_PREP, ADV_LOGS_VIEW, ADV_RELEASES,
    FINISHED
}

private const val DEMO_LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Pellentesque semper, nisi ac cursus vulputate, diam est cursus nibh, non suscipit lorem libero vitae metus. Donec pharetra lectus id erat aliquet, eu volutpat felis condimentum."

private data class StepConfig(
    val title: String,
    val body: String,
    val next: OnboardingState = OnboardingState.FINISHED,
    val alignment: Alignment = Alignment.BottomCenter,
    val btnLabel: String = "下一步"
)

private val OnboardingState.config: StepConfig
    get() = when (this) {
        OnboardingState.BASIC_INTRO -> StepConfig(
            "主要工作区",
            "这是「自动」标签页，包含三个主要部分：输入、密码和输出。",
            next = OnboardingState.BASIC_PROFILE_EXPLAIN
        )
        OnboardingState.BASIC_PROFILE_EXPLAIN -> StepConfig(
            "安全配置方案",
            "使用书签图标切换加密配置方案。\n\n• 印记链：最高安全性（级联加密）。\n• 标准 AES：最高兼容性（原始模式）。\n\n您可以在「自定义」标签页中创建自己的加密链。",
            next = OnboardingState.BASIC_INPUT
        )
        OnboardingState.BASIC_INPUT -> StepConfig(
            "1. 输入",
            "在第一个输入框中输入明文或消息进行处理。",
            next = OnboardingState.BASIC_PASS
        )
        OnboardingState.BASIC_PASS -> StepConfig(
            "2. 密码",
            "使用强密码保护您的消息。您可以使用可见性图标在操作前检查输入。",
            next = OnboardingState.BASIC_ENCRYPT_WAIT
        )
        OnboardingState.BASIC_ENCRYPT_WAIT -> StepConfig(
            "3. 执行",
            "点击「加密」将应用当前配置方案的算法链（例如四层级联）。",
            next = OnboardingState.BASIC_ENCRYPT_DONE,
            alignment = Alignment.TopCenter,
            btnLabel = "加密"
        )
        OnboardingState.BASIC_ENCRYPT_DONE -> StepConfig(
            "处理中...",
            "系统正在处理...\n\n趣味事实：大部分执行时间消耗在密钥派生（KDF）上，而非加密算法本身。",
            next = OnboardingState.BASIC_OUTPUT,
            alignment = Alignment.TopCenter
        )
        OnboardingState.BASIC_OUTPUT -> StepConfig(
            "4. 密文",
            "生成的密文无法破解，需要正确的密钥才能解密。",
            next = OnboardingState.DECRYPT_PREP,
            alignment = Alignment.TopCenter
        )
        OnboardingState.DECRYPT_PREP -> StepConfig(
            "5. 解密",
            "要解密消息，请将密文粘贴回输入框中（可在本机或其他设备上操作）。",
            next = OnboardingState.DECRYPT_WAIT
        )
        OnboardingState.DECRYPT_WAIT -> StepConfig(
            "6. 身份验证",
            "输入正确的密码进行验证并还原原始消息。",
            next = OnboardingState.DECRYPT_DONE,
            btnLabel = "解密"
        )
        OnboardingState.DECRYPT_DONE -> StepConfig(
            "7. 验证",
            "解密成功，原始明文已恢复。",
            next = OnboardingState.DRAWER_SHOW,
            alignment = Alignment.TopCenter
        )
        OnboardingState.DRAWER_SHOW -> StepConfig(
            "导航",
            "您可以从左边缘滑出或点击 ☰ 图标打开导航抽屉，访问更多工具和设置。",
            next = OnboardingState.KEYSTORE_NAV
        )
        OnboardingState.KEYSTORE_NAV -> StepConfig(
            "密钥库模块",
            "密钥库管理您保存的凭据。",
            next = OnboardingState.KEYSTORE_EXPLAIN
        )
        OnboardingState.KEYSTORE_EXPLAIN -> StepConfig(
            "硬件安全",
            "密钥存储在设备硬件支持的可信执行环境中。\n\n为安全起见，切勿通过同一渠道传输密钥和消息。面对面交换是最安全的方式。",
            next = OnboardingState.KEYSTORE_USAGE
        )
        OnboardingState.KEYSTORE_USAGE -> StepConfig(
            "快速访问",
            "您可以通过点击密码字段中的钥匙图标来快速调用已保存的密钥。",
            next = OnboardingState.SETTINGS_NAV
        )
        OnboardingState.SETTINGS_NAV -> StepConfig(
            "设置",
            "设置面板控制应用行为和安全性功能。",
            next = OnboardingState.SETTINGS_EXPLAIN
        )
        OnboardingState.SETTINGS_EXPLAIN -> StepConfig(
            "控制面板",
            "在此配置应用锁、宽松期、加密参数和屏幕保护。\n\n关闭 Material You 后可手动自定义颜色。",
            next = OnboardingState.FORK_SELECTION
        )
        OnboardingState.ADV_CUSTOM_INTRO -> StepConfig(
            "自定义工作台",
            "此界面允许您构建独特的加密链。",
            next = OnboardingState.ADV_CUSTOM_LAYERS
        )
        OnboardingState.ADV_CUSTOM_LAYERS -> StepConfig(
            "算法注册表",
            "从 15 种以上工业级算法中选择，包括 AES、Twofish、Camellia 和 GOST。",
            next = OnboardingState.ADV_CUSTOM_REORDER
        )
        OnboardingState.ADV_CUSTOM_REORDER -> StepConfig(
            "序列控制",
            "使用箭头控制调整执行顺序，或使用添加按钮增加新层。",
            next = OnboardingState.ADV_BLOB_EXPLAIN
        )
        OnboardingState.ADV_BLOB_EXPLAIN -> StepConfig(
            "容器结构",
            "输出结构包含头部、盐值、初始向量、密文和 HMAC。自动模式通过解析头部自动确定解密顺序。",
            next = OnboardingState.ADV_LOGS_PREP,
            alignment = Alignment.TopCenter
        )
        OnboardingState.ADV_LOGS_PREP -> StepConfig(
            "系统控制台",
            "系统控制台允许您实时审计每个加密步骤、时间指标和密钥派生过程。",
            next = OnboardingState.ADV_LOGS_VIEW,
            btnLabel = "打开日志"
        )
        OnboardingState.ADV_LOGS_VIEW -> StepConfig(
            "审计日志",
            "此视图显示您刚执行的加密操作的原始遥测数据。",
            next = OnboardingState.ADV_RELEASES
        )
        OnboardingState.ADV_RELEASES -> StepConfig(
            "透明度",
            "在此查看主要应用更新，或访问代码仓库获取详细版本说明。",
            next = OnboardingState.FINISHED,
            btnLabel = "完成"
        )
        OnboardingState.START_SCREEN,
        OnboardingState.FORK_SELECTION,
        OnboardingState.FINISHED -> error("$this has no StepConfig; handled separately in OnboardingOrchestrator")
    }

@Composable
fun OnboardingOrchestrator(
    viewModel: SigilViewModel,
    onComplete: () -> Unit
) {
    var state by remember { mutableStateOf(OnboardingState.START_SCREEN) }

    LaunchedEffect(state) {
        delay(100)
        when (state) {
            OnboardingState.START_SCREEN -> { /* 静态 */ }
            OnboardingState.BASIC_INTRO -> {
                viewModel.setDemoMode(true)
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.AUTO)
                viewModel.injectDemoData("", "", "")
            }
            OnboardingState.BASIC_INPUT -> viewModel.injectDemoData(DEMO_LOREM, "", "")
            OnboardingState.BASIC_PASS -> viewModel.injectDemoData(DEMO_LOREM, "BlueHorse", "")
            OnboardingState.DECRYPT_PREP -> {
                val output = viewModel.uiState.value.autoOutput
                viewModel.injectDemoData(output, "BlueHorse", "")
            }
            OnboardingState.DRAWER_SHOW -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                delay(300)
                viewModel.toggleDemoDrawer(true)
            }
            OnboardingState.KEYSTORE_NAV -> {
                viewModel.toggleDemoDrawer(false)
                delay(400)
                viewModel.injectDemoVault()
                viewModel.onScreenSelected(AppScreen.KEYSTORE)
            }
            OnboardingState.KEYSTORE_USAGE -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                delay(500)
                viewModel.toggleDemoDropdown(true)
            }
            OnboardingState.SETTINGS_NAV -> {
                viewModel.toggleDemoDropdown(false)
                viewModel.onScreenSelected(AppScreen.SETTINGS)
            }
            OnboardingState.FORK_SELECTION -> viewModel.onScreenSelected(AppScreen.HOME)
            OnboardingState.ADV_CUSTOM_INTRO -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.CUSTOM)
                viewModel.injectDemoData("启动代码", "RedBattery", "")
            }
            OnboardingState.ADV_CUSTOM_REORDER -> {
                delay(500)
                viewModel.demoSwapLayers()
            }
            OnboardingState.ADV_LOGS_PREP -> { if (viewModel.uiState.value.showLogsDialog) viewModel.onLogsClicked() }
            OnboardingState.ADV_LOGS_VIEW -> { if (!viewModel.uiState.value.showLogsDialog) viewModel.onLogsClicked() }
            OnboardingState.ADV_RELEASES -> {
                if (viewModel.uiState.value.showLogsDialog) viewModel.onLogsClicked()
                viewModel.onScreenSelected(AppScreen.DOCS)
                viewModel.setDocsTab(1)
            }
            OnboardingState.FINISHED -> {
                viewModel.setDemoMode(false)
                onComplete()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            OnboardingState.START_SCREEN -> StartScreen(
                onStart = { state = OnboardingState.BASIC_INTRO },
                onSkip = { state = OnboardingState.FINISHED }
            )
            OnboardingState.FORK_SELECTION -> ForkSelectionScreen(
                onFinish = { state = OnboardingState.FINISHED },
                onAdvanced = { state = OnboardingState.ADV_CUSTOM_INTRO }
            )
            OnboardingState.FINISHED -> Box(Modifier.fillMaxSize())
            else -> {
                Box(modifier = Modifier.fillMaxSize().clickable(enabled = true, onClick = {}))

                PromptOverlay(state = state) {
                    when (state) {
                        OnboardingState.BASIC_ENCRYPT_WAIT -> viewModel.onEncrypt()
                        OnboardingState.DECRYPT_WAIT -> viewModel.onDecrypt()
                        OnboardingState.DRAWER_SHOW -> viewModel.toggleDemoDrawer(false)
                        else -> {}
                    }
                    state = state.config.next
                }
            }
        }
    }
}

@Composable
private fun StartScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Security, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))
            Text("印记", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("零信任加密环境", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(64.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("开始引导")
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onSkip) {
                Text("跳过引导", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PromptOverlay(state: OnboardingState, onNext: () -> Unit) {
    val config = state.config

    var offsetX by remember(state) { mutableFloatStateOf(0f) }
    var offsetY by remember(state) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = config.alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(state) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = config.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    text = config.body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onNext) {
                        Text(config.btnLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForkSelectionScreen(onFinish: () -> Unit, onAdvanced: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Text("基础知识已完成", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "您已掌握基本操作。\n\n（注意：配置方案选项卡的引导教程将在 v0.5.0 中添加）",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("完成并启动应用")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null)
                Spacer(Modifier.width(16.dp))
                Text("显示高级详情")
            }
        }
    }
}
