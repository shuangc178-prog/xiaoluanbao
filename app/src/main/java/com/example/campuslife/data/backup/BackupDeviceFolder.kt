package com.example.campuslife.data.backup

import android.os.Build
import java.util.Locale

internal object BackupDeviceFolder {
    fun current(): String = resolve(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
    )

    internal fun resolve(manufacturer: String, model: String, device: String): String {
        if (device.equals("houji", ignoreCase = true) || model.equals("23127PN0CC", ignoreCase = true)) {
            return "xiaomi14"
        }

        return "$manufacturer-$model"
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(MAX_FOLDER_NAME_LENGTH)
            .ifBlank { "android-device" }
    }

    private const val MAX_FOLDER_NAME_LENGTH = 48
}
