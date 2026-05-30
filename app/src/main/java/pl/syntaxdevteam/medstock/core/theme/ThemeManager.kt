package pl.syntaxdevteam.medstock.core.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFERENCES_NAME = "app_theme_preferences"
    private const val KEY_THEME_MODE = "theme_mode"

    fun getThemeMode(context: Context): AppThemeMode {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return AppThemeMode.fromPreferenceValue(preferences.getString(KEY_THEME_MODE, null))
    }

    fun applyStoredTheme(context: Context) {
        applyThemeMode(getThemeMode(context))
    }

    fun setThemeMode(context: Context, themeMode: AppThemeMode) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, themeMode.preferenceValue)
            .apply()
        applyThemeMode(themeMode)
    }

    private fun applyThemeMode(themeMode: AppThemeMode) {
        if (AppCompatDelegate.getDefaultNightMode() != themeMode.nightMode) {
            AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
        }
    }
}
