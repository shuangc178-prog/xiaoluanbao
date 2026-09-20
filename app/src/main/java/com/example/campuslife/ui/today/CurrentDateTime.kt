package com.example.campuslife.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CurrentDateTime(
    val timeLabel: String,
    val dateLabel: String,
)

private val timeFormatter = DateTimeFormatter.ofPattern(
    "HH:mm",
    Locale.SIMPLIFIED_CHINESE,
)

private val dateFormatter = DateTimeFormatter.ofPattern(
    "M月d日 · EEEE",
    Locale.SIMPLIFIED_CHINESE,
)

@Composable
internal fun rememberCurrentDateTime(): CurrentDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            val waitUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(waitUntilNextMinute)
            now = LocalDateTime.now()
        }
    }

    return remember(now) {
        CurrentDateTime(
            timeLabel = now.format(timeFormatter),
            dateLabel = now.format(dateFormatter),
        )
    }
}
