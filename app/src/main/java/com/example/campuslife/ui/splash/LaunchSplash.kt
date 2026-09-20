package com.example.campuslife.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.campuslife.R
import kotlinx.coroutines.delay

private const val SplashDurationMillis = 2_000L

private enum class LaunchCover(val imageRes: Int) {
    CoverOne(R.drawable.launch_cover_1),
    CoverTwo(R.drawable.launch_cover_2),
    CoverThree(R.drawable.launch_cover_3),
    CoverFour(R.drawable.launch_cover_4),
}

/**
 * Displays one of the launch covers and leaves the screen after a short fixed delay.
 * The selected cover is remembered so recomposition does not swap the image.
 */
@Composable
fun LaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover = remember { LaunchCover.entries.random() }

    LaunchedEffect(Unit) {
        delay(SplashDurationMillis)
        onFinished()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(cover.imageRes),
            contentDescription = "启动封面",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
