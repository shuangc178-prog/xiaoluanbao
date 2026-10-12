package com.example.campuslife.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.checkin.CheckInPhase
import com.example.campuslife.ui.card.CardPreviewData
import com.example.campuslife.ui.card.GachaEntryUiState
import com.example.campuslife.ui.checkin.CheckInUiState
import com.example.campuslife.ui.theme.CampusLifeTheme

@Composable
fun TodayScreen(
    state: TodayUiState,
    modifier: Modifier = Modifier,
    checkInState: CheckInUiState = CheckInUiState(),
    /** 当天是否已结束打卡：为 true 时任务整体只读。 */
    isCheckInLocked: Boolean = false,
    completedTaskTitles: Set<String> = emptySet(),
    tomorrowPriorities: List<TodayPriorityUiModel> = emptyList(),
    tomorrowDateLabel: String = "明天",
    isDarkTheme: Boolean = false,
    isCloudBackupConnected: Boolean = false,
    onToggleTheme: () -> Unit = {},
    /** 抽卡入口（打卡按钮下方那块）的状态与点击。 */
    gachaState: GachaEntryUiState = GachaEntryUiState(),
    onGachaClick: (CardSeries) -> Unit = {},
    onTaskToggleTimer: (String) -> Unit = {},
    onTaskFinish: (String) -> Unit = {},
    onTaskAdd: (title: String, supportingText: String, expectedStartMinute: Int?) -> Unit = { _, _, _ -> },
    onTaskUpdate: (
        originalTitle: String,
        title: String,
        supportingText: String,
        expectedStartMinute: Int?,
    ) -> Unit = { _, _, _, _ -> },
    onTaskDelete: (title: String) -> Unit = {},
    onTaskMove: (title: String, targetIndex: Int) -> Unit = { _, _ -> },
    onUseAutomaticTaskOrder: () -> Unit = {},
    onTomorrowTaskAdd: (title: String, supportingText: String, expectedStartMinute: Int?) -> Unit = { _, _, _ -> },
    onTomorrowTaskUpdate: (
        originalTitle: String,
        title: String,
        supportingText: String,
        expectedStartMinute: Int?,
    ) -> Unit = { _, _, _, _ -> },
    onTomorrowTaskDelete: (title: String) -> Unit = {},
    onTomorrowTaskMove: (title: String, targetIndex: Int) -> Unit = { _, _ -> },
    onUseAutomaticTomorrowTaskOrder: () -> Unit = {},
    onCheckInStart: () -> Unit = {},
    onCheckInFinish: () -> Unit = {},
    /** 打开坚果云备份与恢复。 */
    onOpenDataBackup: () -> Unit = {},
    /** 保存了带预期开始时间的任务后回调，用来在真实使用时刻申请通知权限。 */
    onExpectedStartTimeSaved: () -> Unit = {},
    onPriorityClick: (String) -> Unit = {},
    onAddPriorityClick: () -> Unit = {},
    onCheckIn: () -> Unit = {},
) {
    var showCheckInDialog by remember { mutableStateOf(false) }
    var lastPhase by remember { mutableStateOf(checkInState.phase) }
    var showTaskEditor by remember { mutableStateOf(false) }
    var editingPriority by remember { mutableStateOf<TodayPriorityUiModel?>(null) }
    var showTomorrowTaskEditor by remember { mutableStateOf(false) }
    var editingTomorrowPriority by remember { mutableStateOf<TodayPriorityUiModel?>(null) }

    val priorities = remember(state.priorities) {
        state.priorities.sortedForTaskBar()
    }
    val sortedTomorrowPriorities = remember(tomorrowPriorities) {
        tomorrowPriorities.sortedForTaskBar()
    }
    val allTasksCompleted = priorities.isNotEmpty() &&
            priorities.all { it.title in completedTaskTitles }
    val hasTasks = priorities.isNotEmpty()
    val showTomorrowPlan = allTasksCompleted && checkInState.phase == CheckInPhase.Finished

    // 打卡刚刚变绿的那一次才弹完成提示；切到别的 Tab 再回来不会重复弹。
    LaunchedEffect(checkInState.phase) {
        if (checkInState.phase == CheckInPhase.Finished && lastPhase != CheckInPhase.Finished) {
            showCheckInDialog = true
        }
        lastPhase = checkInState.phase
    }

    // 跨过凌晨 6 点或任务被取消完成时，明日区会隐藏；同时关掉可能仍开着的明日编辑框。
    LaunchedEffect(showTomorrowPlan) {
        if (!showTomorrowPlan) {
            showTomorrowTaskEditor = false
            editingTomorrowPriority = null
        }
    }

    if (showCheckInDialog) {
        CheckInDialog(
            onDismiss = { showCheckInDialog = false },
            onConfirmCheckIn = {
                showCheckInDialog = false
                onCheckIn()
            },
        )
    }

    if (showTaskEditor) {
        TaskEditorDialog(
            priority = editingPriority,
            existingTitles = priorities.map { it.title }.toSet(),
            initialPosition = editingPriority?.let { priority ->
                priorities.indexOfFirst { it.id == priority.id }.takeIf { it >= 0 }
            },
            positionCount = priorities.size,
            positionEnabled = !isCheckInLocked,
            onPositionSelect = { targetIndex ->
                editingPriority?.let { onTaskMove(it.title, targetIndex) }
            },
            onDismiss = { showTaskEditor = false },
            onSave = { title, supportingText, expectedStartMinute ->
                val editing = editingPriority
                if (editing == null) {
                    onTaskAdd(title, supportingText, expectedStartMinute)
                } else {
                    onTaskUpdate(editing.title, title, supportingText, expectedStartMinute)
                }
                if (expectedStartMinute != null) onExpectedStartTimeSaved()
                showTaskEditor = false
            },
            onDelete = editingPriority?.let { priority ->
                {
                    onTaskDelete(priority.title)
                    showTaskEditor = false
                }
            },
        )
    }

    if (showTomorrowTaskEditor && showTomorrowPlan) {
        TaskEditorDialog(
            priority = editingTomorrowPriority,
            existingTitles = sortedTomorrowPriorities.map { it.title }.toSet(),
            taskScopeLabel = "明日",
            initialPosition = editingTomorrowPriority?.let { priority ->
                sortedTomorrowPriorities.indexOfFirst { it.id == priority.id }.takeIf { it >= 0 }
            },
            positionCount = sortedTomorrowPriorities.size,
            onPositionSelect = { targetIndex ->
                editingTomorrowPriority?.let { onTomorrowTaskMove(it.title, targetIndex) }
            },
            onDismiss = { showTomorrowTaskEditor = false },
            onSave = { title, supportingText, expectedStartMinute ->
                val editing = editingTomorrowPriority
                if (editing == null) {
                    onTomorrowTaskAdd(title, supportingText, expectedStartMinute)
                } else {
                    onTomorrowTaskUpdate(editing.title, title, supportingText, expectedStartMinute)
                }
                if (expectedStartMinute != null) onExpectedStartTimeSaved()
                showTomorrowTaskEditor = false
            },
            onDelete = editingTomorrowPriority?.let { priority ->
                {
                    onTomorrowTaskDelete(priority.title)
                    showTomorrowTaskEditor = false
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
            ),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
            item(key = "header") {
                TodayHeader(
                    timeLabel = state.timeLabel,
                    dateLabel = state.dateLabel,
                    checkInState = checkInState,
                    allTasksCompleted = allTasksCompleted,
                    hasTasks = hasTasks,
                    isDarkTheme = isDarkTheme,
                    isCloudBackupConnected = isCloudBackupConnected,
                    onToggleTheme = onToggleTheme,
                    gachaState = gachaState,
                    onGachaClick = onGachaClick,
                    onCheckInClick = {
                        when (checkInState.phase) {
                            CheckInPhase.Idle -> if (hasTasks) onCheckInStart()
                            CheckInPhase.Started -> if (allTasksCompleted) onCheckInFinish()
                            CheckInPhase.Finished -> Unit
                        }
                    },
                    onOpenDataBackup = onOpenDataBackup,
                )
            }
            item(key = "priorities") {
                TodayPrioritiesSection(
                    priorities = priorities,
                    isManualOrder = priorities.isManuallyOrdered,
                    completedTitles = completedTaskTitles,
                    isLocked = isCheckInLocked,
                    onPriorityClick = { id ->
                        onPriorityClick(id)
                        editingPriority = priorities.firstOrNull { it.id == id }
                        showTaskEditor = editingPriority != null
                    },
                    onPriorityToggle = { id ->
                        // 已打卡时不再交出计时动作：控制器那边同样会拦，这里只是让「点了没反应」
                        // 与界面上的禁用态一致，不会出现点了先变再弹回来的闪烁。
                        if (!isCheckInLocked) {
                            priorities.firstOrNull { it.id == id }?.let { onTaskToggleTimer(it.title) }
                        }
                    },
                    onPriorityFinish = { id ->
                        if (!isCheckInLocked) priorities.firstOrNull { it.id == id }?.let { onTaskFinish(it.title) }
                    },
                    onUseAutomaticOrder = onUseAutomaticTaskOrder,
                    onAddPriorityClick = {
                        if (!isCheckInLocked) {
                            onAddPriorityClick()
                            editingPriority = null
                            showTaskEditor = true
                        }
                    },
                )
            }
            if (showTomorrowPlan) {
                item(key = "tomorrow-plan") {
                    TomorrowPlanSection(
                        priorities = sortedTomorrowPriorities,
                        isManualOrder = sortedTomorrowPriorities.isManuallyOrdered,
                        dateLabel = tomorrowDateLabel,
                        onPriorityClick = { id ->
                            editingTomorrowPriority = sortedTomorrowPriorities.firstOrNull { it.id == id }
                            showTomorrowTaskEditor = editingTomorrowPriority != null
                        },
                        onAddPriorityClick = {
                            editingTomorrowPriority = null
                            showTomorrowTaskEditor = true
                        },
                        onUseAutomaticOrder = onUseAutomaticTomorrowTaskOrder,
                    )
                }
            }
    }
}

@Preview(
    name = "Default",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun TodayScreenPreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(state = TodayPreviewData.default)
    }
}

@Preview(
    name = "Empty state",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun TodayScreenEmptyPreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(state = TodayPreviewData.empty)
    }
}

@Preview(
    name = "Populated state",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1440,
)
@Composable
fun TodayScreenPopulatedPreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(
            state = TodayPreviewData.populated,
            completedTaskTitles = TodayPreviewData.populatedCompletedTitles,
        )
    }
}

@Preview(
    name = "Check-in started",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun TodayScreenCheckInStartedPreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(
            state = TodayPreviewData.default,
            checkInState = CheckInUiState(
                phase = CheckInPhase.Started,
                cumulativeDays = 12,
                startLabel = "07:20",
            ),
        )
    }
}

@Preview(
    name = "All tasks done",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun TodayScreenAllTasksDonePreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(
            state = TodayPreviewData.default,
            checkInState = CheckInUiState(
                phase = CheckInPhase.Started,
                cumulativeDays = 12,
                startLabel = "07:20",
            ),
            completedTaskTitles = TodayPreviewData.default.priorities.map { it.title }.toSet(),
        )
    }
}

@Preview(
    name = "Check-in finished",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun TodayScreenCheckInFinishedPreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(
            state = TodayPreviewData.populated,
            checkInState = CheckInUiState(
                phase = CheckInPhase.Finished,
                cumulativeDays = 13,
                startLabel = "07:20",
                endLabel = "21:05",
            ),
            // 已结束打卡：任务锁住，计时按钮应该是禁用态，页头状态字多一段「已打卡」。
            isCheckInLocked = true,
            completedTaskTitles = TodayPreviewData.populatedCompletedTitles,
        )
    }
}

@Preview(
    name = "Gacha ready",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun TodayScreenGachaReadyPreview() {
    CampusLifeTheme(darkTheme = false) {
        TodayScreen(
            state = TodayPreviewData.populated,
            checkInState = CheckInUiState(
                phase = CheckInPhase.Finished,
                cumulativeDays = 13,
                startLabel = "07:20",
                endLabel = "21:05",
            ),
            completedTaskTitles = TodayPreviewData.populatedCompletedTitles,
            // 打卡已完成、今天还没抽：入口是亮的。
            gachaState = CardPreviewData.readyEntry,
        )
    }
}

@Preview(
    name = "Dark",
    group = "Today",
    showBackground = true,
    widthDp = 412,
    heightDp = 1320,
)
@Composable
fun TodayScreenDarkPreview() {
    CampusLifeTheme(darkTheme = true) {
        TodayScreen(
            state = TodayPreviewData.default,
            isDarkTheme = true,
        )
    }
}
