package com.example.campuslife.data.study

/**
 * “背单词”页面里被遮挡的一列及其已翻开的单元格。
 *
 * 隐藏操作必须跨进程重启保留：用户翻开某个单词后，下次进来它就应该还是翻开的。
 * 单元格用“第几张表 + 第几列 + 第几行”定位；[sourceHash] 是当时的资料内容哈希，
 * 资料被重新导入后行号可能整体错位，此时按过期处理，宁可回到未遮挡也不要把错误的
 * 单元格盖住。
 */
data class VocabularyTableMaskState(
    val tableIndex: Int,
    val columnIndex: Int,
    val sourceHash: String,
    val revealedRows: Set<Int>,
) {
    /** 资料内容变化后，旧的行列坐标不再可信。 */
    fun matchesSource(contentHash: String): Boolean =
        sourceHash.isNotEmpty() && contentHash.startsWith(sourceHash)
}

/**
 * 编码成能放进 DataStore 单字段的紧凑纯文本：`表:列:内容哈希:已翻开行`。
 * 逗号在阅读器资源里是行号分隔符，这里额外裁掉空段，避免解析出空行号。
 */
internal fun encodeVocabularyTableMask(state: VocabularyTableMaskState): String = listOf(
    state.tableIndex.toString(),
    state.columnIndex.toString(),
    state.sourceHash,
    state.revealedRows.sorted().joinToString(","),
).joinToString(COLUMN_SEPARATOR)

internal fun decodeVocabularyTableMask(encoded: String?): VocabularyTableMaskState? {
    val parts = encoded?.split(COLUMN_SEPARATOR) ?: return null
    if (parts.size != MASK_FIELD_COUNT) return null
    val tableIndex = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val columnIndex = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val sourceHash = parts[2].takeIf { it.isNotBlank() } ?: return null
    val revealedRows = parts[3]
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it >= 0 }
        .toSet()
    return VocabularyTableMaskState(
        tableIndex = tableIndex,
        columnIndex = columnIndex,
        sourceHash = sourceHash,
        revealedRows = revealedRows,
    )
}

private const val COLUMN_SEPARATOR = ":"
private const val MASK_FIELD_COUNT = 4
