package com.example.campuslife.ui.reminder

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.campuslife.MainActivity
import com.example.campuslife.ui.theme.CampusLifeTheme

/**
 * 到点时浮在**别的应用上面**的那张提醒卡片（产品文档 16.32）。
 *
 * 为什么必须是 `TYPE_APPLICATION_OVERLAY`：应用内的弹层（Soul 那种）只活在自己的界面里，
 * 用户切到别的应用就看不见。要压住别人，只有系统级悬浮窗这一条路，
 * 而它对应一项**特殊权限** `SYSTEM_ALERT_WINDOW`（MIUI 上叫「悬浮窗」），
 * 用户不授予就什么都画不出来——所以这里每一处都要先问 [canShow] 再动手，
 * 没权限就安静退场，提醒本身还有振动与通知兜着。
 *
 * 三条硬边界，都在代码里体现而不是留给用户猜：
 * 1. **锁屏/息屏时不弹**：悬浮窗画不到锁屏之上，这是系统的安全边界。
 *    这时候硬加一个窗口只会是一张谁也看不见的卡，所以直接不弹，交给振动与通知。
 * 2. **同一时刻只留一张**：上一条还没消失时来了新提醒，先撤旧的再上新的。
 * 3. **自己会走**：[AUTO_DISMISS_MILLIS] 之后自动撤掉。提醒的价值在「被看见」，
 *    一张赖着不走的卡会挡住用户正在做的事。
 */
internal object ReminderOverlay {

    /** 卡片停留时长。够看清是哪件事，又不至于挡住手上的操作。 */
    private const val AUTO_DISMISS_MILLIS = 15_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var shown: ShownCard? = null

    /** 权限与屏幕状态都合适才谈得上弹卡片。 */
    fun canShow(context: Context): Boolean {
        val appContext = context.applicationContext
        if (!Settings.canDrawOverlays(appContext)) return false
        return isScreenReady(appContext)
    }

    /**
     * 弹一张卡片；任何一步不满足就安静返回，**不抛异常、不影响提醒的其它通道**。
     *
     * [notificationId] 是这条提醒的通知 id：用户在卡片上点「知道了」就等于处理过了，
     * 顺手把通知栏那条也撤掉，不让他再确认第二次。
     */
    fun show(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
    ) {
        val appContext = context.applicationContext
        if (!canShow(appContext)) return
        val windowManager = appContext.getSystemService(WindowManager::class.java) ?: return

        dismiss()

        val owner = OverlayComposeOwner().also { it.create() }
        val view = ComposeView(themedContext(appContext)).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                CampusLifeTheme {
                    ReminderOverlayCard(
                        title = title,
                        body = body,
                        topInset = statusBarHeight(appContext),
                        onDismiss = {
                            if (notificationId != 0) {
                                NotificationManagerCompat.from(appContext).cancel(notificationId)
                            }
                            dismiss()
                        },
                        onOpenApp = {
                            openApp(appContext)
                            dismiss()
                        },
                    )
                }
            }
        }

        // 宽度铺满、高度随卡片：窗口只占它自己那一块，卡片以外的手势照常落到下面的应用上。
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (!added) {
            owner.destroy()
            return
        }
        shown = ShownCard(windowManager, view, owner)
        mainHandler.postDelayed({ dismiss() }, AUTO_DISMISS_MILLIS)
    }

    /** 撤掉当前卡片。没卡片时是空操作——超时、点掉、被新提醒顶掉都走这里。 */
    fun dismiss() {
        mainHandler.removeCallbacksAndMessages(null)
        val card = shown ?: return
        shown = null
        runCatching { card.windowManager.removeView(card.view) }
        card.owner.destroy()
    }

    /**
     * 屏幕得亮着、且没锁。
     *
     * 锁屏时悬浮窗画不出来（系统不让），加了也是白加，所以这里直接判掉；
     * 息屏同理——那时候用户看不见卡片，只有振动和通知有意义。
     */
    private fun isScreenReady(context: Context): Boolean {
        val power = context.getSystemService(android.os.PowerManager::class.java) ?: return false
        if (!power.isInteractive) return false
        val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
        return keyguard?.isKeyguardLocked != true
    }

    /**
     * 悬浮窗的 Context 要自己套一层主题。
     *
     * 它不在任何 Activity 里，拿到的 Context 没有应用的 XML 主题，
     * 直接拿来建 View 会让一些依赖主题属性的控件取不到值；套上 `Theme.CampusLife` 之后，
     * 窗口层配色（状态栏、背景）与主界面一致。
     */
    private fun themedContext(context: Context): Context =
        android.view.ContextThemeWrapper(context, com.example.campuslife.R.style.Theme_CampusLife)

    /**
     * 状态栏高度。
     *
     * Compose 在悬浮窗里拿不到 `WindowInsets`（那个依赖 Activity 的 DecorView），
     * 所以这里从系统资源里取一次，交给卡片做顶部内边距——否则卡片会被状态栏压住半行。
     */
    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
    }

    private fun openApp(context: Context) {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
        }
    }

    private data class ShownCard(
        val windowManager: WindowManager,
        val view: android.view.View,
        val owner: OverlayComposeOwner,
    )
}

/**
 * 让 Compose 在一个**不是 Activity** 的窗口里跑起来所需的最小宿主。
 *
 * `ComposeView` 挂在窗口上时会去找 `ViewTreeLifecycleOwner` 与 `ViewTreeSavedStateRegistryOwner`，
 * 找不到就直接抛异常；悬浮窗没有 Activity 帮它提供这两个，只能自己造一份。
 * 生命周期只有两态：挂上去时 `RESUMED`，撤下来时 `DESTROYED`——悬浮卡片不需要更细的档位。
 */
internal class OverlayComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun create() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
