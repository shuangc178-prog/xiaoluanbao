package com.example.campuslife.data.study

import org.commonmark.node.Code
import org.commonmark.node.Heading
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser

/**
 * Markdown 文档的标题条目，用于生成 Typora 风格的目录。
 *
 * [level] 为 1~6 的标题等级；[anchor] 是注入到正文对应标题元素上的稳定 id，
 * 点击目录后据此定位；[title] 是去掉行内装饰后的纯文本标题。
 */
data class StudyOutlineItem(
    val level: Int,
    val title: String,
    val anchor: String,
)

/**
 * 用 commonmark 解析 Markdown 源文本，按文档顺序收集所有标题。
 *
 * 这里刻意使用与渲染端同一个解析器（commonmark），保证目录里看到的标题
 * 与正文实际渲染出的标题在数量、顺序、文本上完全一致，避免「目录与正文错位」。
 */
internal fun parseMarkdownOutline(source: String): List<StudyOutlineItem> {
    val parser = Parser.builder().build()
    val document = parser.parse(source)
    val items = mutableListOf<StudyOutlineItem>()
    val counts = mutableMapOf<String, Int>()
    collectHeadings(document, items, counts)
    return items
}

private fun collectHeadings(
    node: Node,
    sink: MutableList<StudyOutlineItem>,
    counts: MutableMap<String, Int>,
) {
    var child = node.firstChild
    while (child != null) {
        if (child is Heading) {
            val text = plainText(child)
            if (text.isNotBlank()) {
                sink += StudyOutlineItem(
                    level = child.level,
                    title = text,
                    anchor = anchorWithCount(text, counts),
                )
            }
        } else {
            collectHeadings(child, sink, counts)
        }
        child = child.next
    }
}

private fun plainText(node: Node): String {
    val builder = StringBuilder()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is Text -> builder.append(child.literal)
            is Code -> builder.append(child.literal)
            else -> builder.append(plainText(child))
        }
        child = child.next
    }
    return builder.toString().trim()
}

private fun anchorWithCount(title: String, counts: MutableMap<String, Int>): String {
    val base = title.lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString("")
        .trim('-')
        .ifBlank { "section" }
    val count = counts[base] ?: 0
    counts[base] = count + 1
    return if (count == 0) base else "$base-$count"
}
