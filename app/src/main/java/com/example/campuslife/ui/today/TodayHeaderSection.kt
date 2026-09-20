package com.example.campuslife.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import com.example.campuslife.R
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.checkin.CheckInPhase
import com.example.campuslife.ui.card.GachaEntryUiState
import com.example.campuslife.ui.card.GachaSeriesStack
import com.example.campuslife.ui.checkin.CheckInUiState
import com.example.campuslife.ui.theme.claude

/**
 * 首页顶部：左侧时钟与日期，右侧主题切换气泡和打卡按钮。
 *
 * 抽卡入口排在主题切换与打卡那一行的**下方**，同一列里，靠右对齐——
 * 它要等当天打卡完成才亮，所以放在打卡按钮旁边最能说明「为什么现在点不了」。
 */
@Composable
internal fun TodayHeader(
    timeLabel: String,
    dateLabel: String,
    checkInState: CheckInUiState,
    allTasksCompleted: Boolean,
    hasTasks: Boolean,
    isDarkTheme: Boolean,
    isCloudBackupConnected: Boolean,
    onToggleTheme: () -> Unit,
    onCheckInClick: () -> Unit,
    onOpenPlanArchive: () -> Unit,
    onOpenDataBackup: () -> Unit,
    modifier: Modifier = Modifier,
    gachaState: GachaEntryUiState = GachaEntryUiState(),
    onGachaClick: (CardSeries) -> Unit = {},
) {
    // 主题切换 + 打卡那一行实际有多宽。它就是这一列的宽度上限，也是那叠卡的宽度上限：
    // 按它铺，后面的弹才会往左探出去，而不是把这一列撑宽、把时钟挤走。
    var checkInRowWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // 预留 1dp：实测值四舍五入后如果刚好等于整行宽，这一列会比上面那行宽一丝，
    // 布局就又开始抢时钟那边的空间了。
    val gachaStackWidth = with(density) { checkInRowWidthPx.toDp() } - 1.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = dateLabel,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlanArchiveButton(
                onClick = onOpenPlanArchive,
                modifier = Modifier.padding(top = 10.dp),
            )
            DataBackupButton(
                onClick = onOpenDataBackup,
                isConnected = isCloudBackupConnected,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.onSizeChanged { checkInRowWidthPx = it.width },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeToggleButton(
                    isDarkTheme = isDarkTheme,
                    onClick = onToggleTheme,
                )
                CheckInButton(
                    checkInState = checkInState,
                    allTasksCompleted = allTasksCompleted,
                    hasTasks = hasTasks,
                    onClick = onCheckInClick,
                )
            }
            val checkInHint = when {
                checkInState.phase == CheckInPhase.Idle && !hasTasks -> "添加今天的任务后可打卡"
                checkInState.phase == CheckInPhase.Started && !allTasksCompleted -> "勾完全部任务后可结束"
                else -> null
            }
            if (checkInHint != null) {
                Text(
                    text = checkInHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 抽卡入口：打卡那一行的正下方，一叠卡，最上面那张是当前这一弹。
            // 左右滑动切换弹；第几弹标在卡面右上角（见 GachaSeriesStack 的排版说明）。
            GachaSeriesStack(
                series = gachaState.series,
                onClick = onGachaClick,
                stackWidth = gachaStackWidth,
            )
        }
    }
}

/** 坚果云自动备份与恢复入口；与计划存档并列但使用独立语义，避免误以为是同一份存档。 */
@Composable
private fun DataBackupButton(
    onClick: () -> Unit,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
) {
    val connectedColor = MaterialTheme.claude.green
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) connectedColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        contentColor = if (isConnected) connectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (isConnected) connectedColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.nutstore_icon),
                contentDescription = "备份与恢复",
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "备份与恢复",
                style = MaterialTheme.typography.labelMedium,
                color = if (isConnected) connectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isConnected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/**
 * 每日计划存档入口：日期下方的小气泡。
 *
 * 用图标 + 文字的小圆角气泡，不占首屏多余高度；点进去按周回看每天的计划快照。
 */
@Composable
private fun PlanArchiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = "每日计划存档",
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = "每日计划存档",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 主题切换气泡：明色主题挂天使，暗色主题挂恶魔。
 *
 * 做成只放图标的小圆角气泡，不占首屏多余高度；点一下就在明暗之间切换。
 */
@Composable
internal fun ThemeToggleButton(
    isDarkTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Image(
            painter = painterResource(
                id = if (isDarkTheme) R.drawable.ic_theme_dark else R.drawable.ic_theme_light,
            ),
            contentDescription = if (isDarkTheme) "切换到亮色主题" else "切换到暗色主题",
            modifier = Modifier
                .padding(5.dp)
                .size(22.dp),
        )
    }
}

/**
 * 打卡按钮，沿用原来的日历气泡：日历图标 + 一行文字，右侧跟累计天数。
 *
 * 三个状态：
 * - 无色：今天还没开始打卡；任务栏为空时不可开始（防止「没做任务也打卡」的错误统计）
 * - 红色：已开始，勾完全部任务后才能结束
 * - 绿色：今天已结束，不再可点
 */
@Composable
internal fun CheckInButton(
    checkInState: CheckInUiState,
    allTasksCompleted: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = checkInState.phase
    val canStart = phase == CheckInPhase.Idle && hasTasks
    val canFinish = phase == CheckInPhase.Started && allTasksCompleted
    val enabled = canStart || canFinish

    val containerColor = when (phase) {
        CheckInPhase.Idle -> MaterialTheme.colorScheme.surfaceVariant
        CheckInPhase.Started -> MaterialTheme.claude.accentSoft
        CheckInPhase.Finished -> MaterialTheme.claude.greenSoft
    }
    val contentColor = when (phase) {
        CheckInPhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        CheckInPhase.Started -> MaterialTheme.claude.accentStrong
        CheckInPhase.Finished -> MaterialTheme.claude.green
    }
    val borderColor = when (phase) {
        CheckInPhase.Idle -> MaterialTheme.colorScheme.outlineVariant
        CheckInPhase.Started -> MaterialTheme.claude.accent.copy(alpha = 0.32f)
        CheckInPhase.Finished -> MaterialTheme.claude.green.copy(alpha = 0.32f)
    }
    val label = when {
        phase == CheckInPhase.Finished -> "已打卡"
        phase == CheckInPhase.Started -> "进行中"
        else -> "打卡"
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (canFinish) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (phase == CheckInPhase.Finished) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.DateRange
                },
                contentDescription = "打卡",
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${checkInState.cumulativeDays} 天",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CheckInDialog(
    onDismiss: () -> Unit,
    onConfirmCheckIn: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.claude.green,
                modifier = Modifier.size(34.dp),
            )
        },
        title = {
            Text(
                text = "今日打卡完成",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "“宝剑锋从磨砺出，梅花香自苦寒来。”",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.claude.accentStrong,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmCheckIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("知道了")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}
