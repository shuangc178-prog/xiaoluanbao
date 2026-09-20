package com.example.campuslife.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.GachaCard
import com.example.campuslife.ui.card.CardSlot
import com.example.campuslife.ui.card.CardViewer
import com.example.campuslife.ui.card.rarityAccent
import com.example.campuslife.ui.theme.claude

/**
 * 统计页的卡片收集折叠面板。
 *
 * 放在休息卡上面：两块都是奖励，收集进度是「还差什么」，
 * 休息卡那段最后一句指下面图表（点某一天用卡），顺序不打断它。
 *
 * 收起时只保留跨系列汇总，展开后再呈现每一弹的卡面与兑换操作。
 */
@Composable
internal fun CardCollectionFold(
    collections: List<CardCollectionUiState>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onExchange: (CardSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = summarizeCardCollections(collections)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        stateDescription = if (expanded) "已展开" else "已折叠"
                    }
                    .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                    .padding(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "卡片收集",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${summary.completedSeries}/${summary.seriesCount} 弹集齐",
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (summary.exchangeableSeries > 0) {
                        Text(
                            text = "${summary.exchangeableSeries} 弹可兑换",
                            modifier = Modifier.padding(end = 10.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "${summary.ownedKinds}/${summary.setSize} 种",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Outlined.KeyboardArrowUp
                        } else {
                            Icons.Outlined.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "收起卡片收集" else "展开卡片收集",
                        modifier = Modifier.padding(start = 6.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            if (expanded) {
                collections.forEach { collection ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CardCollectionSeriesContent(
                        collection = collection,
                        onExchange = onExchange,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardCollectionSeriesContent(
    collection: CardCollectionUiState,
    onExchange: (CardSeries) -> Unit,
    modifier: Modifier = Modifier,
) {
    var detailCard by remember { mutableStateOf<GachaCard?>(null) }
    var confirmExchange by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(18.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.series.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = collection.series.collectionSubtitle,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${collection.ownedKinds}/${collection.setSize} 种",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            CollectionProgress(
                slots = collection.slots,
                modifier = Modifier.padding(top = 14.dp),
            )

            // 五格铺满一行：没拿到的也占一格，降饱和，一眼看出还差哪些。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                collection.slots.forEach { slot ->
                    CardSlot(
                        card = slot.card,
                        owned = slot.owned,
                        maxCopies = slot.maxCopies,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { detailCard = slot.card },
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "整套兑换",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    // 只报还差几种（状态），不解释兑换规则——规则在按钮上。
                    if (!collection.isComplete) {
                        Text(
                            text = "还差 ${collection.setSize - collection.ownedKinds} 种",
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = { confirmExchange = true },
                    enabled = collection.canExchange,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("兑换")
                }
            }
    }

    detailCard?.let { card ->
        val slot = collection.slots.firstOrNull { it.card == card }
        CardViewer(
            card = card,
            owned = slot?.owned ?: 0,
            maxCopies = slot?.maxCopies ?: 0,
            onDismiss = { detailCard = null },
        )
    }

    if (confirmExchange) {
        ExchangeConfirmDialog(
            rewardRestCards = collection.rewardRestCards,
            totalCount = collection.totalCount,
            onConfirm = {
                confirmExchange = false
                onExchange(collection.series)
            },
            onDismiss = { confirmExchange = false },
        )
    }
}

internal data class CardCollectionSummary(
    val ownedKinds: Int,
    val setSize: Int,
    val completedSeries: Int,
    val seriesCount: Int,
    val exchangeableSeries: Int,
) {
    val progress: Float
        get() = if (setSize <= 0) 0f else (ownedKinds.toFloat() / setSize).coerceIn(0f, 1f)
}

internal fun summarizeCardCollections(
    collections: List<CardCollectionUiState>,
): CardCollectionSummary = CardCollectionSummary(
    ownedKinds = collections.sumOf { it.ownedKinds },
    setSize = collections.sumOf { it.setSize },
    completedSeries = collections.count { it.isComplete },
    seriesCount = collections.size,
    exchangeableSeries = collections.count { it.canExchange },
)

/** 五段进度条：拿到的那几种按稀有度上色，没拿到的留空槽。 */
@Composable
private fun CollectionProgress(
    slots: List<CardSlotUiModel>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        slots.forEach { slot ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (slot.isOwned) {
                            rarityAccent(slot.card.rarity)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * 兑换前的二次确认。
 *
 * 兑换会**清空卡册**（连重复的卡一起），所以要说清楚消耗多少张、换来什么，
 * 不能只写一个「确定」。
 */
@Composable
private fun ExchangeConfirmDialog(
    rewardRestCards: Int,
    totalCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "消耗全部卡片？",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "$totalCount 张卡 → $rewardRestCards 张休息卡",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("确认兑换")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("再想想") }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}

/** 兑换成功后的提示：只说结果，不解释规则。 */
@Composable
internal fun CardExchangeResultDialog(
    rewardRestCards: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "兑换成功",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("知道了")
            }
        },
        icon = {
            Text(
                text = "×$rewardRestCards",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.claude.accent,
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
    )
}
