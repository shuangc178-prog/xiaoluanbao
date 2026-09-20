package com.example.campuslife.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.campuslife.data.backup.RemoteBackupMetadata
import com.example.campuslife.ui.theme.claude
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CloudBackupDialog(
    controller: CloudBackupController,
    onDismiss: () -> Unit,
) {
    var email by remember(controller.settings.email) { mutableStateOf(controller.settings.email) }
    var appPassword by remember { mutableStateOf("") }
    var pendingConfirmation by remember { mutableStateOf<BackupConfirmation?>(null) }

    LaunchedEffect(email, appPassword) {
        if (!controller.settings.configured) controller.onClearFeedback()
    }

    controller.restoreCandidate?.let { candidate ->
        RestoreConfirmationDialog(
            backup = candidate,
            isBusy = controller.isBusy,
            onDismiss = controller.onCancelRestore,
            onConfirm = controller.onConfirmRestore,
        )
    }

    pendingConfirmation?.let { action ->
        BackupActionConfirmationDialog(
            action = action,
            folderName = controller.remoteFolderName,
            isBusy = controller.isBusy,
            onDismiss = { pendingConfirmation = null },
            onConfirm = {
                pendingConfirmation = null
                when (action) {
                    BackupConfirmation.Upload -> controller.onBackupNow()
                    BackupConfirmation.Disconnect -> controller.onDisconnect()
                }
            },
        )
    }

    Dialog(onDismissRequest = { if (!controller.isBusy) onDismiss() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("备份与恢复", style = MaterialTheme.typography.headlineSmall)

                if (controller.settings.configured) {
                    ConnectedBackupContent(
                        controller = controller,
                        onRequestUpload = { pendingConfirmation = BackupConfirmation.Upload },
                        onRequestDisconnect = { pendingConfirmation = BackupConfirmation.Disconnect },
                    )
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("坚果云邮箱") },
                        singleLine = true,
                        enabled = !controller.isBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    OutlinedTextField(
                        value = appPassword,
                        onValueChange = { appPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("WebDAV 应用密码") },
                        singleLine = true,
                        enabled = !controller.isBusy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    Button(
                        onClick = { controller.onConnect(email, appPassword) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !controller.isBusy && email.isNotBlank() && appPassword.isNotBlank(),
                    ) {
                        Text("连接坚果云")
                    }
                }

                OperationStatus(controller)
                ConnectionStatus(controller)
            }
        }
    }
}

@Composable
private fun ConnectedBackupContent(
    controller: CloudBackupController,
    onRequestUpload: () -> Unit,
    onRequestDisconnect: () -> Unit,
) {
    Button(
        onClick = onRequestUpload,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.isBusy,
    ) {
        Text("上传本机备份")
    }
    OutlinedButton(
        onClick = controller.onPrepareRestore,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.isBusy,
    ) {
        Text("从坚果云恢复")
    }
    TextButton(
        onClick = onRequestDisconnect,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.isBusy,
    ) {
        Text("断开账号")
    }
}

@Composable
private fun ConnectionStatus(controller: CloudBackupController) {
    Text(
        text = if (controller.settings.configured) {
            "已连接 · ${controller.settings.email}"
        } else {
            "未连接"
        },
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        color = if (controller.settings.configured) {
            MaterialTheme.claude.green
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun OperationStatus(controller: CloudBackupController) {
    if (controller.isBusy) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            Text(
                text = when (controller.operation) {
                    CloudBackupOperation.Checking -> "正在连接坚果云…"
                    CloudBackupOperation.BackingUp -> "正在生成并上传完整备份…"
                    CloudBackupOperation.Restoring -> "正在校验并恢复数据…"
                    CloudBackupOperation.Idle -> ""
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    controller.message?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.claude.green)
    }
    (controller.error ?: controller.settings.lastError)?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.claude.danger)
    }
}

@Composable
private fun RestoreConfirmationDialog(
    backup: RemoteBackupMetadata,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("确认恢复云端数据？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("备份时间：${formatBackupTime(backup.createdAtEpochMillis)}")
                Text("备份大小：${formatFileSize(backup.fileSize)}")
                Text("当前数据会被替换；恢复前将自动保留一份本机安全副本。")
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isBusy) { Text("确认恢复") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("取消") }
        },
    )
}

private enum class BackupConfirmation { Upload, Disconnect }

@Composable
private fun BackupActionConfirmationDialog(
    action: BackupConfirmation,
    folderName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isUpload = action == BackupConfirmation.Upload
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(if (isUpload) "确认上传本机备份？" else "确认断开账号？") },
        text = {
            Text(
                if (isUpload) {
                    "本机完整数据将上传到 $folderName。"
                } else {
                    "本机会清除连接信息，坚果云中的备份不会删除。"
                },
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isBusy) {
                Text(if (isUpload) "确认上传" else "确认断开")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("取消") }
        },
    )
}

private val backupTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

private fun formatBackupTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(backupTimeFormatter)

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}
