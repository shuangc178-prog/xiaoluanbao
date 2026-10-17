package com.example.campuslife.ui.stats

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.campuslife.ui.components.ClaudeTextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.theme.CampusLifeTheme

@Composable
fun StatsScreen(
    state: StatsUiState,
    modifier: Modifier = Modifier,
    onSelectDay: (Long) -> Unit = {},
    onShowToday: () -> Unit = {},
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showDayDetail by rememberSaveable { mutableStateOf(false) }
    // 透明底：页面下面垫的是导航层统一画的像素背景。
    Surface(modifier.fillMaxSize(), color = Color.Transparent) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("统计", style = MaterialTheme.typography.headlineLarge)
                        Row {
                            if (!state.day.isToday) ClaudeTextButton(onClick = onShowToday) { Text("回到今天") }
                            ClaudeTextButton(onClick = { showHistory = true }) { Text("历史") }
                        }
                    }
                    Text(state.day.dateLabel, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "streak") { StreakCard(state) }
            item(key = "recent-learning-chart") {
                RecentLearningChart(state.recentDays, state.day.epochDay, onSelectDay = {
                    onSelectDay(it)
                    showDayDetail = true
                })
            }
            item(key = "learning-chart") { TaskLearningChart(state.day) }
        }
    }
    if (showHistory) {
        StatsHistoryPicker(state.day.epochDay, state.todayEpochDay,
            onDismiss = { showHistory = false },
            onSelect = { onSelectDay(it); showHistory = false })
    }
    if (showDayDetail) LearningDayDetailSheet(state.day, onDismiss = { showDayDetail = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsHistoryPicker(
    selectedEpochDay: Long,
    latestEpochDay: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    // Material 日期选择器的时间戳是 UTC 日期，不能按本地时区再偏移一次。
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = selectedEpochDay.coerceAtMost(latestEpochDay) * DAY_MILLIS,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Math.floorDiv(utcTimeMillis, DAY_MILLIS) <= latestEpochDay
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            ClaudeTextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onSelect(Math.floorDiv(it, DAY_MILLIS)) }
            }) { Text("查看") }
        },
        dismissButton = { ClaudeTextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = picker, title = { Text("选择历史日期", modifier = Modifier.fillMaxWidth()) }) }
}

private const val DAY_MILLIS = 86_400_000L

@Preview(name = "Daily learning", group = "Stats", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
fun StatsScreenDefaultPreview() { CampusLifeTheme(darkTheme = false) { StatsScreen(StatsPreviewData.populated) } }

@Preview(name = "Empty", group = "Stats", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
fun StatsScreenEmptyPreview() { CampusLifeTheme(darkTheme = false) { StatsScreen(StatsPreviewData.empty) } }

@Preview(name = "History", group = "Stats", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
fun StatsScreenHistoryPreview() { CampusLifeTheme(darkTheme = false) { StatsScreen(StatsPreviewData.history) } }

@Preview(name = "Card collection", group = "Stats", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
fun StatsScreenCardCollectionPreview() { CampusLifeTheme(darkTheme = false) { StatsScreen(StatsPreviewData.withCards) } }

@Preview(name = "Dark", group = "Stats", showBackground = true, widthDp = 412, heightDp = 1400)
@Composable
fun StatsScreenDarkPreview() { CampusLifeTheme(darkTheme = true) { StatsScreen(StatsPreviewData.populated) } }
