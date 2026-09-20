package com.example.campuslife.ui.checkin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.data.checkin.CheckInPhase
import com.example.campuslife.data.checkin.CheckInRecord
import com.example.campuslife.data.checkin.CheckInStore
import com.example.campuslife.data.checkin.averageCheckInTimes
import com.example.campuslife.data.checkin.checkInEpochDay
import com.example.campuslife.data.checkin.completedCheckInDays
import com.example.campuslife.data.checkin.earnedRestCards
import com.example.campuslife.data.checkin.formatMinuteOfDay
import com.example.campuslife.data.checkin.millisUntilNextCheckInDay
import com.example.campuslife.data.checkin.realCheckInDays
import com.example.campuslife.data.checkin.toMinuteOfDay
import com.example.campuslife.data.plan.DailyPlan
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * 打卡按钮需要的全部信息。
 *
 * [cumulativeDays] 只增不减：换日只把按钮恢复成无色，这个数字继续累加。
 * 它是「历史已打卡天数」——点了结束打卡的，加上换日时任务已全部勾完的
 * （完成全部任务后忘记点结束，由换日自动补记），口径见 [completedCheckInDays]。
 */
data class CheckInUiState(
    val phase: CheckInPhase = CheckInPhase.Idle,
    val cumulativeDays: Int = 0,
    val startLabel: String? = null,
    val endLabel: String? = null,
)

/**
 * 这一天是否已经「落定」到不能再改任务勾选。
 *
 * 只有**已结束打卡**才算数，进行中与未开始都不锁——用户在结束之前随时可以改主意。
 * 判定按打卡状态而不是去查记录：换日自动补记的那一天并没有写入记录，
 * 但当前打卡日的状态此时已经滚到新的一天，所以两种来源都不会漏。
 */
fun isCheckInDayLocked(phase: CheckInPhase): Boolean = phase == CheckInPhase.Finished

class CheckInController(
    val state: CheckInUiState,
    val records: List<CheckInRecord>,
    /** 当前归属的打卡日（凌晨 6 点换日），统计页算连续天数要用它做基准。 */
    val todayEpochDay: Long,
    /**
     * 当天是否已结束打卡。
     *
     * 为 true 时当天任务的勾选被锁住：**打卡一旦完成就不能再取消勾选**。
     * 锁在数据层（`DailyPlanController`）也有一道，界面只是把它显示成不可点。
     */
    val isTodayLocked: Boolean = false,
    val onStart: () -> Unit,
    val onFinish: () -> Unit,
    /**
     * 用一张休息卡把 [epochDay] 补成已打卡，起止时刻按历史平均值写入。
     *
     * 由打卡控制器统一负责写入：休息卡的库存与口径都长在打卡记录上，
     * 统计页只是它的一个视图，不该自己去动这份数据。
     */
    val onUseRestCard: (Long) -> Unit = {},
    /** 有新的休息卡还没看获得动效时为 true。 */
    val shouldRevealRestCard: Boolean = false,
    /** 收起获得动效：把"已读水位"抬到当前张数，同一张卡不再重复播。 */
    val onDismissRestCardReveal: () -> Unit = {},
    /** 旧版整套兑换写在 check_in 的休息卡张数；新版奖励由卡册存储原子记账。 */
    val bonusRestCards: Int = 0,
)

/**
 * 打卡状态持有者。
 *
 * 职责边界：这里只管「开始 / 结束」两个动作；换日的时钟在 [rememberCheckInEpochDay]；
 * 「必须勾完所有任务才能结束打卡」是首页的界面规则，由 TodayScreen 把关；
 * 「结束打卡之后不能再改任务」这道锁在 [DailyPlanController]（真正拦住写入的地方），
 * 这里只负责把 [CheckInController.isTodayLocked] 报出去供界面显示成不可点。
 */
@Composable
fun rememberCheckInController(
    todayEpochDay: Long,
    plans: List<DailyPlan>,
): CheckInController {
    val context = LocalContext.current
    val store = remember(context) { CheckInStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf<List<CheckInRecord>>(emptyList()) }
    var seenRestCardCount by remember { mutableIntStateOf(0) }
    var bonusRestCards by remember { mutableIntStateOf(0) }

    LaunchedEffect(store) {
        store.records.collect { loaded -> records = loaded }
    }
    LaunchedEffect(store) {
        store.seenRestCardCount.collect { seen -> seenRestCardCount = seen }
    }
    LaunchedEffect(store) {
        store.bonusRestCards.collect { bonus -> bonusRestCards = bonus }
    }

    val todayRecord = records.lastOrNull { it.epochDay == todayEpochDay }
    val phase = when {
        todayRecord == null -> CheckInPhase.Idle
        todayRecord.endMinuteOfDay == null -> CheckInPhase.Started
        else -> CheckInPhase.Finished
    }

    // 已获得的卡张数（纯推导），用于「有没有新卡要颁奖」。
    val earnedRestCardCount = earnedRestCards(realCheckInDays(records, plans, todayEpochDay)).size

    return CheckInController(
        state = CheckInUiState(
            phase = phase,
            cumulativeDays = completedCheckInDays(records, plans, todayEpochDay).size,
            startLabel = todayRecord?.startMinuteOfDay?.let(::formatMinuteOfDay),
            endLabel = todayRecord?.endMinuteOfDay?.let(::formatMinuteOfDay),
        ),
        records = records,
        todayEpochDay = todayEpochDay,
        // 换日自动补记的那一天没有记录，但那时当前打卡日已经滚到新的一天，
        // 所以只看当天记录就够了；状态判定统一走这一个纯函数。
        isTodayLocked = isCheckInDayLocked(phase),
        onStart = {
            if (phase == CheckInPhase.Idle) {
                val now = ZonedDateTime.now()
                scope.launch {
                    store.upsert(
                        CheckInRecord(
                            epochDay = now.checkInEpochDay(),
                            startMinuteOfDay = now.toMinuteOfDay(),
                        ),
                    )
                }
            }
        },
        onFinish = {
            val started = todayRecord
            if (phase == CheckInPhase.Started && started != null) {
                val now = ZonedDateTime.now()
                scope.launch {
                    store.upsert(started.copy(endMinuteOfDay = now.toMinuteOfDay()))
                }
            }
        },
        onUseRestCard = { epochDay ->
            scope.launch {
                // 平均时刻取「当前真实记录」的均值，补记完刷新一下就是最新的库存与曲线。
                store.fillWithRestCard(
                    epochDay = epochDay,
                    times = averageCheckInTimes(records),
                )
            }
        },
        shouldRevealRestCard = earnedRestCardCount > seenRestCardCount,
        onDismissRestCardReveal = {
            scope.launch { store.setSeenRestCardCount(earnedRestCardCount) }
        },
        bonusRestCards = bonusRestCards,
    )
}

/**
 * 当前打卡日（凌晨 6 点换日）。打卡与每日计划共用这一把尺子：
 * 到点把「今天」滚动到下一天，昨天任务全勾但没点结束的，就此自动落定为已打卡。
 */
@Composable
fun rememberCheckInEpochDay(): Long {
    var todayEpochDay by remember { mutableLongStateOf(ZonedDateTime.now().checkInEpochDay()) }

    // 到点自动换日：凌晨 6 点把按钮恢复成无色，累计天数不动。
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            todayEpochDay = now.checkInEpochDay()
            delay(now.millisUntilNextCheckInDay())
        }
    }

    return todayEpochDay
}
