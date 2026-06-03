package pl.syntaxdevteam.medstock.core.i18n

import androidx.annotation.StringRes
import pl.syntaxdevteam.medstock.R

enum class AppLanguageMode(
    val preferenceValue: String,
    val languageTag: String?,
    @param:StringRes val displayNameResId: Int
) {
    AUTO(
        preferenceValue = "auto",
        languageTag = null,
        displayNameResId = R.string.settings_language_auto
    ),
    POLISH(
        preferenceValue = "pl",
        languageTag = "pl",
        displayNameResId = R.string.settings_language_polish
    ),
    ENGLISH(
        preferenceValue = "en",
        languageTag = "en",
        displayNameResId = R.string.settings_language_english
    ),
    GERMAN(
        preferenceValue = "de",
        languageTag = "de",
        displayNameResId = R.string.settings_language_german
    ),
    FRENCH(
        preferenceValue = "fr",
        languageTag = "fr",
        displayNameResId = R.string.settings_language_french
    );

    companion object {
        fun fromPreferenceValue(value: String?): AppLanguageMode = entries.firstOrNull { mode ->
            mode.preferenceValue == value
        } ?: AUTO
    }
}
