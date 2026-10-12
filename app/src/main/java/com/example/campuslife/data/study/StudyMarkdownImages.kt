package com.example.campuslife.data.study

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.parser.Parser

/** 收集 Markdown 正文真正使用到的本地图片地址，代码块中的示例不会被误判。 */
internal fun markdownLocalImageSources(source: String): List<String> {
    val document = Parser.builder().build().parse(source)
    val result = linkedSetOf<String>()

    fun collect(node: Node) {
        when (node) {
            is Image -> node.destination
                .takeIf(::isLocalImageSource)
                ?.let(result::add)

            is HtmlInline -> collectRawHtmlImageSources(node.literal, result)
            is HtmlBlock -> collectRawHtmlImageSources(node.literal, result)
        }

        var child = node.firstChild
        while (child != null) {
            collect(child)
            child = child.next
        }
    }

    collect(document)
    return result.toList()
}

/**
 * 将图片地址限制在本次导入快照内，并按 Typora 的相对路径与 `typora-root-url` 规则解析。
 * 返回 null 表示远程地址、越界路径、绝对电脑路径或快照中不存在的文件。
 */
internal fun resolveImportedLocalImage(
    snapshotRoot: File,
    markdownFile: File,
    markdownSource: String,
    imageSource: String,
): File? {
    if (!isLocalImageSource(imageSource)) return null

    val sourcePath = imageSource
        .trim()
        .removeSurrounding("<", ">")
        .replace("&amp;", "&", ignoreCase = true)
        .substringBefore('#')
        .substringBefore('?')
        .let(::decodeUrlPath)
        .replace('\\', '/')
        .trim()
    if (sourcePath.isEmpty() || WINDOWS_ABSOLUTE_PATH.containsMatchIn(sourcePath)) return null

    val base = if (sourcePath.startsWith('/')) {
        typoraRootDirectory(snapshotRoot, markdownFile, markdownSource) ?: snapshotRoot
    } else {
        markdownFile.parentFile ?: snapshotRoot
    }
    val relativePath = sourcePath.trimStart('/')
    if (relativePath.isEmpty()) return null

    val candidate = runCatching { File(base, relativePath).canonicalFile }.getOrNull() ?: return null
    val safeRoot = runCatching { snapshotRoot.canonicalFile }.getOrNull() ?: return null
    if (!candidate.isInside(safeRoot) || !candidate.isFile) return null
    return candidate
}

private fun collectRawHtmlImageSources(html: String, sink: MutableSet<String>) {
    RAW_IMAGE_SOURCE.findAll(html).forEach { match ->
        val source = match.groupValues[2].ifEmpty { match.groupValues[3] }
        if (isLocalImageSource(source)) sink += source
    }
}

private fun isLocalImageSource(source: String): Boolean {
    val value = source.trim().removeSurrounding("<", ">")
    if (value.isEmpty() || value.startsWith('#') || value.startsWith("//")) return false
    if (WINDOWS_ABSOLUTE_PATH.containsMatchIn(value)) return true

    val scheme = URI_SCHEME.find(value)?.value?.dropLast(1)?.lowercase() ?: return true
    return scheme == "file"
}

private fun typoraRootDirectory(
    snapshotRoot: File,
    markdownFile: File,
    markdownSource: String,
): File? {
    val frontMatter = FRONT_MATTER.find(markdownSource)?.groupValues?.get(1) ?: return null
    val rawRoot = TYPORA_ROOT_URL.find(frontMatter)?.groupValues?.get(1)
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    if (URI_SCHEME.containsMatchIn(rawRoot) || WINDOWS_ABSOLUTE_PATH.containsMatchIn(rawRoot)) return null

    val base = markdownFile.parentFile ?: snapshotRoot
    val root = if (rawRoot.startsWith('/')) {
        File(snapshotRoot, rawRoot.trimStart('/'))
    } else {
        File(base, rawRoot.replace('\\', '/'))
    }
    val candidate = runCatching { root.canonicalFile }.getOrNull() ?: return null
    val safeRoot = runCatching { snapshotRoot.canonicalFile }.getOrNull() ?: return null
    return candidate.takeIf { it.isInside(safeRoot) && it.isDirectory }
}

private fun File.isInside(root: File): Boolean {
    val rootPath = root.path.trimEnd(File.separatorChar) + File.separator
    return path.equals(root.path, ignoreCase = File.separatorChar == '\\') ||
        path.startsWith(rootPath, ignoreCase = File.separatorChar == '\\')
}

private fun decodeUrlPath(value: String): String = runCatching {
    // URLDecoder 会把普通的 `+` 当空格；先保护它，图片文件名中的加号才能原样保留。
    URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrDefault(value)

private val RAW_IMAGE_SOURCE = Regex(
    """(?is)<img\b[^>]*?\s+src\s*=\s*(?:([\"'])(.*?)\1|([^\s\"'=<>`]+))""",
)
private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:[/\\\\]")
private val FRONT_MATTER = Regex(
    """(?s)\A(?:\uFEFF)?---[ \t]*\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|$)""",
)
private val TYPORA_ROOT_URL = Regex("(?im)^\\s*typora-root-url\\s*:\\s*([^\\r\\n#]+)")
