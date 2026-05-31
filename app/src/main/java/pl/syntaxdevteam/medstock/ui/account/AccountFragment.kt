package pl.syntaxdevteam.medstock.ui.account

import android.accounts.AccountManager
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentAccountBinding

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AccountViewModel

    private val googleAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
        if (email.isBlank()) {
            Toast.makeText(requireContext(), R.string.account_google_no_account_selected, Toast.LENGTH_LONG).show()
        } else {
            viewModel.connect(email)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[AccountViewModel::class.java]
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        setupClickListeners()
        observeState()
        return binding.root
    }

    private fun setupClickListeners() {
        binding.accountGoogleConnectButton.setOnClickListener { openGoogleAccountPicker() }
        binding.accountGoogleDisconnectButton.setOnClickListener { confirmDisconnect() }
        binding.accountDriveBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            val state = viewModel.uiState.value
            if (state?.driveBackupEnabled == isChecked) return@setOnCheckedChangeListener
            viewModel.setDriveBackupEnabled(isChecked)
        }
        binding.accountDriveBackupNowButton.setOnClickListener { viewModel.createBackupSnapshot() }
    }

    private fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.accountAvatarLabel.text = state.avatarLabel
            binding.accountEmailValue.text = if (state.isConnected) state.email else getString(R.string.account_google_not_connected_email)
            binding.accountStatusValue.text = state.accountStatusText
            binding.accountGoogleConnectButton.visibility = if (state.isConnected) View.GONE else View.VISIBLE
            binding.accountGoogleDisconnectButton.visibility = if (state.isConnected) View.VISIBLE else View.GONE

            if (binding.accountDriveBackupSwitch.isChecked != state.driveBackupEnabled) {
                binding.accountDriveBackupSwitch.isChecked = state.driveBackupEnabled
            }
            binding.accountDriveBackupSwitch.isEnabled = state.driveControlsEnabled
            binding.accountDriveBackupNowButton.isEnabled = state.driveControlsEnabled && state.driveBackupEnabled
            binding.accountDriveStatusValue.text = state.driveStatusText
            binding.accountProgress.visibility = if (state.isBusy) View.VISIBLE else View.GONE

            state.restorePrompt?.let { prompt ->
                showRestorePrompt(prompt)
            }

            state.transientMessageRes?.let { messageRes ->
                val duration = if (messageRes == R.string.account_drive_restore_no_backup_message) {
                    Toast.LENGTH_SHORT
                } else {
                    Toast.LENGTH_LONG
                }
                Toast.makeText(requireContext(), messageRes, duration).show()
                viewModel.messageShown()
            }
        }
    }

    private fun showRestorePrompt(prompt: AccountRestorePrompt) {
        viewModel.dismissRestorePrompt()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_drive_restore_dialog_title)
            .setMessage(
                getString(
                    R.string.account_drive_restore_dialog_message,
                    prompt.createdAtText,
                    prompt.medicationCount,
                    prompt.reminderCount,
                )
            )
            .setPositiveButton(R.string.account_drive_restore_dialog_confirm) { _, _ -> viewModel.restoreBackupSnapshot() }
            .setNegativeButton(R.string.account_drive_restore_dialog_skip, null)
            .show()
    }

    private fun openGoogleAccountPicker() {
        val intent = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf(GOOGLE_ACCOUNT_TYPE),
            null,
            null,
            null,
            null,
        )
        googleAccountPickerLauncher.launch(intent)
    }

    private fun confirmDisconnect() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.account_disconnect_dialog_title)
            .setMessage(R.string.account_disconnect_dialog_message)
            .setPositiveButton(R.string.account_disconnect_dialog_confirm) { _, _ -> viewModel.disconnect() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
