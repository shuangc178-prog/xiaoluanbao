package com.example.campuslife.ui.card

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.R
import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardCollectionStoredData
import com.example.campuslife.data.card.CardCollectionStore
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.GACHA_SERIES_SLOTS
import com.example.campuslife.data.card.GachaAvailability
import com.example.campuslife.data.card.GachaBlockedReason
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.data.card.canExchangeCards
import com.example.campuslife.data.card.gachaAvailability
import kotlinx.coroutines.launch

/** 首页卡堆中的一弹；有 [cardSeries] 的弹拥有独立卡池和进度，并共享每日抽卡机会。 */
data class GachaSeriesSlot(
    val number: Int,
    val cardSeries: CardSeries? = null,
    @param:DrawableRes val coverRes: Int? = null,
    val availability: GachaAvailability = GachaAvailability.blocked(
        GachaBlockedReason.NotCheckedIn,
    ),
    val ownedKinds: Int = 0,
    val setSize: Int = 0,
)

/** 前两弹已上线，其余位置继续保留为空弹。 */
internal val DefaultGachaSeries: List<GachaSeriesSlot> = buildList {
    add(
        GachaSeriesSlot(
            number = CardSeries.Isekai.number,
            cardSeries = CardSeries.Isekai,
            coverRes = R.drawable.gacha_cover,
            setSize = CardSeries.Isekai.cards.size,
        ),
    )
    add(
        GachaSeriesSlot(
            number = CardSeries.Sanguo.number,
            cardSeries = CardSeries.Sanguo,
            coverRes = R.drawable.gacha_cover_sanguo,
            setSize = CardSeries.Sanguo.cards.size,
        ),
    )
    for (number in 3..GACHA_SERIES_SLOTS) add(GachaSeriesSlot(number = number))
}

/** 首页抽卡入口需要的各弹状态。 */
data class GachaEntryUiState(
    val series: List<GachaSeriesSlot> = DefaultGachaSeries,
)

/** 负责读写卡册，并把按弹计算出的状态与动作交给界面。 */
class CardController(
    val collection: CardCollection,
    val drawnCard: GachaCard?,
    val onDraw: (CardSeries) -> Unit,
    val onDismissReveal: () -> Unit,
    val onExchange: (CardSeries) -> Unit,
    val exchangeRestCards: Int = 0,
    private val lastDrawEpochDays: Map<String, Long> = emptyMap(),
    private val currentDayCompleted: Boolean = false,
    private val todayEpochDay: Long = 0L,
    private val dataLoaded: Boolean = true,
    private val mutationInFlight: Boolean = false,
    val justExchanged: Boolean = false,
    val onDismissExchange: () -> Unit = {},
) {
    val gachaEntry: GachaEntryUiState
        get() = GachaEntryUiState(
            series = DefaultGachaSeries.map { slot ->
                val series = slot.cardSeries ?: return@map slot
                slot.copy(
                    availability = availabilityFor(series),
                    ownedKinds = collection.ownedKinds(series),
                    setSize = collection.setSize(series),
                )
            },
        )

    fun ownedOf(card: GachaCard): Int = collection.countOf(card)

    fun availabilityFor(series: CardSeries): GachaAvailability = when {
        !currentDayCompleted -> GachaAvailability.blocked(GachaBlockedReason.NotCheckedIn)
        !dataLoaded -> GachaAvailability.blocked(GachaBlockedReason.Loading)
        mutationInFlight -> GachaAvailability.blocked(GachaBlockedReason.Processing)
        else -> gachaAvailability(
            currentDayCompleted = true,
            lastDrawEpochDays = lastDrawEpochDays,
            todayEpochDay = todayEpochDay,
            collection = collection,
            series = series,
        )
    }

    fun canExchange(series: CardSeries): Boolean =
        dataLoaded && !mutationInFlight && canExchangeCards(collection, series)
}

/** 所有弹每天共用一次机会；抽卡与兑换都等持久层原子提交成功后再更新界面。 */
@Composable
fun rememberCardController(
    currentDayCompleted: Boolean,
    todayEpochDay: Long,
): CardController {
    val context = LocalContext.current
    val store = remember(context) { CardCollectionStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var storedData by remember { mutableStateOf<CardCollectionStoredData?>(null) }
    var drawnCard by remember { mutableStateOf<GachaCard?>(null) }
    var justExchanged by remember { mutableStateOf(false) }
    var mutationInFlight by remember { mutableStateOf(false) }

    LaunchedEffect(store) {
        store.storedData.collect { loaded -> storedData = loaded }
    }

    val collection = storedData?.collection ?: CardCollection.Empty
    val lastDrawEpochDays = storedData?.lastDrawEpochDays.orEmpty()

    return CardController(
        collection = collection,
        drawnCard = drawnCard,
        onDraw = draw@{ series ->
            if (storedData == null || mutationInFlight) return@draw
            val availability = gachaAvailability(
                currentDayCompleted = currentDayCompleted,
                lastDrawEpochDays = lastDrawEpochDays,
                todayEpochDay = todayEpochDay,
                collection = collection,
                series = series,
            )
            if (availability.canDraw) {
                mutationInFlight = true
                scope.launch {
                    try {
                        store.tryDraw(series, todayEpochDay)?.let { result ->
                            storedData = result.data
                            drawnCard = result.card
                        }
                    } finally {
                        mutationInFlight = false
                    }
                }
            }
        },
        onDismissReveal = { drawnCard = null },
        onExchange = { series ->
            if (storedData != null && !mutationInFlight && canExchangeCards(collection, series)) {
                mutationInFlight = true
                scope.launch {
                    try {
                        store.tryExchange(series)?.let { result ->
                            storedData = result.data
                            justExchanged = true
                        }
                    } finally {
                        mutationInFlight = false
                    }
                }
            }
        },
        exchangeRestCards = storedData?.exchangeRestCards ?: 0,
        lastDrawEpochDays = lastDrawEpochDays,
        currentDayCompleted = currentDayCompleted,
        todayEpochDay = todayEpochDay,
        dataLoaded = storedData != null,
        mutationInFlight = mutationInFlight,
        justExchanged = justExchanged,
        onDismissExchange = { justExchanged = false },
    )
}
