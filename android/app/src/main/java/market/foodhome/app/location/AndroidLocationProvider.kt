package market.foodhome.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

class AndroidLocationProvider(
    private val context: Context,
    // Leave time for the native response inside the bridge's 10-second request budget.
    private val timeoutMillis: Long = 8_000,
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

        val providers = listOfNotNull(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER.takeIf { fineGranted },
        ).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) {
            completion(LocationRequestResult.Failed("CAPABILITY_UNAVAILABLE", "Location services are disabled"))
            return
        }

        val request = CurrentLocationRequest(
            providers = providers,
            timeoutMillis = timeoutMillis,
            scheduleTimeout = { delay, action ->
                val timeout = Runnable(action)
                handler.postDelayed(timeout, delay)
                val cancel: () -> Unit = { handler.removeCallbacks(timeout) }
                cancel
            },
            queryProvider = { provider, receive ->
                val signal = CancellationSignal()
                try {
                    // Compat also uses a recent fix on Android 8-10 instead of waiting for a new GPS fix.
                    LocationManagerCompat.getCurrentLocation(
                        locationManager, provider, signal, ContextCompat.getMainExecutor(context),
                    ) { location -> receive(location?.asResult(fineGranted)) }
                } catch (error: Exception) {
                    signal.cancel()
                    throw error
                }
                val cancel: () -> Unit = { signal.cancel() }
                cancel
            },
            completion = { result ->
                cancelActive = null
                completion(result)
            },
        )
        cancelActive = request::cancel
        request.start()
    }

    private fun Location.asResult(precise: Boolean) = LocationRequestResult.Granted(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.NaN,
        precise = precise,
    )
}
