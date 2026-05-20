package pl.syntaxdevteam.medstock.ui.transform

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TransformViewModel : ViewModel() {

    private val _itemNumbers = MutableLiveData<List<Int>>().apply {
        value = (1..16).toList()
    }

    val itemNumbers: LiveData<List<Int>> = _itemNumbers
}
