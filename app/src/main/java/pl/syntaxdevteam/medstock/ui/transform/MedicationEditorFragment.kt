package pl.syntaxdevteam.medstock.ui.transform

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationEditorBinding

class MedicationEditorFragment : Fragment() {

    private var _binding: FragmentMedicationEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TransformViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationEditorBinding.inflate(inflater, container, false)

        val index = arguments?.getInt(ARG_MEDICATION_INDEX, NO_INDEX) ?: NO_INDEX
        val isEdit = index != NO_INDEX

        binding.buttonSaveMedication.text = getString(if (isEdit) R.string.medication_editor_save else R.string.medication_editor_add)

        if (isEdit) {
            val item = viewModel.itemNumbers.value.orEmpty().getOrNull(index)
            if (item != null) {
                binding.editTextMedicationName.setText(item.name)
                binding.editTextMedicationNote.setText(item.note)
            }
        }

        binding.buttonSaveMedication.setOnClickListener {
            val success = if (isEdit) {
                viewModel.updateMedication(index, binding.editTextMedicationName.text.toString(), binding.editTextMedicationNote.text.toString())
            } else {
                viewModel.addMedication(binding.editTextMedicationName.text.toString(), binding.editTextMedicationNote.text.toString())
            }

            if (success) {
                findNavController().navigateUp()
            } else {
                Toast.makeText(requireContext(), getString(R.string.medication_editor_required), Toast.LENGTH_SHORT).show()
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_MEDICATION_INDEX = "medication_index"
        private const val NO_INDEX = -1
    }
}
