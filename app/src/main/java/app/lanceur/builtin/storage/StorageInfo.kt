package app.lanceur.builtin.storage

import java.util.Locale

data class Gauge(val usedBytes: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val warning: Boolean get() = fraction > 0.9f
}

data class StorageReading(val storage: Gauge, val memory: Gauge)

object StorageInfo {
    /** En gigaoctets décimaux, comme les réglages d'Android : « 87 / 128 Go », « 7,1 / 12,0 Go ». */
    fun text(gauge: Gauge, decimals: Int): String = "${gb(gauge.usedBytes, decimals)} / ${gb(gauge.totalBytes, decimals)} Go"

    private fun gb(bytes: Long, decimals: Int) = String.format(Locale.FRENCH, "%.${decimals}f", bytes / 1_000_000_000.0)
}
