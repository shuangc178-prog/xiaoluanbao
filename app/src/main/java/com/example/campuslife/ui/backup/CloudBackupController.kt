package com.example.campuslife.ui.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.data.backup.CloudBackupScheduler
import com.example.campuslife.data.backup.CloudBackupService
import com.example.campuslife.data.backup.CloudBackupSettings
import com.example.campuslife.data.backup.CloudBackupSettingsStore
import com.example.campuslife.data.backup.BackupDeviceFolder
import com.example.campuslife.data.backup.NutstoreCredentials
import com.example.campuslife.data.backup.RemoteBackupMetadata
import com.example.campuslife.data.backup.userFacingBackupMessage
import com.example.campuslife.data.reminder.ReminderBootstrap
import kotlinx.coroutines.launch

enum class CloudBackupOperation { Idle, Checking, BackingUp, Restoring }

class CloudBackupController internal constructor(
    val settings: CloudBackupSettings,
    val remoteFolderName: String,
    val operation: CloudBackupOperation,
    val restoreCandidate: RemoteBackupMetadata?,
    val message: String?,
    val error: String?,
    val onConnect: (email: String, appPassword: String) -> Unit,
    val onPrepareRestore: () -> Unit,
    val onCancelRestore: () -> Unit,
    val onConfirmRestore: () -> Unit,
    val onBackupNow: () -> Unit,
    val onDisconnect: () -> Unit,
    val onClearFeedback: () -> Unit,
) {
    val isBusy: Boolean get() = operation != CloudBackupOperation.Idle
}

@Composable
fun rememberCloudBackupController(): CloudBackupController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember(context) { CloudBackupSettingsStore(context) }
    val service = remember(context) { CloudBackupService(context) }
    val remoteFolderName = remember { BackupDeviceFolder.current() }
    val settings by store.settings.collectAsState(initial = CloudBackupSettings())

    var operation by remember { mutableStateOf(CloudBackupOperation.Idle) }
    var pendingCredentials by remember { mutableStateOf<NutstoreCredentials?>(null) }
    var restoreCandidate by remember { mutableStateOf<RemoteBackupMetadata?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetFeedback() {
        message = null
        error = null
    }

    fun connect(email: String, appPassword: String) {
        if (operation != CloudBackupOperation.Idle) return
        scope.launch {
            resetFeedback()
            operation = CloudBackupOperation.Checking
            try {
                val credentials = NutstoreCredentials(email.trim(), appPassword)
                service.inspect(credentials)
                store.saveCredentials(credentials, automaticBackupEnabled = false)
                pendingCredentials = null
                restoreCandidate = null
                message = null
            } catch (failure: Exception) {
                error = failure.userFacingBackupMessage()
            } finally {
                operation = CloudBackupOperation.Idle
            }
        }
    }

    fun prepareRestore() {
        if (operation != CloudBackupOperation.Idle) return
        scope.launch {
            resetFeedback()
            operation = CloudBackupOperation.Checking
            try {
                val credentials = store.credentials()
                    ?: error("已保存的坚果云密码无法读取")
                val latest = service.inspect(credentials)
                pendingCredentials = credentials
                restoreCandidate = latest
                if (latest == null) {
                    message = "$remoteFolderName 中还没有可恢复的备份"
                }
            } catch (failure: Exception) {
                error = failure.userFacingBackupMessage()
            } finally {
                operation = CloudBackupOperation.Idle
            }
        }
    }

    fun backupNow() {
        if (operation != CloudBackupOperation.Idle) return
        scope.launch {
            resetFeedback()
            operation = CloudBackupOperation.BackingUp
            try {
                val credentials = store.credentials()
                    ?: error("已保存的坚果云密码无法读取")
                val metadata = service.backup(credentials)
                store.enableAutomaticBackup()
                store.markSuccess(metadata)
                CloudBackupScheduler.ensureScheduledAsync(context)
                message = "最新数据已备份到坚果云"
            } catch (failure: Exception) {
                val userMessage = failure.userFacingBackupMessage()
                store.markFailure(userMessage)
                error = userMessage
            } finally {
                operation = CloudBackupOperation.Idle
            }
        }
    }

    fun confirmRestore() {
        if (operation != CloudBackupOperation.Idle) return
        val credentials = pendingCredentials ?: return
        val candidate = restoreCandidate ?: return
        restoreCandidate = null
        scope.launch {
            resetFeedback()
            operation = CloudBackupOperation.Restoring
            try {
                val result = service.restoreLatest(credentials, expected = candidate)
                store.saveCredentials(credentials)
                store.markSuccess(candidate)
                CloudBackupScheduler.ensureScheduledAsync(context)
                ReminderBootstrap.syncAsync(context)
                message = "已恢复 ${result.manifest.appVersionName} 生成的备份；覆盖前的数据也已保留"
            } catch (failure: Exception) {
                error = failure.userFacingBackupMessage()
            } finally {
                operation = CloudBackupOperation.Idle
            }
        }
    }

    fun disconnect() {
        if (operation != CloudBackupOperation.Idle) return
        scope.launch {
            store.disconnect()
            pendingCredentials = null
            restoreCandidate = null
            resetFeedback()
            message = null
        }
    }

    return CloudBackupController(
        settings = settings,
        remoteFolderName = remoteFolderName,
        operation = operation,
        restoreCandidate = restoreCandidate,
        message = message,
        error = error,
        onConnect = ::connect,
        onPrepareRestore = ::prepareRestore,
        onCancelRestore = { restoreCandidate = null },
        onConfirmRestore = ::confirmRestore,
        onBackupNow = ::backupNow,
        onDisconnect = ::disconnect,
        onClearFeedback = ::resetFeedback,
    )
}
