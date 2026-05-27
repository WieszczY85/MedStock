package pl.syntaxdevteam.medstock.ui.medicationlist

import android.os.Bundle
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationEditorBinding

class MedicationEditorFragment : Fragment() {

    private var _binding: FragmentMedicationEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MedicationListViewModel by activityViewModels()
    private lateinit var suggestionRepository: MedicationCatalogSuggestionRepository
    private var suggestionJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationEditorBinding.inflate(inflater, container, false)
        suggestionRepository = MedicationCatalogSuggestionRepository(requireContext())

        val medicationId = arguments?.getLong(ARG_MEDICATION_ID, NO_ID) ?: NO_ID
        val isEdit = medicationId != NO_ID

        binding.buttonSaveMedication.text = getString(if (isEdit) R.string.medication_editor_save else R.string.medication_editor_add)

        if (isEdit) {
            val item = viewModel.medications.value.orEmpty().firstOrNull { it.id == medicationId }
            if (item != null) {
                binding.editTextMedicationName.setText(item.name)
                binding.editTextMedicationStrength.setText(item.strength)
                binding.editTextMedicationSubstance.setText(item.activeSubstance)
                binding.editTextMedicationPackageSize.setText(item.packageSize)
                binding.editTextMedicationUnit.setText(item.unit)
                binding.editTextMedicationCurrentStock.setText(item.currentStock.toString())
                binding.editTextMedicationDosage.setText(item.dosage)
                binding.editTextMedicationAlertDays.setText(item.alertDays.toString())
            }
        }
        val suggestionAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.editTextMedicationName.setAdapter(suggestionAdapter)
        binding.editTextMedicationName.threshold = 2
        binding.editTextMedicationName.setOnItemClickListener { _, _, position, _ ->
            val selectedDisplay = suggestionAdapter.getItem(position).orEmpty()
            applyCatalogSuggestion(selectedDisplay)
        }
        binding.editTextMedicationName.addTextChangedListener(SimpleAfterTextChanged {
            val current = it.toString()
            suggestionJob?.cancel()
            suggestionJob = viewLifecycleOwner.lifecycleScope.launch {
                val suggestions = withContext(Dispatchers.IO) {
                    suggestionRepository.search(current)
                }
                if (!isAdded) return@launch
                suggestionCache = suggestions.associateBy { item -> item.displayName }
                suggestionAdapter.clear()
                suggestionAdapter.addAll(suggestions.map { item -> item.displayName })
                suggestionAdapter.notifyDataSetChanged()
            }
        })

        binding.buttonSaveMedication.setOnClickListener {
            if (isEdit) {
                viewModel.updateMedication(
                    medicationId,
                    binding.editTextMedicationName.text.toString(),
                    binding.editTextMedicationStrength.text.toString(),
                    binding.editTextMedicationSubstance.text.toString(),
                    binding.editTextMedicationPackageSize.text.toString(),
                    binding.editTextMedicationUnit.text.toString(),
                    binding.editTextMedicationCurrentStock.text.toString(),
                    binding.editTextMedicationDosage.text.toString(),
                    binding.editTextMedicationAlertDays.text.toString()
                )
            } else {
                viewModel.addMedication(
                    binding.editTextMedicationName.text.toString(),
                    binding.editTextMedicationStrength.text.toString(),
                    binding.editTextMedicationSubstance.text.toString(),
                    binding.editTextMedicationPackageSize.text.toString(),
                    binding.editTextMedicationUnit.text.toString(),
                    binding.editTextMedicationCurrentStock.text.toString(),
                    binding.editTextMedicationDosage.text.toString(),
                    binding.editTextMedicationAlertDays.text.toString()
                )
            }

            val stockRaw = binding.editTextMedicationCurrentStock.text.toString().trim()
            val alertRaw = binding.editTextMedicationAlertDays.text.toString().trim()
            val invalidNumeric = (stockRaw.isNotBlank() && stockRaw.toIntOrNull() == null) ||
                (alertRaw.isNotBlank() && alertRaw.toIntOrNull() == null)

            if (binding.editTextMedicationName.text.toString().trim().isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.medication_editor_required), Toast.LENGTH_SHORT).show()
            } else if (invalidNumeric) {
                Toast.makeText(requireContext(), getString(R.string.medication_editor_invalid_numbers), Toast.LENGTH_SHORT).show()
            } else {
                findNavController().navigateUp()
            }
        }
        binding.buttonDeleteMedication.setOnClickListener {
            viewModel.deleteMedication(medicationId)
            findNavController().navigateUp()
        }
        binding.buttonDeleteMedication.visibility = if (isEdit) View.VISIBLE else View.GONE

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_MEDICATION_ID = "medication_id"
        private const val NO_ID = -1L
    }

    private var suggestionCache: Map<String, MedicationCatalogSuggestion> = emptyMap()

    private fun applyCatalogSuggestion(selectedDisplay: String) {
        val selected = suggestionCache[selectedDisplay] ?: return
        binding.editTextMedicationName.setText(selected.displayName)
        binding.editTextMedicationName.setSelection(binding.editTextMedicationName.text.length)
        if (binding.editTextMedicationStrength.text.isNullOrBlank()) binding.editTextMedicationStrength.setText(selected.strength)
        if (binding.editTextMedicationPackageSize.text.isNullOrBlank()) binding.editTextMedicationPackageSize.setText(selected.packageDescription)
        if (binding.editTextMedicationSubstance.text.isNullOrBlank()) binding.editTextMedicationSubstance.setText(selected.activeSubstance)
    }
}
