package pl.syntaxdevteam.medstock.ui.alerty.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.stock.MedicationStockStatus
import pl.syntaxdevteam.medstock.databinding.FragmentAlertsListBinding

class AlertsListFragment : Fragment() {

    private var _binding: FragmentAlertsListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val alertsListViewModel = ViewModelProvider(this)[AlertsListViewModel::class.java]
        _binding = FragmentAlertsListBinding.inflate(inflater, container, false)

        val adapter = StockAlertAdapter { alert -> alertsListViewModel.dismissAlert(alert) }
        binding.recyclerviewAlertsList.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewAlertsList.adapter = adapter

        alertsListViewModel.alerts.observe(viewLifecycleOwner) { alerts ->
            adapter.submitList(alerts)
            binding.textAlertsEmpty.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerviewAlertsList.visibility = if (alerts.isEmpty()) View.GONE else View.VISIBLE
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        ViewModelProvider(this)[AlertsListViewModel::class.java].refreshAlerts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class StockAlertAdapter(
    private val onDismiss: (StockAlert) -> Unit,
) : RecyclerView.Adapter<StockAlertViewHolder>() {
    private val items = mutableListOf<StockAlert>()

    fun submitList(alerts: List<StockAlert>) {
        items.clear()
        items.addAll(alerts)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockAlertViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock_alert, parent, false)
        return StockAlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: StockAlertViewHolder, position: Int) {
        holder.bind(items[position]) { onDismiss(items[position]) }
    }

    override fun getItemCount(): Int = items.size
}

private class StockAlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.text_stock_alert_title)
    private val message: TextView = view.findViewById(R.id.text_stock_alert_message)
    private val dismissButton: Button = view.findViewById(R.id.button_stock_alert_dismiss)

    fun bind(alert: StockAlert, onDismiss: () -> Unit) {
        val context = itemView.context
        val card = itemView as MaterialCardView
        val statusText = when (alert.status) {
            MedicationStockStatus.LOW -> {
                applyCardStyle(card, R.color.stock_status_low_stroke, R.color.stock_status_low_background)
                context.getString(R.string.medication_stock_status_low)
            }
            MedicationStockStatus.EMPTY -> {
                applyCardStyle(card, R.color.stock_status_empty_stroke, R.color.stock_status_empty_background)
                context.getString(R.string.medication_stock_status_empty)
            }
            MedicationStockStatus.OK -> context.getString(R.string.medication_stock_status_ok)
        }
        val daysSupplyText = if (alert.daysSupply == Int.MAX_VALUE) {
            context.getString(R.string.medication_stock_days_unknown)
        } else {
            alert.daysSupply.toString()
        }
        val unit = alert.unit.ifBlank { context.getString(R.string.medication_default_unit) }
        title.text = context.getString(R.string.stock_alert_item_title, alert.medicationName, statusText)
        message.text = context.getString(
            R.string.stock_alert_item_message,
            alert.currentStock,
            unit,
            daysSupplyText
        )
        dismissButton.setOnClickListener { onDismiss() }
    }

    private fun applyCardStyle(card: MaterialCardView, strokeColor: Int, backgroundColor: Int) {
        val context = card.context
        card.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.space_1)
        card.strokeColor = ContextCompat.getColor(context, strokeColor)
        card.setCardBackgroundColor(ContextCompat.getColor(context, backgroundColor))
    }
}
