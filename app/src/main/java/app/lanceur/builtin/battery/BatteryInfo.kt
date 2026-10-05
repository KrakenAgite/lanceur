package app.lanceur.builtin.battery

import android.os.BatteryManager
import java.util.Locale

enum class ChargeStatus { CHARGING, PLUGGED, DISCHARGING, FULL }

enum class BatteryTone { GOOD, MEDIUM, LOW }

/** État de la batterie tel que les cartes l'affichent. `chargeTimeRemainingMs` : -1 si Android ne l'estime pas. */
data class BatteryInfo(
    val level: Int,
    val status: ChargeStatus,
    val chargeTimeRemainingMs: Long,
    val temperatureTenths: Int?,
    val powerSave: Boolean,
) {
    val tone: BatteryTone
        get() = when {
            level > 50 -> BatteryTone.GOOD
            level >= 20 -> BatteryTone.MEDIUM
            else -> BatteryTone.LOW
        }

    val statusText: String
        get() = when (status) {
            ChargeStatus.CHARGING ->
                if (chargeTimeRemainingMs > 0) "En charge · pleine dans ${duration(chargeTimeRemainingMs)}" else "En charge"
            // Charge adaptative du Pixel : branché, mais la charge attend
            ChargeStatus.PLUGGED -> "Branchée · charge en pause"
            ChargeStatus.DISCHARGING -> "Sur batterie"
            ChargeStatus.FULL -> "Chargée"
        }

    /** Pour la barre : « ⚡ 42 min », « ⚡ » sans estimation, rien hors charge. */
    val shortChargeText: String?
        get() = if (status != ChargeStatus.CHARGING) null
        else if (chargeTimeRemainingMs > 0) "⚡ ${duration(chargeTimeRemainingMs)}" else "⚡"

    val temperatureText: String? get() = temperatureTenths?.let { "${Math.round(it / 10.0)} °C" }

    val powerSaveText: String get() = if (powerSave) "Économiseur activé" else "Économiseur désactivé"

    companion object {
        /** Arrondi à la minute supérieure : « 42 min », « 1 h 05 ». */
        fun duration(ms: Long): String {
            val minutes = ((ms + 59_999) / 60_000).coerceAtLeast(1)
            return if (minutes < 60) "$minutes min" else String.format(Locale.ROOT, "%d h %02d", minutes / 60, minutes % 60)
        }

        /** Extras de `ACTION_BATTERY_CHANGED`. `scale` ≤ 0 : `level` est déjà un pourcentage. */
        fun from(
            level: Int,
            scale: Int,
            status: Int,
            plugged: Int,
            chargeTimeRemainingMs: Long,
            temperatureTenths: Int?,
            powerSave: Boolean,
        ): BatteryInfo {
            val percent = (if (scale > 0) level * 100 / scale else level).coerceIn(0, 100)
            val charge = when {
                status == BatteryManager.BATTERY_STATUS_FULL || (plugged != 0 && percent >= 100) -> ChargeStatus.FULL
                status == BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
                plugged != 0 -> ChargeStatus.PLUGGED
                else -> ChargeStatus.DISCHARGING
            }
            return BatteryInfo(percent, charge, chargeTimeRemainingMs, temperatureTenths, powerSave)
        }
    }
}
