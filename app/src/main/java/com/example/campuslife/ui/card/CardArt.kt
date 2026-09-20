package com.example.campuslife.ui.card

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.CardRarity
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.ui.theme.claude

/**
 * 稀有度配色。
 *
 * 五个档位直接用 Claude 色板里已有的语义色，不再新造颜色：
 * 绿（R）→ 蓝（SR）→ 棕金（SSR）→ 紫（SSSR）→ 强调橙（XR）。
 * 卡面图里已经画了稀有度徽章，这里的色只用于**卡面之外**的地方
 * （进度条、开卡柔光、格子描边），与图里的配色大致同向。
 */
@Composable
internal fun rarityAccent(rarity: CardRarity): Color = when (rarity) {
    CardRarity.R -> MaterialTheme.claude.green
    CardRarity.SR -> MaterialTheme.claude.blue
    CardRarity.SSR -> MaterialTheme.claude.amber
    CardRarity.SSSR -> MaterialTheme.claude.violet
    CardRarity.XR -> MaterialTheme.claude.accent
}

@Composable
internal fun raritySoft(rarity: CardRarity): Color = when (rarity) {
    CardRarity.R -> MaterialTheme.claude.greenSoft
    CardRarity.SR -> MaterialTheme.claude.blueSoft
    CardRarity.SSR -> MaterialTheme.claude.amberSoft
    CardRarity.SSSR -> MaterialTheme.claude.violetSoft
    CardRarity.XR -> MaterialTheme.claude.accentSoft
}

/** 没拿到的卡压成灰底剪影：一眼能看出「有这一格、还没到手」。 */
internal val Desaturate = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) },
)

/** 没拿到的卡还要压暗：降饱和只能去掉颜色，不去掉「看起来像已经到手了」。 */
internal const val UNOWNED_ART_ALPHA = 0.32f

/**
 * 卡面透明度唯一口径。
 *
 * 收集格子（[CardSlot]）与整屏看卡（`CardStage`）都读它——两处必须一致，
 * 否则会出现「格子里是灰的、点开却是彩色的」这种自相矛盾的状态。
 */
internal fun cardArtAlpha(owned: Int): Float = if (owned > 0) 1f else UNOWNED_ART_ALPHA

/**
 * 一格卡位：卡面 + 右下角的「已有几张」。
 *
 * 卡面图自带外框、卡名与稀有度徽章，所以这里不再叠任何文字标题；
 * 没拿到的卡保留占位（降饱和 + 降透明），让人知道这套一共几张、还差哪些。
 */
@Composable
internal fun CardSlot(
    card: GachaCard,
    owned: Int,
    maxCopies: Int,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    Box(
        modifier = modifier
            .aspectRatio(card.aspectRatio)
            .clip(RoundedCornerShape(cornerRadius)),
    ) {
        Image(
            painter = painterResource(card.artRes),
            // 卡名与稀有度都画在图里，读屏用户靠旁边的文字说明即可，这里不重复念。
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = if (owned > 0) null else Desaturate,
            alpha = cardArtAlpha(owned),
        )
        if (owned > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                contentColor = if (owned >= maxCopies) {
                    rarityAccent(card.rarity)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = "$owned/$maxCopies",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * 单张卡的大图（开卡动效用）。
 *
 * 只约束宽度、高度由素材宽高比推出来——同时写死宽高会把卡面压变形。
 */
@Composable
internal fun CardArt(
    card: GachaCard,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Image(
        painter = painterResource(card.artRes),
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(card.aspectRatio),
        contentScale = ContentScale.Fit,
        alpha = alpha,
    )
}
