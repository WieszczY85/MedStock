package pl.syntaxdevteam.medstock.ui.settings

import pl.syntaxdevteam.medstock.core.i18n.AppLanguageMode
import pl.syntaxdevteam.medstock.core.theme.AppThemeMode

data class SettingsUiState(
    val appName: String,
    val author: String,
    val version: String,
    val lastDatabaseUpdate: String,
    val databaseSize: String,
    val themeMode: AppThemeMode,
    val languageMode: AppLanguageMode
)
