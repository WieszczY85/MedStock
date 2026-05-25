package pl.syntaxdevteam.medstock.ui.transform

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
import pl.syntaxdevteam.medstock.databinding.FragmentTransformBinding

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!
    private val transformViewModel: TransformViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransformBinding.inflate(inflater, container, false)

        val adapter = TransformAdapter { medicationId ->
            findNavController().navigate(
                R.id.nav_medication_editor,
                Bundle().apply { putLong(MedicationEditorFragment.ARG_MEDICATION_ID, medicationId) }
            )
        }
        binding.recyclerviewTransform.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewTransform.adapter = adapter

        transformViewModel.itemNumbers.observe(viewLifecycleOwner) { medications ->
            binding.textTransformSummary.text = getString(R.string.transform_summary, medications.size)
            adapter.submitList(medications)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class TransformAdapter(
    private val onClick: (Long) -> Unit
) : RecyclerView.Adapter<TransformViewHolder>() {
    private val items = mutableListOf<UserMedication>()

    fun submitList(data: List<UserMedication>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransformViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_medication, parent, false)
        return TransformViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransformViewHolder, position: Int) {
        holder.bind(items[position]) { onClick(items[position].id) }
    }

    override fun getItemCount(): Int = items.size
}

private class TransformViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val name: TextView = view.findViewById(R.id.text_user_medication_name)
    private val note: TextView = view.findViewById(R.id.text_user_medication_note)

    fun bind(item: UserMedication, onClick: () -> Unit) {
        val card = itemView as MaterialCardView
        val strengthSuffix = item.strength.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        val dosagePerDay = parseDailyDosage(item.dosage)
        val daysSupply = if (item.currentStock > 0 && dosagePerDay > 0.0) {
            kotlin.math.floor(item.currentStock / dosagePerDay).toInt()
        } else {
            0
        }
        val status = when {
            daysSupply == 0 -> {
                applyAlertCardStyle(card)
                itemView.context.getString(R.string.medication_stock_status_empty)
            }
            daysSupply < item.alertDays -> {
                applyAlertCardStyle(card)
                itemView.context.getString(R.string.medication_stock_status_low)
            }
            else -> {
                applyDefaultCardStyle(card)
                itemView.context.getString(R.string.medication_stock_status_ok)
            }
        }

        name.text = itemView.context.getString(R.string.medication_list_title_with_strength, item.name, strengthSuffix)
        note.text = itemView.context.getString(
            R.string.medication_list_stock_and_days,
            item.currentStock,
            item.unit.ifBlank { itemView.context.getString(R.string.medication_default_unit) },
            daysSupply,
            status
        )
        itemView.setOnClickListener { onClick() }
    }

    private fun parseDailyDosage(rawDosage: String): Double {
        val normalized = rawDosage.replace(',', '.')
        val match = Regex("""\d+(?:\.\d+)?""").find(normalized) ?: return 0.0
        return match.value.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
    }

    private fun applyAlertCardStyle(card: MaterialCardView) {
        val context = card.context
        card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.space_1)
        card.strokeColor = ContextCompat.getColor(context, R.color.alert_pastel_red_stroke)
        card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.alert_pastel_red_background))
    }

    private fun applyDefaultCardStyle(card: MaterialCardView) {
        val context = card.context
        card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.card_stroke)
        card.strokeColor = ContextCompat.getColor(context, R.color.green_200)
        card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_card))
    }
}
