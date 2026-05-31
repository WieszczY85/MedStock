package pl.syntaxdevteam.medstock.ui.baza.medications

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationCatalogDetailBinding

class MedicationCatalogDetailFragment : Fragment() {
    private var _binding: FragmentMedicationCatalogDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationCatalogDetailBinding.inflate(inflater, container, false)
        val viewModel = ViewModelProvider(this)[MedicationCatalogDetailViewModel::class.java]
        val scannedCode = arguments?.getString(ARG_PACKAGE_CODE).orEmpty()

        binding.textScanCode.text = getString(R.string.medication_catalog_detail_scanned_code, scannedCode.ifBlank { "—" })
        viewModel.uiState.observe(viewLifecycleOwner, ::renderState)
        if (savedInstanceState == null || viewModel.uiState.value == null) {
            viewModel.loadByPackageCode(scannedCode)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun renderState(state: MedicationCatalogDetailUiState) {
        when (state) {
            MedicationCatalogDetailUiState.Loading -> {
                binding.textStatus.text = getString(R.string.medication_catalog_detail_loading)
                binding.cardMedicationDetail.visibility = View.GONE
            }
            MedicationCatalogDetailUiState.EmptyDatabase -> {
                binding.textStatus.text = getString(R.string.medication_catalog_empty)
                binding.cardMedicationDetail.visibility = View.GONE
            }
            is MedicationCatalogDetailUiState.NotFound -> {
                binding.textStatus.text = getString(R.string.medication_scan_not_found, state.scannedCode)
                binding.cardMedicationDetail.visibility = View.GONE
            }
            is MedicationCatalogDetailUiState.Found -> {
                binding.textScanCode.text = getString(R.string.medication_catalog_detail_scanned_code, state.scannedCode)
                binding.textStatus.text = getString(R.string.medication_catalog_detail_snapshot, state.snapshotDate)
                bindMedication(state.medication)
            }
        }
    }

    private fun bindMedication(item: MedicationCatalogEntry) {
        binding.cardMedicationDetail.visibility = View.VISIBLE
        binding.textMedicationTitle.text = getString(
            R.string.medication_catalog_title_with_availability,
            item.displayName.ifBlank { getString(R.string.medication_catalog_missing_name) },
            item.dose.ifBlank { "—" },
            availabilityLabel(item.primaryAvailabilityCode).ifBlank { "—" }
        )
        binding.textMedicationDetails.text = buildString {
            appendLine(getString(R.string.medication_catalog_detail_identifier, item.entityKey.ifBlank { "—" }))
            appendLine(getString(R.string.medication_catalog_detail_active_substance, item.commonName.ifBlank { "—" }))
            appendLine(getString(R.string.medication_catalog_detail_form, item.pharmaceuticalForm.ifBlank { "—" }))
            appendLine(getString(R.string.medication_catalog_detail_route, item.route.ifBlank { "—" }))
            appendLine(getString(R.string.medication_catalog_detail_responsible_entity, item.responsibleEntity.ifBlank { "—" }))
            appendLine(getString(R.string.medication_catalog_detail_manufacturer_country, item.manufacturerCountry.ifBlank { "—" }))
            append(getString(R.string.medication_catalog_packages_header))
            if (item.packages.isEmpty()) {
                append("\n• ")
                append(getString(R.string.medication_catalog_package_unknown))
            } else {
                item.packages.forEach { pkg ->
                    append("\n• ")
                    append(getString(R.string.medication_catalog_package_row, pkg.ean, pkg.quantity))
                }
            }
        }
        bindDocumentButton(binding.buttonMedicationLeaflet, item.leafletUrl)
        bindDocumentButton(binding.buttonMedicationCharacteristics, item.characteristicsUrl)
    }

    private fun bindDocumentButton(button: View, url: String) {
        val normalizedUrl = url.trim()
        button.isEnabled = normalizedUrl.isNotBlank()
        button.setOnClickListener {
            if (normalizedUrl.isBlank()) return@setOnClickListener
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.medication_catalog_document_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun availabilityLabel(code: String): String {
        val resId = when (code.trim().uppercase()) {
            "RP" -> R.string.medication_availability_rp
            "RPZ" -> R.string.medication_availability_rpz
            "RPW" -> R.string.medication_availability_rpw
            "OTC" -> R.string.medication_availability_otc
            "LZ" -> R.string.medication_availability_lz
            else -> null
        }
        return resId?.let(::getString).orEmpty()
    }

    companion object {
        const val ARG_PACKAGE_CODE = "package_code"
    }
}
