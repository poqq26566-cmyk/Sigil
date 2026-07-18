package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.theme.AnimationConfig
import dev.animeshvarma.sigil.util.StegoEngine
import java.io.ByteArrayOutputStream

@Composable
fun SteganographyScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("文本", "图片", "视频")

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
            modifier = Modifier.fillMaxWidth(0.9f)
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
                label = "StegoTabTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> TextStegoTab()
                    1 -> ImageStegoTab()
                    2 -> UnderConstructionView()
                }
            }
        }
    }
}

private enum class StegoMode { HIDE, EXTRACT }

@Composable
private fun TextStegoTab() {
    val clipboard = LocalClipboardManager.current
    var mode by remember { mutableStateOf(StegoMode.HIDE) }
    var coverText by remember { mutableStateOf("") }
    var secretText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScrollWorkaround()) {
        SigilSegmentedControl(
            items = listOf("隐藏", "提取"),
            selectedIndex = if (mode == StegoMode.HIDE) 0 else 1,
            onItemSelection = { mode = if (it == 0) StegoMode.HIDE else StegoMode.EXTRACT; resultText = ""; errorText = null },
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(Modifier.height(12.dp))

        if (mode == StegoMode.HIDE) {
            OutlinedTextField(
                value = coverText,
                onValueChange = { coverText = it },
                label = { Text("载体文本（例如一段正常聊天内容）") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = secretText,
                onValueChange = { secretText = it },
                label = { Text("要隐藏的秘密内容") },
                modifier = Modifier.fillMaxWidth().height(110.dp),
                shape = RoundedCornerShape(20.dp)
            )
        } else {
            OutlinedTextField(
                value = coverText,
                onValueChange = { coverText = it },
                label = { Text("粘贴含隐写数据的文本") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(20.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（可选，用于加密隐藏内容）") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                errorText = null
                try {
                    if (mode == StegoMode.HIDE) {
                        resultText = StegoEngine.encodeText(coverText, secretText, password)
                    } else {
                        resultText = StegoEngine.decodeText(coverText, password)
                            ?: "未在文本中检测到隐藏内容。"
                    }
                } catch (e: Exception) {
                    errorText = e.message ?: "操作失败。"
                    resultText = ""
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text(if (mode == StegoMode.HIDE) "隐藏" else "提取")
        }

        errorText?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        if (resultText.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = resultText,
                onValueChange = {},
                readOnly = true,
                label = { Text(if (mode == StegoMode.HIDE) "输出（看起来和原文一样，但携带隐藏数据）" else "提取结果") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(20.dp),
                trailingIcon = {
                    IconButton(onClick = { clipboard.setText(AnnotatedString(resultText)) }) {
                        Icon(Icons.Default.ContentCopy, "复制")
                    }
                }
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "原理：在文本末尾插入零宽字符编码秘密数据，肉眼不可见，复制粘贴时会一并携带。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ImageStegoTab() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(StegoMode.HIDE) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var secretText by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var outputBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val currentToast = remember { mutableStateOf<Toast?>(null) }
    val showToast: (String) -> Unit = { message ->
        currentToast.value?.cancel()
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        currentToast.value = toast
        toast.show()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* Some providers don't support persistable grants; a one-shot read still works. */ }
            imageUri = uri
            outputBitmap = null
            resultText = ""
            errorText = null
            previewBitmap = try {
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
        }
    }

    val saveImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        val bmp = outputBitmap
        if (uri != null && bmp != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                showToast("已保存")
            } catch (e: Exception) {
                showToast("保存失败：${e.message}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SigilSegmentedControl(
            items = listOf("隐藏", "提取"),
            selectedIndex = if (mode == StegoMode.HIDE) 0 else 1,
            onItemSelection = {
                mode = if (it == 0) StegoMode.HIDE else StegoMode.EXTRACT
                resultText = ""; errorText = null; outputBitmap = null
            },
            modifier = Modifier.fillMaxWidth(0.6f)
        )
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { pickImageLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ImageIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (imageUri == null) "选择图片（建议 PNG，避免有损压缩）" else "重新选择图片")
        }

        previewBitmap?.let { bmp ->
            Spacer(Modifier.height(10.dp))
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "预览",
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (mode == StegoMode.HIDE) {
            OutlinedTextField(
                value = secretText,
                onValueChange = { secretText = it },
                label = { Text("要隐藏的秘密内容") },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                shape = RoundedCornerShape(20.dp)
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码（可选，用于加密隐藏内容）") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                errorText = null
                val uri = imageUri
                if (uri == null) {
                    errorText = "请先选择图片。"
                    return@Button
                }
                try {
                    if (mode == StegoMode.HIDE) {
                        val bmp = StegoEngine.encodeImage(context, uri, secretText, password)
                        outputBitmap = bmp
                        showToast("已生成，点击下方保存")
                    } else {
                        resultText = StegoEngine.decodeImage(context, uri, password)
                    }
                } catch (e: Exception) {
                    errorText = e.message ?: "操作失败。"
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(if (mode == StegoMode.HIDE) Icons.Default.ImageIcon else Icons.Default.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (mode == StegoMode.HIDE) "隐藏并生成图片" else "提取")
        }

        errorText?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        outputBitmap?.let {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { saveImageLauncher.launch("sigil_stego_${System.currentTimeMillis()}.png") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("保存为 PNG")
            }
        }

        if (resultText.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("提取结果：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(resultText, fontSize = 14.sp)
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "原理：将秘密数据写入像素颜色的最低位（LSB），肉眼几乎无法察觉。必须保存为 PNG 等无损格式，分享到会压缩图片的平台（如微信朋友圈）会破坏隐藏数据。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Modifier.verticalScrollWorkaround(): Modifier {
    val scrollState = rememberScrollState()
    return this.then(Modifier.verticalScroll(scrollState))
}
