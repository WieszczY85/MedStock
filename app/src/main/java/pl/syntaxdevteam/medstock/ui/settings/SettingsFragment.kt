package pl.syntaxdevteam.medstock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        settingsViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.settingsAppNameValue.text = state.appName
            binding.settingsAuthorValue.text = state.author
            binding.settingsVersionValue.text = state.version
            binding.settingsLastUpdateValue.text = state.lastDatabaseUpdate
            binding.settingsDbSizeValue.text = state.databaseSize
        }

        binding.settingsForceUpdateButton.setOnClickListener {
            (activity as? pl.syntaxdevteam.medstock.MainActivity)?.triggerCatalogForceUpdate()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
