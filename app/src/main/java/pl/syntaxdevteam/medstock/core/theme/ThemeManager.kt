package pl.syntaxdevteam.medstock.core.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREFERENCES_NAME = "app_theme_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLOR_PALETTE = "color_palette"

    fun getThemeMode(context: Context): AppThemeMode {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return AppThemeMode.fromPreferenceValue(preferences.getString(KEY_THEME_MODE, null))
    }

    fun getColorPalette(context: Context): AppColorPalette {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return AppColorPalette.fromPreferenceValue(preferences.getString(KEY_COLOR_PALETTE, null))
    }

    fun getActivityThemeResId(context: Context): Int = getColorPalette(context).themeResId

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

    fun setColorPalette(context: Context, colorPalette: AppColorPalette) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COLOR_PALETTE, colorPalette.preferenceValue)
            .apply()
    }

    private fun applyThemeMode(themeMode: AppThemeMode) {
        if (AppCompatDelegate.getDefaultNightMode() != themeMode.nightMode) {
            AppCompatDelegate.setDefaultNightMode(themeMode.nightMode)
        }
    }
}
