package pl.syntaxdevteam.medstock.core.i18n

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat

object LocaleManager {

    private val supportedLanguageTags = setOf("en", "pl", "de")

    fun applySystemLocale(context: Context) {
        val systemLocales = ConfigurationCompat.getLocales(context.resources.configuration)
        val firstSystemLocale = systemLocales[0]

        val resolvedLanguageTag = firstSystemLocale
            ?.toLanguageTag()
            ?.takeIf { languageTag ->
                val languageCode = languageTag.substringBefore('-')
                supportedLanguageTags.contains(languageCode)
            }

        val appLocales = resolvedLanguageTag
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()

        AppCompatDelegate.setApplicationLocales(appLocales)
    }
}
