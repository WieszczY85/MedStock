package pl.syntaxdevteam.medstock.ui.alerty.reminders

import android.content.Context
import pl.syntaxdevteam.medstock.R

private val fullDayNames = listOf(
    R.string.reminder_day_monday,
    R.string.reminder_day_tuesday,
    R.string.reminder_day_wednesday,
    R.string.reminder_day_thursday,
    R.string.reminder_day_friday,
    R.string.reminder_day_saturday,
    R.string.reminder_day_sunday,
)

private val shortDayNames = listOf(
    R.string.reminder_day_monday_short,
    R.string.reminder_day_tuesday_short,
    R.string.reminder_day_wednesday_short,
    R.string.reminder_day_thursday_short,
    R.string.reminder_day_friday_short,
    R.string.reminder_day_saturday_short,
    R.string.reminder_day_sunday_short,
)

fun reminderDayShortLabels(context: Context): List<String> = shortDayNames.map(context::getString)

fun Int.toDayLabel(context: Context): String {
    if (this == 0) return context.getString(R.string.reminder_once)
    val allDaysMask = (1 shl fullDayNames.size) - 1
    if (this and allDaysMask == allDaysMask) return context.getString(R.string.reminder_every_day)
    return fullDayNames.mapIndexedNotNull { index, stringRes ->
        if (this and (1 shl index) != 0) context.getString(stringRes) else null
    }.joinToString(", ")
}
