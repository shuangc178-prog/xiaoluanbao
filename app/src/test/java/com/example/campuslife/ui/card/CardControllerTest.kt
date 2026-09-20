package com.example.campuslife.ui.card

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.GachaBlockedReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CardControllerTest {

    @Test
    fun completedDayStaysBlockedUntilThePersistedCardStateLoads() {
        val controller = controller(dataLoaded = false)

        val availability = controller.availabilityFor(CardSeries.Sanguo)

        assertFalse(availability.canDraw)
        assertEquals(GachaBlockedReason.Loading.hint, availability.hint)
        assertFalse(controller.canExchange(CardSeries.Sanguo))
    }

    @Test
    fun persistentMutationTemporarilyBlocksAllSeries() {
        val controller = controller(dataLoaded = true, mutationInFlight = true)

        CardSeries.entries.forEach { series ->
            val availability = controller.availabilityFor(series)
            assertFalse(availability.canDraw)
            assertEquals(GachaBlockedReason.Processing.hint, availability.hint)
            assertFalse(controller.canExchange(series))
        }
    }

    @Test
    fun drawingOneSeriesBlocksEverySeriesForTheDay() {
        val controller = controller(
            dataLoaded = true,
            lastDrawEpochDays = mapOf(CardSeries.Isekai.id to 20_000L),
        )

        CardSeries.entries.forEach { series ->
            val availability = controller.availabilityFor(series)
            assertFalse(availability.canDraw)
            assertEquals(GachaBlockedReason.AlreadyDrawn.hint, availability.hint)
        }
    }

    private fun controller(
        dataLoaded: Boolean,
        mutationInFlight: Boolean = false,
        lastDrawEpochDays: Map<String, Long> = emptyMap(),
    ) = CardController(
        collection = CardCollection.Empty,
        drawnCard = null,
        onDraw = {},
        onDismissReveal = {},
        onExchange = {},
        currentDayCompleted = true,
        todayEpochDay = 20_000L,
        lastDrawEpochDays = lastDrawEpochDays,
        dataLoaded = dataLoaded,
        mutationInFlight = mutationInFlight,
    )
}
