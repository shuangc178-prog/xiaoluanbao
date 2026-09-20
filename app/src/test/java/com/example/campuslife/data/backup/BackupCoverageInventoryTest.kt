package com.example.campuslife.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 防止新增持久化后忘记把数据纳入整包备份。
 *
 * 新存储触发本测试时，不能只把名字塞进白名单：先按开发文档完成快照、恢复、格式兼容和
 * 往返测试；只有可重新生成或包含密钥的运行状态才能带着理由列入排除项。
 */
class BackupCoverageInventoryTest {
    @Test
    fun everyPreferencesDataStoreIsClassifiedForBackup() {
        val sourceRoot = locateMainSourceRoot()
        val discovered = kotlinSourceFiles(sourceRoot)
            .flatMap { source ->
                dataStorePattern.findAll(source.readText()).map { match -> match.groupValues[1] }
            }
            .toSortedSet()
        val classified = (backedUpDataStores + excludedDataStores.keys).toSortedSet()

        assertEquals(
            "DataStore 清单已变化。请先核对备份完整性，再更新备份实现、测试和开发文档中的数据清单。",
            classified,
            discovered,
        )
    }

    @Test
    fun everyPrivateFileStorageOwnerIsClassifiedForBackupReview() {
        val sourceRoot = locateMainSourceRoot()
        val discovered = kotlinSourceFiles(sourceRoot)
            .filter { source -> persistentFileApiPattern.containsMatchIn(source.readText()) }
            .map { source -> source.relativeTo(sourceRoot).invariantSeparatorsPath }
            .toSortedSet()
        val classified = (backedUpFileStorageOwners + excludedFileStorageOwners.keys).toSortedSet()

        assertEquals(
            "私有文件存储入口已变化。请核对新目录是否进入 .xlbak，并更新备份实现、测试和开发文档。",
            classified,
            discovered,
        )
    }

    private fun locateMainSourceRoot(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        var cursor: File? = File(workingDirectory).canonicalFile
        while (cursor != null) {
            listOf(
                File(cursor, "app/src/main/java"),
                File(cursor, "src/main/java"),
            ).firstOrNull(File::isDirectory)?.let { return it }
            cursor = cursor.parentFile
        }
        error("找不到 app/src/main/java，无法核对备份覆盖范围")
    }

    private fun kotlinSourceFiles(root: File): List<File> = root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private companion object {
        val backedUpDataStores = setOf(
            "card_collection",
            "check_in",
            "daily_plan",
            "journal",
            "study",
            "theme",
        )

        val excludedDataStores = mapOf(
            "cloud_backup" to "包含加密后的坚果云凭据与备份运行状态，不能递归写入备份",
            "reminder" to "仅保存可由计划重新生成的闹钟簿记与短期投递日志",
        )

        val backedUpFileStorageOwners = setOf(
            "com/example/campuslife/data/study/StudyImporter.kt",
            // 任务勾选覆盖层要读正文里已勾上的项，因此也持有一次私有文件读取；
            // 状态本身在 DataStore `study`，已进整包备份（见开发说明第 3 节）。
            "com/example/campuslife/data/study/StudyStore.kt",
            "com/example/campuslife/ui/study/StudyDocumentRenderer.kt",
        )

        val excludedFileStorageOwners = mapOf(
            "com/example/campuslife/data/backup/AppBackupRepository.kt" to
                "备份临时目录与恢复前安全副本属于备份基础设施，不能嵌套进业务备份",
        )

        val dataStorePattern = Regex(
            """preferencesDataStore\s*\(\s*name\s*=\s*"([^"]+)"""",
        )
        val persistentFileApiPattern = Regex(
            """\bfilesDir\b|\bgetFilesDir\s*\(|\bgetSharedPreferences\s*\(|""" +
                """\bopenFileOutput\s*\(|\bRoom\.databaseBuilder\s*\(""",
        )
    }
}
