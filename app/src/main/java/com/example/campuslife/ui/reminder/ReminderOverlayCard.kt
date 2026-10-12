package com.example.campuslife.ui.reminder

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.example.campuslife.ui.components.ClaudeButton
import androidx.compose.material3.Icon
import com.example.campuslife.ui.components.ClaudeIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.campuslife.ui.components.ClaudeTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.campuslife.R
import com.example.campuslife.ui.theme.CampusLifeTheme
import com.example.campuslife.ui.theme.claude

/**
 * 浮在别的应用上面的那张提醒卡片（产品文档 16.32）。
 *
 * 结构照用户给的那张参考图（他自己截图里 Soul 的弹窗）：**一行身份 + 头像 + 标题副标题 + 一颗大按钮**。
 * 配色不照抄——这里只用应用自己的语义色（`MaterialTheme.claude.*`），
 * 换主题时卡片跟着一起换，这是项目红线。
 *
 * [topInset] 是状态栏高度（像素）。悬浮窗里 Compose 读不到 `WindowInsets`，
 * 只能由 [ReminderOverlay] 从系统资源取好传进来，否则卡片会被状态栏压掉半行。
 */
@Composable
internal fun ReminderOverlayCard(
    title: String,
    body: String,
    topInset: Int,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 从上方滑下来 + 淡入：提醒是「落下来一张卡」，不是「啪」地糊在屏幕上。
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "overlay-enter",
    )
    val topPadding = with(LocalDensity.current) { topInset.toDp() } + 10.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = 12.dp, end = 12.dp)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * -36.dp.toPx()
            },
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.claude.accent),
                    )
                    Text(
                        text = "小卵包",
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        modifier = Modifier.padding(start = 6.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.claude.accentSoft,
                        contentColor = MaterialTheme.claude.accentStrong,
                    ) {
                        Text(
                            text = "任务提醒",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    ClaudeIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭提醒",
                            modifier = Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.claude.accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (body.isNotBlank()) {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                ClaudeButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(top = 12.dp, end = 6.dp)
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Text(text = "知道了", style = MaterialTheme.typography.titleSmall)
                }
                ClaudeTextButton(
                    onClick = onOpenApp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .fillMaxWidth(),
                ) {
                    Text(text = "打开小卵包")
                }
            }
        }
    }
}

@Preview(name = "Default", showBackground = true, widthDp = 400)
@Composable
private fun ReminderOverlayCardPreview() {
    CampusLifeTheme {
        ReminderOverlayCard(
            title = "高等数学 · 第三章习题",
            body = "任务预期开始时间 19:30",
            topInset = 0,
            onDismiss = {},
            onOpenApp = {},
        )
    }
}

@Preview(name = "Long text", showBackground = true, widthDp = 400)
@Composable
private fun ReminderOverlayCardLongPreview() {
    CampusLifeTheme {
        ReminderOverlayCard(
            title = "把上周落下的英语听力补完，顺便整理生词本",
            body = "课本第 4 单元到第 6 单元，先听一遍再对答案；生词本按单元分页。",
            topInset = 0,
            onDismiss = {},
            onOpenApp = {},
        )
    }
}
