package pl.syntaxdevteam.medstock.ui.baza.medications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import pl.syntaxdevteam.medstock.R

class MedicationCatalogViewModel : ViewModel() {

    private val _textRes = MutableLiveData<Int>().apply {
        value = R.string.menu_baza_leki
    }
    val textRes: LiveData<Int> = _textRes
}
