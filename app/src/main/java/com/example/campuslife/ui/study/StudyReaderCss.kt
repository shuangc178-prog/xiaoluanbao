package com.example.campuslife.ui.study

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.campuslife.ui.theme.claude

/**
 * 阅读器正文的排版与配色。
 *
 * 基准就是 Typora 里正在用的那套 Claude 主题
 * （`F:\Typora\resources\appsrc\custom\themes\claude-desktop\claude-desktop-base.css`
 * 加 light / dark 两套变量），所以这里不另起一套设计，只做两件事：
 *
 * 1. 把主题 CSS 里 `#write` 作用域下的规则逐条搬过来，选择器去掉 `#write` 前缀；
 * 2. 颜色一律由参数注入，由调用方从 `MaterialTheme` 取语义色，不在这个文件里写死色值。
 *
 * 标题与正文用衬线族、界面元素用无衬线族、代码用等宽族，与 Typora 主题一致。
 */
internal data class StudyReaderPalette(
    val canvas: Color,
    val panel: Color,
    val subtle: Color,
    val text: Color,
    val textSecondary: Color,
    val muted: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val mark: Color,
    val rowHover: Color,
    val codeBg: Color,
    val codeText: Color,
    val codeInlineBg: Color,
    val codeInlineText: Color,
    val danger: Color,
    val dangerSoft: Color,
    val shadowSoft: String,
    val shadowMedia: String,
    val shadowKey: String,
)

/** 从当前主题取一组阅读器配色；浅色 / 深色跟随 `MaterialTheme`，组件不自己判断明暗。 */
@Composable
@ReadOnlyComposable
internal fun studyReaderPalette(): StudyReaderPalette = StudyReaderPalette(    canvas = MaterialTheme.colorScheme.background,
    panel = MaterialTheme.colorScheme.surface,
    subtle = MaterialTheme.colorScheme.surfaceVariant,
    text = MaterialTheme.colorScheme.onBackground,
    textSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
    muted = MaterialTheme.colorScheme.outline,
    border = MaterialTheme.colorScheme.outlineVariant,
    borderStrong = MaterialTheme.colorScheme.outline,
    accent = MaterialTheme.colorScheme.primary,
    accentSoft = MaterialTheme.colorScheme.primaryContainer,
    mark = MaterialTheme.claude.amberSoft,
    rowHover = MaterialTheme.claude.rowHover,
    codeBg = MaterialTheme.claude.codeBg,
    codeText = MaterialTheme.claude.codeText,
    codeInlineBg = MaterialTheme.claude.codeInlineBg,
    codeInlineText = MaterialTheme.claude.codeInlineText,
    danger = MaterialTheme.colorScheme.error,
    dangerSoft = MaterialTheme.claude.dangerSoft,
    shadowSoft = "0 1px 2px rgba(0, 0, 0, 0.04)",
    shadowMedia = "0 8px 28px rgba(0, 0, 0, 0.09)",
    shadowKey = "rgba(0, 0, 0, 0.16)",
)

internal fun studyReaderCss(palette: StudyReaderPalette): String = """
    <style>
      :root {
        color-scheme: light dark;
        --ui-font: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans CJK SC", "PingFang SC", sans-serif;
        --mono-font: "Cascadia Mono", "SFMono-Regular", Consolas, "Liberation Mono", monospace;
        /* 表格遮罩要盖成「空白」，取值必须与正文底色同源 */
        --study-canvas: ${palette.canvas.cssHex()};
      }
      * { box-sizing: border-box; }
      html { background: ${palette.canvas.cssHex()}; }
      body {
        max-width: 790px;
        margin: 0 auto;
        padding: 30px 24px 80px;
        color: ${palette.text.cssHex()};
        background: ${palette.canvas.cssHex()};
        font-family: Georgia, "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
        font-size: 17px;
        font-kerning: normal;
        line-height: 1.74;
        overflow-wrap: break-word;
        text-rendering: optimizeLegibility;
        -webkit-font-smoothing: antialiased;
      }
      ::selection { color: ${palette.text.cssHex()}; background: ${palette.accentSoft.cssHex()}; }
      p, blockquote, ul, ol, table, pre, hr { margin: 0.9em 0; }
      body > :first-child { margin-top: 0; }

      h1, h2, h3, h4, h5, h6 {
        margin: 1.85em 0 0.65em;
        color: ${palette.text.cssHex()};
        font-family: Georgia, "Noto Serif SC", "Source Han Serif SC", "Songti SC", SimSun, serif;
        font-weight: 600;
        line-height: 1.24;
        letter-spacing: -0.018em;
      }
      h1 { margin-top: 0.5em; font-size: 2.1em; font-weight: 500; letter-spacing: -0.032em; }
      h2 { padding-bottom: 0.3em; border-bottom: 1px solid ${palette.border.cssHex()}; font-size: 1.6em; }
      h3 { font-size: 1.28em; }
      h4 { font-size: 1.08em; }
      h5 { color: ${palette.textSecondary.cssHex()}; font-size: 0.98em; }
      h6 {
        color: ${palette.muted.cssHex()};
        font-family: var(--ui-font);
        font-size: 0.78em;
        font-weight: 650;
        letter-spacing: 0.055em;
        text-transform: uppercase;
      }

      a { color: ${palette.accent.cssHex()}; text-decoration: underline; text-decoration-thickness: 1px; text-underline-offset: 0.18em; }
      strong { color: ${palette.text.cssHex()}; font-weight: 700; }
      em { color: ${palette.textSecondary.cssHex()}; }
      del { color: ${palette.muted.cssHex()}; text-decoration-color: ${palette.muted.cssHex()}; }
      mark { margin: 0 -0.06em; padding: 0.06em 0.17em; border-radius: 4px; color: ${palette.text.cssHex()}; background: ${palette.mark.cssHex()}; }

      ul, ol { padding-left: 1.65em; }
      li { margin: 0.22em 0; padding-left: 0.12em; }
      li::marker { color: ${palette.accent.cssHex()}; font-family: var(--ui-font); font-weight: 650; }
      li > p { margin: 0.3em 0; }

      .task-list-item { list-style: none; }
      .task-list-item > input[type="checkbox"] {
        width: 1.05em;
        height: 1.05em;
        margin: 0 0.5em 0 -1.5em;
        vertical-align: -0.14em;
        accent-color: ${palette.accent.cssHex()};
        cursor: pointer;
      }
      .task-list-item.task-list-done { background: ${palette.accentSoft.cssHex()}; }

      blockquote {
        padding: 0.85rem 1.05rem 0.85rem 1.15rem;
        border: 1px solid ${palette.border.cssHex()};
        border-left: 3px solid ${palette.accent.cssHex()};
        border-radius: 0 12px 12px 0;
        color: ${palette.textSecondary.cssHex()};
        background: ${palette.panel.cssHex()};
        box-shadow: ${palette.shadowSoft};
      }
      blockquote > :first-child { margin-top: 0; }
      blockquote > :last-child { margin-bottom: 0; }
      blockquote blockquote { background: ${palette.subtle.cssHex()}; box-shadow: none; }

      table {
        display: block;
        width: max-content;
        max-width: 100%;
        overflow-x: auto;
        border: 1px solid ${palette.border.cssHex()};
        border-collapse: separate;
        border-radius: 12px;
        border-spacing: 0;
        background: ${palette.panel.cssHex()};
        box-shadow: ${palette.shadowSoft};
        font-family: var(--ui-font);
        font-size: 0.9rem;
        line-height: 1.5;
      }
      th, td {
        padding: 0.68rem 0.8rem;
        border: 0;
        border-right: 1px solid ${palette.border.cssHex()};
        border-bottom: 1px solid ${palette.border.cssHex()};
        text-align: left;
        vertical-align: top;
      }
      th:last-child, td:last-child { border-right: 0; }
      tr:last-child td { border-bottom: 0; }
      thead th { color: ${palette.text.cssHex()}; background: ${palette.subtle.cssHex()}; font-weight: 650; }
      tbody tr:hover { background: ${palette.rowHover.cssHex()}; }

      code, tt, kbd, samp { font-family: var(--mono-font); }
      :not(pre) > code, tt {
        padding: 0.12em 0.34em;
        border: 1px solid ${palette.border.cssHex()};
        border-radius: 5px;
        color: ${palette.codeInlineText.cssHex()};
        background: ${palette.codeInlineBg.cssHex()};
        font-size: 0.86em;
        white-space: break-spaces;
      }
      pre {
        overflow-x: auto;
        padding: 1rem 1.1rem;
        border: 1px solid ${palette.border.cssHex()};
        border-radius: 12px;
        color: ${palette.codeText.cssHex()};
        background: ${palette.codeBg.cssHex()};
        box-shadow: ${palette.shadowSoft};
        font-size: 14px;
        line-height: 1.5;
      }
      pre code { padding: 0; border: 0; color: inherit; background: transparent; font-size: inherit; white-space: pre; }

      img, video { max-width: 100%; height: auto; border-radius: 12px; box-shadow: ${palette.shadowMedia}; }
      figure { margin: 1.4rem 0; }
      figcaption { color: ${palette.muted.cssHex()}; font-family: var(--ui-font); font-size: 0.8rem; text-align: center; }
      hr { height: 1px; margin: 2.5rem 0; border: 0; background: ${palette.border.cssHex()}; }
      kbd {
        display: inline-block;
        min-width: 1.7em;
        padding: 0.08em 0.4em;
        border: 1px solid ${palette.borderStrong.cssHex()};
        border-bottom-width: 2px;
        border-radius: 5px;
        color: ${palette.textSecondary.cssHex()};
        background: ${palette.panel.cssHex()};
        box-shadow: 0 1px 0 ${palette.shadowKey};
        font-family: var(--ui-font);
        font-size: 0.78em;
        line-height: 1.5;
      }
      sup { font-size: 0.72em; }
      input[type="checkbox"] { accent-color: ${palette.accent.cssHex()}; }

      /* Typora「注释」：高亮文字可点，备注在它下面展开 */
      .study-note {
        cursor: pointer;
        border-bottom: 1px dotted ${palette.accent.cssHex()};
      }
      .study-note-text {
        display: none;
        margin: 0.4rem 0 0.7rem;
        padding: 0.65rem 0.85rem;
        border-left: 3px solid ${palette.mark.cssHex()};
        border-radius: 0 10px 10px 0;
        color: ${palette.textSecondary.cssHex()};
        background: ${palette.panel.cssHex()};
        box-shadow: ${palette.shadowSoft};
        font-family: var(--ui-font);
        font-size: 0.88rem;
        line-height: 1.6;
        white-space: pre-wrap;
      }
      .study-note-text.study-note-open { display: block; }
      .study-note-orphan {
        display: inline-block;
        margin: 0.2rem 0;
        padding: 0.3rem 0.6rem;
        border-left: 3px solid ${palette.mark.cssHex()};
        border-radius: 0 8px 8px 0;
        color: ${palette.textSecondary.cssHex()};
        background: ${palette.panel.cssHex()};
        font-family: var(--ui-font);
        font-size: 0.85rem;
        white-space: pre-wrap;
      }
      .reader-resource-note { color: ${palette.muted.cssHex()}; font-size: 0.9em; }
    </style>
""".trimIndent()

private fun Color.cssHex(): String = String.format("#%06X", 0xFFFFFF and toArgb())
