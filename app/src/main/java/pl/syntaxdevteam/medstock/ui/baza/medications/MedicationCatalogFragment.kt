package pl.syntaxdevteam.medstock.ui.baza.medications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationCatalogBinding

class MedicationCatalogFragment : Fragment() {

    private var _binding: FragmentMedicationCatalogBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val medicationCatalogViewModel =
            ViewModelProvider(this).get(MedicationCatalogViewModel::class.java)

        _binding = FragmentMedicationCatalogBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textMedicationCatalog
        medicationCatalogViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}