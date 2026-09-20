package com.example.campuslife.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.campuslife.data.theme.ThemeStore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 明暗主题的当前取值与切换动作。
 *
 * 主题状态必须比 [CampusLifeTheme] 更靠外一层，所以它由 MainActivity 持有，
 * 再把 [onToggle] 往下传给需要入口的页面。
 */
class ThemeController(
    val isDarkTheme: Boolean,
    val onToggle: () -> Unit,
)

/**
 * 主题控制器。
 *
 * 用户没手动切过时跟随系统（[isSystemInDarkTheme]）；一旦点过切换按钮，
 * 就以本地存档为准，重启后仍然保持用户的选择。
 */
@Composable
fun rememberThemeController(): ThemeController {
    val context = LocalContext.current
    val store = remember(context) { ThemeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val systemDarkTheme = isSystemInDarkTheme()

    var storedDarkTheme by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(store) {
        store.darkTheme.collect { stored -> storedDarkTheme = stored }
    }

    val isDarkTheme = storedDarkTheme ?: systemDarkTheme

    return ThemeController(
        isDarkTheme = isDarkTheme,
        onToggle = {
            scope.launch { store.setDarkTheme(!isDarkTheme) }
        },
    )
}
