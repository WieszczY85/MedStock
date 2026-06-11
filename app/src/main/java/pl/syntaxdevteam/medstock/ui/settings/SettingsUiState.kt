package pl.syntaxdevteam.medstock.ui.settings

import pl.syntaxdevteam.medstock.core.i18n.AppLanguageMode
import pl.syntaxdevteam.medstock.core.theme.AppColorPalette
import pl.syntaxdevteam.medstock.core.theme.AppThemeMode

data class SettingsUiState(
    val versionName: String,
    val lastDatabaseUpdate: String?,
    val databaseSizeBytes: Long?,
    val databaseModifiedAtMillis: Long?,
    val themeMode: AppThemeMode,
    val colorPalette: AppColorPalette,
    val languageMode: AppLanguageMode,
    val showInactivePharmacies: Boolean
)
