package pl.syntaxdevteam.medstock.ui.baza.pharmacy

import android.os.Bundle
import android.net.Uri
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentPharmacyCatalogBinding

class PharmacyCatalogFragment : Fragment() {

    private var _binding: FragmentPharmacyCatalogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val pharmacyCatalogViewModel =
            ViewModelProvider(this)[PharmacyCatalogViewModel::class.java]

        _binding = FragmentPharmacyCatalogBinding.inflate(inflater, container, false)

        val listAdapter = PharmacyCatalogAdapter { item -> openPharmacyInGoogleMaps(item) }
        val lettersAdapter = PharmacyLettersAdapter { letter -> pharmacyCatalogViewModel.onLetterSelected(letter) }
        binding.recyclerPharmacyCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPharmacyCatalog.adapter = listAdapter
        binding.recyclerPharmacyLetters.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPharmacyLetters.adapter = lettersAdapter
        lettersAdapter.submitList(buildAlphabetFilter())

        binding.searchPharmacyCatalog.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                pharmacyCatalogViewModel.onSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                pharmacyCatalogViewModel.onSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        binding.recyclerPharmacyCatalog.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= listAdapter.itemCount - 5) {
                    pharmacyCatalogViewModel.loadNextPage()
                }
            }
        })

        pharmacyCatalogViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.textPharmacyCatalogSummary.text = getString(
                state.summaryResId,
                *state.summaryArgs.toTypedArray()
            )
            listAdapter.submitList(state.pharmacies)
            lettersAdapter.updateSelection(state.selectedLetter)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun toggleSearch() {
        val searchView = binding.searchPharmacyCatalog
        val shouldShow = searchView.visibility != View.VISIBLE
        searchView.visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (shouldShow) {
            searchView.requestFocus()
        } else {
            searchView.setQuery("", false)
            searchView.clearFocus()
        }
    }

    private fun openPharmacyInGoogleMaps(item: PharmacyCatalogEntry) {
        val query = buildMapQuery(item)
        if (query.isBlank()) {
            Toast.makeText(requireContext(), R.string.pharmacy_catalog_missing_address, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }

        val packageManager = requireContext().packageManager
        when {
            intent.resolveActivity(packageManager) != null -> startActivity(intent)
            Intent(Intent.ACTION_VIEW, uri).resolveActivity(packageManager) != null -> {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            else -> Toast.makeText(requireContext(), R.string.pharmacy_catalog_map_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildMapQuery(item: PharmacyCatalogEntry): String {
        val addressParts = listOf(
            item.street,
            item.buildingNumber,
            item.unitNumber.takeIf { it.isNotBlank() }?.let { "lok. $it" }.orEmpty(),
            item.city,
            "Polska"
        ).map { it.trim() }.filter { it.isNotBlank() }

        return if (addressParts.isEmpty()) {
            item.name.trim()
        } else {
            listOf(item.name.trim().takeIf { it.isNotBlank() }, addressParts.joinToString(" "))
                .filterNotNull()
                .joinToString(", ")
        }
    }
}

private fun buildAlphabetFilter(): List<String> = listOf("#", "123") + ('A'..'Z').map { it.toString() }

private class PharmacyCatalogAdapter(
    private val onMapClick: (PharmacyCatalogEntry) -> Unit
) : RecyclerView.Adapter<PharmacyCatalogViewHolder>() {
    private val items = mutableListOf<PharmacyCatalogEntry>()

    fun submitList(data: List<PharmacyCatalogEntry>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PharmacyCatalogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_catalog, parent, false)
        return PharmacyCatalogViewHolder(view, onMapClick)
    }

    override fun onBindViewHolder(holder: PharmacyCatalogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class PharmacyCatalogViewHolder(
    view: View,
    private val onMapClick: (PharmacyCatalogEntry) -> Unit
) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.text_pharmacy_title)
    private val subtitle: TextView = view.findViewById(R.id.text_pharmacy_status)
    private val details: TextView = view.findViewById(R.id.text_pharmacy_details)
    private val mapButton: View = view.findViewById(R.id.button_find_on_map)

    fun bind(item: PharmacyCatalogEntry) {
        val context = itemView.context
        title.text = item.name.ifBlank { context.getString(R.string.pharmacy_catalog_missing_name) }
        subtitle.text = item.city.ifBlank { context.getString(R.string.pharmacy_catalog_missing_city) }
        details.text = context.getString(
            R.string.pharmacy_catalog_details,
            item.street.ifBlank { "—" },
            item.buildingNumber.ifBlank { "—" },
            item.unitNumber.ifBlank { "—" },
            item.status.ifBlank { context.getString(R.string.pharmacy_catalog_missing_status) }
        )

        val isInactive = item.status.contains("nieakty", ignoreCase = true) ||
            item.status.contains("inactive", ignoreCase = true)
        if (isInactive) {
            val inactiveTextColor = ContextCompat.getColor(context, R.color.pharmacy_inactive_text)
            title.setTextColor(inactiveTextColor)
            subtitle.setTextColor(inactiveTextColor)
            details.setTextColor(inactiveTextColor)
            title.setTypeface(title.typeface, android.graphics.Typeface.NORMAL)
            itemView.alpha = 0.62f
        } else {
            title.setTextColor(ContextCompat.getColor(context, R.color.pharmacy_active_title))
            subtitle.setTextColor(ContextCompat.getColor(context, R.color.pharmacy_active_supporting))
            details.setTextColor(ContextCompat.getColor(context, R.color.pharmacy_active_supporting))
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            itemView.alpha = 1f
        }
        mapButton.setOnClickListener { onMapClick(item) }
    }
}

private class PharmacyLettersAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<PharmacyLettersAdapter.LetterViewHolder>() {
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
