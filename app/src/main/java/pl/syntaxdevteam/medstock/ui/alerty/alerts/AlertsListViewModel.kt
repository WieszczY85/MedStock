package pl.syntaxdevteam.medstock.ui.alerty.alerts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import pl.syntaxdevteam.medstock.R

class AlertsListViewModel : ViewModel() {

    private val _textRes = MutableLiveData<Int>().apply {
        value = R.string.menu_alerty_lista
    }
    val textRes: LiveData<Int> = _textRes
}
