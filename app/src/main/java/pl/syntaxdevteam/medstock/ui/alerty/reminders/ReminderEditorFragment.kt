package pl.syntaxdevteam.medstock.ui.alerty.reminders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentReminderEditorBinding
import pl.syntaxdevteam.medstock.ui.medicationlist.UserMedication
import java.util.Calendar

class ReminderEditorFragment : Fragment() {

    private var _binding: FragmentReminderEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RemindersViewModel by activityViewModels()
    private var reminderId: Long? = null
    private var selectedHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    private var selectedMinute = Calendar.getInstance().get(Calendar.MINUTE)
    private val selectedMedicationIds = linkedSetOf<Long>()
    private lateinit var medicationsAdapter: ReminderMedicationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderId = arguments?.getLong(ARG_REMINDER_ID)?.takeIf { it > 0L }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReminderEditorBinding.inflate(inflater, container, false)

        medicationsAdapter = ReminderMedicationAdapter(selectedMedicationIds) { medicationId, checked ->
            if (checked) selectedMedicationIds += medicationId else selectedMedicationIds -= medicationId
        }
        binding.recyclerviewReminderMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerviewReminderMedications.adapter = medicationsAdapter

        setupDayChips()
        binding.cardReminderClock.setOnClickListener { showTimePicker() }
        binding.textReminderTime.setOnClickListener { showTimePicker() }
        binding.buttonSaveReminder.setOnClickListener { saveReminder() }
        binding.buttonDeleteReminder.setOnClickListener { deleteReminder() }
        binding.buttonDeleteReminder.visibility = if (reminderId == null) View.GONE else View.VISIBLE

        viewModel.medications.observe(viewLifecycleOwner) { medications ->
            medicationsAdapter.submitList(medications)
            binding.textReminderMedicationsEmpty.visibility = if (medications.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerviewReminderMedications.visibility = if (medications.isEmpty()) View.GONE else View.VISIBLE
        }
        viewModel.reminders.observe(viewLifecycleOwner) { reminders ->
            val reminder = reminderId?.let { id -> reminders.firstOrNull { it.id == id } } ?: return@observe
            fillReminder(reminder)
        }
        viewModel.refresh()
        updateTimeText()
        return binding.root
    }

    private fun setupDayChips() {
        val labels = reminderDayShortLabels(requireContext())
        labels.forEachIndexed { index, label ->
            val chip = Chip(requireContext()).apply {
                text = label
                isCheckable = true
                minWidth = resources.getDimensionPixelSize(R.dimen.reminder_day_chip_size)
                minHeight = resources.getDimensionPixelSize(R.dimen.reminder_day_chip_size)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                tag = index
            }
            binding.chipgroupReminderDays.addView(chip)
        }
    }

    private fun fillReminder(reminder: MedicationReminder) {
        selectedHour = reminder.hour
        selectedMinute = reminder.minute
        selectedMedicationIds.clear()
        selectedMedicationIds.addAll(reminder.medicationIds)
        binding.editReminderLabel.setText(reminder.label)
        binding.chipgroupReminderDays.children.forEach { child ->
            val chip = child as? Chip ?: return@forEach
            val index = chip.tag as Int
            chip.isChecked = reminder.dayMask and (1 shl index) != 0
        }
        medicationsAdapter.notifyDataSetChanged()
        updateTimeText()
    }

    private fun showTimePicker() {
        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText(R.string.reminder_time_picker_title)
            .build()
            .apply {
                addOnPositiveButtonClickListener {
                    selectedHour = hour
                    selectedMinute = minute
                    updateTimeText()
                }
            }
            .show(parentFragmentManager, TIME_PICKER_TAG)
    }

    private fun saveReminder() {
        if (selectedMedicationIds.isEmpty()) {
            Toast.makeText(requireContext(), R.string.reminder_validation_medication_required, Toast.LENGTH_LONG).show()
            return
        }
        viewModel.saveReminder(
            id = reminderId,
            hour = selectedHour,
            minute = selectedMinute,
            dayMask = selectedDayMask(),
            label = binding.editReminderLabel.text?.toString().orEmpty(),
            medicationIds = selectedMedicationIds.toList()
        )
        findNavController().navigateUp()
    }

    private fun deleteReminder() {
        reminderId?.let(viewModel::delete)
        findNavController().navigateUp()
    }

    private fun selectedDayMask(): Int {
        var mask = 0
        binding.chipgroupReminderDays.children.forEach { child ->
            val chip = child as? Chip ?: return@forEach
            if (chip.isChecked) mask = mask or (1 shl (chip.tag as Int))
        }
        return mask
    }

    private fun updateTimeText() {
        binding.textReminderTime.text = "%02d:%02d".format(selectedHour, selectedMinute)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_REMINDER_ID = "reminder_id"
        private const val TIME_PICKER_TAG = "medication_reminder_time_picker"
    }
}

private class ReminderMedicationAdapter(
    private val selectedMedicationIds: Set<Long>,
    private val onCheckedChanged: (Long, Boolean) -> Unit
) : RecyclerView.Adapter<ReminderMedicationViewHolder>() {
    private val items = mutableListOf<UserMedication>()

    fun submitList(data: List<UserMedication>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderMedicationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder_medication, parent, false)
        return ReminderMedicationViewHolder(view, selectedMedicationIds, onCheckedChanged)
    }

    override fun onBindViewHolder(holder: ReminderMedicationViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size
}

private class ReminderMedicationViewHolder(
    itemView: View,
    private val selectedMedicationIds: Set<Long>,
    private val onCheckedChanged: (Long, Boolean) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    private val checkbox: MaterialCheckBox = itemView.findViewById(R.id.checkbox_reminder_medication)

    fun bind(medication: UserMedication) {
        val strength = medication.strength.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        checkbox.setOnCheckedChangeListener(null)
        checkbox.text = itemView.context.getString(R.string.medication_list_title_with_strength, medication.name, strength)
        checkbox.isChecked = selectedMedicationIds.contains(medication.id)
        checkbox.setOnCheckedChangeListener { _, checked -> onCheckedChanged(medication.id, checked) }
        itemView.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
    }
}
