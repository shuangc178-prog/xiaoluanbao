package com.example.campuslife.ui.study

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.campuslife.ui.theme.ClaudeAccent
import com.example.campuslife.ui.theme.ClaudeCanvas
import com.example.campuslife.ui.theme.ClaudeDarkAccent
import com.example.campuslife.ui.theme.ClaudeDarkCanvas
import com.example.campuslife.ui.theme.ClaudeDarkCodeBg
import com.example.campuslife.ui.theme.ClaudeDarkCodeInlineText
import com.example.campuslife.ui.theme.ClaudeDarkText
import com.example.campuslife.ui.theme.ClaudeMark
import com.example.campuslife.ui.theme.ClaudeSubtle
import com.example.campuslife.ui.theme.ClaudeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读器样式是照着 Typora 的 Claude 主题搬的，这里守住「搬得对」的那几条硬特征：
 * 正文与标题走衬线族、表格/引用/代码块的形状与主题一致、颜色全部来自注入的调色板。
 */
class StudyReaderCssTest {

    private val palette = StudyReaderPalette(
        canvas = Color(0xFFFAF9F5),
        panel = Color(0xFFFFFFFF),
        subtle = Color(0xFFF0EEE6),
        text = Color(0xFF141413),
        textSecondary = Color(0xFF3D3D3A),
        muted = Color(0xFF73726C),
        border = Color(0x1F141413),
        borderStrong = Color(0x33141413),
        accent = Color(0xFFC6613F),
        accentSoft = Color(0xFFF8ECE8),
        mark = Color(0xFFF4DDB5),
        rowHover = Color(0x0A141413),
        codeBg = Color(0xFFF0EEE6),
        codeText = Color(0xFF2E2E2B),
        codeInlineBg = Color(0xFFF0EEE6),
        codeInlineText = Color(0xFF8F442E),
        danger = Color(0xFFB6483D),
        dangerSoft = Color(0xFFF5E5E4),
        shadowSoft = "0 1px 2px rgba(0, 0, 0, 0.04)",
        shadowMedia = "0 8px 28px rgba(0, 0, 0, 0.09)",
        shadowKey = "rgba(0, 0, 0, 0.16)",
    )

    @Test
    fun proseUsesTheSerifStackFromTheTyporaTheme() {
        val css = studyReaderCss(palette)

        assertTrue(css.contains("Georgia, \"Noto Serif SC\""))
        assertTrue(css.contains("font-size: 17px"))
        assertTrue(css.contains("line-height: 1.74"))
        assertTrue(css.contains("max-width: 790px"))
    }

    @Test
    fun shapesFollowTheThemeInsteadOfGenericMaterialDefaults() {
        val css = studyReaderCss(palette)

        // 表格：圆角 + 独立边框 + 行悬停底色（主题是 border-collapse: separate）
        assertTrue(css.contains("border-collapse: separate"))
        assertTrue(css.contains("border-radius: 12px"))
        assertTrue(css.contains("tbody tr:hover"))
        // 引用：左侧强调条 + 右侧圆角
        assertTrue(css.contains("border-left: 3px solid #C6613F"))
        assertTrue(css.contains("border-radius: 0 12px 12px 0"))
        // 代码块：等宽族 + 代码底色，行内代码另有边框
        assertTrue(css.contains("--mono-font"))
        assertTrue(css.contains(":not(pre) > code"))
        // 高亮用主题的 mark 色，不是之前那个写死的 #f7d774
        assertTrue(css.contains("background: #F4DDB5"))
        assertFalse(css.contains("#f7d774"))
    }

    @Test
    fun colorsComeFromThePaletteSoDarkModeFollowsTheTheme() {
        val css = studyReaderCss(palette)

        assertTrue(css.contains("#FAF9F5"))
        assertTrue(css.contains("#141413"))
        assertTrue(css.contains("#C6613F"))
        // 组件不自己判断明暗：样式里不该出现 media query 抢主题的控制权
        assertFalse(css.contains("prefers-color-scheme"))
    }

    @Test
    fun notesAndTasksGetTheInteractionStyles() {
        val css = studyReaderCss(palette)

        assertTrue(css.contains(".study-note"))
        assertTrue(css.contains(".study-note-text.study-note-open"))
        assertTrue(css.contains(".task-list-item > input[type=\"checkbox\"]"))
        assertTrue(css.contains("accent-color: #C6613F"))
    }

    @Test
    fun themeTokensMatchTheTyporaClaudePalette() {
        // 阅读器的观感最终由主题令牌决定，这里钉住与 Typora 主题 CSS 的对应关系，
        // 免得哪天有人改了 Color.kt 而阅读器悄悄偏离主题。
        // 用 toArgb() 取值：Color.value 是压缩后的 64 位表示，不是 ARGB。
        assertEquals(0xFFFAF9F5.toInt(), ClaudeCanvas.toArgb())
        assertEquals(0xFF141413.toInt(), ClaudeText.toArgb())
        assertEquals(0xFFC6613F.toInt(), ClaudeAccent.toArgb())
        // 正文高亮用 mark 色，行内代码底色用次级面色，代码块暗色另有专属底色
        assertEquals(0xFFF4DDB5.toInt(), ClaudeMark.toArgb())
        assertEquals(0xFFF0EEE6.toInt(), ClaudeSubtle.toArgb())
        assertEquals(0xFF30302E.toInt(), ClaudeDarkCanvas.toArgb())
        assertEquals(0xFFFAF9F5.toInt(), ClaudeDarkText.toArgb())
        assertEquals(0xFFD97757.toInt(), ClaudeDarkAccent.toArgb())
        assertEquals(0xFF1F1E1D.toInt(), ClaudeDarkCodeBg.toArgb())
        assertEquals(0xFFEDA386.toInt(), ClaudeDarkCodeInlineText.toArgb())
    }
}
