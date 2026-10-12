package com.example.campuslife.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.campuslife.R
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 四个 Tab 各自的像素背景：顶部一条天空 / 纸面，底部一条地面 / 桌面 / 书架 / 图表。
 * 图由 `tools/backdrops/generate_backdrops.py` 生成，要改画面回那里改完重新生成。
 */
enum class BackdropScene(
    @param:DrawableRes val topLight: Int,
    @param:DrawableRes val topDark: Int,
    @param:DrawableRes val bottomLight: Int,
    @param:DrawableRes val bottomDark: Int,
) {
    Plan(
        R.drawable.backdrop_plan_top_light, R.drawable.backdrop_plan_top_dark,
        R.drawable.backdrop_plan_bottom_light, R.drawable.backdrop_plan_bottom_dark,
    ),
    Journal(
        R.drawable.backdrop_journal_top_light, R.drawable.backdrop_journal_top_dark,
        R.drawable.backdrop_journal_bottom_light, R.drawable.backdrop_journal_bottom_dark,
    ),
    Study(
        R.drawable.backdrop_study_top_light, R.drawable.backdrop_study_top_dark,
        R.drawable.backdrop_study_bottom_light, R.drawable.backdrop_study_bottom_dark,
    ),
    Stats(
        R.drawable.backdrop_stats_top_light, R.drawable.backdrop_stats_top_dark,
        R.drawable.backdrop_stats_bottom_light, R.drawable.backdrop_stats_bottom_dark,
    ),
}

private data class BackdropKey(val scene: BackdropScene, val dark: Boolean)

/**
 * 页面底层：Claude Canvas 底色 + 上下两条像素插画。放在页面内容**下面**，内容自己保持透明底。
 *
 * 插画按「整数倍、最近邻」放大到刚好盖住屏宽（多出来的从两侧对称裁掉），像素边永远锐利；
 * 顶部那条贴着屏幕顶，底部那条贴着内容区底（导航栏上沿）。
 * 切 Tab、切明暗时整幅背景淡入淡出，不是一下子跳过去。
 */
@Composable
fun ClaudeBackdrop(
    scene: BackdropScene,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.claude.isDark
    Crossfade(
        targetState = BackdropKey(scene, dark),
        modifier = modifier,
        animationSpec = tween(durationMillis = 420),
        label = "backdrop",
    ) { key ->
        val top = ImageBitmap.imageResource(if (key.dark) key.scene.topDark else key.scene.topLight)
        val bottom = ImageBitmap.imageResource(if (key.dark) key.scene.bottomDark else key.scene.bottomLight)
        val canvas = if (key.dark) ClaudeDarkCanvas else ClaudeCanvas
        Canvas(Modifier.fillMaxSize()) {
            drawRect(canvas)
            drawPixelBand(top, alignBottom = false)
            drawPixelBand(bottom, alignBottom = true)
        }
    }
}

private fun DrawScope.drawPixelBand(image: ImageBitmap, alignBottom: Boolean) {
    if (image.width <= 0 || size.width <= 0f) return
    val scale = ceil(size.width / image.width).toInt().coerceAtLeast(1)
    val dstSize = IntSize(image.width * scale, image.height * scale)
    val left = ((size.width - dstSize.width) / 2f).roundToInt()
    val top = if (alignBottom) (size.height - dstSize.height).roundToInt() else 0
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left, top),
        dstSize = dstSize,
        filterQuality = FilterQuality.None,
    )
}
