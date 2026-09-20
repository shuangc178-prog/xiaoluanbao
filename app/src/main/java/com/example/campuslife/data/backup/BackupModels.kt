package com.example.campuslife.data.backup

import com.example.campuslife.data.card.CardCollectionStoredData
import com.example.campuslife.data.checkin.CheckInStoredData
import com.example.campuslife.data.journal.JournalData
import com.example.campuslife.data.plan.DailyPlan
import com.example.campuslife.data.study.StudyDocument
import com.example.campuslife.data.study.StudyTaskState

/** 备份包里真正需要恢复的业务状态；WebDAV 账号与调度状态刻意不进入备份。 */
internal data class AppBackupSnapshot(
    val checkIn: CheckInStoredData,
    val plans: List<DailyPlan>,
    val journal: JournalData,
    val cards: CardCollectionStoredData,
    val studyDocuments: List<StudyDocument>,
    val darkTheme: Boolean?,
    /** 阅读器里任务列表的勾选覆盖层；导入副本本身不可写，这份状态同样要跟着备份走。 */
    val studyTaskStates: List<StudyTaskState> = emptyList(),
)

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
