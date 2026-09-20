package com.example.campuslife.ui.card

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.campuslife.data.card.GachaCard

/**
 * 卡册里点开一张卡：整屏看大图。
 *
 * 和开卡动效同一屏（[CardStage]），差别只有两个——不播动画、按钮是「知道了」。
 * 卡名与稀有度徽章都画在卡面图里，所以底下只补一个数字：已有几张。
 *
 * 用全屏 `Dialog` 而不是 [androidx.compose.material3.AlertDialog]：
 * 后者会被平台默认宽度夹成一个窄条，卡面两侧留出大片空白；这里顺带把返回键也接管了。
 */
@Composable
internal fun CardViewer(
    card: GachaCard,
    owned: Int,
    maxCopies: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CardStage(card = card, onDismiss = onDismiss, owned = owned) {
            Text(
                text = card.displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = raritySoft(card.rarity),
                contentColor = rarityAccent(card.rarity),
            ) {
                Text(
                    text = "${card.rarity.label} · $owned/$maxCopies",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}
