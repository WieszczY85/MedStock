package pl.syntaxdevteam.medstock.ui.baza.medications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MedicationCatalogViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Katalog leków"
    }
    val text: LiveData<String> = _text
}
