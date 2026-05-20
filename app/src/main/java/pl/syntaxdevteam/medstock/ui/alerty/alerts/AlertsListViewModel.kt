package pl.syntaxdevteam.medstock.ui.alerty.alerts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AlertsListViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Lista alertów"
    }
    val text: LiveData<String> = _text
}
