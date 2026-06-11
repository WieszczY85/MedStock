package pl.syntaxdevteam.medstock.core.settings

import android.content.Context

object DeveloperModeManager {
    private const val PREFERENCES_NAME = "developer_preferences"
    private const val KEY_ENABLED = "developer_mode_enabled"

    fun isEnabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun enable(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .apply()
    }
}
