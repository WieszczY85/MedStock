package pl.syntaxdevteam.medstock.ui.baza.medications

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
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
                bindMedication(state.medication, state.matchedPackageCodes)
            }
        }
    }

    private fun bindMedication(item: MedicationCatalogEntry, matchedPackageCodes: List<String>) {
        binding.cardMedicationDetail.visibility = View.VISIBLE
        binding.textMedicationTitle.text = getString(
            R.string.medication_catalog_title_with_availability,
            item.displayName.ifBlank { getString(R.string.medication_catalog_missing_name) },
            item.dose.ifBlank { "—" },
            availabilityLabel(item.primaryAvailabilityCode).ifBlank { "—" }
        )
        binding.textMedicationDetails.text = buildMedicationDetails(item, matchedPackageCodes)
        bindDocumentButton(binding.buttonMedicationLeaflet, item.leafletUrl)
        bindDocumentButton(binding.buttonMedicationCharacteristics, item.characteristicsUrl)
    }

    private fun buildMedicationDetails(
        item: MedicationCatalogEntry,
        matchedPackageCodes: List<String>
    ): SpannableStringBuilder {
        return SpannableStringBuilder().apply {
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
                    val row = buildPackageRow(pkg, isScannedPackageMatch(pkg, matchedPackageCodes))
                    val rowStart = length
                    append(row)
                    if (isScannedPackageMatch(pkg, matchedPackageCodes)) {
                        setSpan(
                            BackgroundColorSpan(ContextCompat.getColor(requireContext(), R.color.medication_scan_match_background)),
                            rowStart,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(StyleSpan(Typeface.BOLD), rowStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }

    private fun buildPackageRow(pkg: MedicationPackageInfo, matchesScannedCode: Boolean): String {
        val packageRow = getString(R.string.medication_catalog_package_row, pkg.ean, pkg.quantity)
        return if (matchesScannedCode) {
            getString(R.string.medication_catalog_detail_matched_package_row, packageRow)
        } else {
            packageRow
        }
    }

    private fun isScannedPackageMatch(pkg: MedicationPackageInfo, matchedPackageCodes: List<String>): Boolean {
        val packageCode = pkg.ean.filter(Char::isDigit)
        if (packageCode.isBlank()) return false
        return matchedPackageCodes
            .map { it.filter(Char::isDigit) }
            .filter { it.isNotBlank() }
            .any { candidate ->
                packageCode == candidate ||
                    packageCode.contains(candidate) ||
                    candidate.contains(packageCode) ||
                    packageCode.trimStart('0') == candidate.trimStart('0')
            }
    }

    private fun bindDocumentButton(button: View, url: String) {
        button.isEnabled = url.isNotBlank()
        button.setOnClickListener {
            if (url.isBlank()) return@setOnClickListener
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
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
