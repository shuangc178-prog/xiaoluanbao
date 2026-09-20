package com.example.campuslife.data.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GachaRuleTest {

    private val today = 20_000L

    @Test
    fun weightsAddUpToHundred() {
        assertEquals(100, CardRarity.entries.sumOf { it.weight })
    }

    @Test
    fun bothSeriesHaveOneCardAtEveryRarity() {
        CardSeries.entries.forEach { series ->
            assertEquals(CardRarity.entries.toSet(), series.cards.map { it.rarity }.toSet())
            assertEquals(5, series.cards.size)
        }
        assertEquals(10, GachaCard.entries.size)
    }

    @Test
    fun emptySeriesFollowsTheWeights() {
        val counts = drawMany(CardCollection.Empty, CardSeries.Sanguo, seed = 20260918)
        assertNear(0.45, counts, CardRarity.R)
        assertNear(0.28, counts, CardRarity.SR)
        assertNear(0.17, counts, CardRarity.SSR)
        assertNear(0.08, counts, CardRarity.SSSR)
        assertNear(0.02, counts, CardRarity.XR)
    }

    @Test
    fun aCardStopsDroppingAfterTwoCopies() {
        val fullCard = GachaCard.SanguoTaoyuan
        val album = CardCollection(mapOf(fullCard.id to MAX_COPIES_PER_CARD))
        repeat(2_000) { seed ->
            assertFalse(fullCard == rollCard(album, CardSeries.Sanguo, Random(seed)))
        }
    }

    @Test
    fun exhaustedRaritySpreadsItsWeightOnTheRest() {
        val album = CardCollection(
            mapOf(GachaCard.SanguoTaoyuan.id to MAX_COPIES_PER_CARD),
        )
        val counts = drawMany(album, CardSeries.Sanguo, seed = 11)
        assertNear(28.0 / 55, counts, CardRarity.SR)
        assertNear(17.0 / 55, counts, CardRarity.SSR)
        assertNear(8.0 / 55, counts, CardRarity.SSSR)
        assertNear(2.0 / 55, counts, CardRarity.XR)
    }

    @Test
    fun otherSeriesInventoryDoesNotAffectThisPool() {
        val isekaiFull = CardSeries.Isekai.cards.associate { it.id to MAX_COPIES_PER_CARD }
        repeat(100) { seed ->
            assertEquals(
                CardSeries.Sanguo,
                rollCard(CardCollection(isekaiFull), CardSeries.Sanguo, Random(seed))?.series,
            )
        }
    }

    @Test
    fun fullSeriesHasNothingToDrawWithoutBlockingTheOtherSeries() {
        val sanguoFull = CardSeries.Sanguo.cards.associate { it.id to MAX_COPIES_PER_CARD }
        val album = CardCollection(sanguoFull)
        assertNull(rollCard(album, CardSeries.Sanguo, Random(1)))
        assertEquals(
            CardSeries.Isekai,
            rollCard(album, CardSeries.Isekai, Random(1))?.series,
        )
    }

    @Test
    fun drawNeedsTheDayToBeFinished() {
        val blocked = gachaAvailability(
            currentDayCompleted = false,
            lastDrawEpochDays = emptyMap(),
            todayEpochDay = today,
            collection = CardCollection.Empty,
            series = CardSeries.Isekai,
        )
        assertFalse(blocked.canDraw)
        assertEquals(GachaBlockedReason.NotCheckedIn.hint, blocked.hint)
    }

    @Test
    fun allSeriesShareOneDailyDraw() {
        val isekaiDrawn = mapOf(CardSeries.Isekai.id to today)
        assertFalse(
            gachaAvailability(true, isekaiDrawn, today, CardCollection.Empty, CardSeries.Isekai)
                .canDraw,
        )
        assertFalse(
            gachaAvailability(true, isekaiDrawn, today, CardCollection.Empty, CardSeries.Sanguo)
                .canDraw,
        )
    }

    @Test
    fun fullPoolOnlyBlocksItsOwnSeries() {
        val album = CardCollection(
            CardSeries.Sanguo.cards.associate { it.id to MAX_COPIES_PER_CARD },
        )
        assertFalse(
            gachaAvailability(true, emptyMap(), today, album, CardSeries.Sanguo).canDraw,
        )
        assertTrue(
            gachaAvailability(true, emptyMap(), today, album, CardSeries.Isekai).canDraw,
        )
    }

    @Test
    fun exchangeNeedsEveryCardInTheSelectedSeries() {
        val isekaiComplete = CardCollection(CardSeries.Isekai.cards.associate { it.id to 1 })
        assertTrue(canExchangeCards(isekaiComplete, CardSeries.Isekai))
        assertFalse(canExchangeCards(isekaiComplete, CardSeries.Sanguo))

        val sanguoIncomplete = CardCollection(
            CardSeries.Sanguo.cards.dropLast(1).associate { it.id to MAX_COPIES_PER_CARD },
        )
        assertFalse(canExchangeCards(sanguoIncomplete, CardSeries.Sanguo))
    }

    @Test
    fun removingOneSeriesPreservesTheOther() {
        val album = CardCollection(
            mapOf(
                GachaCard.Unicorn.id to 2,
                GachaCard.SanguoTaoyuan.id to 1,
                "future_card" to 1,
            ),
        )
        val remaining = album.without(CardSeries.Isekai)

        assertEquals(0, remaining.countOf(GachaCard.Unicorn))
        assertEquals(1, remaining.countOf(GachaCard.SanguoTaoyuan))
        assertEquals(1, remaining.counts["future_card"])
    }

    @Test
    fun collectionProgressIsScopedToSeries() {
        val album = CardCollection(
            mapOf(
                GachaCard.Unicorn.id to 2,
                GachaCard.Hero.id to 1,
                GachaCard.SanguoTaoyuan.id to 1,
            ),
        )
        assertEquals(3, album.totalCount(CardSeries.Isekai))
        assertEquals(2, album.ownedKinds(CardSeries.Isekai))
        assertEquals(1, album.totalCount(CardSeries.Sanguo))
        assertEquals(1, album.ownedKinds(CardSeries.Sanguo))
        assertEquals(4, album.totalCount)
        assertEquals(5, album.setSize(CardSeries.Sanguo))
    }

    @Test
    fun legacyDrawDayMigratesToIsekaiWithoutOverwritingNewData() {
        assertEquals(
            mapOf(CardSeries.Isekai.id to today),
            decodeLastDrawEpochDays(emptySet(), legacyIsekaiEpochDay = today),
        )

        val decoded = decodeLastDrawEpochDays(
            encoded = setOf(
                "isekai|${today + 1}",
                "sanguo|${today + 2}",
                "unknown|${today + 3}",
                "broken",
            ),
            legacyIsekaiEpochDay = today,
        )
        assertEquals(
            mapOf(
                CardSeries.Isekai.id to today + 1,
                CardSeries.Sanguo.id to today + 2,
            ),
            decoded,
        )
    }

    @Test
    fun storedDrawRechecksThePersistedDayAndCardCap() {
        val onlyRemaining = GachaCard.SanguoChangban
        val counts = CardSeries.Sanguo.cards.associate { card ->
            card.id to if (card == onlyRemaining) 1 else MAX_COPIES_PER_CARD
        }
        val data = CardCollectionStoredData(collection = CardCollection(counts))

        val drawn = prepareStoredDraw(data, CardSeries.Sanguo, today, Random(1))!!

        assertEquals(onlyRemaining, drawn.card)
        assertEquals(MAX_COPIES_PER_CARD, drawn.data.collection.countOf(onlyRemaining))
        assertEquals(today, drawn.data.lastDrawEpochDays[CardSeries.Sanguo.id])
        assertNull(
            prepareStoredDraw(drawn.data, CardSeries.Sanguo, today, Random(2)),
        )
        assertNull(
            prepareStoredDraw(drawn.data, CardSeries.Isekai, today, Random(3)),
        )
    }

    @Test
    fun storedExchangeClearsOnlyOneSeriesAndCreditsRewardOnce() {
        val data = CardCollectionStoredData(
            collection = CardCollection(
                CardSeries.Sanguo.cards.associate { it.id to 1 } +
                    mapOf(GachaCard.Unicorn.id to 2),
            ),
            exchangeRestCards = 4,
        )

        val exchanged = prepareStoredExchange(data, CardSeries.Sanguo)!!

        assertEquals(EXCHANGE_REWARD_REST_CARDS, exchanged.rewardRestCards)
        assertEquals(6, exchanged.data.exchangeRestCards)
        assertEquals(0, exchanged.data.collection.totalCount(CardSeries.Sanguo))
        assertEquals(2, exchanged.data.collection.countOf(GachaCard.Unicorn))
        assertNull(prepareStoredExchange(exchanged.data, CardSeries.Sanguo))
    }

    private fun drawMany(
        album: CardCollection,
        series: CardSeries,
        seed: Int,
        times: Int = 20_000,
    ): Map<CardRarity, Int> {
        val random = Random(seed)
        val counts = mutableMapOf<CardRarity, Int>()
        repeat(times) {
            val card = rollCard(album, series, random)!!
            counts[card.rarity] = (counts[card.rarity] ?: 0) + 1
        }
        return counts
    }

    private fun assertNear(expected: Double, counts: Map<CardRarity, Int>, rarity: CardRarity) {
        val total = counts.values.sum().toDouble()
        val actual = (counts[rarity] ?: 0) / total
        assertTrue(kotlin.math.abs(actual - expected) < 0.03)
    }
}
