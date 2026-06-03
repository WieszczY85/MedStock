package pl.syntaxdevteam.medstock.core.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {

    private const val PREFERENCES_NAME = "app_locale_preferences"
    private const val KEY_LANGUAGE_MODE = "language_mode"

    fun getLanguageMode(context: Context): AppLanguageMode {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return AppLanguageMode.fromPreferenceValue(preferences.getString(KEY_LANGUAGE_MODE, null))
    }

    fun applyStoredLanguage(context: Context) {
        applyLanguageMode(getLanguageMode(context))
    }

    fun setLanguageMode(context: Context, languageMode: AppLanguageMode) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_MODE, languageMode.preferenceValue)
            .apply()
        applyLanguageMode(languageMode)
    }

    private fun applyLanguageMode(languageMode: AppLanguageMode) {
        val appLocales = languageMode.languageTag
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()

        if (AppCompatDelegate.getApplicationLocales() != appLocales) {
            AppCompatDelegate.setApplicationLocales(appLocales)
        }
    }
}
