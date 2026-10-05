package app.lanceur.builtin.shortcuts

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class TorchState(val available: Boolean, val on: Boolean)

/** Lampe torche de la première caméra qui a un flash. Aucune permission n'est nécessaire. */
class TorchController(context: Context) {
    private val manager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? by lazy {
        runCatching {
            manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        }.getOrNull()
    }

    val state: Flow<TorchState> = callbackFlow {
        val id = cameraId
        if (id == null) {
            trySend(TorchState(available = false, on = false))
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) trySend(TorchState(available = true, on = enabled))
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) trySend(TorchState(available = false, on = false))
            }
        }
        // L'enregistrement rappelle aussitôt avec l'état actuel
        manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { manager.unregisterTorchCallback(callback) }
    }.conflate()

    /** `false` si la lampe n'a pas pu changer (caméra occupée, pas de flash). */
    fun set(on: Boolean): Boolean {
        val id = cameraId ?: return false
        return runCatching { manager.setTorchMode(id, on) }.isSuccess
    }
}
