package pl.syntaxdevteam.medstock

import android.app.Application
import pl.syntaxdevteam.medstock.core.i18n.LocaleManager
import pl.syntaxdevteam.medstock.core.theme.ThemeManager

class MedStockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ThemeManager.applyStoredTheme(this)
        LocaleManager.applyStoredLanguage(this)
    }
}
