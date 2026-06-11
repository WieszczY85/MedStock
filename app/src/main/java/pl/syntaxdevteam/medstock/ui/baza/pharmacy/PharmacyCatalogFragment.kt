package pl.syntaxdevteam.medstock.ui.baza.pharmacy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pl.syntaxdevteam.medstock.R
import pl.syntaxdevteam.medstock.databinding.FragmentPharmacyCatalogBinding
import pl.syntaxdevteam.medstock.core.location.LocationCityResolver

class PharmacyCatalogFragment : Fragment() {

    private var _binding: FragmentPharmacyCatalogBinding? = null
    private val binding get() = _binding!!
    private lateinit var pharmacyCatalogViewModel: PharmacyCatalogViewModel

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            findPharmaciesInCurrentCity()
        } else {
            showLocationPermissionExplanation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        pharmacyCatalogViewModel =
            ViewModelProvider(this)[PharmacyCatalogViewModel::class.java]

        _binding = FragmentPharmacyCatalogBinding.inflate(inflater, container, false)

        val listAdapter = PharmacyCatalogAdapter { item -> openPharmacyInGoogleMaps(item) }
        val lettersAdapter = PharmacyLettersAdapter { letter -> pharmacyCatalogViewModel.onLetterSelected(letter) }
        binding.recyclerPharmacyCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPharmacyCatalog.adapter = listAdapter
        binding.recyclerPharmacyLetters.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPharmacyLetters.adapter = lettersAdapter
        lettersAdapter.submitList(buildAlphabetFilter())

        binding.searchPharmacyCatalog.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                pharmacyCatalogViewModel.onSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                pharmacyCatalogViewModel.onSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        binding.buttonPharmaciesInMyCity.setOnClickListener {
            requestLocationAndFindCity()
        }

        binding.recyclerPharmacyCatalog.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (manager.findLastVisibleItemPosition() >= listAdapter.itemCount - 5) {
                    pharmacyCatalogViewModel.loadNextPage()
                }
            }
        })

        pharmacyCatalogViewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.textPharmacyCatalogSummary.text = getString(
                state.summaryResId,
                *state.summaryArgs.toTypedArray()
            )
            listAdapter.submitList(state.pharmacies)
            lettersAdapter.updateSelection(state.selectedLetter)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun openSearch() {
        val searchView = binding.searchPharmacyCatalog
        searchView.visibility = View.VISIBLE
        searchView.isIconified = false
        searchView.requestFocusFromTouch()
        searchView.post {
            val inputMethodManager = requireContext().getSystemService(InputMethodManager::class.java)
            inputMethodManager?.showSoftInput(searchView.findFocus() ?: searchView, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun requestLocationAndFindCity() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFineLocation || hasCoarseLocation) {
            findPharmaciesInCurrentCity()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun findPharmaciesInCurrentCity() {
        binding.buttonPharmaciesInMyCity.isEnabled = false
        binding.buttonPharmaciesInMyCity.setText(R.string.pharmacy_catalog_locating)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = LocationCityResolver(requireContext().applicationContext).resolve()) {
                is LocationCityResolver.Result.Success -> {
                    binding.searchPharmacyCatalog.visibility = View.VISIBLE
                    binding.searchPharmacyCatalog.setQuery(result.city, false)
                    binding.searchPharmacyCatalog.clearFocus()
                    pharmacyCatalogViewModel.filterByCity(result.city)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.pharmacy_catalog_city_detected, result.city),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                LocationCityResolver.Result.LocationDisabled -> showLocationDisabledDialog()
                LocationCityResolver.Result.LocationUnavailable -> showLocationError(R.string.pharmacy_catalog_location_unavailable)
                LocationCityResolver.Result.CityUnavailable -> showLocationError(R.string.pharmacy_catalog_city_unavailable)
            }
            _binding?.buttonPharmaciesInMyCity?.apply {
                isEnabled = true
                setText(R.string.pharmacy_catalog_find_in_my_city)
            }
        }
    }

    private fun showLocationPermissionExplanation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pharmacy_catalog_location_permission_title)
            .setMessage(R.string.pharmacy_catalog_location_permission_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_open_app_settings) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${requireContext().packageName}".toUri()
                    )
                )
            }
            .show()
    }

    private fun showLocationDisabledDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pharmacy_catalog_location_disabled_title)
            .setMessage(R.string.pharmacy_catalog_location_disabled_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pharmacy_catalog_open_location_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .show()
    }

    private fun showLocationError(messageResId: Int) {
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_LONG).show()
    }

    private fun openPharmacyInGoogleMaps(item: PharmacyCatalogEntry) {
        val query = buildMapQuery(item)
        if (query.isBlank()) {
            Toast.makeText(requireContext(), R.string.pharmacy_catalog_missing_address, Toast.LENGTH_SHORT).show()
            return
        }

        val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        val mapIntents = listOf(
            Intent(Intent.ACTION_VIEW, geoUri).apply { setPackage("com.google.android.apps.maps") },
            Intent(Intent.ACTION_VIEW, geoUri),
            Intent(Intent.ACTION_VIEW, webUri).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
        )

        val opened = mapIntents.any { intent ->
            try {
                startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        if (!opened) {
            Toast.makeText(requireContext(), R.string.pharmacy_catalog_map_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildMapQuery(item: PharmacyCatalogEntry): String {
        val addressParts = listOf(
            item.street,
            item.buildingNumber,
            item.unitNumber.takeIf { it.isNotBlank() }?.let { "lok. $it" }.orEmpty(),
            item.city,
            "Polska"
        ).map { it.trim() }.filter { it.isNotBlank() }

        return if (addressParts.isEmpty()) {
            item.name.trim()
        } else {
            listOf(item.name.trim().takeIf { it.isNotBlank() }, addressParts.joinToString(" "))
                .filterNotNull()
                .joinToString(", ")
        }
    }
}

private fun buildAlphabetFilter(): List<String> = listOf("#", "123") + ('A'..'Z').map { it.toString() }

private class PharmacyCatalogAdapter(
    private val onMapClick: (PharmacyCatalogEntry) -> Unit
) : RecyclerView.Adapter<PharmacyCatalogViewHolder>() {
    private val items = mutableListOf<PharmacyCatalogEntry>()

    fun submitList(data: List<PharmacyCatalogEntry>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PharmacyCatalogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pharmacy_catalog, parent, false)
        return PharmacyCatalogViewHolder(view, onMapClick)
    }

    override fun onBindViewHolder(holder: PharmacyCatalogViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}

private class PharmacyCatalogViewHolder(
    view: View,
    private val onMapClick: (PharmacyCatalogEntry) -> Unit
) : RecyclerView.ViewHolder(view) {
    private val title: TextView = view.findViewById(R.id.text_pharmacy_title)
    private val subtitle: TextView = view.findViewById(R.id.text_pharmacy_status)
    private val details: TextView = view.findViewById(R.id.text_pharmacy_details)
    private val mapButton: View = view.findViewById(R.id.button_find_on_map)

    fun bind(item: PharmacyCatalogEntry) {
        val context = itemView.context
        title.text = item.name.ifBlank { context.getString(R.string.pharmacy_catalog_missing_name) }
        subtitle.text = item.city.ifBlank { context.getString(R.string.pharmacy_catalog_missing_city) }
        details.text = context.getString(
            R.string.pharmacy_catalog_details,
            item.street.ifBlank { "—" },
            item.buildingNumber.ifBlank { "—" },
            item.unitNumber.ifBlank { "—" },
            item.status.ifBlank { context.getString(R.string.pharmacy_catalog_missing_status) }
        )

        val isInactive = item.status.contains("nieakty", ignoreCase = true) ||
            item.status.contains("inactive", ignoreCase = true)
        if (isInactive) {
            val inactiveTextColor = ContextCompat.getColor(context, R.color.pharmacy_inactive_text)
            title.setTextColor(inactiveTextColor)
            subtitle.setTextColor(inactiveTextColor)
            details.setTextColor(inactiveTextColor)
            title.setTypeface(title.typeface, android.graphics.Typeface.NORMAL)
            itemView.alpha = 0.62f
        } else {
            title.setTextColor(ContextCompat.getColor(context, R.color.pharmacy_active_title))
            subtitle.setTextColor(ContextCompat.getColor(context, R.color.pharmacy_active_supporting))
            details.setTextColor(ContextCompat.getColor(context, R.color.pharmacy_active_supporting))
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
            itemView.alpha = 1f
        }
        mapButton.setOnClickListener { onMapClick(item) }
    }
}

private class PharmacyLettersAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<PharmacyLettersAdapter.LetterViewHolder>() {
    private val items = mutableListOf<String>()
    private var selected: String = "#"

    fun submitList(data: List<String>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    fun updateSelection(letter: String) {
        selected = letter
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LetterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication_letter, parent, false)
        return LetterViewHolder(view)
    }

    override fun onBindViewHolder(holder: LetterViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selected, onClick)
    }

    override fun getItemCount(): Int = items.size

    class LetterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val text: TextView = view.findViewById(R.id.text_medication_letter)

        fun bind(letter: String, isSelected: Boolean, onClick: (String) -> Unit) {
            text.text = letter
            text.alpha = if (isSelected) 1f else 0.82f
            text.setTypeface(text.typeface, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            itemView.setOnClickListener { onClick(letter) }
        }
    }
}
