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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * `.xlbak` 内的数据采用长度前缀二进制编码：没有分隔符转义问题，也不会依赖 Android
 * 私有的 DataStore protobuf 格式。以后字段变化时只需要增加一个格式版本分支。
 */
internal object BackupSnapshotCodec {
    private const val MAGIC = 0x584C424B // ASCII: XLBK
    private const val VERSION = 6
    private const val MAX_COLLECTION_SIZE = 200_000
    private const val MAX_STRING_BYTES = 16 * 1024 * 1024

    fun write(snapshot: AppBackupSnapshot, output: OutputStream) {
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)

            data.writeSize(snapshot.checkIn.records.size)
            snapshot.checkIn.records.forEach { record ->
                data.writeLong(record.epochDay)
                data.writeInt(record.startMinuteOfDay)
                data.writeNullableInt(record.endMinuteOfDay)
                data.writeText(record.kind.name)
            }
            data.writeInt(snapshot.checkIn.seenRestCardCount)
            data.writeInt(snapshot.checkIn.bonusRestCards)

            data.writeSize(snapshot.plans.size)
            snapshot.plans.forEach { plan ->
                data.writeLong(plan.epochDay)
                data.writeSize(plan.tasks.size)
                plan.tasks.forEach { task ->
                    data.writeText(task.title)
                    data.writeText(task.supportingText)
                    data.writeBoolean(task.isCompleted)
                    data.writeNullableInt(task.expectedStartMinute)
                    data.writeNullableInt(task.manualOrder)
                }
            }

            data.writeSize(snapshot.journal.categories.size)
            snapshot.journal.categories.forEach { category ->
                data.writeText(category.id)
                data.writeText(category.name)
                data.writeLong(category.createdAtEpochMillis)
            }
            data.writeSize(snapshot.journal.entries.size)
            snapshot.journal.entries.forEach { entry ->
                data.writeText(entry.id)
                data.writeText(entry.content)
                data.writeText(entry.categoryId)
                data.writeLong(entry.createdAtEpochMillis)
                data.writeLong(entry.updatedAtEpochMillis)
            }
            data.writeBoolean(snapshot.journal.autoSaveEnabled)

            data.writeSize(snapshot.cards.collection.counts.size)
            snapshot.cards.collection.counts.toSortedMap().forEach { (id, count) ->
                data.writeText(id)
                data.writeInt(count)
            }
            data.writeSize(snapshot.cards.lastDrawEpochDays.size)
            snapshot.cards.lastDrawEpochDays.toSortedMap().forEach { (seriesId, epochDay) ->
                data.writeText(seriesId)
                data.writeLong(epochDay)
            }
            data.writeInt(snapshot.cards.exchangeRestCards)

            data.writeSize(snapshot.studyDocuments.size)
            snapshot.studyDocuments.forEach { document ->
                data.writeText(document.id)
                data.writeText(document.title)
                data.writeText(document.format.name)
                data.writeText(document.sourceName)
                data.writeText(document.folderLabel)
                data.writeText(document.snapshotId)
                data.writeText(document.relativePath)
                data.writeLong(document.sizeBytes)
                data.writeLong(document.sourceModifiedAtEpochMillis)
                data.writeLong(document.importedAtEpochMillis)
                data.writeText(document.contentHash)
                data.writeInt(document.version)
                data.writeLong(document.lastReadAtEpochMillis)
                data.writeInt(document.scrollY)
                data.writeFloat(document.progress)
                data.writeText(document.category.name)
            }

            // v6 起：阅读器任务勾选的覆盖层。正文里已勾上的项不在这里，读回来仍按异或合并。
            data.writeSize(snapshot.studyTaskStates.size)
            snapshot.studyTaskStates.forEach { state ->
                data.writeText(state.documentId)
                data.writeSize(state.checkedIndexes.size)
                state.checkedIndexes.sorted().forEach { index -> data.writeInt(index) }
                data.writeText(state.sourceFingerprint)
            }

            data.writeNullableBoolean(snapshot.darkTheme)
        }
    }

    fun read(input: InputStream): AppBackupSnapshot {
        try {
            DataInputStream(input).use { data ->
                if (data.readInt() != MAGIC) throw BackupFormatException("不是小卵包备份文件")
                val version = data.readInt()
                if (version !in 1..VERSION) {
                    throw BackupFormatException("暂不支持备份格式版本 $version")
                }

                val records = List(data.readSize("打卡记录")) {
                    CheckInRecord(
                        epochDay = data.readLong(),
                        startMinuteOfDay = data.readInt(),
                        endMinuteOfDay = data.readNullableInt(),
                        kind = data.readEnum<CheckInRecordKind>("打卡来源"),
                    )
                }
                val checkIn = CheckInStoredData(
                    records = records,
                    seenRestCardCount = data.readInt().coerceAtLeast(0),
                    bonusRestCards = data.readInt().coerceAtLeast(0),
                )

                val plans = List(data.readSize("每日计划")) {
                    val epochDay = data.readLong()
                    val tasks = List(data.readSize("计划任务")) {
                        PlanTask(
                            title = data.readText(),
                            supportingText = data.readText(),
                            isCompleted = data.readBoolean(),
                            expectedStartMinute = data.readNullableInt(),
                            manualOrder = if (version >= 2) data.readNullableInt() else null,
                        )
                    }
                    DailyPlan(epochDay = epochDay, tasks = tasks)
                }

                val categories = List(data.readSize("日记分类")) {
                    JournalCategory(
                        id = data.readText(),
                        name = data.readText(),
                        createdAtEpochMillis = data.readLong(),
                    )
                }
                val entries = List(data.readSize("日记")) {
                    JournalEntry(
                        id = data.readText(),
                        content = data.readText(),
                        categoryId = data.readText(),
                        createdAtEpochMillis = data.readLong(),
                        updatedAtEpochMillis = data.readLong(),
                    )
                }
                val journal = JournalData(
                    categories = categories,
                    entries = entries,
                    autoSaveEnabled = data.readBoolean(),
                )

                val cardCounts = buildMap {
                    repeat(data.readSize("卡册")) {
                        put(data.readText(), data.readInt().coerceAtLeast(0))
                    }
                }
                val lastDrawEpochDays = if (version >= 3) {
                    buildMap {
                        repeat(data.readSize("各弹抽卡日")) {
                            put(data.readText(), data.readLong())
                        }
                    }
                } else {
                    data.readNullableLong()?.let { legacy ->
                        mapOf(CardSeries.Isekai.id to legacy)
                    }.orEmpty()
                }
                val cards = CardCollectionStoredData(
                    collection = CardCollection(cardCounts),
                    lastDrawEpochDays = lastDrawEpochDays,
                    exchangeRestCards = if (version >= 5) {
                        data.readInt().coerceAtLeast(0)
                    } else {
                        0
                    },
                )

                val studyDocuments = List(data.readSize("学习资料")) {
                    StudyDocument(
                        id = data.readText(),
                        title = data.readText(),
                        format = data.readEnum<StudyDocumentFormat>("学习资料格式"),
                        sourceName = data.readText(),
                        folderLabel = data.readText(),
                        snapshotId = data.readText(),
                        relativePath = data.readText(),
                        sizeBytes = data.readLong(),
                        sourceModifiedAtEpochMillis = data.readLong(),
                        importedAtEpochMillis = data.readLong(),
                        contentHash = data.readText(),
                        version = data.readInt(),
                        lastReadAtEpochMillis = data.readLong(),
                        scrollY = data.readInt().coerceAtLeast(0),
                        progress = data.readFloat().coerceIn(0f, 1f),
                        category = if (version >= 4) {
                            data.readEnum<StudyCategory>("学习资料分类")
                        } else {
                            StudyCategory.Notes
                        },
                    )
                }

                val studyTaskStates = if (version >= 6) {
                    List(data.readSize("学习资料任务状态")) {
                        StudyTaskState(
                            documentId = data.readText(),
                            checkedIndexes = List(data.readSize("勾选的任务")) {
                                data.readInt().coerceAtLeast(0)
                            }.toSet(),
                            sourceFingerprint = data.readText(),
                        )
                    }
                } else {
                    emptyList()
                }

                return AppBackupSnapshot(
                    checkIn = checkIn,
                    plans = plans,
                    journal = journal,
                    cards = cards,
                    studyDocuments = studyDocuments,
                    darkTheme = data.readNullableBoolean(),
                    studyTaskStates = studyTaskStates,
                )
            }
        } catch (error: BackupFormatException) {
            throw error
        } catch (error: Exception) {
            throw BackupFormatException("备份内容不完整或已损坏", error)
        }
    }

    private fun DataOutputStream.writeSize(size: Int) {
        require(size in 0..MAX_COLLECTION_SIZE) { "备份条目过多" }
        writeInt(size)
    }

    private fun DataInputStream.readSize(label: String): Int {
        val size = readInt()
        if (size !in 0..MAX_COLLECTION_SIZE) {
            throw BackupFormatException("$label 数量异常")
        }
        return size
    }

    private fun DataOutputStream.writeText(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES) { "单段文本过大" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readText(): String {
        val size = readInt()
        if (size !in 0..MAX_STRING_BYTES) throw BackupFormatException("文本长度异常")
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableInt(value: Int?) {
        writeBoolean(value != null)
        if (value != null) writeInt(value)
    }

    private fun DataInputStream.readNullableInt(): Int? =
        if (readBoolean()) readInt() else null

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null

    private fun DataOutputStream.writeNullableBoolean(value: Boolean?) {
        writeBoolean(value != null)
        if (value != null) writeBoolean(value)
    }

    private fun DataInputStream.readNullableBoolean(): Boolean? =
        if (readBoolean()) readBoolean() else null

    private inline fun <reified T : Enum<T>> DataInputStream.readEnum(label: String): T {
        val stored = readText()
        return enumValues<T>().firstOrNull { it.name == stored }
            ?: throw BackupFormatException("$label 无法识别")
    }
}
