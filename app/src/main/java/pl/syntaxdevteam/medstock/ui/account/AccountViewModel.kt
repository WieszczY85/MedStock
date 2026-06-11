package pl.syntaxdevteam.medstock.ui.account

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.account.AccountState
import pl.syntaxdevteam.medstock.core.account.AccountStateStore
import pl.syntaxdevteam.medstock.core.account.BackupSnapshotMetadata
import pl.syntaxdevteam.medstock.core.account.DriveBackupAuthorizationRequiredException
import pl.syntaxdevteam.medstock.core.account.DriveBackupScheduler
import pl.syntaxdevteam.medstock.core.account.DriveBackupSnapshotRepository
import pl.syntaxdevteam.medstock.core.account.GoogleAccountProfileClient
import pl.syntaxdevteam.medstock.core.account.GoogleProfileAuthorizationRequiredException
import pl.syntaxdevteam.medstock.core.reminders.ReminderScheduler
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val accountStore = AccountStateStore(application)
    private val backupRepository = DriveBackupSnapshotRepository(application)
    private val profileClient = GoogleAccountProfileClient(application)
    private val reminderScheduler = ReminderScheduler(application)

    private val _uiState = MutableLiveData<AccountUiState>()
    val uiState: LiveData<AccountUiState> = _uiState
    private var pendingDriveAction: PendingDriveAction? = null
    private var pendingAuthorizationTarget: PendingAuthorizationTarget? = null

    init {
        val state = accountStore.getState()
        publishState(state)
        if (state.isConnected) {
            refreshGoogleProfile()
        }
    }

    fun connect(email: String) {
        accountStore.connect(email)
        val state = accountStore.getState()
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            finishConnect(state.email)
        }
    }

    fun disconnect() {
        DriveBackupScheduler.cancel(getApplication())
        accountStore.disconnect()
        publishState(accountStore.getState(), transientMessageRes = R.string.account_google_disconnected_message)
    }

    fun setDriveBackupEnabled(enabled: Boolean) {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        accountStore.setDriveBackupEnabled(enabled)
        if (enabled) {
            createBackupSnapshot()
        } else {
            DriveBackupScheduler.cancel(getApplication())
            publishState(accountStore.getState(), transientMessageRes = R.string.account_drive_disabled_message)
        }
    }

    fun restoreBackupSnapshot() {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        pendingDriveAction = PendingDriveAction.RESTORE_BACKUP
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                backupRepository.restoreLatestSnapshot(state.email)
                reminderScheduler.rescheduleAll()
                accountStore.getState()
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        pendingAuthorizationTarget = null
                        pendingDriveAction = null
                        publishState(it, transientMessageRes = R.string.account_drive_restore_success_message)
                    },
                    onFailure = { throwable -> handleDriveFailure(throwable, R.string.account_drive_restore_failed_message) }
                )
            }
        }
    }

    fun dismissRestorePrompt() {
        _uiState.value = _uiState.value?.copy(restorePrompt = null)
    }

    fun createBackupSnapshot() {
        val state = accountStore.getState()
        if (!state.isConnected) {
            publishState(state, transientMessageRes = R.string.account_drive_requires_google)
            return
        }
        pendingDriveAction = PendingDriveAction.CREATE_BACKUP
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                backupRepository.createAndUploadSnapshot(state.email)
                accountStore.markBackupCreated()
                accountStore.getState()
            }
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        pendingAuthorizationTarget = null
                        pendingDriveAction = null
                        DriveBackupScheduler.schedule(getApplication())
                        publishState(it, transientMessageRes = R.string.account_drive_snapshot_uploaded_message)
                    },
                    onFailure = { throwable -> handleDriveFailure(throwable, R.string.account_drive_snapshot_failed_message) }
                )
            }
        }
    }

    fun refreshGoogleProfile() {
        val state = accountStore.getState()
        if (!state.isConnected) return
        pendingDriveAction = PendingDriveAction.REFRESH_PROFILE
        _uiState.value = buildUiState(state, isBusy = true)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { profileClient.fetchProfile(state.email) }
            val authorizationException = result.exceptionOrNull() as? GoogleProfileAuthorizationRequiredException
            if (authorizationException?.authorizationIntent != null) {
                pendingAuthorizationTarget = PendingAuthorizationTarget.GOOGLE_PROFILE
                withContext(Dispatchers.Main) {
                    _uiState.value = buildUiState(
                        state = accountStore.getState(),
                        transientMessageRes = R.string.account_google_authorization_required_message,
                        driveAuthorizationIntent = authorizationException.authorizationIntent,
                    )
                }
                return@launch
            }
            result.getOrNull()?.avatarUrl?.let { accountStore.setAvatarUrl(state.email, it) }
            withContext(Dispatchers.Main) {
                pendingAuthorizationTarget = null
                pendingDriveAction = null
                publishState(
                    accountStore.getState(),
                    transientMessageRes = if (result.isFailure) R.string.account_google_avatar_failed_message else null,
                )
            }
        }
    }

    fun driveAuthorizationLaunched() {
        _uiState.value = _uiState.value?.copy(driveAuthorizationIntent = null)
    }

    fun driveAuthorizationCancelled() {
        when (pendingAuthorizationTarget) {
            PendingAuthorizationTarget.GOOGLE_PROFILE -> {
                pendingAuthorizationTarget = null
                if (pendingDriveAction == PendingDriveAction.CONNECT_LOOKUP) {
                    val state = accountStore.getState()
                    _uiState.value = buildUiState(state, isBusy = true)
                    viewModelScope.launch(Dispatchers.IO) {
                        findBackupAfterConnect(state)
                    }
                } else {
                    pendingDriveAction = null
                    publishState(
                        accountStore.getState(),
                        transientMessageRes = R.string.account_google_avatar_failed_message,
                    )
                }
            }
            PendingAuthorizationTarget.GOOGLE_DRIVE, null -> {
                pendingAuthorizationTarget = null
                pendingDriveAction = null
                publishState(accountStore.getState(), transientMessageRes = R.string.account_drive_authorization_cancelled_message)
            }
        }
    }

    fun retryPendingDriveAction() {
        when (pendingDriveAction) {
            PendingDriveAction.CONNECT_LOOKUP -> {
                val state = accountStore.getState()
                _uiState.value = buildUiState(state, isBusy = true)
                viewModelScope.launch(Dispatchers.IO) { finishConnect(state.email) }
            }
            PendingDriveAction.CREATE_BACKUP -> createBackupSnapshot()
            PendingDriveAction.RESTORE_BACKUP -> restoreBackupSnapshot()
            PendingDriveAction.REFRESH_PROFILE -> refreshGoogleProfile()
            null -> publishState(accountStore.getState())
        }
    }

    fun messageShown() {
        _uiState.value = _uiState.value?.copy(transientMessageRes = null)
    }

    private suspend fun finishConnect(email: String) {
        pendingDriveAction = PendingDriveAction.CONNECT_LOOKUP
        val profileResult = runCatching { profileClient.fetchProfile(email) }
        val authorizationException = profileResult.exceptionOrNull() as? GoogleProfileAuthorizationRequiredException
        if (authorizationException?.authorizationIntent != null) {
            pendingAuthorizationTarget = PendingAuthorizationTarget.GOOGLE_PROFILE
            withContext(Dispatchers.Main) {
                _uiState.value = buildUiState(
                    state = accountStore.getState(),
                    transientMessageRes = R.string.account_google_authorization_required_message,
                    driveAuthorizationIntent = authorizationException.authorizationIntent,
                )
            }
            return
        }
        pendingAuthorizationTarget = null
        profileResult.getOrNull()?.avatarUrl?.let { avatarUrl ->
            accountStore.setAvatarUrl(email, avatarUrl)
        }
        findBackupAfterConnect(accountStore.getState())
    }

    private suspend fun findBackupAfterConnect(state: AccountState) {
        pendingDriveAction = PendingDriveAction.CONNECT_LOOKUP
        val result = runCatching {
            backupRepository.findRestorableSnapshot(state.email)?.let { metadata ->
                AccountRestorePrompt(
                    createdAtText = metadata.formattedCreatedAt(),
                    medicationCount = metadata.medicationCount,
                    reminderCount = metadata.reminderCount,
                )
            }
        }
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { restorePrompt ->
                    pendingAuthorizationTarget = null
                    pendingDriveAction = null
                    publishState(
                        state = accountStore.getState(),
                        transientMessageRes = if (restorePrompt == null) R.string.account_drive_restore_no_backup_message else null,
                        restorePrompt = restorePrompt,
                    )
                },
                onFailure = { throwable -> handleDriveFailure(throwable, R.string.account_drive_restore_failed_message) }
            )
        }
    }

    private fun handleDriveFailure(throwable: Throwable, fallbackMessageRes: Int) {
        val state = accountStore.getState()
        if (throwable is DriveBackupAuthorizationRequiredException && throwable.authorizationIntent != null) {
            pendingAuthorizationTarget = PendingAuthorizationTarget.GOOGLE_DRIVE
            _uiState.value = buildUiState(
                state = state,
                transientMessageRes = R.string.account_drive_authorization_required_message,
                driveAuthorizationIntent = throwable.authorizationIntent,
            )
        } else {
            pendingAuthorizationTarget = null
            pendingDriveAction = null
            publishState(state, transientMessageRes = fallbackMessageRes)
        }
    }

    private fun publishState(
        state: AccountState,
        transientMessageRes: Int? = null,
        restorePrompt: AccountRestorePrompt? = null,
    ) {
        _uiState.value = buildUiState(
            state = state,
            transientMessageRes = transientMessageRes,
            restorePrompt = restorePrompt,
        )
    }

    private fun buildUiState(
        state: AccountState,
        isBusy: Boolean = false,
        transientMessageRes: Int? = null,
        restorePrompt: AccountRestorePrompt? = null,
        driveAuthorizationIntent: Intent? = null,
    ): AccountUiState {
        val context = getApplication<Application>()
        val lastBackup = state.formattedLastBackup()
        return AccountUiState(
            isConnected = state.isConnected,
            email = state.email,
            avatarLabel = state.avatarLabel,
            avatarUrl = state.avatarUrl,
            driveBackupEnabled = state.driveBackupEnabled,
            driveControlsEnabled = state.isConnected && !isBusy,
            isBusy = isBusy,
            accountStatusText = if (state.isConnected) {
                context.getString(R.string.account_google_connected_status, state.email)
            } else {
                context.getString(R.string.account_google_disconnected_status)
            },
            driveStatusText = when {
                !state.isConnected -> context.getString(R.string.account_drive_status_connect_first)
                state.driveBackupEnabled && lastBackup != null -> context.getString(R.string.account_drive_status_enabled_with_date, lastBackup)
                state.driveBackupEnabled -> context.getString(R.string.account_drive_status_enabled)
                else -> context.getString(R.string.account_drive_status_disabled)
            },
            transientMessageRes = transientMessageRes,
            restorePrompt = restorePrompt,
            driveAuthorizationIntent = driveAuthorizationIntent,
        )
    }

    private fun BackupSnapshotMetadata.formattedCreatedAt(zoneId: ZoneId = ZoneId.systemDefault()): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .format(Instant.ofEpochMilli(createdAtUtc).atZone(zoneId))
    }
}

data class AccountUiState(
    val isConnected: Boolean,
    val email: String,
    val avatarLabel: String,
    val avatarUrl: String?,
    val driveBackupEnabled: Boolean,
    val driveControlsEnabled: Boolean,
    val isBusy: Boolean,
    val accountStatusText: String,
    val driveStatusText: String,
    val transientMessageRes: Int?,
    val restorePrompt: AccountRestorePrompt?,
    val driveAuthorizationIntent: Intent?,
)

private enum class PendingDriveAction {
    CONNECT_LOOKUP,
    CREATE_BACKUP,
    RESTORE_BACKUP,
    REFRESH_PROFILE,
}

private enum class PendingAuthorizationTarget {
    GOOGLE_PROFILE,
    GOOGLE_DRIVE,
}

data class AccountRestorePrompt(
    val createdAtText: String,
    val medicationCount: Int,
    val reminderCount: Int,
)
