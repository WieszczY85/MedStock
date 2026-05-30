package pl.syntaxdevteam.medstock.core.theme

import androidx.appcompat.app.AppCompatDelegate

enum class AppThemeMode(
    val preferenceValue: String,
    val nightMode: Int
) {
    AUTO(
        preferenceValue = "auto",
        nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    ),
    ON(
        preferenceValue = "on",
        nightMode = AppCompatDelegate.MODE_NIGHT_YES
    ),
    OFF(
        preferenceValue = "off",
        nightMode = AppCompatDelegate.MODE_NIGHT_NO
    );

    companion object {
        fun fromPreferenceValue(value: String?): AppThemeMode = entries.firstOrNull { mode ->
            mode.preferenceValue == value
        } ?: AUTO
    }
}
