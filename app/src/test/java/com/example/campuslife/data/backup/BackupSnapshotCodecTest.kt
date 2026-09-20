package com.example.campuslife.data.backup

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardCollectionStoredData
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInRecordKind
import com.example.campuslife.data.checkin.CheckInStoredData
import com.example.campuslife.data.journal.JournalCategory
import com.example.campuslife.data.journal.JournalData
import com.example.campuslife.data.journal.JournalEntry
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.PlanTask
import com.example.campuslife.data.study.StudyDocument
import com.example.campuslife.data.study.StudyCategory
import com.example.campuslife.data.study.StudyDocumentFormat
import com.example.campuslife.data.study.StudyTaskState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class BackupSnapshotCodecTest {
    @Test
    fun completeSnapshotRoundTripsWithoutLosingUserText() {
        val snapshot = sampleSnapshot()
        val bytes = ByteArrayOutputStream().also { output ->
            BackupSnapshotCodec.write(snapshot, output)
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(snapshot, restored)
    }

    @Test
    fun versionOneSnapshotStillRestoresTasksInAutomaticMode() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(1)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(1)
                data.writeLong(20_000)
                data.writeInt(1)
                data.writeTestText("旧版任务")
                data.writeTestText("旧版说明")
                data.writeBoolean(false)
                data.writeBoolean(true)
                data.writeInt(9 * 60)
                data.writeInt(0)
                data.writeInt(0)
                data.writeBoolean(true)
                data.writeInt(0)
                data.writeBoolean(true)
                data.writeLong(19_999L)
                data.writeInt(0)
                data.writeBoolean(false)
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))
        val task = restored.plans.single().tasks.single()

        assertEquals("旧版任务", task.title)
        assertEquals(9 * 60, task.expectedStartMinute)
        assertEquals(null, task.manualOrder)
        assertEquals(
            mapOf(CardSeries.Isekai.id to 19_999L),
            restored.cards.lastDrawEpochDays,
        )
    }

    @Test
    fun versionTwoLastDrawDayMigratesToIsekai() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(2)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeBoolean(false)
                data.writeInt(0)
                data.writeBoolean(true)
                data.writeLong(20_123L)
                data.writeInt(0)
                data.writeBoolean(false)
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(
            mapOf(CardSeries.Isekai.id to 20_123L),
            restored.cards.lastDrawEpochDays,
        )
    }

    @Test
    fun versionThreeStudyDocumentDefaultsToNotesCategory() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(3)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeBoolean(true)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(1)
                data.writeTestText("legacy-doc")
                data.writeTestText("旧学习资料")
                data.writeTestText(StudyDocumentFormat.Markdown.name)
                data.writeTestText("legacy.md")
                data.writeTestText("旧文件夹")
                data.writeTestText("legacy-snapshot")
                data.writeTestText("legacy.md")
                data.writeLong(12L)
                data.writeLong(100L)
                data.writeLong(200L)
                data.writeTestText("legacy-hash")
                data.writeInt(1)
                data.writeLong(300L)
                data.writeInt(40)
                data.writeFloat(0.5f)
                data.writeBoolean(false)
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(StudyCategory.Notes, restored.studyDocuments.single().category)
        assertEquals(0, restored.cards.exchangeRestCards)
    }

    @Test
    fun versionFourKeepsStudyCategoryAndDefaultsAtomicExchangeRewards() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(4)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeBoolean(false)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(1)
                data.writeTestText("v4-doc")
                data.writeTestText("单词本")
                data.writeTestText(StudyDocumentFormat.Markdown.name)
                data.writeTestText("words.md")
                data.writeTestText("英语")
                data.writeTestText("v4-snapshot")
                data.writeTestText("words.md")
                data.writeLong(12L)
                data.writeLong(100L)
                data.writeLong(200L)
                data.writeTestText("v4-hash")
                data.writeInt(1)
                data.writeLong(300L)
                data.writeInt(40)
                data.writeFloat(0.5f)
                data.writeTestText(StudyCategory.Vocabulary.name)
                data.writeBoolean(false)
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(StudyCategory.Vocabulary, restored.studyDocuments.single().category)
        assertEquals(0, restored.cards.exchangeRestCards)
    }

    @Test
    fun versionFiveSnapshotDefaultsTaskOverlayToEmpty() {
        // v5 在任务勾选覆盖层之前：没有这一段时读回来必须是空列表，而不是解析错位。
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(5)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeBoolean(false)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(0)
                data.writeInt(1)
                data.writeTestText("v5-doc")
                data.writeTestText("单词本")
                data.writeTestText(StudyDocumentFormat.Markdown.name)
                data.writeTestText("words.md")
                data.writeTestText("英语")
                data.writeTestText("v5-snapshot")
                data.writeTestText("words.md")
                data.writeLong(12L)
                data.writeLong(100L)
                data.writeLong(200L)
                data.writeTestText("v5-hash")
                data.writeInt(1)
                data.writeLong(300L)
                data.writeInt(40)
                data.writeFloat(0.5f)
                data.writeTestText(StudyCategory.Vocabulary.name)
                // v5 已经带上整套兑换休息卡数量，缺这一段会让后面的字段整体错位。
                data.writeInt(0)
                data.writeBoolean(false)
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(1, restored.studyDocuments.size)
        assertEquals(emptyList<StudyTaskState>(), restored.studyTaskStates)
    }

    private fun sampleSnapshot() = AppBackupSnapshot(
        checkIn = CheckInStoredData(
            records = listOf(
                CheckInRecord(20_000, 480, 1_020, CheckInRecordKind.Real),
                CheckInRecord(20_001, 540, 1_080, CheckInRecordKind.RestCard),
            ),
            seenRestCardCount = 3,
            bonusRestCards = 2,
        ),
        plans = listOf(
            DailyPlan(
                epochDay = 20_000,
                tasks = listOf(
                    PlanTask("学习 Kotlin｜协程", "包含换行\n以及中文", true, 8 * 60 + 30, 1),
                    PlanTask("散步", "", false, null, 0),
                ),
            ),
            DailyPlan(epochDay = 20_001, tasks = emptyList()),
        ),
        journal = JournalData(
            categories = listOf(JournalCategory("life", "生活 | 随笔", 100L)),
            entries = listOf(JournalEntry("entry-1", "标题\n正文🙂", "life", 101L, 102L)),
            autoSaveEnabled = false,
        ),
        cards = CardCollectionStoredData(
            collection = CardCollection(
                mapOf(
                    "isekai_001_unicorn" to 2,
                    "sanguo_002_changban_sr" to 1,
                ),
            ),
            lastDrawEpochDays = mapOf(
                CardSeries.Isekai.id to 20_000,
                CardSeries.Sanguo.id to 20_001,
            ),
            exchangeRestCards = 6,
        ),
        studyDocuments = listOf(
            StudyDocument(
                id = "doc-1",
                title = "课程笔记",
                format = StudyDocumentFormat.Markdown,
                sourceName = "notes.md",
                folderLabel = "课程",
                snapshotId = "snapshot-1",
                relativePath = "notes.md",
                sizeBytes = 12,
                sourceModifiedAtEpochMillis = 200L,
                importedAtEpochMillis = 201L,
                contentHash = "abc123",
                version = 2,
                lastReadAtEpochMillis = 202L,
                scrollY = 88,
                progress = 0.42f,
                category = StudyCategory.Vocabulary,
            ),
        ),
        darkTheme = true,
        studyTaskStates = listOf(
            StudyTaskState(
                documentId = "doc-1",
                checkedIndexes = setOf(0, 2, 5),
                sourceFingerprint = "abc123:notes.md",
            ),
        ),
    )

    private fun DataOutputStream.writeTestText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
