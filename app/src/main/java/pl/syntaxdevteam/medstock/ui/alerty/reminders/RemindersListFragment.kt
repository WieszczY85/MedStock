package pl.syntaxdevteam.medstock.ui.alerty.reminders

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.reminders.ReminderScheduler
import pl.syntaxdevteam.medstock.databinding.FragmentRemindersListBinding

class RemindersListFragment : Fragment() {

    private var _binding: FragmentRemindersListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()
    private lateinit var scheduler: ReminderScheduler

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRemindersListBinding.inflate(inflater, container, false)
        scheduler = ReminderScheduler(requireContext())

        val adapter = RemindersAdapter(
            onClick = { reminder ->
                findNavController().navigate(
                    R.id.nav_reminder_editor,
                    Bundle().apply { putLong(ReminderEditorFragment.ARG_REMINDER_ID, reminder.id) }
                )
            },
            onEnabledChanged = viewModel::setEnabled
        )
        binding.recyclerviewReminders.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewReminders.adapter = adapter

        binding.buttonReminderPermissions.setOnClickListener { requestMissingPermission() }

        viewModel.reminders.observe(viewLifecycleOwner) { reminders ->
            adapter.submitList(reminders)
            binding.textRemindersEmpty.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerviewReminders.visibility = if (reminders.isEmpty()) View.GONE else View.VISIBLE
            binding.textRemindersSummary.text = getString(
                R.string.reminder_summary,
                reminders.count { it.enabled },
                reminders.size
            )
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        updatePermissionCard()
    }

    private fun updatePermissionCard() {
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val exactSettingsIntent = scheduler.exactAlarmSettingsIntent()
        binding.cardReminderPermissions.visibility = if (needsNotification || exactSettingsIntent != null) View.VISIBLE else View.GONE
        binding.textReminderPermissionsMessage.text = when {
            needsNotification -> getString(R.string.reminder_notification_permission_message)
            exactSettingsIntent != null -> getString(R.string.reminder_exact_alarm_permission_message)
            else -> getString(R.string.reminder_permissions_message)
        }
    }

    private fun requestMissingPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            openPermissionSettings(notificationSettingsIntent())
            return
        }
        scheduler.exactAlarmSettingsIntent()?.let(::openPermissionSettings)
    }

    private fun openPermissionSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                if (it is ActivityNotFoundException) {
                    runCatching { startActivity(appDetailsSettingsIntent()) }
                        .onFailure { binding.cardReminderPermissions.visibility = View.GONE }
                }
            }
    }

    private fun notificationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
    }

    private fun appDetailsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

private class RemindersAdapter(
    private val onClick: (MedicationReminder) -> Unit,
    private val onEnabledChanged: (MedicationReminder, Boolean) -> Unit
) : RecyclerView.Adapter<ReminderViewHolder>() {
    private val items = mutableListOf<MedicationReminder>()

    fun submitList(data: List<MedicationReminder>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view, onClick, onEnabledChanged)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}

private class ReminderViewHolder(
    itemView: View,
    private val onClick: (MedicationReminder) -> Unit,
    private val onEnabledChanged: (MedicationReminder, Boolean) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val time: TextView = itemView.findViewById(R.id.text_reminder_time)
    private val days: TextView = itemView.findViewById(R.id.text_reminder_days)
    private val medications: TextView = itemView.findViewById(R.id.text_reminder_medications)
    private val enabled: SwitchMaterial = itemView.findViewById(R.id.switch_reminder_enabled)

    fun bind(reminder: MedicationReminder) {
        time.text = reminder.timeLabel
        days.text = reminder.dayMask.toDayLabel(itemView.context)
        medications.text = reminder.medications.joinToString { it.name }.ifBlank {
            itemView.context.getString(R.string.reminder_no_medications_selected)
        }
        enabled.setOnCheckedChangeListener(null)
        enabled.isChecked = reminder.enabled
        enabled.setOnCheckedChangeListener { _, isChecked -> onEnabledChanged(reminder, isChecked) }
        itemView.setOnClickListener { onClick(reminder) }
    }
}
