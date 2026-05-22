package pl.syntaxdevteam.medstock.ui.baza.medications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
}

private fun buildAlphabetFilter(): List<String> = listOf("#", "123") + ('A'..'Z').map { it.toString() }

private class MedicationCatalogAdapter : RecyclerView.Adapter<MedicationCatalogViewHolder>() {
    private val items = mutableListOf<MedicationCatalogEntry>()

    fun submitList(data: List<MedicationCatalogEntry>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationCatalogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication_catalog, parent, false)
        return MedicationCatalogViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicationCatalogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class MedicationCatalogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.text_medication_title)
    private val subtitle: TextView = view.findViewById(R.id.text_medication_common_name)
    private val details: TextView = view.findViewById(R.id.text_medication_details)

    fun bind(item: MedicationCatalogEntry) {
        val context = itemView.context
        val medicationName = item.displayName.ifBlank {
            context.getString(R.string.medication_catalog_missing_name)
        }
        val commonName = item.commonName.ifBlank {
            context.getString(R.string.medication_catalog_missing_common_name)
        }

        title.text = medicationName
        subtitle.text = commonName
        details.text = context.getString(
            R.string.medication_catalog_details,
            item.dose.ifBlank { "—" },
            item.ean.ifBlank { "—" },
            item.packageSize.ifBlank { "—" }
        )
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
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return LetterViewHolder(view)
    }

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selected, onClick)
    }

    override fun getItemCount(): Int = items.size

    class LetterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(android.R.id.text1)

        fun bind(letter: String, isSelected: Boolean, onClick: (String) -> Unit) {
            text.text = letter
            text.textSize = 11f
            text.alpha = if (isSelected) 1f else 0.6f
            itemView.setOnClickListener { onClick(letter) }
        }
    }
}
