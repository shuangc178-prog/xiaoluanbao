package com.example.campuslife.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.campuslife.data.checkin.CHECK_IN_DAY_START_HOUR
import com.example.campuslife.data.checkin.checkInEpochDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class CloudBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settingsStore = CloudBackupSettingsStore(applicationContext)
        if (!settingsStore.current().automaticBackupEnabled) return Result.success()
        val credentials = try {
            settingsStore.credentials()
        } catch (_: Exception) {
            settingsStore.markFailure("无法读取已保存的坚果云应用密码，请重新连接")
            return Result.success()
        } ?: return Result.success()

        val today = ZonedDateTime.now().checkInEpochDay()
        if (settingsStore.current().lastSuccessfulLogicalEpochDay == today) {
            return Result.success()
        }

        return try {
            val metadata = CloudBackupService(applicationContext).backup(credentials)
            settingsStore.markSuccess(metadata)
            Result.success()
        } catch (error: Exception) {
            settingsStore.markFailure(error.userFacingBackupMessage())
            val authenticationFailure = (error as? NutstoreException)?.isAuthenticationFailure == true
            if (!authenticationFailure && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 2
    }
}

object CloudBackupScheduler {
    private const val PERIODIC_WORK_NAME = "nutstore-daily-backup"
    private const val CATCH_UP_WORK_NAME = "nutstore-backup-catch-up"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun ensureScheduledAsync(context: Context) {
        val appContext = context.applicationContext
        scope.launch { ensureScheduled(appContext) }
    }

    fun enqueueCatchUp(context: Context) {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            CATCH_UP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    internal fun ensureScheduled(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(millisUntilNextSix(now), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    internal fun millisUntilNextSix(now: ZonedDateTime): Long {
        var next = now.toLocalDate().atTime(CHECK_IN_DAY_START_HOUR, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }

    private fun networkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

internal fun Throwable.userFacingBackupMessage(): String = when (this) {
    is NutstoreException -> message ?: "坚果云连接失败"
    is BackupFormatException -> message ?: "备份文件处理失败"
    is java.net.UnknownHostException -> "当前无法连接网络"
    is java.net.SocketTimeoutException -> "连接坚果云超时，稍后会重试"
    is javax.net.ssl.SSLException -> "坚果云安全连接失败"
    else -> "备份失败，请稍后重试"
}
