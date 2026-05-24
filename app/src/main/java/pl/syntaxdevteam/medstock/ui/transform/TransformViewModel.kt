package pl.syntaxdevteam.medstock.ui.transform

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

data class UserMedication(
    val name: String,
    val note: String,
)

class TransformViewModel : ViewModel() {

    private val _itemNumbers = MutableLiveData<List<UserMedication>>().apply {
        value = listOf(
            UserMedication("Ibuprofen 200 mg", "Tabletki, po śniadaniu"),
            UserMedication("Paracetamol 500 mg", "Doraźnie przeciwbólowo"),
            UserMedication("Witamina D3 4000 IU", "1 kapsułka dziennie")
        )
    }

    val itemNumbers: LiveData<List<UserMedication>> = _itemNumbers

    fun addMedication(rawName: String, rawNote: String): Boolean {
        val normalizedName = rawName.trim()
        val normalizedNote = rawNote.trim()
        if (normalizedName.isBlank() || normalizedNote.isBlank()) {
            return false
        }

        val current = _itemNumbers.value.orEmpty()
        _itemNumbers.value = current + UserMedication(normalizedName, normalizedNote)
        return true
    }

    fun updateMedication(index: Int, rawName: String, rawNote: String): Boolean {
        val normalizedName = rawName.trim()
        val normalizedNote = rawNote.trim()
        if (normalizedName.isBlank() || normalizedNote.isBlank()) return false
        val current = _itemNumbers.value.orEmpty().toMutableList()
        if (index !in current.indices) return false
        current[index] = UserMedication(normalizedName, normalizedNote)
        _itemNumbers.value = current
        return true
    }
}
