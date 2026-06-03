package pl.syntaxdevteam.medstock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.core.download.RegistryIngestDatabaseHelper
import pl.syntaxdevteam.medstock.core.i18n.AppLanguageMode
import pl.syntaxdevteam.medstock.core.theme.AppThemeMode
import pl.syntaxdevteam.medstock.databinding.FragmentSettingsBinding
import java.io.FileInputStream

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val exportDatabaseLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        exportDatabaseToUri(uri)
    }

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
            setCheckedThemeMode(state.themeMode)
            setCheckedLanguageMode(state.languageMode)
        }

        binding.settingsThemeModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val themeMode = when (checkedId) {
                R.id.settings_theme_auto_button -> AppThemeMode.AUTO
                R.id.settings_theme_on_button -> AppThemeMode.ON
                R.id.settings_theme_off_button -> AppThemeMode.OFF
                else -> return@addOnButtonCheckedListener
            }
            settingsViewModel.setThemeMode(themeMode)
        }

        binding.settingsLanguageModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val languageMode = when (checkedId) {
                R.id.settings_language_auto_button -> AppLanguageMode.AUTO
                R.id.settings_language_pl_button -> AppLanguageMode.POLISH
                R.id.settings_language_en_button -> AppLanguageMode.ENGLISH
                R.id.settings_language_de_button -> AppLanguageMode.GERMAN
                R.id.settings_language_fr_button -> AppLanguageMode.FRENCH
                else -> return@addOnButtonCheckedListener
            }
            settingsViewModel.setLanguageMode(languageMode)
        }

        binding.settingsForceUpdateButton.setOnClickListener {
            (activity as? pl.syntaxdevteam.medstock.MainActivity)?.triggerCatalogForceUpdate()
        }

        binding.settingsExportDatabaseButton.setOnClickListener {
            exportDatabaseLauncher.launch(getString(R.string.settings_export_database_default_filename))
        }

        return binding.root
    }

    private fun setCheckedThemeMode(themeMode: AppThemeMode) {
        val checkedButtonId = when (themeMode) {
            AppThemeMode.AUTO -> R.id.settings_theme_auto_button
            AppThemeMode.ON -> R.id.settings_theme_on_button
            AppThemeMode.OFF -> R.id.settings_theme_off_button
        }
        if (binding.settingsThemeModeGroup.checkedButtonId != checkedButtonId) {
            binding.settingsThemeModeGroup.check(checkedButtonId)
        }
    }

    private fun setCheckedLanguageMode(languageMode: AppLanguageMode) {
        val checkedButtonId = when (languageMode) {
            AppLanguageMode.AUTO -> R.id.settings_language_auto_button
            AppLanguageMode.POLISH -> R.id.settings_language_pl_button
            AppLanguageMode.ENGLISH -> R.id.settings_language_en_button
            AppLanguageMode.GERMAN -> R.id.settings_language_de_button
            AppLanguageMode.FRENCH -> R.id.settings_language_fr_button
        }
        if (binding.settingsLanguageModeGroup.checkedButtonId != checkedButtonId) {
            binding.settingsLanguageModeGroup.check(checkedButtonId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun exportDatabaseToUri(uri: android.net.Uri) {
        val context = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val dbPath = RegistryIngestDatabaseHelper.getInstance(context).readableDatabase.path
            val sourceFile = java.io.File(dbPath)
            if (!sourceFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.settings_export_database_missing, Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val wasSuccessful = runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    true
                } ?: false
            }.getOrElse { false }

            withContext(Dispatchers.Main) {
                val message = if (wasSuccessful) {
                    R.string.settings_export_database_success
                } else {
                    R.string.settings_export_database_failed
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
