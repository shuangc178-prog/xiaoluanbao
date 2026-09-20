package com.example.campuslife.data.card

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.random.Random

private val Context.cardCollectionDataStore: DataStore<Preferences> by
    preferencesDataStore(
        name = "card_collection",
        produceMigrations = { listOf(LegacyLastDrawMigration) },
    )

/**
 * 卡册的本地持久化。
 *
 * 卡数仍编码为 `卡 id|张数`。最后抽卡日编码为 `系列 id|epochDay`，用于保留用户选择
 * 的系列；所有系列共用每日一次机会。读取旧版单值 `last_draw_epoch_day` 时按第一弹处理，
 * 并在下一次写入时移除旧键。整套兑换奖励也放在这个 DataStore，保证「清卡 + 发奖励」
 * 是一次提交。
 */
class CardCollectionStore(private val context: Context) {

    val storedData: Flow<CardCollectionStoredData> =
        context.cardCollectionDataStore.data.map(::decodeStoredData)

    val collection: Flow<CardCollection> = storedData.map { it.collection }

    /** 各弹最后一次被选择的打卡日；每日门禁对全部值统一检查（凌晨 6 点换日）。 */
    val lastDrawEpochDays: Flow<Map<String, Long>> = storedData.map { it.lastDrawEpochDays }

    /**
     * 用磁盘里的最新状态完成一次抽卡；当天任意弹已抽过或所选弹满池时返回 null。
     *
     * 判断、随机选卡、加卡和推进抽卡日都在同一个 DataStore edit 中，冷启动或快速连点
     * 不能绕过每日门禁，也不能把单卡写到 2 张以上。
     */
    suspend fun tryDraw(
        series: CardSeries,
        todayEpochDay: Long,
        random: Random = Random.Default,
    ): StoredCardDraw? {
        var result: StoredCardDraw? = null
        context.cardCollectionDataStore.edit { preferences ->
            prepareStoredDraw(
                data = decodeStoredData(preferences),
                series = series,
                todayEpochDay = todayEpochDay,
                random = random,
            )?.let { draw ->
                preferences.writeStoredData(draw.data)
                result = draw
            }
        }
        return result
    }

    /**
     * 原子兑换指定弹：资格复核、清卡和增加休息卡奖励一次落盘，成功后才返回结果。
     */
    suspend fun tryExchange(series: CardSeries): StoredCardExchange? {
        var result: StoredCardExchange? = null
        context.cardCollectionDataStore.edit { preferences ->
            prepareStoredExchange(decodeStoredData(preferences), series)?.let { exchange ->
                preferences.writeStoredData(exchange.data)
                result = exchange
            }
        }
        return result
    }

    suspend fun snapshot(): CardCollectionStoredData {
        val preferences = context.cardCollectionDataStore.data.first()
        return decodeStoredData(preferences)
    }

    suspend fun replaceAll(data: CardCollectionStoredData) {
        context.cardCollectionDataStore.edit { preferences ->
            val counts = data.collection.counts
                .filterKeys { GachaCard.fromId(it) != null }
                .mapValues { (_, count) -> count.coerceIn(0, MAX_COPIES_PER_CARD) }
            val lastDraws = data.lastDrawEpochDays.filterKeys { CardSeries.fromId(it) != null }
            preferences.writeStoredData(
                CardCollectionStoredData(
                    collection = CardCollection(counts),
                    lastDrawEpochDays = lastDraws,
                    exchangeRestCards = data.exchangeRestCards.coerceAtLeast(0),
                ),
            )
        }
    }
}

data class CardCollectionStoredData(
    val collection: CardCollection = CardCollection.Empty,
    val lastDrawEpochDays: Map<String, Long> = emptyMap(),
    /** 新版整套兑换产生的休息卡；旧版 `check_in.bonus_rest_cards` 仍单独兼容读取。 */
    val exchangeRestCards: Int = 0,
)

data class StoredCardDraw(
    val card: GachaCard,
    val data: CardCollectionStoredData,
)

data class StoredCardExchange(
    val series: CardSeries,
    val rewardRestCards: Int,
    val data: CardCollectionStoredData,
)

internal fun prepareStoredDraw(
    data: CardCollectionStoredData,
    series: CardSeries,
    todayEpochDay: Long,
    random: Random,
): StoredCardDraw? {
    if (hasDrawnOn(data.lastDrawEpochDays, todayEpochDay)) return null
    val card = rollCard(data.collection, series, random) ?: return null
    return StoredCardDraw(
        card = card,
        data = data.copy(
            collection = data.collection.plus(card),
            lastDrawEpochDays = data.lastDrawEpochDays + (series.id to todayEpochDay),
        ),
    )
}

internal fun prepareStoredExchange(
    data: CardCollectionStoredData,
    series: CardSeries,
): StoredCardExchange? {
    if (!canExchangeCards(data.collection, series)) return null
    if (data.exchangeRestCards > Int.MAX_VALUE - EXCHANGE_REWARD_REST_CARDS) return null
    return StoredCardExchange(
        series = series,
        rewardRestCards = EXCHANGE_REWARD_REST_CARDS,
        data = data.copy(
            collection = data.collection.without(series),
            exchangeRestCards = data.exchangeRestCards + EXCHANGE_REWARD_REST_CARDS,
        ),
    )
}

private val CARD_COUNTS_KEY = stringSetPreferencesKey("card_counts")
private val LAST_DRAW_DAYS_KEY = stringSetPreferencesKey("last_draw_epoch_days")
private val LEGACY_LAST_DRAW_DAY_KEY = longPreferencesKey("last_draw_epoch_day")
private val EXCHANGE_REST_CARDS_KEY = intPreferencesKey("exchange_rest_cards")
private const val FIELD_SEPARATOR = "|"

private object LegacyLastDrawMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[LEGACY_LAST_DRAW_DAY_KEY] != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val migrated = decodeLastDrawEpochDays(currentData)
        return currentData.toMutablePreferences().apply {
            if (migrated.isEmpty()) {
                remove(LAST_DRAW_DAYS_KEY)
            } else {
                this[LAST_DRAW_DAYS_KEY] = migrated
                    .map { (seriesId, epochDay) -> encodeLastDraw(seriesId, epochDay) }
                    .toSet()
            }
            remove(LEGACY_LAST_DRAW_DAY_KEY)
        }
    }

    override suspend fun cleanUp() = Unit
}

private fun decodeCollection(preferences: Preferences): CardCollection =
    CardCollection(
        counts = preferences[CARD_COUNTS_KEY]
            .orEmpty()
            .mapNotNull(::decodeCount)
            .toMap(),
    )

private fun decodeStoredData(preferences: Preferences): CardCollectionStoredData =
    CardCollectionStoredData(
        collection = decodeCollection(preferences),
        lastDrawEpochDays = decodeLastDrawEpochDays(preferences),
        exchangeRestCards = (preferences[EXCHANGE_REST_CARDS_KEY] ?: 0).coerceAtLeast(0),
    )

private fun MutablePreferences.writeStoredData(data: CardCollectionStoredData) {
    val counts = data.collection.counts
    if (counts.isEmpty()) {
        remove(CARD_COUNTS_KEY)
    } else {
        this[CARD_COUNTS_KEY] = counts
            .map { (id, count) -> encodeCount(id, count) }
            .toSet()
    }

    if (data.lastDrawEpochDays.isEmpty()) {
        remove(LAST_DRAW_DAYS_KEY)
    } else {
        this[LAST_DRAW_DAYS_KEY] = data.lastDrawEpochDays
            .map { (seriesId, epochDay) -> encodeLastDraw(seriesId, epochDay) }
            .toSet()
    }

    if (data.exchangeRestCards > 0) {
        this[EXCHANGE_REST_CARDS_KEY] = data.exchangeRestCards
    } else {
        remove(EXCHANGE_REST_CARDS_KEY)
    }
    remove(LEGACY_LAST_DRAW_DAY_KEY)
}

internal fun decodeLastDrawEpochDays(preferences: Preferences): Map<String, Long> {
    return decodeLastDrawEpochDays(
        encoded = preferences[LAST_DRAW_DAYS_KEY].orEmpty(),
        legacyIsekaiEpochDay = preferences[LEGACY_LAST_DRAW_DAY_KEY],
    )
}

internal fun decodeLastDrawEpochDays(
    encoded: Set<String>,
    legacyIsekaiEpochDay: Long?,
): Map<String, Long> {
    val decoded = encoded.mapNotNull(::decodeLastDraw).toMap().toMutableMap()
    legacyIsekaiEpochDay?.let { legacy ->
        decoded.putIfAbsent(CardSeries.Isekai.id, legacy)
    }
    return decoded
}

private fun encodeCount(cardId: String, count: Int): String = "$cardId$FIELD_SEPARATOR$count"

private fun decodeCount(encoded: String): Pair<String, Int>? {
    val parts = encoded.split(FIELD_SEPARATOR)
    if (parts.size != 2) return null
    val id = parts[0].takeIf { GachaCard.fromId(it) != null } ?: return null
    val count = parts[1].toIntOrNull()?.coerceIn(0, MAX_COPIES_PER_CARD) ?: return null
    return id to count
}

private fun encodeLastDraw(seriesId: String, epochDay: Long): String =
    "$seriesId$FIELD_SEPARATOR$epochDay"

private fun decodeLastDraw(encoded: String): Pair<String, Long>? {
    val parts = encoded.split(FIELD_SEPARATOR)
    if (parts.size != 2) return null
    val seriesId = parts[0].takeIf { CardSeries.fromId(it) != null } ?: return null
    val epochDay = parts[1].toLongOrNull() ?: return null
    return seriesId to epochDay
}
