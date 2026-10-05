package app.lanceur.builtin.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Batterie en direct, sans permission. N'écoute que tant qu'une carte collecte `info`. */
class BatterySource(private val context: Context) {
    val info: Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // Diffusions du système : reçues même sans exportation
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        trySend(read())
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()

    /** Lecture immédiate : `ACTION_BATTERY_CHANGED` est collant, l'enregistrement sans récepteur rend le dernier état. */
    fun read(): BatteryInfo {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(BatteryManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        return BatteryInfo.from(
            level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
                ?: manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1,
            status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0,
            plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0,
            chargeTimeRemainingMs = manager.computeChargeTimeRemaining(),
            temperatureTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE },
            powerSave = power.isPowerSaveMode,
        )
    }
}
