package pl.syntaxdevteam.medstock.ui.account

import android.accounts.AccountManager
import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentAccountBinding
import java.net.HttpURLConnection
import java.net.URL

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AccountViewModel
    private var avatarLoadJob: Job? = null

    private val googleAccountPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
        if (email.isBlank()) {
            Toast.makeText(requireContext(), R.string.account_google_no_account_selected, Toast.LENGTH_LONG).show()
        } else {
            viewModel.connect(email)
        }
    }

    private val driveAuthorizationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.retryPendingDriveAction()
        } else {
            viewModel.driveAuthorizationCancelled()
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
        binding.accountGoogleHelp.setOnClickListener {
            showHelp(R.string.account_google_title, R.string.account_google_help_message)
        }
        binding.accountDriveHelp.setOnClickListener {
            showHelp(R.string.account_drive_title, R.string.account_drive_help_message)
        }
        binding.accountDriveBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            val state = viewModel.uiState.value
            if (state?.driveBackupEnabled == isChecked) return@setOnCheckedChangeListener
            viewModel.setDriveBackupEnabled(isChecked)
        }
        binding.accountDriveBackupNowButton.setOnClickListener { viewModel.createBackupSnapshot() }
    }

    private fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            bindAvatar(state)
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

            state.driveAuthorizationIntent?.let { intent ->
                viewModel.driveAuthorizationLaunched()
                driveAuthorizationLauncher.launch(intent)
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

    private fun bindAvatar(state: AccountUiState) {
        binding.accountAvatarLabel.text = state.avatarLabel
        val avatarUrl = state.avatarUrl
        if (!state.isConnected || avatarUrl.isNullOrBlank()) {
            avatarLoadJob?.cancel()
            binding.accountAvatarImage.tag = null
            binding.accountAvatarImage.setImageDrawable(null)
            binding.accountAvatarImage.visibility = View.GONE
            binding.accountAvatarLabel.visibility = View.VISIBLE
            return
        }

        if (binding.accountAvatarImage.tag == avatarUrl && binding.accountAvatarImage.drawable != null) {
            binding.accountAvatarLabel.visibility = View.GONE
            binding.accountAvatarImage.visibility = View.VISIBLE
            return
        }

        avatarLoadJob?.cancel()
        binding.accountAvatarImage.tag = avatarUrl
        binding.accountAvatarImage.setImageDrawable(null)
        binding.accountAvatarImage.visibility = View.GONE
        binding.accountAvatarLabel.visibility = View.VISIBLE
        avatarLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            val avatarBitmap = withContext(Dispatchers.IO) {
                runCatching { downloadAvatarBitmap(avatarUrl) }.getOrNull()
            }
            val currentBinding = _binding ?: return@launch
            if (!isActive || currentBinding.accountAvatarImage.tag != avatarUrl) return@launch
            if (avatarBitmap != null) {
                currentBinding.accountAvatarImage.setImageBitmap(avatarBitmap)
                currentBinding.accountAvatarImage.visibility = View.VISIBLE
                currentBinding.accountAvatarLabel.visibility = View.GONE
            } else {
                currentBinding.accountAvatarImage.visibility = View.GONE
                currentBinding.accountAvatarLabel.visibility = View.VISIBLE
            }
        }
    }

    private fun downloadAvatarBitmap(avatarUrl: String) =
        (URL(avatarUrl).openConnection() as HttpURLConnection).run {
            try {
                connectTimeout = AVATAR_CONNECT_TIMEOUT_MILLIS
                readTimeout = AVATAR_READ_TIMEOUT_MILLIS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "image/*")
                if (responseCode !in 200..299) return@run null
                inputStream.use(BitmapFactory::decodeStream)
            } finally {
                disconnect()
            }
        }

    private fun showHelp(titleResId: Int, messageResId: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleResId)
            .setMessage(messageResId)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
        avatarLoadJob?.cancel()
        avatarLoadJob = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val AVATAR_CONNECT_TIMEOUT_MILLIS = 10_000
        private const val AVATAR_READ_TIMEOUT_MILLIS = 10_000
    }
}
