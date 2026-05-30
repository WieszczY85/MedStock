package pl.syntaxdevteam.medstock.ui.alerty.reminders

import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

data class MedicationReminder(
    val id: Long,
    val hour: Int,
    val minute: Int,
    val dayMask: Int,
    val enabled: Boolean,
    val label: String,
    val medicationIds: List<Long>,
    val medications: List<UserMedication> = emptyList(),
) {
    val timeLabel: String = "%02d:%02d".format(hour, minute)

    fun repeatsOnAnyDay(): Boolean = dayMask != 0
}
