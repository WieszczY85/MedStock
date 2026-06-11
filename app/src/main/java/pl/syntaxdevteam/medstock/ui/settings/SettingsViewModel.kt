package pl.syntaxdevteam.medstock.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import pl.syntaxdevteam.medstock.core.i18n.AppLanguageMode
import pl.syntaxdevteam.medstock.core.i18n.LocaleManager
import pl.syntaxdevteam.medstock.core.theme.AppColorPalette
import pl.syntaxdevteam.medstock.core.theme.AppThemeMode
import pl.syntaxdevteam.medstock.core.theme.AppColorPalette
import pl.syntaxdevteam.medstock.core.theme.ThemeManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableLiveData<SettingsUiState>()
    val uiState: LiveData<SettingsUiState> = _uiState

    init {
        loadSettingsInfo()
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        val context = getApplication<Application>()
        ThemeManager.setThemeMode(context, themeMode)
        _uiState.value = _uiState.value?.copy(themeMode = themeMode)
    }

    fun setColorPalette(colorPalette: AppColorPalette) {
        val context = getApplication<Application>()
        ThemeManager.setColorPalette(context, colorPalette)
        _uiState.value = _uiState.value?.copy(colorPalette = colorPalette)
    }

    fun setLanguageMode(languageMode: AppLanguageMode) {
        val context = getApplication<Application>()
        LocaleManager.setLanguageMode(context, languageMode)
        _uiState.value = _uiState.value?.copy(languageMode = languageMode)
    }

    private fun loadSettingsInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val versionName = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()

            val dbHelper = RegistryIngestDatabaseHelper.getInstance(context)
            val lastDatabaseUpdate = dbHelper.readableDatabase.rawQuery(
                "SELECT MAX(data_snapshot) FROM (SELECT data_snapshot FROM rpl UNION ALL SELECT data_snapshot FROM ra UNION ALL SELECT data_snapshot FROM rdg)",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
            }
            val databaseFile = File(dbHelper.readableDatabase.path)

            _uiState.postValue(
                SettingsUiState(
                    versionName = versionName,
                    lastDatabaseUpdate = lastDatabaseUpdate,
                    databaseSizeBytes = databaseFile.takeIf(File::exists)?.length(),
                    databaseModifiedAtMillis = databaseFile.takeIf(File::exists)?.lastModified(),
                    themeMode = ThemeManager.getThemeMode(context),
                    colorPalette = ThemeManager.getColorPalette(context),
                    languageMode = LocaleManager.getLanguageMode(context)
                )
            )
        }
    }
}
