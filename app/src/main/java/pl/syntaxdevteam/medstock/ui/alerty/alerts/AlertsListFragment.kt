package pl.syntaxdevteam.medstock.ui.alerty.alerts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import pl.syntaxdevteam.medstock.databinding.FragmentAlertsListBinding

class AlertsListFragment : Fragment() {

    private var _binding: FragmentAlertsListBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val alertsListViewModel =
            ViewModelProvider(this).get(AlertsListViewModel::class.java)

        _binding = FragmentAlertsListBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textAlertsList
        alertsListViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}