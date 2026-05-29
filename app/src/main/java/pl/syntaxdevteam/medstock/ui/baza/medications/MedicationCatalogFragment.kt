package pl.syntaxdevteam.medstock.ui.baza.medications

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationCatalogBinding

class MedicationCatalogFragment : Fragment() {

    private var _binding: FragmentMedicationCatalogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val medicationCatalogViewModel =
            ViewModelProvider(this)[MedicationCatalogViewModel::class.java]

        _binding = FragmentMedicationCatalogBinding.inflate(inflater, container, false)

        val listAdapter = MedicationCatalogAdapter()
        val lettersAdapter = MedicationLettersAdapter { letter -> medicationCatalogViewModel.onLetterSelected(letter) }
        binding.recyclerMedicationCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedicationCatalog.adapter = listAdapter
        binding.recyclerMedicationLetters.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedicationLetters.adapter = lettersAdapter
        lettersAdapter.submitList(buildAlphabetFilter())

        binding.searchMedicationCatalog.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                medicationCatalogViewModel.onSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                medicationCatalogViewModel.onSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        binding.recyclerMedicationCatalog.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= listAdapter.itemCount - 5) {
                    medicationCatalogViewModel.loadNextPage()
                }
            }
        })

        medicationCatalogViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.textMedicationCatalogSummary.text = getString(
                state.summaryResId,
                *state.summaryArgs.toTypedArray()
            )
            listAdapter.submitList(state.medications)
            lettersAdapter.updateSelection(state.selectedLetter)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun toggleSearch() {
        val searchView = binding.searchMedicationCatalog
        val shouldShow = searchView.visibility != View.VISIBLE
        searchView.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (shouldShow) {
            searchView.requestFocus()
        } else {
            searchView.setQuery("", false)
            searchView.clearFocus()
        }
    }
}

private fun buildAlphabetFilter(): List<String> = listOf("#", "123") + ('A'..'Z').map { it.toString() }

private class MedicationCatalogAdapter : RecyclerView.Adapter<MedicationCatalogViewHolder>() {
    private val items = mutableListOf<MedicationCatalogEntry>()
    private val expandedKeys = mutableSetOf<String>()

    fun submitList(data: List<MedicationCatalogEntry>) {
        items.clear()
        items.addAll(data)
        expandedKeys.retainAll(data.mapTo(mutableSetOf()) { it.entityKey })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationCatalogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication_catalog, parent, false)
        return MedicationCatalogViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicationCatalogViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, expandedKeys.contains(item.entityKey)) {
            if (!expandedKeys.add(item.entityKey)) {
                expandedKeys.remove(item.entityKey)
            }
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = items.size
}

private class MedicationCatalogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.text_medication_title)
    private val subtitle: TextView = view.findViewById(R.id.text_medication_common_name)
    private val collapsedHint: TextView = view.findViewById(R.id.text_medication_collapsed_hint)
    private val details: TextView = view.findViewById(R.id.text_medication_details)
    private val leafletButton: MaterialButton = view.findViewById(R.id.button_medication_leaflet)
    private val characteristicsButton: MaterialButton = view.findViewById(R.id.button_medication_characteristics)

    fun bind(item: MedicationCatalogEntry, expanded: Boolean, onClick: () -> Unit) {
        val context = itemView.context
        val medicationName = item.displayName.ifBlank {
            context.getString(R.string.medication_catalog_missing_name)
        }
        val commonName = item.commonName.ifBlank {
            context.getString(R.string.medication_catalog_missing_common_name)
        }
        val primaryAvailability = availabilityLabel(item.primaryAvailabilityCode, title)
        title.text = if (primaryAvailability.isBlank()) {
            listOf(medicationName, item.dose).filter(String::isNotBlank).joinToString("  ")
        } else {
            context.getString(
                R.string.medication_catalog_title_with_availability,
                medicationName,
                item.dose.ifBlank { "—" },
                primaryAvailability
            )
        }
        subtitle.text = context.getString(
            R.string.medication_catalog_collapsed_subtitle,
            commonName,
            item.pharmaceuticalForm.ifBlank { item.route.ifBlank { "—" } }
        )
        collapsedHint.visibility = if (expanded) View.GONE else View.VISIBLE
        details.visibility = if (expanded) View.VISIBLE else View.GONE
        leafletButton.visibility = if (expanded) View.VISIBLE else View.GONE
        characteristicsButton.visibility = if (expanded) View.VISIBLE else View.GONE
        details.text = buildString {
            append(context.getString(R.string.medication_catalog_responsible_entity_label))
            append("\n")
            append(item.responsibleEntity.ifBlank { "—" })
            if (item.manufacturerCountry.isNotBlank()) {
                append(" • ")
                append(item.manufacturerCountry)
            }
            append("\n")
            append(context.getString(R.string.medication_catalog_packages_header))
            if (item.packages.isEmpty()) {
                append("\n• ")
                append(context.getString(R.string.medication_catalog_package_unknown))
            } else {
                item.packages.forEach { pkg ->
                    append("\n")
                    append(context.getString(R.string.medication_catalog_package_row, pkg.ean, pkg.quantity))
                }
            }
        }
        bindDocumentButton(leafletButton, item.leafletUrl)
        bindDocumentButton(characteristicsButton, item.characteristicsUrl)
        itemView.setOnClickListener { onClick() }
    }

    private fun bindDocumentButton(button: MaterialButton, url: String) {
        val context = button.context
        val normalizedUrl = url.trim()
        button.isEnabled = normalizedUrl.isNotBlank()
        button.setOnClickListener {
            if (normalizedUrl.isBlank()) return@setOnClickListener
            val uri = Uri.parse(normalizedUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, R.string.medication_catalog_document_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun availabilityLabel(code: String, labelView: TextView): String {
        val resId = when (code.trim().uppercase(Locale.ROOT)) {
            "RP" -> R.string.medication_availability_rp
            "RPZ" -> R.string.medication_availability_rpz
            "RPW" -> R.string.medication_availability_rpw
            "OTC" -> R.string.medication_availability_otc
            "LZ" -> R.string.medication_availability_lz
            else -> null
        }
        return resId?.let(labelView.context::getString).orEmpty()
    }
}

private class MedicationLettersAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<MedicationLettersAdapter.LetterViewHolder>() {
    private val items = mutableListOf<String>()
    private var selected: String = "#"

    fun submitList(data: List<String>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    fun updateSelection(letter: String) {
        selected = letter
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication_letter, parent, false)
        return LetterViewHolder(view)
    }

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selected, onClick)
    }

    override fun getItemCount(): Int = items.size

    class LetterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.text_medication_letter)

        fun bind(letter: String, isSelected: Boolean, onClick: (String) -> Unit) {
            text.text = letter
            text.alpha = if (isSelected) 1f else 0.82f
            text.setTypeface(text.typeface, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            itemView.setOnClickListener { onClick(letter) }
        }
    }
}
