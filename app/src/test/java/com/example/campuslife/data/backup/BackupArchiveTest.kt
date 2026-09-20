package com.example.campuslife.data.backup

import com.example.campuslife.data.card.CardCollectionStoredData
import com.example.campuslife.data.checkin.CheckInStoredData
import com.example.campuslife.data.journal.JournalData
import com.example.campuslife.data.study.StudyDocument
import com.example.campuslife.data.study.StudyDocumentFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun archiveRestoresMetadataAndStudyFiles() {
        val root = temporaryFolder.newFolder("source")
        val sourceFile = File(root, "snapshot-1/notes.md").apply {
            parentFile?.mkdirs()
            writeText("# 完整笔记\n正文", Charsets.UTF_8)
        }
        val snapshot = AppBackupSnapshot(
            checkIn = CheckInStoredData(),
            plans = emptyList(),
            journal = JournalData(),
            cards = CardCollectionStoredData(),
            studyDocuments = listOf(
                StudyDocument(
                    id = "doc-1",
                    title = "完整笔记",
                    format = StudyDocumentFormat.Markdown,
                    sourceName = "notes.md",
                    folderLabel = "资料",
                    snapshotId = "snapshot-1",
                    relativePath = "notes.md",
                    sizeBytes = sourceFile.length(),
                    sourceModifiedAtEpochMillis = 1L,
                    importedAtEpochMillis = 2L,
                    contentHash = "hash",
                ),
            ),
            darkTheme = null,
        )
        val manifest = BackupArchiveManifest(1, 123L, 20_000L, "0.1.0")
        val archive = temporaryFolder.newFile("sample.xlbak")

        BackupArchive.write(archive, snapshot, manifest, root)
        val extraction = File(temporaryFolder.root, "extracted")
        val restored = BackupArchive.read(archive, extraction)

        assertEquals(manifest, restored.manifest)
        assertEquals(snapshot, restored.snapshot)
        assertEquals("# 完整笔记\n正文", File(extraction, "snapshot-1/notes.md").readText())
        assertTrue(BackupArchive.sha256(archive).matches(Regex("[0-9a-f]{64}")))
    }

    @Test(expected = BackupFormatException::class)
    fun archiveRejectsPathTraversalBeforeExtraction() {
        val archive = temporaryFolder.newFile("unsafe.xlbak")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("study/../outside.txt"))
            zip.write("bad".toByteArray())
            zip.closeEntry()
        }

        BackupArchive.read(archive, File(temporaryFolder.root, "target"))
    }
}
