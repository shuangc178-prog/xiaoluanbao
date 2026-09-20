package com.example.campuslife.data.backup

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * 坚果云的 WebDAV 根目录只是同步文件夹列表，不能直接 PUT 文件。
 * 每台设备使用自己的子目录；MKCOL 由 OkHttp 发出，其余大文件传输继续使用流式连接。
 */
class NutstoreWebDavClient(
    deviceFolder: String = BackupDeviceFolder.current(),
) {
    private val storageBaseUrl = "$ACCOUNT_BASE_URL$deviceFolder/"
    private val folderClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MILLIS.toLong(), TimeUnit.MILLISECONDS)
        .build()

    suspend fun latestBackup(credentials: NutstoreCredentials): RemoteBackupMetadata? =
        withContext(Dispatchers.IO) {
            ensureStorageFolder(credentials)
            val connection = openConnection(remoteUrl(LATEST_FILE), "GET", credentials)
            try {
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_FOUND -> null
                    in 200..299 -> RemoteBackupMetadataCodec.decode(
                        connection.inputStream.use { it.readLimited(MAX_INDEX_BYTES) },
                    )
                    else -> throw httpError(status)
                }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun uploadBackup(
        credentials: NutstoreCredentials,
        file: File,
        metadata: RemoteBackupMetadata,
    ) = withContext(Dispatchers.IO) {
        ensureStorageFolder(credentials)
        require(file.name == metadata.fileName)
        put(credentials, remoteUrl(metadata.fileName), "application/octet-stream", file.length()) { output ->
            file.inputStream().buffered().use { input -> input.copyTo(output) }
        }
        val latestBytes = RemoteBackupMetadataCodec.encode(metadata)
        // 索引最后上传：看到新索引时，对应的完整备份一定已经在云端。
        put(credentials, remoteUrl(LATEST_FILE), "text/plain; charset=utf-8", latestBytes.size.toLong()) {
            it.write(latestBytes)
        }
    }

    suspend fun downloadBackup(
        credentials: NutstoreCredentials,
        metadata: RemoteBackupMetadata,
        destination: File,
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.partial")
        partial.delete()
        val connection = openConnection(remoteUrl(metadata.fileName), "GET", credentials)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(status)
            val announced = connection.contentLengthLong
            if (announced > BackupArchive.MAX_REMOTE_FILE_BYTES || announced > metadata.fileSize) {
                throw NutstoreException("云端备份大小与索引不一致")
            }
            FileOutputStream(partial).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > BackupArchive.MAX_REMOTE_FILE_BYTES || total > metadata.fileSize) {
                            throw NutstoreException("云端备份大小异常")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (partial.length() != metadata.fileSize) {
                throw NutstoreException("云端备份下载不完整")
            }
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private inline fun put(
        credentials: NutstoreCredentials,
        url: String,
        contentType: String,
        contentLength: Long,
        writeBody: (java.io.OutputStream) -> Unit,
    ) {
        val connection = openConnection(url, "PUT", credentials).apply {
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setFixedLengthStreamingMode(contentLength)
        }
        try {
            connection.outputStream.buffered().use(writeBody)
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                throw NutstoreException(
                    "坚果云中找不到“我的坚果云”同步文件夹",
                    httpStatus = status,
                )
            }
            if (status !in 200..299) throw httpError(status)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        url: String,
        method: String,
        credentials: NutstoreCredentials,
    ): HttpsURLConnection {
        val normalizedEmail = credentials.email.trim()
        if (normalizedEmail.isBlank() || credentials.appPassword.isBlank()) {
            throw NutstoreException("请填写坚果云邮箱和应用密码")
        }
        val authorization = authorization(credentials, normalizedEmail)
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Authorization", authorization)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("User-Agent", "XiaoLuanBao-Android")
        }
    }

    private fun ensureStorageFolder(credentials: NutstoreCredentials) {
        val normalizedEmail = credentials.email.trim()
        if (normalizedEmail.isBlank() || credentials.appPassword.isBlank()) {
            throw NutstoreException("请填写坚果云邮箱和应用密码")
        }
        val request = Request.Builder()
            .url(storageBaseUrl)
            .method("MKCOL", null)
            .header("Authorization", authorization(credentials, normalizedEmail))
            .header("User-Agent", "XiaoLuanBao-Android")
            .build()
        folderClient.newCall(request).execute().use { response ->
            when (response.code) {
                200, 201, 204, 405 -> Unit
                409 -> throw NutstoreException(
                    "无法在“我的坚果云”中创建本机备份文件夹",
                    httpStatus = response.code,
                )
                else -> throw httpError(response.code)
            }
        }
    }

    private fun authorization(credentials: NutstoreCredentials, normalizedEmail: String): String {
        val token = Base64.getEncoder().encodeToString(
            "$normalizedEmail:${credentials.appPassword}".toByteArray(Charsets.UTF_8),
        )
        return "Basic $token"
    }

    private fun remoteUrl(fileName: String): String = storageBaseUrl + fileName

    private fun httpError(status: Int): NutstoreException {
        Log.w(LOG_TAG, "WebDAV request failed with HTTP $status")
        val message = when (status) {
            401 -> "坚果云账号或应用密码不正确（401）"
            403 -> "坚果云拒绝了本次访问（403）"
            404 -> "云端备份文件不存在"
            413 -> "备份文件超过坚果云允许的大小"
            507 -> "坚果云空间或本月上传流量不足"
            in 500..599 -> "坚果云服务暂时不可用"
            else -> "坚果云连接失败（$status）"
        }
        return NutstoreException(message, httpStatus = status)
    }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw BackupFormatException("云端备份索引过大")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val LOG_TAG = "NutstoreWebDav"
        const val ACCOUNT_BASE_URL =
            "https://dav.jianguoyun.com/dav/%E6%88%91%E7%9A%84%E5%9D%9A%E6%9E%9C%E4%BA%91/"
        const val LATEST_FILE = "xiaoluanbao_latest.properties"
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val MAX_INDEX_BYTES = 64 * 1024
    }
}
