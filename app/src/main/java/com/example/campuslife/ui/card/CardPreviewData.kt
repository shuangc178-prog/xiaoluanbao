package com.example.campuslife.ui.card

import com.example.campuslife.data.card.CardCollection
import com.example.campuslife.data.card.CardSeries
import com.example.campuslife.data.card.GachaAvailability
import com.example.campuslife.data.card.GachaCard

/**
 * 仅供 Android Studio Preview 使用的固定卡册。
 *
 * 运行时的卡册来自 `CardCollectionStore`（本地 DataStore），不会调用这里的内容。
 * 与统计页的示例数据同一条原则：这里只为了让 Preview 有形态可看，不进任何真实数字。
 */
object CardPreviewData {

    /** 首页入口：打卡已完成、今天还没抽过（按钮是亮的）。 */
    val readyEntry = GachaEntryUiState(
        series = DefaultGachaSeries.map { slot ->
            if (slot.cardSeries == CardSeries.Isekai) {
                slot.copy(availability = GachaAvailability.Ready, ownedKinds = 3)
            } else {
                slot
            }
        },
    )

    /** 首页入口：还没打卡（按钮压暗，状态字说明原因）。 */
    val blockedEntry = GachaEntryUiState()

    /** 半集齐的卡册：三张到手，其中一张重复。 */
    val collection = CardCollection(
        counts = mapOf(
            GachaCard.Unicorn.id to 2,
            GachaCard.ElfKing.id to 1,
            GachaCard.BlackMage.id to 1,
            GachaCard.SanguoTaoyuan.id to 1,
        ),
    )
}
