package com.example.campuslife.ui.splash

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.example.campuslife.R
import com.example.campuslife.ui.theme.ClaudeCanvas
import kotlinx.coroutines.delay

/** 视频每段 3 秒；解码卡住或异常时最多等这么久就直接进入应用。 */
private const val SplashTimeoutMillis = 6_000L

/**
 * 四段启动视频，对应四个 Tab。源文件与逐帧渲染脚本在 `tools/launch_video/`，
 * 改画面要回那里改完重新渲染，不要直接替换 mp4。
 */
private enum class LaunchVideo(@RawRes val videoRes: Int) {
    Plan(R.raw.launch_video_1),
    Journal(R.raw.launch_video_2),
    Study(R.raw.launch_video_3),
    Stats(R.raw.launch_video_4),
}

/**
 * Plays one of the launch videos and leaves the screen when it ends.
 * The selected video is remembered so recomposition does not restart another one.
 *
 * 四段视频的首帧都是 Claude Canvas 米白（与窗口背景同色），首帧出来前先垫这个颜色，
 * 系统启动窗 → 视频之间不会闪黑。
 */
@Composable
fun LaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val video = remember { LaunchVideo.entries.random() }
    val currentOnFinished by rememberUpdatedState(onFinished)
    var finished by remember { mutableStateOf(false) }
    val finish = remember {
        {
            if (!finished) {
                finished = true
                currentOnFinished()
            }
        }
    }
    var firstFrameShown by remember { mutableStateOf(false) }
    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameShown) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "launchVideoAlpha",
    )

    LaunchedEffect(Unit) {
        delay(SplashTimeoutMillis)
        finish()
    }

    Box(modifier = modifier.fillMaxSize().background(ClaudeCanvas)) {
        AndroidView(
            factory = { context ->
                LaunchVideoView(
                    context = context,
                    videoRes = video.videoRes,
                    onFirstFrame = { firstFrameShown = true },
                    onEnded = finish,
                )
            },
            onRelease = { it.release() },
            modifier = Modifier
                .fillMaxSize()
                .alpha(videoAlpha)
                .semantics { contentDescription = "启动动画" },
        )
    }
}

/**
 * TextureView + MediaPlayer 播放 `res/raw` 里的视频，按 center-crop 铺满整屏。
 *
 * 不用 VideoView：它是 SurfaceView，首帧之前会先黑一下，而且只会等比缩放留边，
 * 屏幕比例和 20:9 的视频不一致时铺不满。
 */
private class LaunchVideoView(
    context: Context,
    @RawRes private val videoRes: Int,
    private val onFirstFrame: () -> Unit,
    private val onEnded: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val target = Surface(texture).also { surface = it }
        val mediaPlayer = MediaPlayer()
        try {
            context.resources.openRawResourceFd(videoRes).use { fd ->
                mediaPlayer.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        } catch (e: Exception) {
            mediaPlayer.release()
            onEnded()
            return
        }
        mediaPlayer.setSurface(target)
        mediaPlayer.setVolume(0f, 0f)
        mediaPlayer.setOnVideoSizeChangedListener { _, w, h ->
            videoWidth = w
            videoHeight = h
            applyCenterCrop()
        }
        mediaPlayer.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onFirstFrame()
            false
        }
        mediaPlayer.setOnPreparedListener { it.start() }
        mediaPlayer.setOnCompletionListener { onEnded() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            onEnded()
            true
        }
        player = mediaPlayer
        mediaPlayer.prepareAsync()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        applyCenterCrop()
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    /** 可重复调用：AndroidView 释放与 SurfaceTexture 销毁都会走到这里。 */
    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    /** TextureView 默认把视频拉伸到 View 大小；这里换算回等比放大后居中裁切。 */
    private fun applyCenterCrop() {
        if (videoWidth == 0 || videoHeight == 0 || width == 0 || height == 0) return
        val scale = maxOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        val matrix = Matrix()
        matrix.setScale(
            videoWidth * scale / width,
            videoHeight * scale / height,
            width / 2f,
            height / 2f,
        )
        setTransform(matrix)
    }
}
