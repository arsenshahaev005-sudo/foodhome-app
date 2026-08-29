package market.foodhome.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

sealed interface LocationRequestResult {
    data class Granted(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double,
        val precise: Boolean,
    ) : LocationRequestResult

    data class Failed(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : LocationRequestResult
}

class AndroidLocationProvider(
    private val context: Context,
    private val timeoutMillis: Long = 10_000,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var cancelActive: (() -> Unit)? = null

    fun cancel() {
        cancelActive?.invoke()
        cancelActive = null
    }

    @SuppressLint("MissingPermission")
    fun requestCurrentLocation(completion: (LocationRequestResult) -> Unit) {
        cancel()
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            completion(LocationRequestResult.Failed("CAPABILITY_UNAVAILABLE", "Location permission is unavailable"))
            return
        }

        val provider = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { fineGranted },
            LocationManager.NETWORK_PROVIDER,
        ).firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            completion(LocationRequestResult.Failed("CAPABILITY_UNAVAILABLE", "Location services are disabled"))
            return
        }

        var completed = false
        fun finish(result: LocationRequestResult) {
            if (completed) return
            completed = true
            cancelActive?.invoke()
            cancelActive = null
            completion(result)
        }

        val timeout = Runnable {
            finish(LocationRequestResult.Failed("TIMEOUT", "Location request timed out", retryable = true))
        }
        handler.postDelayed(timeout, timeoutMillis)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cancelActive = {
                handler.removeCallbacks(timeout)
                signal.cancel()
            }
            locationManager.getCurrentLocation(
                provider,
                signal,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                if (location == null) {
                    finish(LocationRequestResult.Failed("CAPABILITY_UNAVAILABLE", "Location is unavailable", retryable = true))
                } else {
                    finish(location.asResult(fineGranted))
                }
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    finish(location.asResult(fineGranted))
                }

                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) {
                    finish(LocationRequestResult.Failed("CAPABILITY_UNAVAILABLE", "Location services are disabled"))
                }
            }
            cancelActive = {
                handler.removeCallbacks(timeout)
                locationManager.removeUpdates(listener)
            }
            @Suppress("DEPRECATION")
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun Location.asResult(precise: Boolean) = LocationRequestResult.Granted(
        latitude = latitude.coerceIn(-90.0, 90.0),
        longitude = longitude.coerceIn(-180.0, 180.0),
        accuracyMeters = accuracy.toDouble().coerceIn(0.0, 100_000.0),
        precise = precise,
    )
}
