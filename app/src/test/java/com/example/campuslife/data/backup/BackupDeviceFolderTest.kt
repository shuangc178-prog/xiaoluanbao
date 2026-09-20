package com.example.campuslife.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDeviceFolderTest {
    @Test
    fun xiaomi14UsesStableFolderName() {
        assertEquals(
            "xiaomi14",
            BackupDeviceFolder.resolve("Xiaomi", "23127PN0CC", "houji"),
        )
    }

    @Test
    fun otherDevicesGetSeparateSafeFolderNames() {
        assertEquals(
            "google-pixel-9-pro",
            BackupDeviceFolder.resolve("Google", "Pixel 9 Pro", "komodo"),
        )
    }
}
