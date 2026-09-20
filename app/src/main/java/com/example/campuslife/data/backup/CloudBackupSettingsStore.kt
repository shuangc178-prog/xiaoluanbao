package com.example.campuslife.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.cloudBackupDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "cloud_backup")

data class CloudBackupSettings(
    val configured: Boolean = false,
    val automaticBackupEnabled: Boolean = false,
    val email: String = "",
    val lastSuccessAtEpochMillis: Long? = null,
    val lastFileName: String? = null,
    val lastFileSize: Long? = null,
    val lastSuccessfulLogicalEpochDay: Long? = null,
    val lastError: String? = null,
)

class CloudBackupSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val cipher = CredentialCipher()

    val settings: Flow<CloudBackupSettings> = appContext.cloudBackupDataStore.data.map(::decodeSettings)

    suspend fun current(): CloudBackupSettings = settings.first()

    suspend fun credentials(): NutstoreCredentials? {
        val preferences = appContext.cloudBackupDataStore.data.first()
        if (preferences[ENABLED_KEY] != true) return null
        val email = preferences[EMAIL_KEY].orEmpty()
        val encrypted = preferences[PASSWORD_KEY].orEmpty()
        if (email.isBlank() || encrypted.isBlank()) return null
        return withContext(Dispatchers.IO) {
            NutstoreCredentials(email = email, appPassword = cipher.decrypt(encrypted))
        }
    }

    suspend fun saveCredentials(
        credentials: NutstoreCredentials,
        automaticBackupEnabled: Boolean = true,
    ) {
        val email = credentials.email.trim()
        require(email.isNotBlank() && credentials.appPassword.isNotBlank())
        val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(credentials.appPassword) }
        appContext.cloudBackupDataStore.edit { preferences ->
            preferences[EMAIL_KEY] = email
            preferences[PASSWORD_KEY] = encrypted
            preferences[ENABLED_KEY] = true
            preferences[AUTOMATIC_BACKUP_ENABLED_KEY] = automaticBackupEnabled
            preferences.remove(LAST_ERROR_KEY)
        }
    }

    suspend fun enableAutomaticBackup() {
        appContext.cloudBackupDataStore.edit { preferences ->
            if (preferences[ENABLED_KEY] == true) {
                preferences[AUTOMATIC_BACKUP_ENABLED_KEY] = true
            }
        }
    }

    suspend fun markSuccess(metadata: RemoteBackupMetadata) {
        appContext.cloudBackupDataStore.edit { preferences ->
            preferences[LAST_SUCCESS_AT_KEY] = System.currentTimeMillis()
            preferences[LAST_FILE_NAME_KEY] = metadata.fileName
            preferences[LAST_FILE_SIZE_KEY] = metadata.fileSize
            preferences[LAST_SUCCESSFUL_DAY_KEY] = metadata.logicalEpochDay
            preferences.remove(LAST_ERROR_KEY)
        }
    }

    suspend fun markFailure(message: String) {
        appContext.cloudBackupDataStore.edit { preferences ->
            preferences[LAST_ERROR_KEY] = message.take(MAX_ERROR_LENGTH)
        }
    }

    suspend fun disconnect() {
        appContext.cloudBackupDataStore.edit { preferences ->
            preferences.remove(EMAIL_KEY)
            preferences.remove(PASSWORD_KEY)
            preferences.remove(ENABLED_KEY)
            preferences.remove(AUTOMATIC_BACKUP_ENABLED_KEY)
            preferences.remove(LAST_ERROR_KEY)
        }
    }

    private fun decodeSettings(preferences: Preferences): CloudBackupSettings {
        val email = preferences[EMAIL_KEY].orEmpty()
        return CloudBackupSettings(
            configured = preferences[ENABLED_KEY] == true &&
                email.isNotBlank() &&
                !preferences[PASSWORD_KEY].isNullOrBlank(),
            automaticBackupEnabled = preferences[AUTOMATIC_BACKUP_ENABLED_KEY] == true,
            email = email,
            lastSuccessAtEpochMillis = preferences[LAST_SUCCESS_AT_KEY],
            lastFileName = preferences[LAST_FILE_NAME_KEY],
            lastFileSize = preferences[LAST_FILE_SIZE_KEY],
            lastSuccessfulLogicalEpochDay = preferences[LAST_SUCCESSFUL_DAY_KEY],
            lastError = preferences[LAST_ERROR_KEY],
        )
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 300
        val ENABLED_KEY = booleanPreferencesKey("enabled")
        val AUTOMATIC_BACKUP_ENABLED_KEY = booleanPreferencesKey("automatic_backup_enabled")
        val EMAIL_KEY = stringPreferencesKey("email")
        val PASSWORD_KEY = stringPreferencesKey("encrypted_app_password")
        val LAST_SUCCESS_AT_KEY = longPreferencesKey("last_success_at")
        val LAST_FILE_NAME_KEY = stringPreferencesKey("last_file_name")
        val LAST_FILE_SIZE_KEY = longPreferencesKey("last_file_size")
        val LAST_SUCCESSFUL_DAY_KEY = longPreferencesKey("last_successful_logical_day")
        val LAST_ERROR_KEY = stringPreferencesKey("last_error")
    }
}
