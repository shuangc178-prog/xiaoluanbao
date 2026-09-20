package com.example.campuslife.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 底部导航的四个入口。
 *
 * 顺序即展示顺序，「统计」固定放在最后：它是回顾性的，不适合插在三个日常入口中间。
 */
enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Plan(
        label = "本日计划",
        selectedIcon = Icons.Filled.DateRange,
        unselectedIcon = Icons.Filled.DateRange,
    ),
    Journal(
        label = "日记随想",
        selectedIcon = Icons.Filled.Edit,
        unselectedIcon = Icons.Filled.Edit,
    ),
    Study(
        label = "学习",
        selectedIcon = Icons.AutoMirrored.Filled.MenuBook,
        unselectedIcon = Icons.AutoMirrored.Filled.MenuBook,
    ),
    Stats(
        label = "统计",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Filled.BarChart,
    ),
}
