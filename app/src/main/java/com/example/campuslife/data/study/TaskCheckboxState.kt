package com.example.campuslife.data.study

import java.security.MessageDigest

/**
 * 阅读器里被用户勾选的一个任务复选框。
 *
 * 表格行任务与列表任务项共用一套定位：[tableIndex] 为 -1 时表示列表项，此时
 * [rowIndex] 是全篇列表任务项的序号；表格行则用“第几张表 + 第几行 + 第几列”。
 * [itemHash] 是该行（或该项）自身文字算出的短哈希，用来在资料被改写后只作废
 * 真正变了的那几条，而不是整篇勾选一起丢。
 *
 * 编码后每条记录形如 `表号;行号;列号;文字哈希`，多条之间用换行分隔（见本文件末尾的
 * [encodeTaskCheckboxState]）。
 */
data class TaskCheckboxMark(
    val tableIndex: Int,
    val rowIndex: Int,
    val columnIndex: Int,
    val itemHash: String,
) {
    val isListItem: Boolean get() = tableIndex < 0
}

/**
 * 一份学习资料里复选框的完整 App 内覆盖状态。
 *
 * 复选框状态只活在 App 里：资料的本地快照（也就是 Typora 里那份 Markdown）保持只读，
 * 勾选既不改 Markdown，也不回写用户的原文件。[sourceHash] 记录当时的资料内容哈希。
 * [marks] 保存当前处于勾选状态的项目；即使集合为空，状态头仍要落盘，才能表达用户把
 * Markdown 原本的 `[x]` 取消勾选了。
 */
data class TaskCheckboxState(
    val sourceHash: String,
    val marks: Set<TaskCheckboxMark>,
) {
    /** 资料内容与保存时不同源时，所有坐标都不再可信。 */
    fun matchesSource(contentHash: String): Boolean =
        sourceHash.isNotEmpty() && contentHash.startsWith(sourceHash)
}

internal fun encodeTaskCheckboxState(state: TaskCheckboxState): String? {
    val sourceHash = state.sourceHash.trim().take(HASH_CHAR_COUNT).normalizedTaskHashOrNull()
        ?: return null
    val marks = state.marks
        .mapNotNull(::encodeTaskCheckboxMark)
        .sorted()
    return (listOf(CODEC_VERSION, sourceHash) + marks).joinToString(LINE_SEPARATOR)
}

internal fun decodeTaskCheckboxState(encoded: String?): TaskCheckboxState? {
    val lines = encoded
        ?.split(LINE_SEPARATOR)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?: return null
    if (lines.size < 2 || lines[0] != CODEC_VERSION) return null
    val sourceHash = lines[1].normalizedTaskHashOrNull() ?: return null
    val marks = lines.drop(2).mapNotNull(::decodeTaskCheckboxMark).toSet()
    return TaskCheckboxState(sourceHash = sourceHash, marks = marks)
}

/** 每行一条记录：`表号;行号;列号;文字哈希`；列表项的表号固定为 -1。 */
private fun encodeTaskCheckboxMark(mark: TaskCheckboxMark): String? {
    val itemHash = mark.itemHash.trim().take(HASH_CHAR_COUNT).normalizedTaskHashOrNull()
        ?: return null
    if (mark.rowIndex < 0 || mark.columnIndex < -1) return null
    return listOf(
        mark.tableIndex.toString(),
        mark.rowIndex.toString(),
        mark.columnIndex.toString(),
        itemHash,
    ).joinToString(FIELD_SEPARATOR)
}

private fun decodeTaskCheckboxMark(line: String): TaskCheckboxMark? {
    val parts = line.split(FIELD_SEPARATOR)
    if (parts.size != MARK_FIELD_COUNT) return null
    val tableIndex = parts[0].toIntOrNull() ?: return null
    val rowIndex = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val columnIndex = parts[2].toIntOrNull()?.takeIf { it >= -1 } ?: return null
    val itemHash = parts[3].normalizedTaskHashOrNull() ?: return null
    return TaskCheckboxMark(
        tableIndex = tableIndex,
        rowIndex = rowIndex,
        columnIndex = columnIndex,
        itemHash = itemHash,
    )
}

/** 短哈希长度：够区分一份资料里的行，又不至于让 DataStore 里的状态串过长。 */
internal const val HASH_CHAR_COUNT = 12
internal const val LIST_ITEM_TABLE_INDEX = -1

/**
 * 任务行文字与列表项文字的短哈希。Kotlin 侧算出它并写进单元格属性，页面脚本只负责比对，
 * 这样“哪一条勾选还算数”只有一处判定口径：零宽字符是 Typora 渲染时塞进来的，
 * 折叠空白是因为 Markdown 表格的单元格排版会带上多余空格。
 */
internal fun taskItemHash(text: String): String {
    val normalized = text.replace("\u200B", "").replace(Regex("\\s+"), " ").trim()
    val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
    return buildString(HASH_CHAR_COUNT) {
        for (index in 0 until HASH_CHAR_COUNT / 2) {
            val value = digest[index].toInt() and 0xFF
            append(HEX_DIGITS[value shr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }
}

private const val CODEC_VERSION = "v1"
private const val LINE_SEPARATOR = "\n"

/**
 * 记录内的字段分隔符。它不能与资料索引编码（[encodeStudyDocument]）的字段分隔符相同：
 * 复选框状态整体只是资料记录里的一段，出现同名字符会把那段拆成多段，整条资料随之解不出来。
 */
private const val FIELD_SEPARATOR = ";"
private const val MARK_FIELD_COUNT = 4
private const val HEX_DIGITS = "0123456789abcdef"

private fun String.normalizedTaskHashOrNull(): String? = trim()
    .takeIf { value ->
        value.length == HASH_CHAR_COUNT && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
    ?.lowercase()
