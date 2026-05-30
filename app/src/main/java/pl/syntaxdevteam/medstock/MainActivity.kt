package pl.syntaxdevteam.medstock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.syntaxdevteam.medstock.core.barcode.MedicationPackageScanner
import pl.syntaxdevteam.medstock.core.download.StartupIngestionRunner
import pl.syntaxdevteam.medstock.databinding.ActivityMainBinding
import pl.syntaxdevteam.medstock.ui.alerty.reminders.RemindersListFragment
import pl.syntaxdevteam.medstock.ui.baza.medications.MedicationCatalogFragment
import pl.syntaxdevteam.medstock.ui.baza.pharmacy.PharmacyCatalogFragment
import pl.syntaxdevteam.medstock.ui.medicationlist.MedicationEditorFragment
import pl.syntaxdevteam.medstock.ui.medicationlist.MedicationListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.appBarMain.fab?.setOnClickListener {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            val currentFragment = (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment)
                ?.childFragmentManager
                ?.primaryNavigationFragment
            if (currentFragment is MedicationCatalogFragment) {
                currentFragment.toggleSearch()
            } else if (currentFragment is PharmacyCatalogFragment) {
                currentFragment.toggleSearch()
            } else if (currentFragment is MedicationListFragment) {
                showMedicationAddSubMenu(it, navController)
            } else if (currentFragment is RemindersListFragment) {
                navController.navigate(R.id.nav_reminder_editor)
            }
        }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        binding.navView?.let { navView ->
            appBarConfiguration = AppBarConfiguration(
                topLevelDestinations(),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)
        }


        navController.addOnDestinationChangedListener { _, destination, _ ->
            val titleToolbar = binding.appBarMain.titleToolbar
            when (destination.id) {
                R.id.nav_baza_leki_screen -> {
                    titleToolbar.title = getString(R.string.menu_baza)
                    titleToolbar.subtitle = getString(R.string.menu_baza_leki)
                    binding.appBarMain.fab?.setImageResource(android.R.drawable.ic_menu_search)
                    binding.appBarMain.fab?.contentDescription = getString(R.string.fab_search_content_description)
                }

                R.id.nav_baza_apteki_screen -> {
                    titleToolbar.title = getString(R.string.menu_baza)
                    titleToolbar.subtitle = getString(R.string.menu_baza_apteki)
                    binding.appBarMain.fab?.setImageResource(android.R.drawable.ic_menu_search)
                    binding.appBarMain.fab?.contentDescription = getString(R.string.fab_search_content_description)
                }

                R.id.nav_alerty_lista_screen -> {
                    titleToolbar.title = getString(R.string.menu_alerty)
                    titleToolbar.subtitle = getString(R.string.menu_alerty_lista)
                }

                R.id.nav_alerty_przypomnienia_screen -> {
                    titleToolbar.title = getString(R.string.menu_alerty)
                    titleToolbar.subtitle = getString(R.string.menu_alerty_przypomnienia)
                    binding.appBarMain.fab?.setImageResource(android.R.drawable.ic_input_add)
                    binding.appBarMain.fab?.contentDescription = getString(R.string.fab_add_reminder_content_description)
                }

                R.id.nav_medication_list -> {
                    titleToolbar.title = getString(R.string.menu_medication_list)
                    titleToolbar.subtitle = getString(R.string.medication_list_subtitle)
                    binding.appBarMain.fab?.setImageResource(android.R.drawable.ic_input_add)
                    binding.appBarMain.fab?.contentDescription = getString(R.string.fab_add_medication_content_description)
                }

                R.id.nav_medication_editor -> {
                    titleToolbar.title = getString(R.string.medication_editor_title)
                    titleToolbar.subtitle = getString(R.string.medication_list_subtitle)
                    binding.appBarMain.fab?.hide()
                }

                R.id.nav_reminder_editor -> {
                    titleToolbar.title = getString(R.string.reminder_editor_title)
                    titleToolbar.subtitle = getString(R.string.menu_alerty_przypomnienia)
                    binding.appBarMain.fab?.hide()
                }

                else -> {
                    titleToolbar.title = destination.label ?: getString(R.string.app_name)
                    titleToolbar.subtitle = null
                    binding.appBarMain.fab?.setImageResource(android.R.drawable.ic_dialog_email)
                    binding.appBarMain.fab?.contentDescription = getString(R.string.fab_content_description)
                }
            }

            syncNavigationSelection(destination.id)

            if (destination.id != R.id.nav_medication_editor && destination.id != R.id.nav_reminder_editor) {
                binding.appBarMain.fab?.show()
            }
        }


        if (savedInstanceState == null) {
            binding.root.post { requestStartupNotificationPermissionIfNeeded() }
            runIngestionWithPreloader(force = false)
        } else {
            hideStartupPreloader()
        }

        binding.appBarMain.contentMain.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(
                topLevelDestinations()
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_baza_leki_screen -> {
                        showBottomSubMenu(it, R.menu.baza_submenu, navController)
                        false
                    }

                    R.id.nav_alerty_lista_screen -> {
                        showBottomSubMenu(it, R.menu.alerty_submenu, navController)
                        false
                    }

                    else -> {
                        navigateTopLevel(navController, item.itemId)
                        true
                    }
                }
            }
        }
    }

    private fun requestStartupNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun topLevelDestinations(): Set<Int> = setOf(
        R.id.nav_medication_list,
        R.id.nav_baza_leki_screen,
        R.id.nav_baza_apteki_screen,
        R.id.nav_alerty_lista_screen,
        R.id.nav_alerty_przypomnienia_screen,
        R.id.nav_account,
        R.id.nav_settings,
    )

    private fun showMedicationAddSubMenu(anchor: View, navController: NavController) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(R.menu.medication_add_submenu, popupMenu.menu)
        popupMenu.setForceShowIcon(true)
        popupMenu.setOnMenuItemClickListener { selected ->
            when (selected.itemId) {
                R.id.nav_medication_add_manual -> navController.navigate(R.id.nav_medication_editor)
                R.id.nav_medication_add_scan -> startMedicationPackageScanner { code ->
                    navController.navigate(
                        R.id.nav_medication_editor,
                        Bundle().apply { putString(MedicationEditorFragment.ARG_PACKAGE_CODE, code) }
                    )
                }
            }
            true
        }
        popupMenu.show()
    }

    private fun startMedicationPackageScanner(onPackageCodeScanned: (String) -> Unit) {
        MedicationPackageScanner(this).start(
            onPackageCodeScanned = onPackageCodeScanned,
            onEmptyResult = { showLongToast(getString(R.string.medication_scan_empty_result)) },
            onFailure = { showLongToast(getString(R.string.medication_scan_failed)) },
        )
    }

    private fun showLongToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
    }

    private fun showBottomSubMenu(anchor: View, menuRes: Int, navController: NavController) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setForceShowIcon(true)
        popupMenu.setOnMenuItemClickListener { selected ->
            when (selected.itemId) {
                R.id.nav_baza_leki -> navigateTopLevel(navController, R.id.nav_baza_leki_screen)
                R.id.nav_baza_scan_find -> startMedicationPackageScanner { code ->
                    navigateTopLevel(
                        navController,
                        R.id.nav_baza_leki_screen,
                        Bundle().apply { putString(MedicationCatalogFragment.ARG_PACKAGE_CODE, code) }
                    )
                }
                R.id.nav_baza_apteki -> navigateTopLevel(navController, R.id.nav_baza_apteki_screen)
                R.id.nav_alerty_lista -> navigateTopLevel(navController, R.id.nav_alerty_lista_screen)
                R.id.nav_alerty_przypomnienia -> navigateTopLevel(navController, R.id.nav_alerty_przypomnienia_screen)
            }
            true
        }
        popupMenu.show()
    }

    private fun navigateTopLevel(navController: NavController, destinationId: Int, args: Bundle? = null) {
        navController.navigate(destinationId, args, navOptions {
            launchSingleTop = args == null
            restoreState = false
            popUpTo(navController.graph.startDestinationId) {
                saveState = false
            }
        })
    }


    fun triggerCatalogForceUpdate() {
        runIngestionWithPreloader(force = true)
    }

    private fun syncNavigationSelection(@IdRes destinationId: Int) {
        val navView = binding.navView ?: return
        val checkedId = when (destinationId) {
            R.id.nav_baza_leki_screen -> R.id.nav_baza_leki_screen
            R.id.nav_baza_apteki_screen -> R.id.nav_baza_apteki_screen
            R.id.nav_alerty_lista_screen -> R.id.nav_alerty_lista_screen
            R.id.nav_alerty_przypomnienia_screen -> R.id.nav_alerty_przypomnienia_screen
            R.id.nav_medication_list -> R.id.nav_medication_list
            R.id.nav_account -> R.id.nav_account
            R.id.nav_settings -> R.id.nav_settings
            else -> null
        }

        clearCheckedItems(navView.menu)
        checkedId?.let(navView::setCheckedItem)
    }

    private fun clearCheckedItems(menu: Menu) {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            item.isChecked = false
            item.subMenu?.let(::clearCheckedItems)
        }
    }

    private fun hideStartupPreloader() {
        binding.activityContainer.findViewById<View>(R.id.startup_preloader)?.visibility = View.GONE
    }

    private fun runIngestionWithPreloader(force: Boolean) {
        val preloader = binding.activityContainer.findViewById<View>(R.id.startup_preloader)
        val progress = binding.activityContainer.findViewById<android.widget.ProgressBar>(R.id.preloader_progress)
        val status = binding.activityContainer.findViewById<android.widget.TextView>(R.id.preloader_status)

        progress.progress = 0
        status.text = getString(R.string.preloader_status_init)
        preloader.visibility = View.VISIBLE

        preloader.post {
            lifecycleScope.launch {
                var latestState: pl.syntaxdevteam.medstock.core.download.StartupProgress? = null
                var displayedPercent = 0
                var targetPercent = 0
                var stateUpdatedAt = SystemClock.uptimeMillis()
                var lastSyntheticProgressAt = 0L
                var animationJob: Job? = null
                runCatching {
                    animationJob = launch {
                        while (true) {
                            val state = latestState
                            if (state != null) {
                                val now = SystemClock.uptimeMillis()
                                if (displayedPercent < targetPercent) {
                                    val step = (targetPercent - displayedPercent).coerceAtMost(PRELOADER_REAL_PROGRESS_STEP)
                                    displayedPercent += step
                                } else if (
                                    state.isLongRunning &&
                                    displayedPercent < PRELOADER_SYNTHETIC_PROGRESS_LIMIT &&
                                    now - stateUpdatedAt >= PRELOADER_LONG_RUNNING_NOTICE_DELAY_MS &&
                                    now - lastSyntheticProgressAt >= PRELOADER_SYNTHETIC_PROGRESS_INTERVAL_MS
                                ) {
                                    displayedPercent++
                                    lastSyntheticProgressAt = now
                                }
                                progress.progress = displayedPercent.coerceIn(0, 100)
                                status.text = getString(
                                    R.string.preloader_status_with_progress,
                                    displayedPercent,
                                    state.currentTask,
                                    state.totalTasks,
                                    state.message
                                )
                            }
                            delay(PRELOADER_ANIMATION_FRAME_MS)
                        }
                    }
                    StartupIngestionRunner(applicationContext).run(force = force).collect { state ->
                        latestState = state
                        stateUpdatedAt = SystemClock.uptimeMillis()
                        targetPercent = maxOf(targetPercent, state.progressPercent)
                    }
                }.onFailure {
                    status.text = getString(R.string.preloader_status_failed, it.message ?: getString(R.string.common_unknown_error))
                }.also {
                    animationJob?.cancel()
                }
                preloader.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
    companion object {
        private const val PRELOADER_ANIMATION_FRAME_MS = 120L
        private const val PRELOADER_REAL_PROGRESS_STEP = 2
        private const val PRELOADER_LONG_RUNNING_NOTICE_DELAY_MS = 2_500L
        private const val PRELOADER_SYNTHETIC_PROGRESS_INTERVAL_MS = 900L
        private const val PRELOADER_SYNTHETIC_PROGRESS_LIMIT = 95
    }

}
