package com.example.campuslife.ui.stats

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.EXCHANGE_REWARD_REST_CARDS
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.data.card.MAX_COPIES_PER_CARD
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInRecordKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统计页的收集分区与「兑换来的休息卡」。
 *
 * 两条容易写错的地方：
 * ① 卡册要在统计页铺满整套格子（没拿到的也占一格），否则「还差几种」算不出来；
 * ② 兑换换来的休息卡不是打卡攒的，推导不出来，必须靠单独记账并计入库存。
 */
class StatsCardCollectionTest {

    private val today = 20_000L

    private fun real(day: Long) = CheckInRecord(
        epochDay = day,
        startMinuteOfDay = 470,
        endMinuteOfDay = 650,
        kind = CheckInRecordKind.Real,
    )

    @Test
    fun slotsCoverTheWholeSetInCardOrder() {
        val state = buildStatsUiState(
            records = listOf(real(today)),
            todayEpochDay = today,
            ownedCards = CardCollection(mapOf(GachaCard.Unicorn.id to 1)),
        )
        assertEquals(CardSeries.Isekai.cards, state.collection.slots.map { it.card })
        assertEquals(1, state.collection.ownedKinds)
        assertEquals(5, state.collection.setSize)
        assertEquals(1, state.collection.totalCount)
        assertFalse(state.collection.isComplete)
        assertFalse(state.collection.canExchange)
    }

    @Test
    fun ownedAndFullFlagsFollowTheCounts() {
        val state = buildStatsUiState(
            records = listOf(real(today)),
            todayEpochDay = today,
            ownedCards = CardCollection(
                mapOf(
                    GachaCard.Unicorn.id to MAX_COPIES_PER_CARD,
                    GachaCard.Hero.id to 1,
                ),
            ),
        )
        val slots = state.collection.slots.associateBy { it.card }
        val unicorn = slots.getValue(GachaCard.Unicorn)
        assertTrue(unicorn.isOwned)
        assertTrue("拿到 2 张就是满", unicorn.isFull)
        assertEquals(2, unicorn.owned)

        val hero = slots.getValue(GachaCard.Hero)
        assertTrue(hero.isOwned)
        assertFalse(hero.isFull)

        val dragon = slots.getValue(GachaCard.AncientDragon)
        assertFalse("没拿到的格子也要占位", dragon.isOwned)
        assertEquals(0, dragon.owned)
    }

    @Test
    fun completeSetCanBeExchanged() {
        val state = buildStatsUiState(
            records = listOf(real(today)),
            todayEpochDay = today,
            ownedCards = CardCollection(CardSeries.Isekai.cards.associate { it.id to 1 }),
        )
        assertTrue(state.collection.isComplete)
        assertTrue(state.collection.canExchange)
        assertEquals(EXCHANGE_REWARD_REST_CARDS, state.collection.rewardRestCards)
    }

    @Test
    fun seriesSectionsKeepProgressAndExchangeStateIndependent() {
        val isekaiComplete = buildStatsUiState(
            records = listOf(real(today)),
            todayEpochDay = today,
            ownedCards = CardCollection(
                CardSeries.Isekai.cards.associate { it.id to 1 } +
                    mapOf(
                        GachaCard.SanguoTaoyuan.id to 2,
                        GachaCard.SanguoChangban.id to 1,
                    ),
            ),
        ).collections.associateBy { it.series }

        assertEquals(CardSeries.entries.toSet(), isekaiComplete.keys)
        with(isekaiComplete.getValue(CardSeries.Isekai)) {
            assertEquals(5, ownedKinds)
            assertEquals(5, totalCount)
            assertTrue(isComplete)
            assertTrue(canExchange)
        }
        with(isekaiComplete.getValue(CardSeries.Sanguo)) {
            assertEquals(2, ownedKinds)
            assertEquals(3, totalCount)
            assertFalse(isComplete)
            assertFalse(canExchange)
        }

        val sanguoComplete = buildStatsUiState(
            records = listOf(real(today)),
            todayEpochDay = today,
            ownedCards = CardCollection(
                CardSeries.Sanguo.cards.associate { it.id to 1 } +
                    mapOf(GachaCard.Unicorn.id to 2),
            ),
        ).collections.associateBy { it.series }

        with(sanguoComplete.getValue(CardSeries.Isekai)) {
            assertEquals(1, ownedKinds)
            assertEquals(2, totalCount)
            assertFalse(isComplete)
            assertFalse(canExchange)
        }
        with(sanguoComplete.getValue(CardSeries.Sanguo)) {
            assertEquals(5, ownedKinds)
            assertEquals(5, totalCount)
            assertTrue(isComplete)
            assertTrue(canExchange)
        }
    }

    @Test
    fun foldedCollectionSummaryAggregatesEverySeries() {
        val collections = buildStatsUiState(
            records = listOf(real(today)),
            todayEpochDay = today,
            ownedCards = CardCollection(
                CardSeries.Isekai.cards.associate { it.id to 1 } +
                    mapOf(
                        GachaCard.SanguoTaoyuan.id to 2,
                        GachaCard.SanguoChangban.id to 1,
                    ),
            ),
        ).collections

        val summary = summarizeCardCollections(collections)

        assertEquals(7, summary.ownedKinds)
        assertEquals(10, summary.setSize)
        assertEquals(1, summary.completedSeries)
        assertEquals(2, summary.seriesCount)
        assertEquals(1, summary.exchangeableSeries)
        assertEquals(0.7f, summary.progress)
    }

    @Test
    fun emptyCollectionSummaryHasZeroProgress() {
        val summary = summarizeCardCollections(emptyList())

        assertEquals(0, summary.seriesCount)
        assertEquals(0f, summary.progress)
    }

    @Test
    fun exchangedRestCardsJoinTheStock() {
        // 10 天连续真实打卡 → 打卡攒出 2 张休息卡。
        val records = (0 until 10).map { offset -> real(today - offset) }
        val before = buildStatsUiState(records = records, todayEpochDay = today)
        assertEquals(2, before.restCards.earned)
        assertEquals(2, before.restCards.available)
        assertEquals(0, before.restCards.fromExchange)

        // 兑换换来的 2 张记在另一个字段上，库存要算进去。
        val after = buildStatsUiState(
            records = records,
            todayEpochDay = today,
            bonusRestCards = EXCHANGE_REWARD_REST_CARDS,
        )
        assertEquals(2 + EXCHANGE_REWARD_REST_CARDS, after.restCards.earned)
        assertEquals(2 + EXCHANGE_REWARD_REST_CARDS, after.restCards.available)
        assertEquals(EXCHANGE_REWARD_REST_CARDS, after.restCards.fromExchange)
        // 兑换来的卡不影响「还差几天得下一张」的进度口径。
        assertEquals(before.restCards.daysToNext, after.restCards.daysToNext)
    }

    @Test
    fun usedRestCardsSpendTheExchangedStock() {
        // 补记过一天（休息卡用掉 1 张），再叠上兑换来的 2 张。
        val records = (0 until 10).map { offset ->
            if (offset == 1) {
                CheckInRecord(
                    epochDay = today - offset,
                    startMinuteOfDay = 470,
                    endMinuteOfDay = 650,
                    kind = CheckInRecordKind.RestCard,
                )
            } else {
                real(today - offset)
            }
        }
        val state = buildStatsUiState(
            records = records,
            todayEpochDay = today,
            bonusRestCards = EXCHANGE_REWARD_REST_CARDS,
        )
        // 真实连续段被补记的那天截成 8 天 + 1 天，只有前一段够 5 天 → 打卡只攒出 1 张。
        assertEquals(1 + EXCHANGE_REWARD_REST_CARDS, state.restCards.earned)
        assertEquals(1, state.restCards.used)
        assertEquals(EXCHANGE_REWARD_REST_CARDS, state.restCards.available)
        assertEquals(EXCHANGE_REWARD_REST_CARDS, state.restCards.fromExchange)
    }

    @Test
    fun collectionShowsEvenWithNoCheckInRecords() {
        // 一条打卡都没有时整页走空态，但卡册是另一份数据，不该跟着消失。
        val state = buildStatsUiState(
            records = emptyList(),
            todayEpochDay = today,
            ownedCards = CardCollection(mapOf(GachaCard.Hero.id to 1)),
        )
        assertEquals(0, state.completedDays)
        assertEquals(1, state.collection.ownedKinds)
        assertEquals(1, state.collection.totalCount)
    }
}
