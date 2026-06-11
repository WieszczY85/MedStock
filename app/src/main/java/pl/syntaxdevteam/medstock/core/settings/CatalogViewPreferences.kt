package pl.syntaxdevteam.medstock.core.settings

import android.content.Context

object CatalogViewPreferences {
    private const val PREFERENCES_NAME = "catalog_view_preferences"
    private const val KEY_SHOW_INACTIVE_PHARMACIES = "show_inactive_pharmacies"

    fun shouldShowInactivePharmacies(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_INACTIVE_PHARMACIES, false)

    fun setShowInactivePharmacies(context: Context, showInactivePharmacies: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_INACTIVE_PHARMACIES, showInactivePharmacies)
            .apply()
    }
}
