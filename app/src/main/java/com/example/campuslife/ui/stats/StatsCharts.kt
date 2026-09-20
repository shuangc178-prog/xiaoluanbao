package com.example.campuslife.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.checkin.formatDurationLabel
import com.example.campuslife.data.checkin.formatMinuteOfDay
import com.example.campuslife.ui.theme.claude
import kotlin.math.ceil

private val AxisLabelWidth = 46.dp
private val AxisGap = 8.dp
private val BarCornerRadius = 3.dp

/** 甘特图左侧那一列日期标签比通用轴标签宽一些：要放得下 `9/14` 与「今天」色标。 */
private val GanttLabelWidth = 56.dp

/**
 * 甘特条的最小可见宽度。
 *
 * 打卡只有几分钟时条子会退化成一条线，看上去像「没有记录」；
 * 给一个最小宽度，至少能看见「这天打过卡」。
 */
private val GanttMinBarWidth = 6.dp
private val GanttRowGap = 4.dp

/** 甘特图基准高度：近 7 天一行 22dp，近 30 天压到 10dp，再多行就靠滚动。 */
private val GanttRowHeightMax = 22.dp
private val GanttRowHeightMin = 10.dp

/** 甘特条的最小厚度，避免行高压到 10dp 时条子细成一条发丝线。 */
private const val GanttBarMinHeightPx = 3f

/** 每日打卡时长柱状图。 */
@Composable
internal fun DurationBarChart(
    dayLabels: List<String>,
    values: List<Int?>,
    barColor: Color,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 170.dp,
    selectedIndex: Int? = null,
    onDayClick: ((Int) -> Unit)? = null,
) {
    if (dayLabels.isEmpty()) return
    val peak = values.filterNotNull().maxOrNull() ?: 0
    val yMax = (ceil(peak / 60f).toInt().coerceAtLeast(1)) * 60f
    val middle = yMax / 2f
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            AxisLabels(
                top = compactDurationLabel(yMax.toInt()),
                middle = compactDurationLabel(middle.toInt()),
                bottom = "0",
            )
            Spacer(modifier = Modifier.width(AxisGap))
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .columnTapInput(dayLabels.size, onDayClick),
            ) {
                drawHorizontalGrid(gridColor)
                drawSelectionBand(selectedIndex, dayLabels.size, selectionColor)
                drawBars(
                    values = values,
                    yMax = yMax,
                    color = barColor,
                    selectedIndex = selectedIndex,
                )
            }
        }
        XAxisLabels(dayLabels = dayLabels, highlightedIndex = selectedIndex)
    }
}

@Composable
internal fun ChartLegend(
    entries: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 每天一条横杠的打卡甘特图。
 *
 * 一行一天、时间沿横轴铺开，一根实心条就是那天的「开始打卡 → 结束打卡」；
 * 已经开始但还没结束的当天，后半段用渐隐的浅色接着画到此刻，
 * 所以「做完了」和「还在进行」在图上一眼能分开。
 *
 * 横轴按小时取整、只覆盖真正出现过的钟点（见 [ganttAxisFor]）；
 * 左边一列日期同时充当 y 轴标签与点选高亮的位置，上下再各排一行时刻刻度。
 */
@Composable
internal fun CheckInGanttChart(
    days: List<StatsDayUiModel>,
    barColor: Color,
    axis: GanttAxis,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 190.dp,
    selectedIndex: Int? = null,
    onDayClick: ((Int) -> Unit)? = null,
) {
    if (days.isEmpty()) return

    val rowHeight = ganttRowHeight(days.size, chartHeight)
    val density = LocalDensity.current
    // dp → px 需要 Density 作用域，而 DrawScope 不是 @Composable 上下文，
    // 所以在这里先把像素值算好，再带进 Canvas。
    val radius = with(density) { BarCornerRadius.toPx() }
    val minBarWidth = with(density) { GanttMinBarWidth.toPx() }
    val dotRadius = (with(density) { rowHeight.toPx() } / 2f * 0.34f)
        .coerceIn(2f, with(density) { 4.dp.toPx() })
    val showDots = rowHeight >= 12.dp
    val ticks = ganttTimeTicks(axis, days.size)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val selectionColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        GanttTickRow(
            ticks = ticks,
            axis = axis,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            GanttDayLabels(days = days, selectedIndex = selectedIndex, rowHeight = rowHeight)
            Spacer(modifier = Modifier.width(AxisGap))
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .rowTapInput(days.size, rowHeight, GanttRowGap, onDayClick),
            ) {
                drawGanttRows(
                    days = days,
                    axis = axis,
                    barColor = barColor,
                    trackColor = trackColor,
                    selectionColor = selectionColor,
                    selectedIndex = selectedIndex,
                    rowHeightPx = rowHeight.toPx(),
                    minBarWidth = minBarWidth,
                    radius = radius,
                    dotRadius = dotRadius,
                    showDots = showDots,
                )
            }
        }

        GanttTickRow(
            ticks = ticks,
            axis = axis,
            showBaseLine = true,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * 甘特图上方 / 下方的时刻刻度。
 *
 * 用布局偏移而不是 Canvas 里的 drawText：字体度量与系统字号缩放交给 Text 处理，
 * 位置换算与色块共用同一套 fraction，刻度不会和条子错位。
 */
@Composable
private fun GanttTickRow(
    ticks: List<Int>,
    axis: GanttAxis,
    modifier: Modifier = Modifier,
    showBaseLine: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(GanttLabelWidth + AxisGap))
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val widthPx = constraints.maxWidth
            val density = LocalDensity.current
            if (showBaseLine) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            ticks.forEachIndexed { index, minute ->
                val x = with(density) {
                    (widthPx * axis.fractionOf(minute.toFloat())).toInt().toDp()
                }
                Text(
                    text = formatMinuteOfDay(minute),
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = x)
                        .widthIn(min = 34.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when {
                        index == 0 -> TextAlign.Start
                        index == ticks.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                )
            }
        }
    }
}

/** 左侧日期列。甘特图里它就是横轴的对侧标签，点选高亮也标在这一侧。 */
@Composable
private fun GanttDayLabels(
    days: List<StatsDayUiModel>,
    selectedIndex: Int?,
    rowHeight: Dp,
) {
    Column(
        modifier = Modifier.width(GanttLabelWidth),
        verticalArrangement = Arrangement.spacedBy(GanttRowGap),
    ) {
        days.forEachIndexed { index, day ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = day.axisLabel,
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == selectedIndex) {
                        MaterialTheme.claude.accent
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (day.isToday) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .width(16.dp)
                            .height(2.dp)
                            .background(MaterialTheme.claude.accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisLabels(
    top: String,
    middle: String,
    bottom: String,
) {
    Column(
        modifier = Modifier
            .width(AxisLabelWidth)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End,
    ) {
        AxisLabelText(top)
        AxisLabelText(middle)
        AxisLabelText(bottom)
    }
}

@Composable
private fun AxisLabelText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun XAxisLabels(
    dayLabels: List<String>,
    highlightedIndex: Int? = null,
) {
    val step = when {
        dayLabels.size <= 7 -> 1
        dayLabels.size <= 10 -> 2
        else -> 3
    }
    val lastIndex = dayLabels.lastIndex
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Spacer(modifier = Modifier.width(AxisLabelWidth + AxisGap))
        dayLabels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val visible = index % step == 0 || (index == lastIndex && lastIndex % step >= 2)
                if (visible) {
                    val isHighlighted = index == highlightedIndex
                    Text(
                        text = label,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isHighlighted) {
                            MaterialTheme.claude.accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * 柱状图的点选：把横向落点换算成第几列。
 *
 * 点位与柱子都取每列中心，所以这里按列宽向下取整即可，边界处夹回最后一列。
 */
private fun Modifier.columnTapInput(
    columnCount: Int,
    onDayClick: ((Int) -> Unit)?,
): Modifier = if (onDayClick == null || columnCount <= 0) {
    this
} else {
    pointerInput(columnCount) {
        detectTapGestures { offset ->
            val columnWidth = size.width.toFloat() / columnCount
            if (columnWidth > 0f) {
                onDayClick(barColumnIndexAt(offset.x, columnWidth, columnCount))
            }
        }
    }
}

/**
 * 甘特图的点选：它是一行一天，所以按**纵向**落点换算成第几行。
 *
 * 柱状图的行列在屏幕上是反过来的（那边一天一列），两者不能共用一份换算：
 * 曾经共用按横向算的那份，于是点哪一行都由手指的横向位置决定，
 * 想点 9/15 的那根横杠会跳到别的日子上。
 */
private fun Modifier.rowTapInput(
    rowCount: Int,
    rowHeight: Dp,
    rowGap: Dp,
    onDayClick: ((Int) -> Unit)?,
): Modifier = if (onDayClick == null || rowCount <= 0) {
    this
} else {
    pointerInput(rowCount, rowHeight, rowGap) {
        detectTapGestures { offset ->
            onDayClick(ganttRowIndexAt(offset.y, rowHeight.toPx(), rowGap.toPx(), rowCount))
        }
    }
}

/** 横向落点 → 第几列（柱状图）。按列宽向下取整，边界处夹回最后一列。 */
internal fun barColumnIndexAt(x: Float, columnWidth: Float, columnCount: Int): Int {
    if (columnCount <= 0 || columnWidth <= 0f) return 0
    return (x / columnWidth).toInt().coerceIn(0, columnCount - 1)
}

/**
 * 纵向落点 → 第几行（甘特图）。
 *
 * 行与行之间还隔着一个 [rowGap]，所以步长是「行高 + 间隙」；
 * 点在最后一行下方的空白上时夹回最后一行。
 */
internal fun ganttRowIndexAt(y: Float, rowHeight: Float, rowGap: Float, rowCount: Int): Int {
    if (rowCount <= 0) return 0
    val slot = rowHeight + rowGap
    if (slot <= 0f) return 0
    return (y / slot).toInt().coerceIn(0, rowCount - 1)
}

private fun DrawScope.drawHorizontalGrid(color: Color) {
    listOf(0f, 0.5f, 1f).forEach { fraction ->
        val y = size.height * fraction
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

/**
 * 甘特图的行高：先按可用高度平摊，再夹在 10–22dp 之间。
 *
 * 近 7 天一行 22dp 看着舒展；近 30 天压到 10dp，否则整张图会长到要翻两屏。
 */
internal fun ganttRowHeight(dayCount: Int, chartHeight: Dp): Dp {
    if (dayCount <= 0) return GanttRowHeightMax
    val perRow = (chartHeight - GanttRowGap * (dayCount - 1)) / dayCount
    return perRow.coerceIn(GanttRowHeightMin, GanttRowHeightMax)
}

private fun DrawScope.drawGanttRows(
    days: List<StatsDayUiModel>,
    axis: GanttAxis,
    barColor: Color,
    trackColor: Color,
    selectionColor: Color,
    selectedIndex: Int?,
    rowHeightPx: Float,
    minBarWidth: Float,
    radius: Float,
    dotRadius: Float,
    showDots: Boolean,
) {
    if (days.isEmpty() || rowHeightPx <= 0f) return
    val slot = rowHeightPx + GanttRowGap.toPx()
    val trackCenter = rowHeightPx / 2f
    // 条子占行高的六成，上下留白，相邻两天的条子不会黏成一片。
    val barHeight = (rowHeightPx * 0.6f).coerceAtLeast(GanttBarMinHeightPx)
    val trackHeight = barHeight.coerceAtMost(2.dp.toPx())

    days.forEachIndexed { index, day ->
        val top = index * slot
        val centerY = top + trackCenter

        if (index == selectedIndex) {
            drawRect(
                color = selectionColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, rowHeightPx),
            )
        }

        // 没有记录的那天只留一条浅色底线，让「缺了一天」看得见而不是整行空白。
        drawRoundRect(
            color = trackColor.copy(alpha = 0.45f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
        )

        val start = day.startMinutes ?: return@forEachIndexed
        val startX = size.width * axis.fractionOf(start)
        val ongoing = day.endMinutes == null && day.ongoingEndMinutes != null

        val endX = when {
            day.endMinutes != null -> size.width * axis.fractionOf(day.endMinutes)
            ongoing -> size.width * axis.fractionOf(day.ongoingEndMinutes!!)
            else -> size.width
        }

        // 未结束的当天：实心部分只画到此刻，剩下的用渐隐浅色接着画到轴末端，
        // 这样「还在进行」和「已经结束」在图上分得开。
        if (ongoing) {
            val tailWidth = size.width - endX
            if (tailWidth > 0f) {
                drawRoundRect(
                    color = lerp(barColor, Color.Transparent, 0.72f),
                    topLeft = Offset(endX, centerY - barHeight / 2f),
                    size = Size(tailWidth, barHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }

        val width = (endX - startX).coerceAtLeast(minBarWidth).coerceAtMost(size.width - startX)
        drawRoundRect(
            color = barColor,
            topLeft = Offset(startX, centerY - barHeight / 2f),
            size = Size(width, barHeight),
            cornerRadius = CornerRadius(radius, radius),
        )

        if (showDots) {
            drawCircle(
                color = barColor,
                radius = dotRadius,
                center = Offset(startX + dotRadius, centerY),
            )
            if (!ongoing) {
                drawCircle(
                    color = barColor,
                    radius = dotRadius,
                    center = Offset(startX + width - dotRadius, centerY),
                )
            }
        }
    }
}

private fun DrawScope.drawSelectionBand(
    selectedIndex: Int?,
    columnCount: Int,
    color: Color,
) {
    if (selectedIndex == null || columnCount <= 0) return
    if (selectedIndex < 0 || selectedIndex >= columnCount) return
    val columnWidth = size.width / columnCount
    drawRect(
        color = color,
        topLeft = Offset(selectedIndex * columnWidth, 0f),
        size = Size(columnWidth, size.height),
    )
}

private fun DrawScope.drawBars(
    values: List<Int?>,
    yMax: Float,
    color: Color,
    selectedIndex: Int? = null,
) {
    if (values.isEmpty() || yMax <= 0f) return
    val step = size.width / values.size
    val barWidth = step * 0.52f
    val radius = BarCornerRadius.toPx()

    values.forEachIndexed { index, value ->
        if (value == null || value <= 0) return@forEachIndexed
        val barHeight = size.height * (value / yMax).coerceIn(0f, 1f)
        // 选中某一天时，其余柱子压暗，让被点中的那根自己跳出来。
        val barColor = if (selectedIndex != null && index != selectedIndex) {
            color.copy(alpha = 0.35f)
        } else {
            color
        }
        drawRoundRect(
            color = barColor,
            topLeft = Offset((index + 0.5f) * step - barWidth / 2f, size.height - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private fun compactDurationLabel(minutes: Int): String = when {
    minutes <= 0 -> "0"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> formatDurationLabel(minutes)
}
