package com.example.campuslife.ui.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.reminder.ExactAlarmCapability
import com.example.campuslife.data.reminder.NextAlarmOwnership
import com.example.campuslife.data.reminder.ReminderBootstrap
import com.example.campuslife.data.reminder.ReminderStatus
import com.example.campuslife.data.reminder.ReminderStatusReader
import com.example.campuslife.data.reminder.anchorReminderTriggerAtMillis
import com.example.campuslife.data.reminder.formatReminderMoment
import com.example.campuslife.data.reminder.isReminderMomentPast
import com.example.campuslife.ui.theme.claude
import kotlinx.coroutines.launch

/** 提示的语气：只影响配色，不改变要不要把这条话说出来。 */
enum class ReminderNoteTone { Ok, Warning, Muted }

/**
 * 提示行上那个按钮的落点。
 *
 * 每种系统设置各有各的页面，所以不能让动作跟着文案走——以前是拿 `"重新排入"` 去比字符串，
 * 文案改一个字就会把跳转改坏。文案只是 [label]，判据是这里的枚举。
 */
enum class ReminderNoteAction(val label: String) {
    /** 通知权限没开：去本应用的通知设置页。 */
    OpenNotificationSettings("去开启"),

    /** 精确闹钟没授权：去精确闹钟专页。 */
    OpenExactAlarmSettings("去授权"),

    /** 没给悬浮窗权限：去「显示在其他应用上层」那一页（MIUI 上叫「悬浮窗」）。 */
    OpenOverlaySettings("去开启"),

    /** 系统里的排程丢了：这一条不用跳转，应用自己就能重排。 */
    Reschedule("重新排入"),
}

/**
 * 提醒状态那一行要显示的内容。
 *
 * 拆成数据是为了让「什么情况该说什么话」能单测：真机上看一眼状态栏的成本太高，
 * 而这段话的每一种分支都对应一个具体的系统状态，说错了会直接误导用户。
 */
data class ReminderStatusNote(
    val label: String,
    val detail: String,
    val tone: ReminderNoteTone,
    val action: ReminderNoteAction? = null,
) {
    /** 按钮上的字；留着这个只读属性，文案判定就能在不认识枚举的测试里单独断言。 */
    val actionLabel: String? get() = action?.label
}

/**
 * 按「这条提醒的时刻 + 系统状态」决定提示哪一条。
 *
 * 判据顺序是刻意的：**先看这条提醒本身还成不成立，再看系统让不让它弹**。
 * 反过来会出现「通知权限没开，但这条提醒其实早就过期了」这种答非所问的提示。
 * 通知权限与外层状态也要排在「已排入下一条」之前——闹钟排上了但弹不出来，
 * 是比「排没排上」更靠前的失败点。
 */
fun buildReminderStatusNote(
    status: ReminderStatus?,
    triggerAtMillis: Long?,
    nowMillis: Long,
): ReminderStatusNote {
    if (status == null) {
        return ReminderStatusNote(
            label = "提醒",
            detail = "正在读取系统排程状态…",
            tone = ReminderNoteTone.Muted,
        )
    }

    if (triggerAtMillis != null && isReminderMomentPast(triggerAtMillis, nowMillis)) {
        return ReminderStatusNote(
            label = "时间已过",
            detail = "这个钟点已经过去了，不会再提醒。改到稍后的时间才会排入。",
            tone = ReminderNoteTone.Warning,
        )
    }

    val outsideNote = buildOutsideNote(status, nowMillis)
    if (outsideNote != null) return outsideNote

    // 走到这里系统侧没有已知问题。剩下的分支都要提到「下一条提醒」，
    // 但 Android 12 以下根本没有用户可见闹钟这一档，`getNextAlarmClock()` 永远读不到，
    // 此时不能对着 null 去格式化时刻（以前这里是 `!!`，会直接崩）。
    val nextAlarmAtMillis = status.nextAlarmAtMillis
        ?: return ReminderStatusNote(
            label = "准时",
            detail = "本机系统版本没有用户可见闹钟，提醒走精确闹钟排程；" +
                "到点会弹出通知，但无法在这里显示下一条的时间。",
            tone = ReminderNoteTone.Muted,
        )

    val nextAlarmDetail = "系统里已排入下一条提醒 · " +
        formatReminderMoment(nextAlarmAtMillis, nowMillis) +
        "，退到后台或划掉应用也会响。"
    return when {
        status.nextAlarmOwnership == NextAlarmOwnership.OtherApp -> ReminderStatusNote(
            label = "排程正常",
            detail = "提醒已排入；系统里最近的一条用户可见闹钟来自其它应用，所以这里显示的不是本条。",
            tone = ReminderNoteTone.Muted,
        )
        status.batteryOptimizationIgnored -> ReminderStatusNote(
            label = "准时",
            detail = "$nextAlarmDetail 已加入电池优化白名单。",
            tone = ReminderNoteTone.Ok,
        )
        else -> ReminderStatusNote(
            label = "准时",
            detail = "$nextAlarmDetail 但应用还没加入电池优化白名单，" +
                "少数系统仍可能在到点前把排程清掉。",
            tone = ReminderNoteTone.Muted,
        )
    }
}

/**
 * 「本日计划」页那一行常驻提示的内容。
 *
 * **只在系统侧真的有问题时才说话**（2026-09-19 用户口径：他是设计师，知道这个功能怎么用，
 * 不需要首页替他解释一遍）。所以「一切正常」不再返回那句
 * 「在任务上安排预期开始时间，到点会弹一次通知……」，而是返回 null —— 页面据此整行不占位置。
 *
 * 代价写在明处：原来附在这句话后面的「上一次「某任务」延迟 N 分钟」也跟着没有界面了。
 * 那是「提醒准不准」唯一的实测数字，数据仍在落库（[ReminderDelivery]），
 * 将来若要保留，得给它另找一个落点（任务弹窗或统计页）。
 */
fun buildGeneralReminderStatusNote(
    status: ReminderStatus?,
    nowMillis: Long,
): ReminderStatusNote? {
    // 状态还没读出来时也不占位置：首页不该为了「正在读取」先闪一行字。
    if (status == null) return null
    return buildOutsideNote(status, nowMillis)
}

/**
 * 系统侧让提醒弹不出来、或弹不准的原因，按「谁先失败」排。
 *
 * 返回 null 表示系统这一侧没有问题了。
 */
private fun buildOutsideNote(status: ReminderStatus, nowMillis: Long): ReminderStatusNote? {
    if (!status.notificationPermissionGranted) {
        return ReminderStatusNote(
            label = "通知未开启",
            detail = "到点不会弹出通知，任务上的时间仍会照常保存。",
            tone = ReminderNoteTone.Warning,
            action = ReminderNoteAction.OpenNotificationSettings,
        )
    }

    if (status.capability == ExactAlarmCapability.Missing) {
        return ReminderStatusNote(
            label = "可能延迟",
            detail = "系统未授予精确闹钟权限，到点提醒可能被推迟几分钟到十几分钟。",
            tone = ReminderNoteTone.Warning,
            action = ReminderNoteAction.OpenExactAlarmSettings,
        )
    }

    if (status.capability != ExactAlarmCapability.NotRequired &&
        status.nextAlarmOwnership == NextAlarmOwnership.None
    ) {
        return ReminderStatusNote(
            label = "未找到排程",
            detail = "系统里现在查不到本应用的提醒，可能被省电策略清理过。",
            tone = ReminderNoteTone.Warning,
            action = ReminderNoteAction.Reschedule,
        )
    }

    // 排在最后：前面几条决定「提醒会不会响、准不准」，这一条只决定「弹得好不好看」——
    // 少了它，提醒照样震三下、照样进通知栏，只是不浮卡片。
    if (!status.overlayAllowed) {
        return ReminderStatusNote(
            label = "不浮卡片",
            detail = "系统未允许本应用「显示在其他应用上层」：到点仍会震三下、通知栏留一条，" +
                "但不会浮出一张卡片压在正在用的应用上面。",
            tone = ReminderNoteTone.Warning,
            action = ReminderNoteAction.OpenOverlaySettings,
        )
    }

    return null
}

/**
 * 读取一次提醒状态。
 *
 * 触发时机是刻意选的两处：进入界面时读一次（此刻正要看状态），
 * 计划一变再读一次（用户刚动过排程，状态多半也变了）。
 * 不做轮询——它读的是系统状态与存档，不会自己变，多读只是白耗电。
 */
@Composable
internal fun rememberReminderStatus(refreshKey: Any?): ReminderStatus? {
    if (LocalInspectionMode.current) return null
    val context = LocalContext.current
    var status by remember { mutableStateOf<ReminderStatus?>(null) }
    LaunchedEffect(refreshKey) {
        status = ReminderStatusReader.read(context)
    }
    return status
}

/**
 * 弹窗里那一行提示。
 *
 * [checkInEpochDay] 是当前打卡日，锚定时要用它判断「这条任务的日期是不是只比打卡日晚一天」。
 * 返回 null 表示这一行不该出现，调用方据此不占位置。
 */
@Composable
internal fun rememberTaskReminderNote(
    status: ReminderStatus?,
    priorityId: String?,
    expectedStartMinute: Int?,
    epochDay: Long,
    checkInEpochDay: Long,
): ReminderStatusNote? {
    if (priorityId == null) return null
    // 进入弹窗那一刻取一次就够：用户等下改了钟点，会重算出一个新的时刻，
    // 而「现在几点」在这几秒里不会变。
    val now = remember(priorityId) { System.currentTimeMillis() }
    return remember(status, priorityId, expectedStartMinute, epochDay, checkInEpochDay, now) {
        if (status == null) return@remember null
        val triggerAt = expectedStartMinute?.let {
            anchorReminderTriggerAtMillis(
                epochDay = epochDay,
                minuteOfDay = it,
                nowMillis = now,
                checkInEpochDay = checkInEpochDay,
            )
        }
        buildReminderStatusNote(status = status, triggerAtMillis = triggerAt, nowMillis = now)
    }
}

/**
 * 任务表单之外的常驻提示。
 *
 * 只讲**系统侧**的问题（权限、排程丢失）与上一次弹出的实际延迟，
 * 不讲「这条提醒的时间已经过去」——那是用户在弹窗里挑钟点时的即时反馈；
 * 放到首页会因为时间流逝变得越来越不准，而它又不随任何状态变化重算。
 */
@Composable
internal fun rememberGeneralReminderNote(status: ReminderStatus?): ReminderStatusNote? {
    if (LocalInspectionMode.current) return null
    return remember(status) { buildGeneralReminderStatusNote(status, System.currentTimeMillis()) }
}

/** 提醒状态提示行；[note] 为空时不占位置，不让表单多出一行空话。 */
@Composable
internal fun ReminderStatusRow(
    note: ReminderStatusNote?,
    modifier: Modifier = Modifier,
    onOpenSystemSettings: ((ReminderNoteAction) -> Unit)? = null,
    onReschedule: (() -> Unit)? = null,
) {
    if (note == null) return

    val toneColor: Color = when (note.tone) {
        ReminderNoteTone.Ok -> MaterialTheme.claude.accentStrong
        ReminderNoteTone.Warning -> MaterialTheme.claude.amber
        ReminderNoteTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val actionLabel = note.actionLabel
    // 系统设置那几档都由同一个动作按枚举选页面（见 rememberOpenReminderSettings）；
    // 「重新排入」不是系统设置，是应用自己就能做的事。
    val action: (() -> Unit)? = when (val target = note.action) {
        null -> null
        ReminderNoteAction.Reschedule -> onReschedule
        else -> onOpenSystemSettings?.let { open -> { open(target) } }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = toneColor.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(toneColor),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = note.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = toneColor,
                )
                Text(
                    text = note.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (actionLabel != null && action != null) {
                TextButton(onClick = action) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * 提示行上那个按钮的统一跳转动作：按 [ReminderNoteAction] 去对应的系统页面。
 *
 * 各档设置都有专页，而**专页在不同机型上不一定存在**（有些 ROM 砍掉了精确闹钟专页），
 * 所以每一档都排了一条后路：专页 → 本应用通知页 → 应用详情页。
 * 挨个试比在应用里按厂商名去猜可靠；应用详情页一定存在，用户在那一页里能处理通知限制、
 * 电池优化与自启动，是最后的兜底。
 *
 * MIUI / HyperOS 上「悬浮通知」这个开关就在本应用的通知页里，所以横幅被系统收掉时，
 * 这条跳转也是用户唯一能自己救回来的入口。
 */
@Composable
internal fun rememberOpenReminderSettings(): (ReminderNoteAction) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { action ->
            val chain = when (action) {
                ReminderNoteAction.OpenNotificationSettings ->
                    listOf(notificationSettingsIntent(context))
                ReminderNoteAction.OpenExactAlarmSettings ->
                    listOfNotNull(
                        exactAlarmSettingsIntent(context),
                        // 精确闹钟页被砍掉的机型上，退到通知页让用户能处理通知限制与省电策略。
                        notificationSettingsIntent(context),
                    )
                ReminderNoteAction.OpenOverlaySettings ->
                    listOf(overlaySettingsIntent(context))
                // 「重新排入」不走系统设置；真走到这里也用应用详情页兜住，不至于点了没反应。
                ReminderNoteAction.Reschedule -> emptyList()
            }
            openFirstAvailable(context, chain + appDetailsIntent(context))
        }
    }
}

/** 依次尝试打开这些页面，成功一个就停。 */
private fun openFirstAvailable(context: Context, intents: List<Intent>) {
    for (intent in intents) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/** 本应用的通知设置页；「悬浮通知」「锁屏通知」这类开关都在这里。 */
private fun notificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** 精确闹钟专页，Android 12（API 31）起才有。 */
private fun exactAlarmSettingsIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        null
    }

/** 应用详情页：所有跳转的最终兜底，这一页一定存在。 */
private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * 「显示在其他应用上层」那一页。
 *
 * 同样要带 `package:` 数据，否则打开的是整机应用列表而不是本应用的开关。
 * MIUI / HyperOS 上这一页就是「悬浮窗」权限页。
 */
private fun overlaySettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** 手动「重新排入」：状态行上那个按钮的动作，排完顺手把状态读一遍。 */
@Composable
internal fun rememberRescheduleReminders(onFinished: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, onFinished) {
        {
            scope.launch {
                ReminderBootstrap.syncNow(context)
                onFinished()
            }
        }
    }
}
