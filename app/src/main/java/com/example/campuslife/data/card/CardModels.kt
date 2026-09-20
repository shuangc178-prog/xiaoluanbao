package com.example.campuslife.data.card

import androidx.annotation.DrawableRes
import com.example.campuslife.R

/** 收集卡稀有度；权重会在当前弹仍可抽取的档位之间重新归一。 */
enum class CardRarity(
    val label: String,
    val weight: Int,
) {
    R("R", 45),
    SR("SR", 28),
    SSR("SSR", 17),
    SSSR("SSSR", 8),
    XR("XR", 2),
}

/**
 * 抽卡系列。
 *
 * [id] 同时是本地存储和备份里的稳定标识；展示名称变化时不能改它。
 */
enum class CardSeries(
    val id: String,
    val number: Int,
    val displayName: String,
    val collectionSubtitle: String,
) {
    Isekai(
        id = "isekai",
        number = 1,
        displayName = "异世界之旅",
        collectionSubtitle = "像素冒险收藏",
    ),
    Sanguo(
        id = "sanguo",
        number = 2,
        displayName = "三国演义",
        collectionSubtitle = "像素群雄收藏",
    ),
    ;

    val cards: List<GachaCard>
        get() = GachaCard.entries.filter { it.series == this }

    companion object {
        fun fromId(id: String): CardSeries? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 所有已上线系列共用的运行时卡表。
 *
 * 卡面已经包含外框、卡名和稀有度，界面只需按素材自己的 [aspectRatio] 展示。
 */
enum class GachaCard(
    val id: String,
    val displayName: String,
    val rarity: CardRarity,
    val series: CardSeries,
    @param:DrawableRes val artRes: Int,
    val aspectRatio: Float,
) {
    Unicorn(
        id = "isekai_001_unicorn",
        displayName = "独角兽",
        rarity = CardRarity.R,
        series = CardSeries.Isekai,
        artRes = R.drawable.isekai_001_unicorn,
        aspectRatio = 0.6738f,
    ),
    ElfKing(
        id = "isekai_002_elf_king",
        displayName = "精灵王",
        rarity = CardRarity.SR,
        series = CardSeries.Isekai,
        artRes = R.drawable.isekai_002_elf_king,
        aspectRatio = 0.6760f,
    ),
    Hero(
        id = "isekai_003_hero",
        displayName = "勇者",
        rarity = CardRarity.SSR,
        series = CardSeries.Isekai,
        artRes = R.drawable.isekai_003_hero,
        aspectRatio = 0.6765f,
    ),
    BlackMage(
        id = "isekai_004_black_mage",
        displayName = "黑魔法师",
        rarity = CardRarity.SSSR,
        series = CardSeries.Isekai,
        artRes = R.drawable.isekai_004_black_mage,
        aspectRatio = 0.6849f,
    ),
    AncientDragon(
        id = "isekai_005_ancient_dragon",
        displayName = "古龙",
        rarity = CardRarity.XR,
        series = CardSeries.Isekai,
        artRes = R.drawable.isekai_005_ancient_dragon,
        aspectRatio = 0.6750f,
    ),
    SanguoTaoyuan(
        id = "sanguo_001_taoyuan_r",
        displayName = "桃园结义",
        rarity = CardRarity.R,
        series = CardSeries.Sanguo,
        artRes = R.drawable.sanguo_001_taoyuan_r,
        aspectRatio = 0.6618f,
    ),
    SanguoChangban(
        id = "sanguo_002_changban_sr",
        displayName = "长坂孤骑",
        rarity = CardRarity.SR,
        series = CardSeries.Sanguo,
        artRes = R.drawable.sanguo_002_changban_sr,
        aspectRatio = 0.6750f,
    ),
    SanguoKongcheng(
        id = "sanguo_003_kongcheng_ssr",
        displayName = "空城绝响",
        rarity = CardRarity.SSR,
        series = CardSeries.Sanguo,
        artRes = R.drawable.sanguo_003_kongcheng_ssr,
        aspectRatio = 0.6743f,
    ),
    SanguoChibi(
        id = "sanguo_004_chibi_sssr",
        displayName = "赤壁焚天",
        rarity = CardRarity.SSSR,
        series = CardSeries.Sanguo,
        artRes = R.drawable.sanguo_004_chibi_sssr,
        aspectRatio = 0.6792f,
    ),
    SanguoTianxia(
        id = "sanguo_005_tianxia_xr",
        displayName = "天下三分",
        rarity = CardRarity.XR,
        series = CardSeries.Sanguo,
        artRes = R.drawable.sanguo_005_tianxia_xr,
        aspectRatio = 0.6745f,
    ),
    ;

    companion object {
        fun fromId(id: String): GachaCard? = entries.firstOrNull { it.id == id }
    }
}

/** 首页卡堆保留的总弹数；尚未上线的弹仍显示为空位。 */
const val GACHA_SERIES_SLOTS = 5

/** 卡册以全局卡 id 记数；读取进度、满池和兑换资格时必须明确传入系列。 */
data class CardCollection(val counts: Map<String, Int> = emptyMap()) {

    fun countOf(card: GachaCard): Int = counts[card.id] ?: 0

    /** 所有系列一共持有的张数（含重复）。 */
    val totalCount: Int get() = counts.values.sum()

    fun totalCount(series: CardSeries): Int = series.cards.sumOf(::countOf)

    fun ownedKinds(series: CardSeries): Int = series.cards.count { countOf(it) > 0 }

    fun setSize(series: CardSeries): Int = series.cards.size

    val isEmpty: Boolean get() = totalCount == 0

    fun isSetComplete(series: CardSeries): Boolean = series.cards.all { countOf(it) > 0 }

    fun isFull(series: CardSeries): Boolean =
        series.cards.all { countOf(it) >= MAX_COPIES_PER_CARD }

    fun plus(card: GachaCard): CardCollection =
        copy(counts = counts + (card.id to countOf(card) + 1))

    /** 兑换时只清掉指定弹，其他弹以及未来版本暂不认识的 id 都原样保留。 */
    fun without(series: CardSeries): CardCollection {
        val ids = series.cards.mapTo(mutableSetOf()) { it.id }
        return copy(counts = counts.filterKeys { it !in ids })
    }

    companion object {
        val Empty = CardCollection()
    }
}
