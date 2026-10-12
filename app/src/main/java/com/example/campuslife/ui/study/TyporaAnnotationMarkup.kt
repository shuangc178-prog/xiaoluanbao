package com.example.campuslife.ui.study

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/**
 * 把 Typora 自定义「注释」标记换成阅读器自己生成的安全 HTML。
 *
 * 当前 Typora 写入格式为：
 * `==可见文字<!--typora-annotation-v1-<id>-<base64>-->==`
 *
 * 旧笔记中还可能出现冒号分隔符，或元数据紧跟在高亮外部；两种形式都兼容。
 * 注释正文始终重新编码进 data 属性，浏览器端只通过 textContent 展示，绝不作为 HTML 执行。
 */
internal fun substituteTyporaAnnotations(source: String): String {
    val withInternalAnnotations = TYPORA_ANNOTATION_INSIDE_HIGHLIGHT.replace(source) { match ->
        annotationMarkup(
            visibleText = match.groupValues[1],
            id = match.groupValues[2],
            payload = match.groupValues[3],
        )
    }
    return TYPORA_ANNOTATION_AFTER_HIGHLIGHT.replace(withInternalAnnotations) { match ->
        annotationMarkup(
            visibleText = match.groupValues[1],
            id = match.groupValues[2],
            payload = match.groupValues[3],
        )
    }
}

private fun annotationMarkup(
    visibleText: String,
    id: String,
    payload: String,
): String {
    val note = decodeAnnotationPayload(payload)
    if (note.isNullOrEmpty()) return "<mark>$visibleText</mark>"

    val safePayload = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(note.toByteArray(Charsets.UTF_8))
    return buildString {
        append("<mark class=\"typora-annotation\"")
        append(" data-annotation-id=\"")
        append(id.lowercase())
        append("\" data-annotation=\"")
        append(safePayload)
        append("\" role=\"button\" tabindex=\"0\" aria-label=\"查看注释\">")
        append(visibleText)
        append("</mark>")
    }
}

private fun decodeAnnotationPayload(payload: String): String? = runCatching {
    val normalized = payload.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val bytes = Base64.getDecoder().decode(padded)
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}.getOrNull()

private const val ANNOTATION_MARKER =
    "<!--\\s*typora-annotation[-:]v1[-:]([a-f0-9]+)[-:]([A-Za-z0-9+/_=-]*)\\s*-->"

private val TYPORA_ANNOTATION_INSIDE_HIGHLIGHT = Regex(
    "(?i)==((?:(?!==)[^\\r\\n])*?)$ANNOTATION_MARKER\\s*==",
)

private val TYPORA_ANNOTATION_AFTER_HIGHLIGHT = Regex(
    "(?i)==((?:(?!==)[^\\r\\n])*?)==[ \\t]*$ANNOTATION_MARKER",
)
