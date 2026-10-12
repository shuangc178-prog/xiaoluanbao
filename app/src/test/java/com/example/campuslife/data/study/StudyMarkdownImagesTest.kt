package com.example.campuslife.data.study

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMarkdownImagesTest {

    @Test
    fun findsMarkdownAndRawHtmlLocalImagesButIgnoresRemoteAndCodeSamples() {
        val source = """
            ![网络图](assets/network.png)
            <img src="assets/python screen.png" alt="Python">
            ![远程图](https://example.com/remote.png)
            ![内嵌图](data:image/png;base64,AAAA)

            ```html
            <img src="assets/not-a-real-reference.png">
            ```
        """.trimIndent()

        assertEquals(
            listOf("assets/network.png", "assets/python screen.png"),
            markdownLocalImageSources(source),
        )
    }

    @Test
    fun resolvesRelativeAndTyporaRootImagesInsideSnapshot() {
        val root = Files.createTempDirectory("study-images").toFile()
        try {
            val notes = root.resolve("notes").also { it.mkdirs() }
            val shared = root.resolve("shared").also { it.mkdirs() }
            val markdown = notes.resolve("chapter.md").also { it.writeText("# chapter") }
            val relativeImage = notes.resolve("assets/local image.png").also {
                requireNotNull(it.parentFile).mkdirs()
                it.writeBytes(byteArrayOf(1, 2, 3))
            }
            val rootImage = shared.resolve("root.png").also { it.writeBytes(byteArrayOf(4, 5, 6)) }
            val source = """
                ---
                typora-root-url: ../
                ---
                ![](assets/local%20image.png)
                ![](/shared/root.png)
            """.trimIndent()

            assertEquals(
                relativeImage.canonicalFile,
                resolveImportedLocalImage(root, markdown, source, "assets/local%20image.png"),
            )
            assertEquals(
                rootImage.canonicalFile,
                resolveImportedLocalImage(root, markdown, source, "/shared/root.png"),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingRemoteAndEscapingImages() {
        val root = Files.createTempDirectory("study-images-safe").toFile()
        val outside = Files.createTempFile("outside-study-image", ".png").toFile()
        try {
            val markdown = root.resolve("note.md").also { it.writeText("# note") }
            assertNull(resolveImportedLocalImage(root, markdown, "", "assets/missing.png"))
            assertNull(resolveImportedLocalImage(root, markdown, "", "https://example.com/a.png"))
            assertNull(resolveImportedLocalImage(root, markdown, "", "../${outside.name}"))
            assertTrue(outside.isFile)
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }
}
