package pl.syntaxdevteam.medstock

import android.app.Application
import pl.syntaxdevteam.medstock.core.i18n.LocaleManager

class MedStockApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        LocaleManager.applySystemLocale(this)
    }
}
