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
        binding.recyclerMedicationCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedicationCatalog.adapter = listAdapter

        medicationCatalogViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.textMedicationCatalogSummary.text = getString(
                state.summaryResId,
                *state.summaryArgs.toTypedArray()
            )
            listAdapter.submitList(state.medications)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class MedicationCatalogAdapter : RecyclerView.Adapter<MedicationCatalogViewHolder>() {
    private val items = mutableListOf<MedicationCatalogEntry>()

    fun submitList(data: List<MedicationCatalogEntry>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationCatalogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return MedicationCatalogViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicationCatalogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class MedicationCatalogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(android.R.id.text1)
    private val subtitle: TextView = view.findViewById(android.R.id.text2)

    fun bind(item: MedicationCatalogEntry) {
        val context = itemView.context
        val medicationName = item.displayName.ifBlank {
            context.getString(R.string.medication_catalog_missing_name)
        }
        val commonName = item.commonName.ifBlank {
            context.getString(R.string.medication_catalog_missing_common_name)
        }

        title.text = "${item.entityKey} • $medicationName"
        subtitle.text = commonName
    }
}
