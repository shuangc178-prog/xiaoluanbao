package com.example.campuslife.data.card

import kotlin.random.Random

/** 每种卡最多能拿到几张。 */
const val MAX_COPIES_PER_CARD = 2

/** 所有已上线弹共用的每日抽卡次数（机会不累积）。 */
const val DRAWS_PER_DAY = 1

/** 整套兑换的回报：2 张休息卡。 */
const val EXCHANGE_REWARD_REST_CARDS = 2

/**
 * 从指定 [series] 抽一张卡。
 *
 * 已满两张的卡先退出卡池，再按剩余稀有度权重重新归一；当前弹全满时返回 null。
 */
fun rollCard(
    collection: CardCollection,
    series: CardSeries,
    random: Random,
): GachaCard? {
    val available = series.cards.filter { collection.countOf(it) < MAX_COPIES_PER_CARD }
    if (available.isEmpty()) return null

    val candidates = available.groupBy { it.rarity }.toSortedMap(compareBy { it.ordinal })
    val totalWeight = candidates.keys.sumOf { it.weight }
    var point = random.nextInt(totalWeight)
    val picked = candidates.keys.first { rarity ->
        point -= rarity.weight
        point < 0
    }
    val pool = candidates.getValue(picked)
    return pool[random.nextInt(pool.size)]
}

/** 今天抽不了当前弹的原因。 */
enum class GachaBlockedReason(val hint: String) {
    Loading("加载中"),
    Processing("处理中"),
    NotCheckedIn("打卡后可抽"),
    AlreadyDrawn("今日已抽"),
    PoolComplete("已集满"),
}

data class GachaAvailability(
    val canDraw: Boolean,
    val hint: String? = null,
) {
    companion object {
        val Ready = GachaAvailability(canDraw = true)

        fun blocked(reason: GachaBlockedReason) =
            GachaAvailability(canDraw = false, hint = reason.hint)
    }
}

/**
 * 当前弹今天是否可以抽取。
 *
 * [lastDrawEpochDays] 保留最后抽取的系列，但所有系列共用一次每日机会：当天任意一弹
 * 已经抽过，其余弹也一起锁定。
 */
fun gachaAvailability(
    currentDayCompleted: Boolean,
    lastDrawEpochDays: Map<String, Long>,
    todayEpochDay: Long,
    collection: CardCollection,
    series: CardSeries,
): GachaAvailability = when {
    !currentDayCompleted -> GachaAvailability.blocked(GachaBlockedReason.NotCheckedIn)
    hasDrawnOn(lastDrawEpochDays, todayEpochDay) ->
        GachaAvailability.blocked(GachaBlockedReason.AlreadyDrawn)
    collection.isFull(series) -> GachaAvailability.blocked(GachaBlockedReason.PoolComplete)
    else -> GachaAvailability.Ready
}

internal fun hasDrawnOn(lastDrawEpochDays: Map<String, Long>, epochDay: Long): Boolean =
    lastDrawEpochDays.values.any { it == epochDay }

/** 每种卡都至少有一张时，该弹可以兑换。 */
fun canExchangeCards(collection: CardCollection, series: CardSeries): Boolean =
    collection.isSetComplete(series)
