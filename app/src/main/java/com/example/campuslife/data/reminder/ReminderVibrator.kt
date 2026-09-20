package com.example.campuslife.data.reminder

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * 到点时的三下短震。
 *
 * **为什么直接调 `Vibrator`，而不是只靠通知渠道的振动模式**（用户实测「装了没震」的修法）：
 * 渠道那条链路上有好几个应用看不见、也改不了的静默开关——MIUI / HyperOS 的通知设置里
 * 有单独一个「振动」开关，勿扰与静音状态也会把通知振动一并吃掉，渠道重要性还可能在
 * 「不重要通知折叠」里被降级。振动是这条提醒唯一「不靠看」的通道，所以由应用自己发出来，
 * 不把它托付给通知系统。
 *
 * 三个刻意的选择：
 * - **`repeat = -1`（不循环）**：震完这三下就结束，不会一直震。
 * - **`USAGE_ALARM`**：勿扰与静音下仍然震得到。这**不是响铃、不出声**，
 *   只是让这条通道不被系统的静音策略吃掉。哪天想改成「勿扰时就不震」，
 *   把下面这一处换成 `USAGE_NOTIFICATION` 即可。
 * - 所有失败一律咽掉：没有马达、被系统拒绝，都不该让提醒本身崩掉。
 */
internal object ReminderVibrator {

    /**
     * `等 0 → 震 400 → 停 250 → 震 400 → 停 250 → 震 400`。
     *
     * 400/250 这组长度是能被身体数出「三下」的下限：再短会糊成一下，再长就成了打扰。
     */
    private val THREE_PULSES = longArrayOf(0L, 400L, 250L, 400L, 250L, 400L)

    /** 没有马达或马达被系统拒绝时静默返回，不做任何补救。 */
    fun buzz(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            val effect = VibrationEffect.createWaveform(THREE_PULSES, NO_REPEAT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, alarmAudioAttributes())
            }
        }
    }

    /** `createWaveform` 的第二个参数为 **-1 表示不重复**（0 才是从第 0 段开始循环）。 */
    private const val NO_REPEAT = -1

    /** Android 13 以下没有 `VibrationAttributes`，用旧的音频用途常量表达同一件事。 */
    private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
}
