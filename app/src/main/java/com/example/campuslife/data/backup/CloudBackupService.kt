package com.example.campuslife.data.backup

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** WebDAV 与本地备份包之间的单一编排入口，界面和后台任务共用。 */
class CloudBackupService(context: Context) {
    private val appContext = context.applicationContext
    private val repository = AppBackupRepository(appContext)
    private val webDav = NutstoreWebDavClient()

    /**
     * 直接读取最新索引来同时验证凭据。坚果云会对 WebDAV 根目录的 HEAD 返回 403，
     * 即使同一组凭据执行 PROPFIND 已成功；索引 GET 的 404 则能明确表示“已连接但暂无备份”。
     */
    suspend fun inspect(credentials: NutstoreCredentials): RemoteBackupMetadata? =
        webDav.latestBackup(credentials)

    suspend fun backup(credentials: NutstoreCredentials): RemoteBackupMetadata {
        val (file, created) = repository.createTemporaryBackup()
        try {
            val metadata = RemoteBackupMetadata(
                fileName = created.fileName,
                fileSize = created.fileSize,
                sha256 = created.sha256,
                createdAtEpochMillis = created.manifest.createdAtEpochMillis,
                logicalEpochDay = created.manifest.logicalEpochDay,
                appVersionName = created.manifest.appVersionName,
            )
            webDav.uploadBackup(credentials, file, metadata)
            return metadata
        } finally {
            file.delete()
        }
    }

    suspend fun restoreLatest(
        credentials: NutstoreCredentials,
        expected: RemoteBackupMetadata? = null,
    ): RestoreBackupResult {
        val metadata = webDav.latestBackup(credentials)
            ?: throw NutstoreException("坚果云中还没有小卵包备份")
        if (expected != null && metadata != expected) {
            throw NutstoreException("云端最新备份已经变化，请重新确认后再恢复")
        }
        val directory = File(appContext.cacheDir, "cloud_backup_downloads").also(File::mkdirs)
        val destination = File(directory, "${UUID.randomUUID()}_${metadata.fileName}")
        try {
            webDav.downloadBackup(credentials, metadata, destination)
            val downloadedHash = BackupArchive.sha256(destination)
            if (!MessageDigest.isEqual(
                    downloadedHash.toByteArray(Charsets.US_ASCII),
                    metadata.sha256.toByteArray(Charsets.US_ASCII),
                )
            ) {
                throw BackupFormatException("云端备份校验失败，文件可能不完整")
            }
            return repository.restoreBackup(destination)
        } finally {
            withContext(Dispatchers.IO) { destination.delete() }
        }
    }
}
