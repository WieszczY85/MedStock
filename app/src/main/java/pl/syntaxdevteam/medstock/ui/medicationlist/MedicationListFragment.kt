package pl.syntaxdevteam.medstock.ui.medicationlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.stock.MedicationStockCalculator
import pl.syntaxdevteam.medstock.core.stock.MedicationStockStatus
import pl.syntaxdevteam.medstock.databinding.FragmentMedicationListBinding

class MedicationListFragment : Fragment() {

    private var _binding: FragmentMedicationListBinding? = null
    private val binding get() = _binding!!
    private val medicationListViewModel: MedicationListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationListBinding.inflate(inflater, container, false)

        val adapter = MedicationListAdapter { medicationId ->
            findNavController().navigate(
                R.id.nav_medication_editor,
                Bundle().apply { putLong(MedicationEditorFragment.ARG_MEDICATION_ID, medicationId) }
            )
        }
        binding.recyclerviewMedicationList.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewMedicationList.adapter = adapter

        medicationListViewModel.medications.observe(viewLifecycleOwner) { medications ->
            binding.textMedicationListSummary.text = getString(R.string.medication_list_summary, medications.size)
            adapter.submitList(medications)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class MedicationListAdapter(
    private val onClick: (Long) -> Unit
) : RecyclerView.Adapter<MedicationListViewHolder>() {
    private val items = mutableListOf<UserMedication>()

    fun submitList(data: List<UserMedication>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicationListViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_medication, parent, false)
        return MedicationListViewHolder(view)
    }

    override fun onBindViewHolder(holder: MedicationListViewHolder, position: Int) {
        holder.bind(items[position]) { onClick(items[position].id) }
    }

    override fun getItemCount(): Int = items.size
}

private class MedicationListViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val name: TextView = view.findViewById(R.id.text_user_medication_name)
    private val note: TextView = view.findViewById(R.id.text_user_medication_note)

    fun bind(item: UserMedication, onClick: () -> Unit) {
        val card = itemView as MaterialCardView
        val strengthSuffix = item.strength.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val stockInfo = MedicationStockCalculator.calculate(item)
        val status = when (stockInfo.status) {
            MedicationStockStatus.EMPTY -> {
                applyStatusCardStyle(
                    card = card,
                    strokeColor = R.color.stock_status_empty_stroke,
                    backgroundColor = R.color.stock_status_empty_background
                )
                itemView.context.getString(R.string.medication_stock_status_empty)
            }
            MedicationStockStatus.LOW -> {
                applyStatusCardStyle(
                    card = card,
                    strokeColor = R.color.stock_status_low_stroke,
                    backgroundColor = R.color.stock_status_low_background
                )
                itemView.context.getString(R.string.medication_stock_status_low)
            }
            MedicationStockStatus.OK -> {
                applyStatusCardStyle(
                    card = card,
                    strokeColor = R.color.stock_status_ok_stroke,
                    backgroundColor = R.color.stock_status_ok_background
                )
                itemView.context.getString(R.string.medication_stock_status_ok)
            }
        }
        val daysSupplyText = if (stockInfo.daysSupply == Int.MAX_VALUE) {
            itemView.context.getString(R.string.medication_stock_days_unknown)
        } else {
            stockInfo.daysSupply.toString()
        }

        name.text = itemView.context.getString(R.string.medication_list_title_with_strength, item.name, strengthSuffix)
        note.text = itemView.context.getString(
            R.string.medication_list_stock_and_days,
            item.currentStock,
            MedicationUnitFormatter.abbreviate(item.unit).ifBlank { itemView.context.getString(R.string.medication_default_unit) },
            daysSupplyText,
            status
        )
        itemView.setOnClickListener { onClick() }
    }

    private fun applyStatusCardStyle(card: MaterialCardView, strokeColor: Int, backgroundColor: Int) {
        val context = card.context
        card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.space_1)
        card.strokeColor = ContextCompat.getColor(context, strokeColor)
        card.setCardBackgroundColor(ContextCompat.getColor(context, backgroundColor))
    }
}
