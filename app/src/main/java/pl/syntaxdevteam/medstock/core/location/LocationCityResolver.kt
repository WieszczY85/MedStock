package pl.syntaxdevteam.medstock.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LocationCityResolver(private val context: Context) {

    sealed interface Result {
        data class Success(val city: String) : Result
        data object LocationDisabled : Result
        data object LocationUnavailable : Result
        data object CityUnavailable : Result
    }

    suspend fun resolve(): Result {
        val locationManager = context.getSystemService(LocationManager::class.java)
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return Result.LocationDisabled
        }

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            currentLocation(locationManager, provider)
        } ?: return Result.LocationUnavailable

        val city = reverseGeocode(location)
        return if (city.isNullOrBlank()) Result.CityUnavailable else Result.Success(city)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(locationManager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(location: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        try {
            Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { address -> address.locality ?: address.subAdminArea ?: address.adminArea }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
    }
}
