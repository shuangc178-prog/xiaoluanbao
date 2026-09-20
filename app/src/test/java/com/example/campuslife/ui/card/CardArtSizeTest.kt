package com.example.campuslife.ui.card

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡面尺寸口径。
 *
 * 这条很容易写歪，而且歪了要真机才看得出来：只按宽度算，屏幕一矮
 * （小屏、分屏、横屏）卡就会顶出屏幕、把底下的文字和按钮挤没。
 * 所以两个方向都要钉住：宽屏时听宽度，矮屏时听高度。
 */
class CardArtSizeTest {

    private val ratio = 0.6738f

    /** 常态手机：高度够，尺寸由宽度决定，且高度确实被用起来而不是缩水。 */
    @Test
    fun tallPhoneIsDrivenByWidth() {
        val size = calculateCardArtSize(
            availableWidth = 360.dp,
            availableHeight = 740.dp,
            aspectRatio = ratio,
        )
        assertEquals(360.dp * 0.76f, size.width)
        assertEquals(size.width / ratio, size.height)
    }

    /** 横屏 / 分屏：高度先到顶，卡面必须缩到框里，不能整张顶出去。 */
    @Test
    fun shortScreenIsDrivenByHeight() {
        val availableHeight = 360.dp
        val size = calculateCardArtSize(
            availableWidth = 740.dp,
            availableHeight = availableHeight,
            aspectRatio = ratio,
        )
        assertTrue(
            "卡面高 ${size.height} 超出了一屏 $availableHeight",
            size.height < availableHeight,
        )
        // 卡面宽度被高度反推出来：可用高度扣掉底部留白后，再按宽高比折算。
        assertEquals(
            (availableHeight - 116.dp).value * ratio,
            size.width.value,
            0.01f,
        )
    }

    /** 宽度不变时比例恒定：卡面永远不变形，这是素材本身的宽高比。 */
    @Test
    fun widthAndHeightKeepTheArtworkRatio() {
        listOf(360.dp, 412.dp, 300.dp).forEach { width ->
            val size = calculateCardArtSize(width, 900.dp, ratio)
            assertEquals(width.value * 0.76f, size.height.value * ratio, 0.01f)
        }
    }

    /** 退化输入不返回负数——负尺寸会让 Compose 直接抛异常。 */
    @Test
    fun degenerateSpaceNeverGoesNegative() {
        val size = calculateCardArtSize(
            availableWidth = 0.dp,
            availableHeight = 0.dp,
            aspectRatio = ratio,
        )
        assertEquals(0.dp, size.width)
        assertEquals(0.dp, size.height)
    }

    /**
     * 还没到手的卡必须是灰的。
     *
     * 收集格子和整屏看卡都读这一个函数，所以只要它区分 0 张，两处就一起变灰。
     * 这里钉的是「看卡和格子口径一致」——曾经整屏那边直接铺了原图，
     * 格子里灰、点开却是彩色。
     */
    @Test
    fun unownedCardIsGreyedOut() {
        assertEquals(UNOWNED_ART_ALPHA, cardArtAlpha(owned = 0))
        assertEquals(1f, cardArtAlpha(owned = 1))
        // 重复抽到的第 2 张仍然是彩色：到手过就是到手过。
        assertEquals(1f, cardArtAlpha(owned = 2))
        assertTrue("压暗值应当小于不透明", UNOWNED_ART_ALPHA < 1f)
    }
}
