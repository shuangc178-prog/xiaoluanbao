package com.example.campuslife.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Properties
import java.io.ByteArrayOutputStream

class RemoteBackupMetadataCodecTest {
    @Test
    fun latestPointerRoundTrips() {
        val metadata = RemoteBackupMetadata(
            fileName = "xiaoluanbao_2026-09-20_060001.xlbak",
            fileSize = 12_345L,
            sha256 = "ab".repeat(32),
            createdAtEpochMillis = 100L,
            logicalEpochDay = 20_000L,
            appVersionName = "0.1.0",
        )

        assertEquals(metadata, RemoteBackupMetadataCodec.decode(RemoteBackupMetadataCodec.encode(metadata)))
    }

    @Test(expected = BackupFormatException::class)
    fun latestPointerRejectsRemotePathInjection() {
        val properties = Properties().apply {
            setProperty("format", "xiaoluanbao-latest")
            setProperty("file_name", "../stolen.xlbak")
            setProperty("file_size", "10")
            setProperty("sha256", "ab".repeat(32))
            setProperty("created_at", "100")
            setProperty("logical_epoch_day", "20000")
            setProperty("app_version", "0.1.0")
        }
        val bytes = ByteArrayOutputStream().use { output ->
            properties.store(output, null)
            output.toByteArray()
        }

        RemoteBackupMetadataCodec.decode(bytes)
    }
}
