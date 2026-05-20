package pl.syntaxdevteam.medstock

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import pl.syntaxdevteam.medstock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.appBarMain.fab?.setOnClickListener { view ->
            Snackbar.make(view, getString(R.string.snackbar_placeholder_action), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.common_action), null)
                .setAnchorView(R.id.fab).show()
        }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        binding.navView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_baza_leki_screen, R.id.nav_alerty_lista_screen, R.id.nav_settings, R.id.nav_account
                ),
                binding.drawerLayout
            )
            setupActionBarWithNavController(navController, appBarConfiguration)
            it.setupWithNavController(navController)
        }


        navController.addOnDestinationChangedListener { _, destination, _ ->
            val titleToolbar = binding.appBarMain.titleToolbar
            when (destination.id) {
                R.id.nav_baza_leki_screen -> {
                    titleToolbar.title = getString(R.string.menu_baza)
                    titleToolbar.subtitle = getString(R.string.menu_baza_leki)
                }

                R.id.nav_baza_apteki_screen -> {
                    titleToolbar.title = getString(R.string.menu_baza)
                    titleToolbar.subtitle = getString(R.string.menu_baza_apteki)
                }

                R.id.nav_alerty_lista_screen -> {
                    titleToolbar.title = getString(R.string.menu_alerty)
                    titleToolbar.subtitle = getString(R.string.menu_alerty_lista)
                }

                R.id.nav_alerty_przypomnienia_screen -> {
                    titleToolbar.title = getString(R.string.menu_alerty)
                    titleToolbar.subtitle = getString(R.string.menu_alerty_przypomnienia)
                }

                else -> {
                    titleToolbar.title = destination.label ?: getString(R.string.app_name)
                    titleToolbar.subtitle = null
                }
            }
        }

        binding.appBarMain.contentMain.bottomNavView?.let {
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_transform, R.id.nav_baza_leki_screen, R.id.nav_alerty_lista_screen, R.id.nav_account
                )
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

    private fun showBottomSubMenu(anchor: View, menuRes: Int, navController: NavController) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.menuInflater.inflate(menuRes, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { selected ->
            when (selected.itemId) {
                R.id.nav_baza_leki -> navigateTopLevel(navController, R.id.nav_baza_leki_screen)
                R.id.nav_baza_apteki -> navigateTopLevel(navController, R.id.nav_baza_apteki_screen)
                R.id.nav_alerty_lista -> navigateTopLevel(navController, R.id.nav_alerty_lista_screen)
                R.id.nav_alerty_przypomnienia -> navigateTopLevel(navController, R.id.nav_alerty_przypomnienia_screen)
            }
            true
        }
        popupMenu.show()
    }

    private fun navigateTopLevel(navController: NavController, destinationId: Int) {
        navController.navigate(destinationId, null, navOptions {
            launchSingleTop = true
            restoreState = false
            popUpTo(navController.graph.startDestinationId) {
                saveState = false
            }
        })
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
}
