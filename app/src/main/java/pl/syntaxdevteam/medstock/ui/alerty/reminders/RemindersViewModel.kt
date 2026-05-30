package pl.syntaxdevteam.medstock.ui.alerty.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.core.download.UserMedicationRepository
import pl.syntaxdevteam.medstock.core.reminders.MedicationReminderRepository
import pl.syntaxdevteam.medstock.core.reminders.ReminderScheduler
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication

class RemindersViewModel(application: Application) : AndroidViewModel(application) {

    private val reminderRepository = MedicationReminderRepository(getApplication())
    private val medicationRepository = UserMedicationRepository(getApplication())
    private val scheduler = ReminderScheduler(getApplication())

    private val _reminders = MutableLiveData<List<MedicationReminder>>(emptyList())
    val reminders: LiveData<List<MedicationReminder>> = _reminders

    private val _medications = MutableLiveData<List<UserMedication>>(emptyList())
    val medications: LiveData<List<UserMedication>> = _medications

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _medications.postValue(medicationRepository.getAll())
            _reminders.postValue(reminderRepository.getAll())
        }
    }

    fun findReminder(id: Long): MedicationReminder? = reminders.value.orEmpty().firstOrNull { it.id == id }

    fun saveReminder(id: Long?, hour: Int, minute: Int, dayMask: Int, label: String, medicationIds: List<Long>) {
        if (hour !in 0..23 || minute !in 0..59 || medicationIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val savedId = if (id == null || id <= 0L) {
                reminderRepository.insert(hour, minute, dayMask, enabled = true, label = label.trim(), medicationIds = medicationIds)
            } else {
                reminderRepository.update(id, hour, minute, dayMask, enabled = true, label = label.trim(), medicationIds = medicationIds)
                id
            }
            reminderRepository.findById(savedId)?.let(scheduler::schedule)
            refresh()
        }
    }

    fun setEnabled(reminder: MedicationReminder, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            reminderRepository.setEnabled(reminder.id, enabled)
            if (enabled) {
                reminderRepository.findById(reminder.id)?.let(scheduler::schedule)
            } else {
                scheduler.cancel(reminder.id)
            }
            refresh()
        }
    }

    fun delete(reminderId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.cancel(reminderId)
            reminderRepository.delete(reminderId)
            refresh()
        }
    }
}
