package pl.syntaxdevteam.medstock.ui.medicationlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
    private var barcodeLookupJob: Job? = null
    private var suggestionCache: Map<String, MedicationCatalogSuggestion> = emptyMap()
    private lateinit var unitAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationEditorBinding.inflate(inflater, container, false)
        suggestionRepository = MedicationCatalogSuggestionRepository(requireContext())
        unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.editTextMedicationUnit.setAdapter(unitAdapter)
        binding.editTextMedicationUnit.threshold = 0
        binding.editTextMedicationUnit.setOnClickListener { binding.editTextMedicationUnit.showDropDown() }

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
                binding.editTextMedicationUnit.setText(item.unit, false)
                binding.editTextMedicationCurrentStock.setText(item.currentStock.toString())
                applyDosageToFields(item.dosage)
                binding.editTextMedicationAlertDays.setText(item.alertDays.toString())
                updateUnitChoices(item.unit)
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

        val scannedCode = arguments?.getString(ARG_PACKAGE_CODE).orEmpty()
        if (scannedCode.isNotBlank()) {
            lookupScannedPackage(scannedCode)
        }

        binding.buttonSaveMedication.setOnClickListener {
            val form = readMedicationForm()
            if (!validateMedicationForm(form)) {
                return@setOnClickListener
            }

            if (isEdit) {
                viewModel.updateMedication(
                    medicationId,
                    form.name,
                    form.strength,
                    form.activeSubstance,
                    form.packageSize,
                    form.unit,
                    form.currentStock,
                    form.dosage,
                    form.alertDays
                )
                findNavController().navigateUp()
                return@setOnClickListener
            }

            val matchingMedications = viewModel.matchingActiveSubstanceMedications(form.activeSubstance)
            if (matchingMedications.isEmpty()) {
                addMedicationAsSeparateEntry(form)
            } else {
                showActiveSubstanceDuplicateDialog(form, matchingMedications)
            }
        }
        binding.buttonDeleteMedication.setOnClickListener {
            viewModel.deleteMedication(medicationId)
            findNavController().navigateUp()
        }
        binding.buttonDeleteMedication.visibility = if (isEdit) View.VISIBLE else View.GONE

        return binding.root
    }

    private fun readMedicationForm(): MedicationEditorForm {
        return MedicationEditorForm(
            name = binding.editTextMedicationName.text.toString(),
            strength = binding.editTextMedicationStrength.text.toString(),
            activeSubstance = binding.editTextMedicationSubstance.text.toString(),
            packageSize = binding.editTextMedicationPackageSize.text.toString(),
            unit = binding.editTextMedicationUnit.text.toString(),
            currentStock = binding.editTextMedicationCurrentStock.text.toString(),
            dosage = buildDosageFromFields(),
            alertDays = binding.editTextMedicationAlertDays.text.toString()
        )
    }

    private fun validateMedicationForm(form: MedicationEditorForm): Boolean {
        val stockRaw = form.currentStock.trim()
        val alertRaw = form.alertDays.trim()
        val invalidNumeric = (stockRaw.isNotBlank() && stockRaw.toIntOrNull() == null) ||
            (alertRaw.isNotBlank() && alertRaw.toIntOrNull() == null)

        return when {
            form.name.trim().isBlank() -> {
                Toast.makeText(requireContext(), getString(R.string.medication_editor_required), Toast.LENGTH_SHORT).show()
                false
            }
            invalidNumeric -> {
                Toast.makeText(requireContext(), getString(R.string.medication_editor_invalid_numbers), Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun showActiveSubstanceDuplicateDialog(
        form: MedicationEditorForm,
        matchingMedications: List<UserMedication>
    ) {
        var selectedIndex = 0
        val items = matchingMedications.map { medication ->
            getString(
                R.string.medication_duplicate_existing_item,
                medication.name,
                medication.strength.ifBlank { getString(R.string.medication_duplicate_strength_missing) },
                medication.currentStock,
                MedicationUnitFormatter.abbreviate(medication.unit).ifBlank { getString(R.string.medication_default_unit) }
            )
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.medication_duplicate_title)
            .setMessage(
                getString(
                    R.string.medication_duplicate_message,
                    form.activeSubstance.trim(),
                    form.name.trim(),
                    form.currentStock.trim().ifBlank { "0" }
                )
            )
            .setSingleChoiceItems(items, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.medication_duplicate_update_existing) { _, _ ->
                val selectedMedication = matchingMedications[selectedIndex]
                viewModel.addStockToExistingMedication(
                    selectedMedication.id,
                    form.currentStock.trim().toIntOrNull() ?: 0
                )
                findNavController().navigateUp()
            }
            .setNegativeButton(R.string.medication_duplicate_add_separate) { _, _ ->
                addMedicationAsSeparateEntry(form)
            }
            .setNeutralButton(R.string.medication_duplicate_cancel, null)
            .show()
    }

    private fun addMedicationAsSeparateEntry(form: MedicationEditorForm) {
        viewModel.addMedication(
            form.name,
            form.strength,
            form.activeSubstance,
            form.packageSize,
            form.unit,
            form.currentStock,
            form.dosage,
            form.alertDays
        )
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        suggestionJob?.cancel()
        barcodeLookupJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun applyCatalogSuggestion(selectedDisplay: String) {
        val selected = suggestionCache[selectedDisplay] ?: return
        fillFromCatalogSuggestion(selected)
    }

    private fun fillFromCatalogSuggestion(selected: MedicationCatalogSuggestion) {
        binding.editTextMedicationName.setText(selected.medicationName)
        binding.editTextMedicationName.setSelection(binding.editTextMedicationName.text.length)
        binding.editTextMedicationStrength.setText(selected.strength)
        binding.editTextMedicationPackageSize.setText(selected.packageSize)
        binding.editTextMedicationUnit.setText(selected.packageUnit, false)
        binding.editTextMedicationSubstance.setText(selected.activeSubstance)
        updateUnitChoices(selected.packageUnit, selected.pharmaceuticalForm)
    }

    private fun updateUnitChoices(vararg choices: String) {
        val units = choices.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        unitAdapter.clear()
        unitAdapter.addAll(units)
        unitAdapter.notifyDataSetChanged()
    }

    private fun lookupScannedPackage(scannedCode: String) {
        barcodeLookupJob?.cancel()
        barcodeLookupJob = viewLifecycleOwner.lifecycleScope.launch {
            val suggestion = withContext(Dispatchers.IO) {
                suggestionRepository.findByPackageCode(scannedCode)
            }
            if (!isAdded) return@launch
            if (suggestion == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.medication_scan_not_found, scannedCode),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                fillFromCatalogSuggestion(suggestion)
            }
        }
    }

    private fun buildDosageFromFields(): String {
        return listOf(
            binding.editTextMedicationDosageMorning.text.toString().trim(),
            binding.editTextMedicationDosageNoon.text.toString().trim(),
            binding.editTextMedicationDosageEvening.text.toString().trim(),
        ).joinToString(DOSAGE_SEPARATOR)
    }

    private fun applyDosageToFields(dosage: String) {
        val parts = dosage.split(DOSAGE_SEPARATOR)
        if (parts.size == DOSAGE_PARTS) {
            binding.editTextMedicationDosageMorning.setText(parts[0])
            binding.editTextMedicationDosageNoon.setText(parts[1])
            binding.editTextMedicationDosageEvening.setText(parts[2])
        } else {
            binding.editTextMedicationDosageMorning.setText(dosage)
        }
    }

    companion object {
        const val ARG_MEDICATION_ID = "medication_id"
        const val ARG_PACKAGE_CODE = "package_code"
        private const val NO_ID = -1L
        private const val DOSAGE_SEPARATOR = "|"
        private const val DOSAGE_PARTS = 3
    }

    private data class MedicationEditorForm(
        val name: String,
        val strength: String,
        val activeSubstance: String,
        val packageSize: String,
        val unit: String,
        val currentStock: String,
        val dosage: String,
        val alertDays: String
    )
}
