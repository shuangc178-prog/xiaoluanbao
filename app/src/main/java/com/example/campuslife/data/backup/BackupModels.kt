package com.example.campuslife.data.backup

import com.example.campuslife.data.card.CardCollectionStoredData
import com.example.campuslife.data.checkin.CheckInStoredData
import com.example.campuslife.data.journal.JournalData
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.plan.pauseLearning
import com.example.campuslife.data.study.StudyDocument

/** 备份包里真正需要恢复的业务状态；WebDAV 账号与调度状态刻意不进入备份。 */
internal data class AppBackupSnapshot(
    val checkIn: CheckInStoredData,
    val plans: List<DailyPlan>,
    val journal: JournalData,
    val cards: CardCollectionStoredData,
    val studyDocuments: List<StudyDocument>,
    val darkTheme: Boolean?,
)

/** 活动计时只计到备份生成时刻；此副本不改变本机的活动段。 */
internal fun AppBackupSnapshot.pauseTaskTimers(atEpochMillis: Long): AppBackupSnapshot =
    copy(plans = plans.map { it.pauseLearning(atEpochMillis) })

data class BackupArchiveManifest(
    val formatVersion: Int,
    val createdAtEpochMillis: Long,
    val logicalEpochDay: Long,
    val appVersionName: String,
)

data class CreatedBackup(
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val manifest: BackupArchiveManifest,
)

data class RemoteBackupMetadata(
    val fileName: String,
    val fileSize: Long,
    val sha256: String,
    val createdAtEpochMillis: Long,
    val logicalEpochDay: Long,
    val appVersionName: String,
)

data class NutstoreCredentials(
    val email: String,
    /** 坚果云“第三方应用管理”里生成的应用密码，不是账户登录密码。 */
    val appPassword: String,
)

class BackupFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class NutstoreException(
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    val isAuthenticationFailure: Boolean get() = httpStatus == 401 || httpStatus == 403
}
