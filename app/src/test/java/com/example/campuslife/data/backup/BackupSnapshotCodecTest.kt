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
        assertEquals(0L, task.learningElapsedMillis)
        assertEquals(null, task.learningStartedAtEpochMillis)
        assertEquals(false, task.hasLearningStarted)
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
        val bytes = versionFourSnapshotBytes()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(StudyCategory.Vocabulary, restored.studyDocuments.single().category)
        assertEquals(0, restored.cards.exchangeRestCards)
    }

    private fun versionFourSnapshotBytes(): ByteArray = ByteArrayOutputStream().also { output ->
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

    @Test
    fun versionFiveStudyDocumentDefaultsToNoVocabularyMask() {
        // v5 备份里学习资料记录以分类结尾，没有背单词遮挡状态；读取时必须继续按“未遮挡”恢复。
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(5)
                data.writeInt(0) // 打卡记录
                data.writeInt(0) // 已看休息卡水位
                data.writeInt(0) // 旧版兑换所得休息卡
                data.writeInt(0) // 每日计划
                data.writeInt(0) // 日记分类
                data.writeInt(0) // 日记
                data.writeBoolean(false) // 日记自动保存
                data.writeInt(0) // 卡册
                data.writeInt(0) // 各弹最后抽卡日
                data.writeInt(0) // 整套兑换休息卡
                data.writeInt(1) // 学习资料数量
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
                data.writeBoolean(false) // 夜间主题
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals(StudyCategory.Vocabulary, restored.studyDocuments.single().category)
        assertEquals(null, restored.studyDocuments.single().vocabularyMask)
    }

    @Test
    fun versionSixStudyDocumentDefaultsToNoTaskCheckboxState() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(6)
                data.writeInt(0) // 打卡记录
                data.writeInt(0) // 已看休息卡水位
                data.writeInt(0) // 旧版兑换所得休息卡
                data.writeInt(0) // 每日计划
                data.writeInt(0) // 日记分类
                data.writeInt(0) // 日记
                data.writeBoolean(false) // 日记自动保存
                data.writeInt(0) // 卡册
                data.writeInt(0) // 各弹最后抽卡日
                data.writeInt(0) // 整套兑换休息卡
                data.writeInt(1) // 学习资料数量
                data.writeTestText("v6-doc")
                data.writeTestText("单词本")
                data.writeTestText(StudyDocumentFormat.Markdown.name)
                data.writeTestText("words.md")
                data.writeTestText("英语")
                data.writeTestText("v6-snapshot")
                data.writeTestText("words.md")
                data.writeLong(12L)
                data.writeLong(100L)
                data.writeLong(200L)
                data.writeTestText("v6-hash")
                data.writeInt(1)
                data.writeLong(300L)
                data.writeInt(40)
                data.writeFloat(0.5f)
                data.writeTestText(StudyCategory.Vocabulary.name)
                data.writeTestText("0:1:v6-hash:2") // v6 已有背单词遮挡状态
                data.writeBoolean(false) // 夜间主题
            }
        }.toByteArray()

        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))

        assertEquals("0:1:v6-hash:2", restored.studyDocuments.single().vocabularyMask)
        assertEquals(null, restored.studyDocuments.single().taskCheckboxState)
    }

    @Test
    fun versionSevenTasksKeepCompletionButDefaultToZeroLearningTime() {
        val bytes = ByteArrayOutputStream().also { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(0x584C424B)
                data.writeInt(7)
                repeat(3) { data.writeInt(0) } // 打卡记录、已看水位、旧奖励
                data.writeInt(1)
                data.writeLong(20_000L)
                data.writeInt(1)
                data.writeTestText("旧版任务")
                data.writeTestText("旧版说明")
                data.writeBoolean(true)
                data.writeBoolean(false) // 无预期时间
                data.writeBoolean(false) // 无手动位置
                data.writeInt(0) // 日记分类
                data.writeInt(0) // 日记
                data.writeBoolean(false)
                data.writeInt(0) // 卡册
                data.writeInt(0) // 抽卡日期映射
                data.writeInt(0) // 兑换奖励
                data.writeInt(0) // 学习资料
                data.writeBoolean(false) // 无主题选择
            }
        }.toByteArray()
        val task = BackupSnapshotCodec.read(ByteArrayInputStream(bytes)).plans.single().tasks.single()
        assertEquals(true, task.isCompleted)
        assertEquals(0L, task.learningElapsedMillis)
        assertEquals(null, task.learningStartedAtEpochMillis)
        assertEquals(false, task.hasLearningStarted)
    }

    @Test
    fun activeTaskBackupSettlesAndRestoresPausedWithoutCountingOfflineTime() {
        val day = 20_000L
        val backedUpAt = com.example.campuslife.data.plan.learningDayEndMillis(day) - 3_600_000L
        val live = sampleSnapshot().copy(plans = listOf(DailyPlan(day, listOf(
            PlanTask("正在学习", "", learningElapsedMillis = 60_000L,
                learningStartedAtEpochMillis = backedUpAt - 30_000L, hasLearningStarted = true),
        ))))
        val frozen = live.pauseTaskTimers(backedUpAt)
        val bytes = ByteArrayOutputStream().also { BackupSnapshotCodec.write(frozen, it) }.toByteArray()
        val restored = BackupSnapshotCodec.read(ByteArrayInputStream(bytes))
            .pauseTaskTimers(backedUpAt + 86_400_000L)
        val task = restored.plans.single().tasks.single()
        assertEquals(90_000L, task.learningElapsedMillis)
        assertEquals(null, task.learningStartedAtEpochMillis)
        assertEquals(true, task.hasLearningStarted)
        assertEquals(backedUpAt - 30_000L, live.plans.single().tasks.single().learningStartedAtEpochMillis)
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
                    PlanTask("散步", "", false, null, 0,
                        learningElapsedMillis = 123_456L, learningStartedAtEpochMillis = 1_790_000_000_000L,
                        hasLearningStarted = true),
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
                vocabularyMask = "0:1:abc123:2,7",
                taskCheckboxState = "v1\nabcdef123456\n0;3;0;9f3a1c229f3a",
            ),
        ),
        darkTheme = true,
    )

    private fun DataOutputStream.writeTestText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
