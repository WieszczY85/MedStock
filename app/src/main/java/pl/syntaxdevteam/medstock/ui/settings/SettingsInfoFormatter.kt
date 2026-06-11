package pl.syntaxdevteam.medstock.ui.settings

import android.content.Context
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date
import pl.syntaxdevteam.medstock.R

data class SettingsInfoText(
    val appName: String,
    val author: String,
    val version: String,
    val lastDatabaseUpdate: String,
    val databaseSize: String
)

object SettingsInfoFormatter {

    fun format(context: Context, state: SettingsUiState): SettingsInfoText {
        val unknownValue = context.getString(R.string.settings_unknown_value)
        val versionName = state.versionName.ifBlank { unknownValue }
        val lastDatabaseUpdate = state.lastDatabaseUpdate?.let { snapshotDate ->
            context.getString(R.string.settings_db_date_value, snapshotDate)
        } ?: unknownValue
        val databaseSize = if (
            state.databaseSizeBytes != null && state.databaseModifiedAtMillis != null
        ) {
            val formattedSize = Formatter.formatShortFileSize(context, state.databaseSizeBytes)
            val locale = context.resources.configuration.locales[0]
            val modifiedAt = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                locale
            ).format(Date(state.databaseModifiedAtMillis))
            context.getString(R.string.settings_database_size_value, formattedSize, modifiedAt)
        } else {
            unknownValue
        }

        return SettingsInfoText(
            appName = context.getString(R.string.app_name),
            author = context.getString(R.string.settings_author_value),
            version = context.getString(R.string.settings_version_value, versionName),
            lastDatabaseUpdate = lastDatabaseUpdate,
            databaseSize = databaseSize
        )
    }
}
