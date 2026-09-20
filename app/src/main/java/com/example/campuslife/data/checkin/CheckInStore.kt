package com.example.campuslife.data.checkin

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.checkInDataStore: DataStore<Preferences> by preferencesDataStore(name = "check_in")

/**
 * 打卡记录的本地持久化。
 *
 * 用 Preferences DataStore 而不是数据库：记录就是「一天一条」的小结构，
 * 编码成字符串放进一个 Set 里足够，也省掉建表与迁移。
 *
 * 编码格式：`epochDay|开始分钟|结束分钟|来源`，未结束时结束位为 `-`。
 * 第 4 段（来源）是后来加的：老记录只有 3 段，读到时按**真实打卡**处理，
 * 因此不需要写迁移。`R` = 休息卡补记，其余/缺失 = 真实打卡。
 */
class CheckInStore(private val context: Context) {

    val records: Flow<List<CheckInRecord>> = context.checkInDataStore.data.map { preferences ->
        preferences[RECORDS_KEY]
            .orEmpty()
            .mapNotNull(::decodeRecord)
            .sortedBy { it.epochDay }
    }

    /**
     * 上次已经"看过颁奖动效"的休息卡张数。
     *
     * 发卡是推导出来的，所以「什么时候该播获得动效」不能靠发卡本身判断——
     * 得记一个已读水位：库存第一次超过它时播一次，用户收起后把水位抬到当前张数。
     */
    val seenRestCardCount: Flow<Int> = context.checkInDataStore.data.map { preferences ->
        preferences[SEEN_REST_CARD_COUNT_KEY] ?: 0
    }

    suspend fun setSeenRestCardCount(count: Int) {
        context.checkInDataStore.edit { preferences ->
            preferences[SEEN_REST_CARD_COUNT_KEY] = count
        }
    }

    /**
     * 旧版本写在 `check_in` 的整套兑换奖励。
     *
     * 新兑换已改为与卡册同库原子记账；这里继续只读并参与库存合计，避免升级后丢失旧奖励。
     */
    val bonusRestCards: Flow<Int> = context.checkInDataStore.data.map { preferences ->
        preferences[BONUS_REST_CARDS_KEY] ?: 0
    }

    /** 同一天重复写入时覆盖旧值。 */
    suspend fun upsert(record: CheckInRecord) {
        context.checkInDataStore.edit { preferences ->
            val kept = preferences[RECORDS_KEY]
                .orEmpty()
                .filterNot { encodedEpochDay(it) == record.epochDay }
            preferences[RECORDS_KEY] = (kept + encodeRecord(record)).toSet()
        }
    }

    /**
     * 用休息卡把 [epochDay] 补成已打卡：起止时刻取历史平均值。
     *
     * 只覆盖「那天没有已完成的真实打卡」的情况——真实记录不该被奖励兑换改写。
     * 返回是否真的写入，供界面决定提示文案。
     */
    suspend fun fillWithRestCard(
        epochDay: Long,
        times: AverageCheckInTimes,
    ): Boolean {
        var written = false
        context.checkInDataStore.edit { preferences ->
            val encoded = preferences[RECORDS_KEY].orEmpty()
            val existing = encoded.mapNotNull(::decodeRecord).firstOrNull { it.epochDay == epochDay }
            if (existing != null && existing.isCompleted && !existing.isRestCardDay) return@edit
            preferences[RECORDS_KEY] = (
                encoded.filterNot { encodedEpochDay(it) == epochDay } +
                    encodeRecord(
                        CheckInRecord(
                            epochDay = epochDay,
                            startMinuteOfDay = times.startMinuteOfDay,
                            endMinuteOfDay = times.endMinuteOfDay,
                            kind = CheckInRecordKind.RestCard,
                        ),
                    )
                ).toSet()
            written = true
        }
        return written
    }

    /**
     * 云备份读取的是一份同一时刻的完整快照，不能分别订阅三个 Flow 再拼接，
     * 否则正好遇到写入时可能拿到前后两个版本。
     */
    suspend fun snapshot(): CheckInStoredData {
        val preferences = context.checkInDataStore.data.first()
        return CheckInStoredData(
            records = preferences[RECORDS_KEY]
                .orEmpty()
                .mapNotNull(::decodeRecord)
                .sortedBy { it.epochDay },
            seenRestCardCount = preferences[SEEN_REST_CARD_COUNT_KEY] ?: 0,
            bonusRestCards = preferences[BONUS_REST_CARDS_KEY] ?: 0,
        )
    }

    /** 整包恢复：只替换本模块认识的字段，写完后现有 Flow 会立即把新数据推给界面。 */
    suspend fun replaceAll(data: CheckInStoredData) {
        context.checkInDataStore.edit { preferences ->
            preferences[RECORDS_KEY] = data.records.map(::encodeRecord).toSet()
            preferences[SEEN_REST_CARD_COUNT_KEY] = data.seenRestCardCount.coerceAtLeast(0)
            preferences[BONUS_REST_CARDS_KEY] = data.bonusRestCards.coerceAtLeast(0)
        }
    }
}

data class CheckInStoredData(
    val records: List<CheckInRecord> = emptyList(),
    val seenRestCardCount: Int = 0,
    val bonusRestCards: Int = 0,
)

private val RECORDS_KEY = stringSetPreferencesKey("records")
private val SEEN_REST_CARD_COUNT_KEY =
    androidx.datastore.preferences.core.intPreferencesKey("seen_rest_card_count")
private val BONUS_REST_CARDS_KEY =
    androidx.datastore.preferences.core.intPreferencesKey("bonus_rest_cards")
private const val FIELD_SEPARATOR = "|"
private const val EMPTY_END = "-"
private const val KIND_REST_CARD = "R"
private const val KIND_REAL = "C"

private fun encodeRecord(record: CheckInRecord): String = listOf(
    record.epochDay.toString(),
    record.startMinuteOfDay.toString(),
    record.endMinuteOfDay?.toString() ?: EMPTY_END,
    if (record.isRestCardDay) KIND_REST_CARD else KIND_REAL,
).joinToString(FIELD_SEPARATOR)

internal fun decodeRecord(encoded: String): CheckInRecord? {
    val parts = encoded.split(FIELD_SEPARATOR)
    // 3 段是老格式（没有来源），4 段是现在；其余一律当作畸形数据丢弃。
    if (parts.size != 3 && parts.size != 4) return null
    val epochDay = parts[0].toLongOrNull() ?: return null
    val start = parts[1].toIntOrNull() ?: return null
    val end = if (parts[2] == EMPTY_END) null else parts[2].toIntOrNull() ?: return null
    val kind = if (parts.size == 4 && parts[3] == KIND_REST_CARD) {
        CheckInRecordKind.RestCard
    } else {
        CheckInRecordKind.Real
    }
    return CheckInRecord(
        epochDay = epochDay,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        kind = kind,
    )
}

private fun encodedEpochDay(encoded: String): Long? =
    encoded.substringBefore(FIELD_SEPARATOR).toLongOrNull()
