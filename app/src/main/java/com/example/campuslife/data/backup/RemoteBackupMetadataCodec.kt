package com.example.campuslife.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

internal object RemoteBackupMetadataCodec {
    private val allowedFileName = Regex("xiaoluanbao_[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{6}\\.xlbak")
    private val allowedHash = Regex("[0-9a-f]{64}")

    fun encode(metadata: RemoteBackupMetadata): ByteArray {
        require(allowedFileName.matches(metadata.fileName))
        require(allowedHash.matches(metadata.sha256))
        val properties = Properties().apply {
            setProperty("format", "xiaoluanbao-latest")
            setProperty("file_name", metadata.fileName)
            setProperty("file_size", metadata.fileSize.toString())
            setProperty("sha256", metadata.sha256)
            setProperty("created_at", metadata.createdAtEpochMillis.toString())
            setProperty("logical_epoch_day", metadata.logicalEpochDay.toString())
            setProperty("app_version", metadata.appVersionName)
        }
        return ByteArrayOutputStream().use { output ->
            properties.store(output, null)
            output.toByteArray()
        }
    }

    fun decode(bytes: ByteArray): RemoteBackupMetadata {
        try {
            val properties = Properties().apply { load(ByteArrayInputStream(bytes)) }
            if (properties.getProperty("format") != "xiaoluanbao-latest") {
                throw BackupFormatException("云端索引不是小卵包格式")
            }
            val fileName = properties.getProperty("file_name").orEmpty()
            val sha256 = properties.getProperty("sha256").orEmpty().lowercase()
            if (!allowedFileName.matches(fileName) || !allowedHash.matches(sha256)) {
                throw BackupFormatException("云端备份索引无效")
            }
            val size = properties.getProperty("file_size")?.toLongOrNull()
                ?: throw BackupFormatException("云端备份大小无效")
            if (size !in 1..BackupArchive.MAX_REMOTE_FILE_BYTES) {
                throw BackupFormatException("云端备份大小异常")
            }
            return RemoteBackupMetadata(
                fileName = fileName,
                fileSize = size,
                sha256 = sha256,
                createdAtEpochMillis = properties.getProperty("created_at")?.toLongOrNull()
                    ?: throw BackupFormatException("云端备份时间无效"),
                logicalEpochDay = properties.getProperty("logical_epoch_day")?.toLongOrNull()
                    ?: throw BackupFormatException("云端备份日期无效"),
                appVersionName = properties.getProperty("app_version").orEmpty(),
            )
        } catch (error: BackupFormatException) {
            throw error
        } catch (error: Exception) {
            throw BackupFormatException("云端备份索引无法读取", error)
        }
    }
}
