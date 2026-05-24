package pl.syntaxdevteam.medstock.ui.baza.pharmacy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

        val listAdapter = PharmacyCatalogAdapter()
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
}

private fun buildAlphabetFilter(): List<String> = listOf("#", "123") + ('A'..'Z').map { it.toString() }

private class PharmacyCatalogAdapter : RecyclerView.Adapter<PharmacyCatalogViewHolder>() {
    private val items = mutableListOf<PharmacyCatalogEntry>()

    fun submitList(data: List<PharmacyCatalogEntry>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PharmacyCatalogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_catalog, parent, false)
        return PharmacyCatalogViewHolder(view)
    }

    override fun onBindViewHolder(holder: PharmacyCatalogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class PharmacyCatalogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.text_pharmacy_title)
    private val subtitle: TextView = view.findViewById(R.id.text_pharmacy_status)
    private val details: TextView = view.findViewById(R.id.text_pharmacy_details)

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
        val textColor = ContextCompat.getColor(
            context,
            if (isInactive) android.R.color.darker_gray else android.R.color.black
        )
        title.setTextColor(textColor)
        subtitle.setTextColor(textColor)
        details.setTextColor(textColor)
        itemView.alpha = if (isInactive) 0.72f else 1f
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
