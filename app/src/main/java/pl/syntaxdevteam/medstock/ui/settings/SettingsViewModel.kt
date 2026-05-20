package pl.syntaxdevteam.medstock.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import pl.syntaxdevteam.medstock.R

class SettingsViewModel : ViewModel() {

    private val _textRes = MutableLiveData<Int>().apply {
        value = R.string.ui_preview_title
    }
    val textRes: LiveData<Int> = _textRes
}
