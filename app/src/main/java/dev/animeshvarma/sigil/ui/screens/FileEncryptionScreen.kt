package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.SigilButtonGroup
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl

private enum class FileOpMode { ENCRYPT, DECRYPT }

@Composable
fun FileEncryptionScreen(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current
    var opMode by remember { mutableStateOf(FileOpMode.ENCRYPT) }
    var passwordVisible by remember { mutableStateOf(false) }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "文件"
            viewModel.onFileSourceSelected(uri, name, isDirectory = false)
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "文件夹"
            viewModel.onFileSourceSelected(uri, name, isDirectory = true)
        }
    }

    val pickDestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "目录"
            viewModel.onFileDestSelected(uri, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        SigilSegmentedControl(
            items = listOf("加密", "解密"),
            selectedIndex = if (opMode == FileOpMode.ENCRYPT) 0 else 1,
            onItemSelection = { opMode = if (it == 0) FileOpMode.ENCRYPT else FileOpMode.DECRYPT },
            modifier = Modifier.fillMaxWidth(0.65f)
        )

        Spacer(Modifier.height(16.dp))

        // --- Source selection ---
        Text("来源", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))

        if (opMode == FileOpMode.ENCRYPT) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { pickFileLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.InsertDriveFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件")
                }
                OutlinedButton(onClick = { pickFolderLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件夹")
                }
            }
        } else {
            OutlinedButton(
                onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("选择 .sigil 加密文件")
            }
        }

        if (uiState.fileSourceName.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionChip(
                icon = if (uiState.fileSourceIsDirectory) Icons.Default.Folder else Icons.Default.Description,
                label = uiState.fileSourceName
            )
        }

        Spacer(Modifier.height(16.dp))

        // --- Destination selection ---
        Text("保存位置", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = { pickDestLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("选择保存目录")
        }
        if (uiState.fileDestName.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionChip(icon = Icons.Default.Folder, label = uiState.fileDestName)
        }

        Spacer(Modifier.height(16.dp))

        // --- Password ---
        Text("密码", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = uiState.filePassword,
            onValueChange = { viewModel.onFilePasswordChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(if (passwordVisible) Icons.Default.LockOpen else Icons.Default.Lock, null)
                }
            },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "使用当前配置方案：${uiState.activeProfile.name}（可在首页“加密配置”中切换）。单个文件/文件夹总大小需 ≤ 10MB。",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        SigilButtonGroup(
            onLogs = { viewModel.onLogsClicked() },
            onEncrypt = { if (opMode == FileOpMode.ENCRYPT) viewModel.onFileEncrypt(context) },
            onDecrypt = { if (opMode == FileOpMode.DECRYPT) viewModel.onFileDecrypt(context) }
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                uiState.fileStatusText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SelectionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, maxLines = 1)
    }
}
