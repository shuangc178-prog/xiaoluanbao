package com.example.campuslife.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.campuslife.ui.components.ClaudeTextButton

/** 点击日期柱后立即打开；长任务列表可滚动，零时长日期也能查看计划。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearningDayDetailSheet(day: StatsDayUiModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("任务用时", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(end = 8.dp))
                    ClaudeTextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
            item { TaskLearningChart(day) }
        }
    }
}
