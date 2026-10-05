package app.lanceur.builtin.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import app.lanceur.search.SearchPermissions
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class WeatherSource(private val context: Context, private val network: Network) {
    suspend fun search(query: String): List<Place> =
        when (val result = network.get(WeatherQuery.geocodeUrl(query))) {
            is NetResult.Ok -> Geocoding.parse(result.text())
            is NetResult.Failed -> emptyList()
        }

    /** Ne rappelle le réseau que si le cache a plus de 30 min. Renvoie le résultat et le cache à garder. */
    suspend fun refresh(config: WeatherConfig, cache: WeatherCache?, now: Long): Pair<WeatherOutcome, WeatherCache?> {
        if (!app.lanceur.builtin.Freshness.isStale(cache?.fetchedAt, now)) return WeatherOutcome.CACHED to cache
        val coords = if (config.usePosition) position() else config.place?.let { it.latitude to it.longitude }
        if (coords == null) return (if (config.usePosition) WeatherOutcome.NO_POSITION else WeatherOutcome.FAILED) to cache
        return when (val result = network.get(WeatherQuery.forecastUrl(coords.first, coords.second))) {
            is NetResult.Ok -> {
                val raw = result.text()
                val readable = withContext(Dispatchers.Default) { Forecast.parse(raw, java.time.Instant.ofEpochMilli(now)) != null }
                if (readable) WeatherOutcome.FRESH to WeatherCache(raw, now)
                else WeatherOutcome.FAILED to cache
            }
            is NetResult.Failed -> WeatherOutcome.FAILED to cache
        }
    }

    /** Dernière position de moins d'1 h, sinon une demande ponctuelle (15 s au plus) ; `null` sans permission. */
    @SuppressLint("MissingPermission")
    private suspend fun position(): Pair<Double, Double>? {
        if (!SearchPermissions.granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val manager = context.getSystemService(LocationManager::class.java)
        if (!manager.isLocationEnabled) return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.hasProvider(it) }
        providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            ?.takeIf { System.currentTimeMillis() - it.time < 3_600_000 }
            ?.let { return it.latitude to it.longitude }
        val provider = providers.firstOrNull() ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (cont.isActive) cont.resume(location?.let { it.latitude to it.longitude })
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }
}
