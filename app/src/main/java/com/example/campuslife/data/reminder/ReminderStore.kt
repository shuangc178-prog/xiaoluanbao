package com.example.campuslife.data.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder")

/** 已弹过通知的记录最长保留 30 天，之后清掉，避免存档无限增长。 */
private const val DELIVERED_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

/** 弹出实况只留最近这些条：它的用途是「刚才那次准不准」，攒多了只会把 DataStore 撑大。 */
private const val DELIVERY_LOG_LIMIT = 16

/**
 * 任务预期开始时间提醒的调度簿记。
 *
 * 日历事件已经回滚，这里不再存事件本身，只留三份调度用的账本：
 * - 已提醒 token：通知弹过一次就记下来，**绝不重复弹**；
 * - 已排闹钟码：AlarmManager 里的闹钟在进程重启后仍然存在，需要这份记录才能取消已经作废的那些；
 * - 弹出实况：计划时刻与实际弹出时刻的差值，也就是「到底准不准时」的唯一证据。
 *
 * 弹出实况的格式是 `计划时刻|实际弹出时刻|标题`，按落库顺序排列。标题里可能带 `|`，
 * 所以解析时只按前两个分隔符切，剩下的整段都算标题。
 */
class ReminderStore(private val context: Context) {

    suspend fun deliveredTokens(): Set<String> =
        context.reminderDataStore.data.first()[DELIVERED_KEY].orEmpty()

    /**
     * 记下「这条提醒已经弹过通知」，顺手清掉过期的记录。
     *
     * [scheduledAtMillis] 从 token 里取得（token 的第一段就是排程时的触发时刻），
     * 与 [deliveredAtMillis] 一起构成这次弹出的实际延迟。
     */
    suspend fun markDelivered(
        token: String,
        scheduledAtMillis: Long,
        deliveredAtMillis: Long,
        title: String,
    ) {
        context.reminderDataStore.edit { preferences ->
            val cutoff = deliveredAtMillis - DELIVERED_RETENTION_MILLIS
            val kept = preferences[DELIVERED_KEY]
                .orEmpty()
                .filter { entry ->
                    val at = entry.substringBefore('|').toLongOrNull()
                    at == null || at >= cutoff
                }
            preferences[DELIVERED_KEY] = kept.toSet() + token
            // 日志用「一个字符串 + 换行分隔」而不是字符串集合：DataStore 的字符串集合
            // **不保证顺序**，而这里要的正是「最近一条」，无序就等于读不出来。
            // 换行不会出现在通知标题里（标题来自任务名，单行输入），所以不需要转义。
            preferences[DELIVERY_LOG_KEY] = (
                preferences[DELIVERY_LOG_KEY].orEmpty()
                    .split(DELIVERY_SEPARATOR)
                    .filter { it.isNotBlank() } +
                    encodeReminderDelivery(scheduledAtMillis, deliveredAtMillis, title)
                ).takeLast(DELIVERY_LOG_LIMIT).joinToString(DELIVERY_SEPARATOR)
        }
    }

    /** 最近一次弹出的实况；一条都没有时返回 null。 */
    suspend fun lastDelivery(): ReminderDelivery? =
        context.reminderDataStore.data.first()[DELIVERY_LOG_KEY]
            .orEmpty()
            .split(DELIVERY_SEPARATOR)
            .mapNotNull(::decodeReminderDelivery)
            .lastOrNull()

    suspend fun scheduledRequestCodes(): Set<Int> =
        context.reminderDataStore.data.first()[SCHEDULED_CODES_KEY]
            .orEmpty()
            .mapNotNull(String::toIntOrNull)
            .toSet()

    suspend fun saveScheduledRequestCodes(codes: Set<Int>) {
        context.reminderDataStore.edit { preferences ->
            preferences[SCHEDULED_CODES_KEY] = codes.map(Int::toString).toSet()
        }
    }

    /**
     * 恢复到另一台设备后，旧设备的 AlarmManager 码与“已经弹过”的 token 都没有意义。
     * 清空后由 ReminderBootstrap 按恢复出来的计划重新排程。
     */
    suspend fun resetAfterDataRestore() {
        context.reminderDataStore.edit { preferences ->
            preferences.remove(DELIVERED_KEY)
            preferences.remove(SCHEDULED_CODES_KEY)
            preferences.remove(DELIVERY_LOG_KEY)
        }
    }
}

/** `计划时刻|实际弹出时刻|标题`，多条之间用换行分隔并保持先后顺序。 */
internal const val DELIVERY_SEPARATOR = "\n"

/** `计划时刻|实际弹出时刻|标题`。标题放最后，所以里面带 `|` 也不会解析错。 */
internal fun encodeReminderDelivery(
    scheduledAtMillis: Long,
    deliveredAtMillis: Long,
    title: String,
): String = "$scheduledAtMillis|$deliveredAtMillis|$title"

/** 解析不出来的记录返回 null，由调用方跳过——存档坏了不该让整个提醒功能停摆。 */
internal fun decodeReminderDelivery(encoded: String): ReminderDelivery? {
    val firstSeparator = encoded.indexOf('|')
    if (firstSeparator <= 0) return null
    val secondSeparator = encoded.indexOf('|', startIndex = firstSeparator + 1)
    if (secondSeparator <= firstSeparator) return null
    val scheduled = encoded.substring(0, firstSeparator).toLongOrNull() ?: return null
    val delivered = encoded.substring(firstSeparator + 1, secondSeparator).toLongOrNull() ?: return null
    return ReminderDelivery(
        triggerAtMillis = scheduled,
        deliveredAtMillis = delivered,
        title = encoded.substring(secondSeparator + 1),
    )
}

private val DELIVERED_KEY = stringSetPreferencesKey("delivered_tokens")
private val SCHEDULED_CODES_KEY = stringSetPreferencesKey("scheduled_alarm_codes")
private val DELIVERY_LOG_KEY = stringPreferencesKey("delivery_log")
