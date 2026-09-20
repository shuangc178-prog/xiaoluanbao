package com.example.campuslife.data.study

import java.util.Base64

/**
 * 阅读器里任务列表的勾选覆盖层。
 *
 * 导入的资料是**只读副本**：App 不回头改 `study_imports` 里的 Markdown，也不改电脑上的原稿。
 * 所以勾选状态单独存在这里，键是任务在文档里的出现序号（0 开始），与 Markdown 里的
 * `[ ]` / `[x]` 一一对应；正文里已经勾上的项不需要重复记录。
 *
 * [sourceFingerprint] 把这份状态钉在某一版正文上：重新导入同一份资料会换掉内容哈希，
 * 指纹对不上就整条作废，避免旧勾选错位到新正文的行上。
 */
data class StudyTaskState(
    val documentId: String,
    val checkedIndexes: Set<Int> = emptySet(),
    val sourceFingerprint: String,
) {
    fun toggled(index: Int, checked: Boolean): StudyTaskState {
        if (index < 0) return this
        val next = checkedIndexes.toMutableSet()
        if (checked) next += index else next -= index
        return copy(checkedIndexes = next)
    }
}

/** 同一条资料记录里，[StudyDocument.contentHash] 与 [StudyDocument.relativePath] 唯一确定一版正文。 */
fun studyTaskStateFingerprint(document: StudyDocument): String =
    "${document.contentHash}:${document.relativePath}"

private const val FIELD_SEPARATOR = "|"
private const val CODEC_VERSION = "1"
private const val FIELD_COUNT = 4

internal fun encodeStudyTaskState(state: StudyTaskState): String = listOf(
    CODEC_VERSION,
    encodeTaskText(state.documentId),
    state.checkedIndexes.sorted().joinToString(","),
    encodeTaskText(state.sourceFingerprint),
).joinToString(FIELD_SEPARATOR)

internal fun decodeStudyTaskState(encoded: String): StudyTaskState? {
    val parts = encoded.split(FIELD_SEPARATOR)
    if (parts.size != FIELD_COUNT || parts[0] != CODEC_VERSION) return null

    return runCatching {
        val documentId = decodeTaskText(parts[1]).takeIf { it.isNotBlank() } ?: return null
        val fingerprint = decodeTaskText(parts[3]).takeIf { it.isNotBlank() } ?: return null
        StudyTaskState(
            documentId = documentId,
            checkedIndexes = decodeTaskIndexes(parts[2]),
            sourceFingerprint = fingerprint,
        )
    }.getOrNull()
}

private fun decodeTaskIndexes(encoded: String): Set<Int> = encoded
    .split(',')
    .mapNotNull { piece -> piece.trim().toIntOrNull()?.takeIf { it >= 0 } }
    .toSet()

/**
 * 按出现顺序取出 Markdown 里任务列表项的勾选状态。
 *
 * 任务列表语法为 `- [ ]` / `- [x]`（有序列表 `1. [x]` 同理），前导缩进属于嵌套列表，
 * 同样算作一个任务。返回的下标就是渲染后复选框的 DOM 顺序，两者用同一份源文本推导，
 * 因此不需要把行号带进 HTML。
 */
internal fun parseMarkdownTaskChecks(source: String): List<Boolean> {
    val checks = mutableListOf<Boolean>()
    source.lineSequence().forEach { line ->
        val match = TASK_LIST_ITEM.find(line) ?: return@forEach
        // 只用「是不是 x」判定，不再依赖捕获组的长度：`[ ]` 与 `[x]` 的捕获组长度都是 1，
        // 拿长度当勾选判据会把未勾选也读成已勾选。
        val mark = match.groupValues.getOrNull(1).orEmpty()
        checks += mark.equals("x", ignoreCase = true)
    }
    return checks
}

/** Markdown 里已经勾上的任务下标，用来和覆盖层做异或得到最终状态。 */
internal fun markdownCheckedIndexes(source: String): Set<Int> = parseMarkdownTaskChecks(source)
    .withIndex()
    .filter { (_, checked) -> checked }
    .map { (index, _) -> index }
    .toSet()

/**
 * 正文里的勾选与用户覆盖层合并后的最终状态。
 *
 * 用异或而不是并集：`[x]` 的项点一下应当取消勾选，覆盖层只记录「相对正文翻转过」的项，
 * 这样覆盖层可以保持很小，正文自己改过的完成状态也仍然认得出来。
 */
internal fun effectiveCheckedTaskIndexes(
    source: String,
    storedCheckedIndexes: Set<Int>,
): Set<Int> {
    val fromMarkdown = markdownCheckedIndexes(source)
    return fromMarkdown.xor(storedCheckedIndexes)
}

/** [Set] 没有成员版异或，这里补上；两边都出现或都不出现的下标不勾，只留差异。 */
private fun Set<Int>.xor(other: Set<Int>): Set<Int> =
    (this - other) + (other - this)

private fun encodeTaskText(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(Charsets.UTF_8))

private fun decodeTaskText(value: String): String = String(
    Base64.getUrlDecoder().decode(value),
    Charsets.UTF_8,
)

// `\s` 会吃掉换行，把前一行的空白和下一行的 `- [ ]` 连起来一起匹配；缩进只能用水平空白，
// 标记后的分隔也只能是空格或制表符。
private val TASK_LIST_ITEM = Regex("^[ \\t]*(?:[-*+]|\\d+[.)])[ \\t]+\\[([ xX])][ \\t]+")
