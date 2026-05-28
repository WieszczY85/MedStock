package pl.syntaxdevteam.medstock.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import java.io.File
import java.text.DateFormat
import java.util.Date

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableLiveData<SettingsUiState>()
    val uiState: LiveData<SettingsUiState> = _uiState

    init {
        loadSettingsInfo()
    }

    private fun loadSettingsInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val appName = context.getString(R.string.app_name)
            val author = context.getString(R.string.settings_author_value)
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: context.getString(R.string.settings_unknown_value)
            val version = context.getString(R.string.settings_version_value, versionName)

            val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)
            val lastDbUpdate = dbHelper.readableDatabase.rawQuery(
                "SELECT MAX(data_snapshot) FROM (SELECT data_snapshot FROM rpl UNION ALL SELECT data_snapshot FROM ra UNION ALL SELECT data_snapshot FROM rdg)",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

            val lastDbUpdateValue = if (lastDbUpdate.isNullOrBlank()) {
                context.getString(R.string.settings_unknown_value)
            } else {
                lastDbUpdate
            }

            val dbSizeValue = resolveDatabaseSize(dbHelper.readableDatabase.path)

            _uiState.postValue(
                SettingsUiState(
                    appName = appName,
                    author = author,
                    version = version,
                    lastDatabaseUpdate = lastDbUpdateValue,
                    databaseSize = dbSizeValue
                )
            )
        }
    }

    private fun resolveDatabaseSize(databasePath: String): String {
        val context = getApplication<Application>()
        val file = File(databasePath)
        if (!file.exists()) return context.getString(R.string.settings_unknown_value)

        val bytes = file.length()
        val formatter = android.text.format.Formatter.formatShortFileSize(context, bytes)
        val modifiedAt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(file.lastModified()))
        return context.getString(R.string.settings_database_size_value, formatter, modifiedAt)
    }
}

data class SettingsUiState(
    val appName: String,
    val author: String,
    val version: String,
    val lastDatabaseUpdate: String,
    val databaseSize: String
)
