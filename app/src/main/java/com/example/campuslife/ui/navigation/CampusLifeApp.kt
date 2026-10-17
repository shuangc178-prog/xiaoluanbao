package com.example.campuslife.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import com.example.campuslife.ui.theme.BackdropScene
import com.example.campuslife.ui.theme.ClaudeBackdrop
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import com.example.campuslife.data.card.EXCHANGE_REWARD_REST_CARDS
import com.example.campuslife.data.card.MAX_COPIES_PER_CARD
import com.example.campuslife.data.checkin.CheckInPhase
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.learningDurationMillis
import com.example.campuslife.data.plan.learningPhase
import com.example.campuslife.data.plan.TaskLearningPhase
import com.example.campuslife.data.plan.formatPlanDateLabel
import com.example.campuslife.data.reminder.ReminderBootstrap
import com.example.campuslife.ui.card.CardController
import com.example.campuslife.ui.card.CardPreviewData
import com.example.campuslife.ui.card.GachaReveal
import com.example.campuslife.ui.card.rememberCardController
import com.example.campuslife.ui.backup.CloudBackupDialog
import com.example.campuslife.ui.backup.rememberCloudBackupController
import com.example.campuslife.ui.checkin.CheckInController
import com.example.campuslife.ui.checkin.CheckInUiState
import com.example.campuslife.ui.checkin.rememberCheckInController
import com.example.campuslife.ui.checkin.rememberCheckInEpochDay
import com.example.campuslife.ui.journal.JournalController
import com.example.campuslife.ui.journal.JournalPreviewData
import com.example.campuslife.ui.journal.JournalScreen
import com.example.campuslife.ui.journal.rememberJournalController
import com.example.campuslife.ui.plan.DailyPlanController
import com.example.campuslife.ui.plan.rememberDailyPlanController
import com.example.campuslife.ui.reminder.rememberReminderStatus
import com.example.campuslife.ui.stats.StatsController
import com.example.campuslife.ui.stats.StatsPreviewData
import com.example.campuslife.ui.stats.StatsScreen
import com.example.campuslife.ui.stats.RestCardReveal
import com.example.campuslife.ui.stats.CardExchangeResultDialog
import com.example.campuslife.ui.stats.rememberStatsController
import com.example.campuslife.ui.study.StudyController
import com.example.campuslife.ui.study.StudyPreviewData
import com.example.campuslife.ui.study.StudyReaderScreen
import com.example.campuslife.ui.study.StudyReaderUiState
import com.example.campuslife.ui.study.StudyScreen
import com.example.campuslife.ui.study.rememberStudyController
import com.example.campuslife.ui.theme.CampusLifeTheme
import com.example.campuslife.ui.today.TodayCategory
import com.example.campuslife.ui.today.TodayItemStatus
import com.example.campuslife.ui.today.TodayPreviewData
import com.example.campuslife.ui.today.TodayPriorityUiModel
import com.example.campuslife.ui.today.TodayScreen
import com.example.campuslife.ui.today.TodayRewardButtons
import com.example.campuslife.ui.today.TodayRewardPanel
import com.example.campuslife.ui.today.TodayRewardSheet
import com.example.campuslife.ui.today.rememberCurrentDateTime
import java.time.LocalDate

/** Preview 用的固定日期，保证设计预览里不出现真实系统时间。 */
private val PreviewEpochDay = LocalDate.of(2026, 9, 14).toEpochDay()

@Composable
fun CampusLifeApp(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = false,
    onToggleTheme: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Plan) }
    // 换日时钟提到最外层：打卡与每日计划共用同一把「今天」，统计页也以它为基准。
    // 昨天任务全完成但没点结束打卡的，到点自动落定为已打卡（见 completedCheckInDays）。
    val todayEpochDay = if (LocalInspectionMode.current) {
        PreviewEpochDay
    } else {
        rememberCheckInEpochDay()
    }
    // 每天任务的写入锁。它同时被「计划控制器」和「界面」读到，而它的来源是打卡记录，
    // 打卡控制器又依赖计划的历史——直接互相传参会绕成死结。
    // 所以用一份可变状态桥接：计划控制器先建好，打卡状态就绪后再把锁推进来。
    var isTodayLocked by remember { mutableStateOf(false) }
    val plan = rememberAppDailyPlanController(
        todayEpochDay = todayEpochDay,
        isTodayLocked = isTodayLocked,
    )
    val checkIn = rememberAppCheckInController(
        todayEpochDay = todayEpochDay,
        plans = plan.history,
    )
    // 打卡一旦完成就锁住，换日之后按钮复位、锁自动解开。
    // 放在 LaunchedEffect 里只是「把控制器算出来的值同步进状态」，不是业务判断本身。
    LaunchedEffect(checkIn.isTodayLocked) {
        isTodayLocked = checkIn.isTodayLocked
    }
    val journal = rememberAppJournalController()
    val study = rememberAppStudyController()
    // 抽卡的门禁是「今天已完成打卡」，用的是打卡控制器里那把凌晨 6 点的尺子。
    // 新兑换奖励与卡册在同一个 DataStore 原子落盘；旧版奖励账本继续并入总库存。
    val card = rememberAppCardController(
        currentDayCompleted = checkIn.state.phase == CheckInPhase.Finished,
        todayEpochDay = todayEpochDay,
    )
    val stats = rememberAppStatsController(checkIn = checkIn, plan = plan, card = card)

    // 计划一变，就把系统闹钟重新对齐一次：新增的排上、删掉的取消、勾完的撤销，
    // 已经弹过通知的（token 已落库）不会重复排。日历事件已经回滚，提醒只剩任务预期开始时间。
    //
    // 这只是「计划变化」这一类触发点。「回到前台」「开机 / 改时间 / 换时区」
    // 「周期自愈」分别由 MainActivity、ReminderRestoreReceiver、ReminderHealWorker 负责，
    // 都走同一个入口，避免并发重排互相覆盖簿记。
    if (!LocalInspectionMode.current) {
        val context = LocalContext.current
        LaunchedEffect(Unit) {
            ReminderBootstrap.ensureHealingScheduledAsync(context)
        }
        LaunchedEffect(plan.plansForReminders) {
            ReminderBootstrap.syncNow(
                context = context,
                plans = plan.plansForReminders,
            )
        }
    }

    // 再点一次当前 Tab 时让导航栏上的小卵包原地跳一下；纯装饰，不参与任何逻辑。
    var mascotPokes by remember { mutableIntStateOf(0) }

    // 开卡动效要盖住整个界面（含底部导航），所以 Scaffold 外面包一层 Box。
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Box {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        MainTab.entries.forEach { tab ->
                            val isSelected = tab == selectedTab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (tab == selectedTab) mascotPokes++
                                    selectedTab = tab
                                },
                                icon = {
                                    BouncyTabIcon(selected = isSelected) {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label,
                                        )
                                    }
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
                // 站在导航栏上沿、当前 Tab 正上方的像素小卵包。
                TabMascot(
                    selectedIndex = selectedTab.ordinal,
                    tabCount = MainTab.entries.size,
                    pokeCount = mascotPokes,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .layout { measurable, constraints ->
                            // 画在导航栏上方：不占导航栏的高度，整块往上挪出自己的高度。
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            layout(placeable.width, 0) { placeable.place(0, -placeable.height) }
                        },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        // 页面底下垫一层像素背景插画；页面本身是透明底，切 Tab 时背景淡入淡出。
        Box(Modifier.fillMaxSize()) {
        ClaudeBackdrop(
            scene = selectedTab.backdrop,
            modifier = contentModifier.fillMaxSize(),
        )
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // 往右边的 Tab 走，新页面从右侧轻轻滑进来；往左走则反过来。
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(durationMillis = 220, delayMillis = 70)) +
                    slideInHorizontally(tween(durationMillis = 300, easing = FastOutSlowInEasing)) { width ->
                        direction * width / 12
                    }) togetherWith
                    (fadeOut(tween(durationMillis = 140)) +
                        slideOutHorizontally(tween(durationMillis = 220)) { width -> -direction * width / 16 })
            },
            label = "tabContent",
        ) { tab ->
        when (tab) {
            MainTab.Plan -> TodayRoute(
                checkIn = checkIn,
                plan = plan,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                stats = stats,
                card = card,
                modifier = contentModifier,
            )
            MainTab.Journal -> JournalScreen(
                state = journal.state,
                modifier = contentModifier,
                onCategoryClick = journal.onSelectCategory,
                onCreateCategory = journal.onCreateCategory,
                onRenameCategory = journal.onRenameCategory,
                onDeleteCategory = journal.onDeleteCategory,
                onSaveEntry = journal.onSaveEntry,
                onToggleAutoSave = journal.onToggleAutoSave,
                onDeleteEntry = journal.onDeleteEntry,
            )
            MainTab.Study -> StudyRoute(
                study = study,
                modifier = contentModifier,
            )
            MainTab.Stats -> StatsScreen(
                state = stats.state,
                modifier = contentModifier,
                onSelectDay = stats.onSelectDay,
                onShowToday = stats.onShowToday,
            )
        }
        }
        }
        }

        // 开卡动效：抽到之后立刻盖上来，三个出口都能收起。
        card.drawnCard?.let { drawn ->
            GachaReveal(
                card = drawn,
                owned = card.ownedOf(drawn),
                maxCopies = MAX_COPIES_PER_CARD,
                onDismiss = card.onDismissReveal,
            )
        }
    }
}

/** 每个 Tab 底下垫哪一幅像素背景。 */
private val MainTab.backdrop: BackdropScene
    get() = when (this) {
        MainTab.Plan -> BackdropScene.Plan
        MainTab.Journal -> BackdropScene.Journal
        MainTab.Study -> BackdropScene.Study
        MainTab.Stats -> BackdropScene.Stats
    }

/** 被选中的那一刻图标「啵」地弹一下；首次出现不弹。 */
@Composable
private fun BouncyTabIcon(
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    val pop = remember { Animatable(1f) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!appeared) {
            appeared = true
            return@LaunchedEffect
        }
        if (selected) {
            pop.snapTo(0.72f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 520f))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
    ) {
        content()
    }
}

/**
 * 设计预览（Inspection 模式）下不要真的去读写 DataStore，
 * 换成一个固定的假控制器，Preview 才能稳定渲染。
 */
@Composable
private fun rememberAppCheckInController(
    todayEpochDay: Long,
    plans: List<DailyPlan>,
): CheckInController {
    if (LocalInspectionMode.current) {
        return CheckInController(
            state = CheckInUiState(
                phase = CheckInPhase.Started,
                cumulativeDays = 12,
                startLabel = "07:20",
            ),
            records = StatsPreviewData.sampleRecords(PreviewEpochDay),
            todayEpochDay = PreviewEpochDay,
            onStart = {},
            onFinish = {},
        )
    }
    return rememberCheckInController(todayEpochDay = todayEpochDay, plans = plans)
}

@Composable
private fun rememberAppDailyPlanController(
    todayEpochDay: Long,
    isTodayLocked: Boolean,
): DailyPlanController {
    if (LocalInspectionMode.current) {
        val plans = StatsPreviewData.samplePlans(PreviewEpochDay)
        return DailyPlanController(
            today = plans.first(),
            tomorrow = DailyPlan(epochDay = PreviewEpochDay + 1L, tasks = emptyList()),
            history = plans.sortedByDescending { it.epochDay },
            plansForReminders = plans,
            isTodayLocked = isTodayLocked,
            onToggleTaskTimer = {},
            onFinishTask = {},
            nowEpochMillis = StatsPreviewData.nowEpochMillis,
            onAddTask = { _, _, _ -> },
            onUpdateTask = { _, _, _, _ -> },
            onDeleteTask = {},
            onMoveTask = { _, _ -> },
            onUseAutomaticTaskOrder = {},
            onAddTomorrowTask = { _, _, _ -> },
            onUpdateTomorrowTask = { _, _, _, _ -> },
            onDeleteTomorrowTask = {},
            onMoveTomorrowTask = { _, _ -> },
            onUseAutomaticTomorrowTaskOrder = {},
        )
    }
    return rememberDailyPlanController(
        // 不再用 Preview 示例任务建当天快照：新的一天从空任务栏开始，只显示用户自己加的内容。
        // seedTasks 仍保留在控制器里（跨天换日需要一个「当天还没有存档」时的基准），只是这里传空。
        seedTasks = emptyList(),
        todayEpochDay = todayEpochDay,
        isTodayLocked = isTodayLocked,
    )
}

@Composable
private fun rememberAppJournalController(): JournalController {
    if (LocalInspectionMode.current) {
        return JournalController(
            state = JournalPreviewData.default,
            onSelectCategory = {},
            onCreateCategory = {},
            onRenameCategory = { _, _ -> },
            onDeleteCategory = {},
            onSaveEntry = { _, _, _ -> },
            onToggleAutoSave = {},
            onDeleteEntry = {},
        )
    }
    return rememberJournalController()
}

@Composable
private fun rememberAppStudyController(): StudyController {
    if (LocalInspectionMode.current) {
        return StudyController(
            state = StudyPreviewData.default,
            allDocuments = StudyPreviewData.default.documents,
            onSelectCategory = {},
            onSearchQueryChange = {},
            onImportFiles = {},
            onImportFolder = {},
            onDismissImportResult = {},
            onDeleteDocument = {},
            onSetDocumentCategory = { _, _ -> },
            onSaveReading = { _, _, _ -> },
            onSaveVocabularyMask = { _, _ -> },
            onSaveTaskCheckboxState = { _, _ -> },
        )
    }
    return rememberStudyController()
}

/**
 * 统计页只消费打卡与计划控制器传来的同一份数据，不重复创建数据订阅。
 * 卡册与「兑换来的休息卡张数」也是从外面递进来的，统计页自己不读 DataStore。
 */
@Composable
private fun rememberAppStatsController(
    checkIn: CheckInController,
    plan: DailyPlanController,
    card: CardController,
): StatsController {
    if (LocalInspectionMode.current) {
        return StatsController(
            state = StatsPreviewData.withCards,
            onSelectDay = {},
            onShowToday = {},
            onUseRestCard = {},
        )
    }
    return rememberStatsController(
        records = checkIn.records,
        todayEpochDay = checkIn.todayEpochDay,
        plans = plan.history,
        nowEpochMillis = plan.nowEpochMillis,
        onUseRestCard = checkIn.onUseRestCard,
        ownedCards = card.collection,
        bonusRestCards = (checkIn.bonusRestCards.toLong() + card.exchangeRestCards)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt(),
    )
}

/**
 * 卡册控制器；设计预览下换成固定数据，避免 Preview 去读写 DataStore。
 */
@Composable
private fun rememberAppCardController(
    currentDayCompleted: Boolean,
    todayEpochDay: Long,
): CardController {
    if (LocalInspectionMode.current) {
        return CardController(
            collection = CardPreviewData.collection,
            drawnCard = null,
            onDraw = {},
            onDismissReveal = {},
            onExchange = {},
            currentDayCompleted = true,
            todayEpochDay = todayEpochDay,
        )
    }
    return rememberCardController(
        currentDayCompleted = currentDayCompleted,
        todayEpochDay = todayEpochDay,
    )
}

@Composable
private fun StudyRoute(
    study: StudyController,
    modifier: Modifier = Modifier,
) {
    var openedDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    val openedDocument = openedDocumentId?.let { id ->
        study.allDocuments.firstOrNull { it.id == id }
    }
    var readerProgress by remember(openedDocumentId, openedDocument?.progress) {
        mutableStateOf(openedDocument?.progress ?: 0f)
    }
    var readerScrollY by remember(openedDocumentId, openedDocument?.scrollY) {
        mutableStateOf(openedDocument?.scrollY ?: 0)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        study.onImportFiles(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(study.onImportFolder)
    }

    if (openedDocument == null) {
        StudyScreen(
            state = study.state,
            modifier = modifier,
            onPickFilesClick = {
                filePicker.launch(
                    arrayOf(
                        "text/markdown",
                        "text/plain",
                        "application/zip",
                        "application/octet-stream",
                    ),
                )
            },
            onPickFolderClick = { folderPicker.launch(null) },
            onSelectCategory = study.onSelectCategory,
            onSearchQueryChange = study.onSearchQueryChange,
            onDocumentClick = { openedDocumentId = it },
            onSetDocumentCategory = study.onSetDocumentCategory,
            onDeleteDocument = study.onDeleteDocument,
            onDismissImportResult = study.onDismissImportResult,
        )
    } else {
        val readerState = StudyReaderUiState(
            document = openedDocument,
            progress = readerProgress,
            progressLabel = if (readerProgress <= 0f) {
                "从文首开始"
            } else {
                "已读 ${(readerProgress * 100).toInt().coerceIn(1, 100)}%"
            },
        )
        StudyReaderScreen(
            state = readerState,
            modifier = modifier,
            onBackClick = {
                study.onSaveReading(openedDocument.id, readerScrollY, readerProgress)
                openedDocumentId = null
            },
            onProgressChange = { scrollY, progress ->
                readerScrollY = scrollY
                readerProgress = progress
            },
            onVocabularyTableMaskChange = { mask ->
                study.onSaveVocabularyMask(openedDocument.id, mask)
            },
            onTaskCheckboxStateChange = { state ->
                study.onSaveTaskCheckboxState(openedDocument.id, state)
            },
        )
    }
}

@Composable
private fun TodayRoute(
    checkIn: CheckInController,
    plan: DailyPlanController,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    stats: StatsController,
    card: CardController,
    modifier: Modifier = Modifier,
) {
    var showCloudBackup by rememberSaveable { mutableStateOf(false) }
    var rewardPanel by rememberSaveable { mutableStateOf<TodayRewardPanel?>(null) }
    val rewardActions: @Composable () -> Unit = {
        TodayRewardButtons(stats.state, onOpen = { panel ->
            rewardPanel = panel
        })
    }
    rewardPanel?.let { panel ->
        TodayRewardSheet(
            panel = panel,
            state = stats.state,
            onDismiss = { rewardPanel = null },
            dayDetails = stats.dayDetails,
            onUseRestCard = checkIn.onUseRestCard,
            onExchangeCards = card.onExchange,
        )
    }
    if (checkIn.shouldRevealRestCard) RestCardReveal(onDismiss = checkIn.onDismissRestCardReveal)
    if (card.justExchanged) {
        CardExchangeResultDialog(
            rewardRestCards = EXCHANGE_REWARD_REST_CARDS,
            onDismiss = card.onDismissExchange,
        )
    }

    val cloudBackup = if (LocalInspectionMode.current) {
        null
    } else {
        rememberCloudBackupController()
    }

    if (showCloudBackup && cloudBackup != null) {
        CloudBackupDialog(
            controller = cloudBackup,
            onDismiss = { showCloudBackup = false },
        )
    }

    val completedTitles = plan.today.tasks.filter { it.isCompleted }.map { it.title }.toSet()
    val priorityByTitle = TodayPreviewData.default.priorities.associateBy { it.title }
    val todayPriorities = plan.today.orderedTasks.map { task ->
        val preview = priorityByTitle[task.title]
        TodayPriorityUiModel(
            id = task.title,
            title = task.title,
            supportingText = task.supportingText,
            category = preview?.category ?: TodayCategory.Personal,
            status = when {
                task.isCompleted -> TodayItemStatus.Completed
                task.learningPhase == TaskLearningPhase.Running -> TodayItemStatus.InProgress
                else -> TodayItemStatus.NotStarted
            },
            expectedStartMinute = task.expectedStartMinute,
            manualOrder = task.manualOrder,
            learningPhase = task.learningPhase,
            learningDurationMillis = task.learningDurationMillis(plan.today.epochDay, plan.nowEpochMillis),
        )
    }
    val tomorrowPriorities = plan.tomorrow.orderedTasks.map { task ->
        val preview = priorityByTitle[task.title]
        TodayPriorityUiModel(
            id = task.title,
            title = task.title,
            supportingText = task.supportingText,
            category = preview?.category ?: TodayCategory.Personal,
            status = TodayItemStatus.NotStarted,
            expectedStartMinute = task.expectedStartMinute,
            manualOrder = task.manualOrder,
        )
    }
    val todayState = TodayPreviewData.default.copy(priorities = todayPriorities)

    // 提醒状态要在预览分支之前读出来，Preview 才能连着状态行一起渲染。
    // 手动重排完之后抬一次 tick，用户才能立刻看到新的结论而不是停在旧的上面。
    val reminderStatus = rememberReminderStatus(
        refreshKey = plan.plansForReminders,
    )
    if (LocalInspectionMode.current) {
        TodayScreen(
            state = todayState,
            modifier = modifier,
            checkInState = checkIn.state,
            isCheckInLocked = checkIn.isTodayLocked,
            completedTaskTitles = completedTitles,
            tomorrowPriorities = tomorrowPriorities,
            tomorrowDateLabel = formatPlanDateLabel(plan.tomorrow.date),
            isDarkTheme = isDarkTheme,
            isCloudBackupConnected = cloudBackup?.settings?.configured == true,
            onToggleTheme = onToggleTheme,
            gachaState = card.gachaEntry,
            onGachaClick = card.onDraw,
            rewardActions = rewardActions,
            onOpenDataBackup = {},
            // 首页不再显示「提醒」气泡（用户口径：正常时一个字都不说），
            // 预览里也就不再摆一句占位的话——设计稿上看到的应该是真实形态。
        )
        return
    }

    val dateTime = rememberCurrentDateTime()
    // 保存带预期开始时间的任务后，在真实使用时刻申请通知权限（Android 13+ 才有这条运行时权限）。
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* 用户选没选都不阻塞保存，结果不必在这里处理 */ }

    TodayScreen(
        state = todayState.copy(
            timeLabel = dateTime.timeLabel,
            dateLabel = dateTime.dateLabel,
        ),
        modifier = modifier,
        checkInState = checkIn.state,
        isCheckInLocked = checkIn.isTodayLocked,
        completedTaskTitles = completedTitles,
        tomorrowPriorities = tomorrowPriorities,
        tomorrowDateLabel = formatPlanDateLabel(plan.tomorrow.date),
        isDarkTheme = isDarkTheme,
        isCloudBackupConnected = cloudBackup?.settings?.configured == true,
        onToggleTheme = onToggleTheme,
        gachaState = card.gachaEntry,
        onGachaClick = card.onDraw,
        rewardActions = rewardActions,
        onTaskToggleTimer = plan.onToggleTaskTimer,
        onTaskFinish = plan.onFinishTask,
        onTaskAdd = plan.onAddTask,
        onTaskUpdate = plan.onUpdateTask,
        onTaskDelete = plan.onDeleteTask,
        onTaskMove = plan.onMoveTask,
        onUseAutomaticTaskOrder = plan.onUseAutomaticTaskOrder,
        onTomorrowTaskAdd = plan.onAddTomorrowTask,
        onTomorrowTaskUpdate = plan.onUpdateTomorrowTask,
        onTomorrowTaskDelete = plan.onDeleteTomorrowTask,
        onTomorrowTaskMove = plan.onMoveTomorrowTask,
        onUseAutomaticTomorrowTaskOrder = plan.onUseAutomaticTomorrowTaskOrder,
        onCheckInStart = checkIn.onStart,
        onCheckInFinish = checkIn.onFinish,
        onOpenDataBackup = { showCloudBackup = true },
        // 只有真的排了预期开始时间才值得打断用户去要通知权限，
        // 而且只在还没授权时才请求：已经拒绝过的用户再弹一次也不会有对话框，
        // 那时候该做的是把状态行上的「去开启」露给他，而不是反复调起一个不会出现的弹窗。
        onExpectedStartTimeSaved = {
            if (!reminderStatus?.notificationPermissionGranted.orFalse()) {
                notificationLauncher.launch(ReminderBootstrap.NOTIFICATION_PERMISSION)
            }
        },    )
}

/** 还没读到状态时按「已授权」处理：宁可不弹那个多半不会出现的权限对话框，也不重复打扰。 */
private fun Boolean?.orFalse(): Boolean = this ?: true
@Preview(
    name = "Plan tab",
    group = "MainApp",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun CampusLifeAppPlanPreview() {
    CampusLifeTheme(darkTheme = false) {
        CampusLifeApp()
    }
}

@Preview(
    name = "Journal tab",
    group = "MainApp",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun CampusLifeAppJournalPreview() {
    CampusLifeTheme(darkTheme = false) {
        CampusLifeApp()
    }
}

@Preview(
    name = "Stats tab",
    group = "MainApp",
    showBackground = true,
    widthDp = 412,
    heightDp = 900,
)
@Composable
fun CampusLifeAppStatsPreview() {
    CampusLifeTheme(darkTheme = false) {
        CampusLifeApp()
    }
}
