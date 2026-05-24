package pl.syntaxdevteam.medstock.ui.transform

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

        val adapter = TransformAdapter { index ->
            findNavController().navigate(
                R.id.nav_medication_editor,
                Bundle().apply { putInt(MedicationEditorFragment.ARG_MEDICATION_INDEX, index) }
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
    private val onClick: (Int) -> Unit
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
        holder.bind(items[position]) { onClick(position) }
    }

    override fun getItemCount(): Int = items.size
}

private class TransformViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    private val name: TextView = view.findViewById(R.id.text_user_medication_name)
    private val note: TextView = view.findViewById(R.id.text_user_medication_note)

    fun bind(item: UserMedication, onClick: () -> Unit) {
        name.text = item.name
        note.text = item.note
        itemView.setOnClickListener { onClick() }
    }
}
