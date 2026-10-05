package app.lanceur.builtin.storage

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Stockage interne (taille arrondie comme dans les réglages) et mémoire vive. Aucune permission. */
class StorageSource(private val context: Context) {
    /** Relu à l'affichage, puis toutes les 5 s tant qu'une carte collecte. */
    val readings: Flow<StorageReading> = flow {
        while (true) {
            emit(read())
            delay(5_000)
        }
    }.flowOn(Dispatchers.IO)

    fun read(): StorageReading {
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val statFs by lazy { StatFs(Environment.getDataDirectory().path) }
        val total = runCatching { stats.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrElse { statFs.totalBytes }
        val free = runCatching { stats.getFreeBytes(StorageManager.UUID_DEFAULT) }.getOrElse { statFs.availableBytes }
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return StorageReading(
            storage = Gauge(total - free, total),
            memory = Gauge(memory.totalMem - memory.availMem, memory.totalMem),
        )
    }
}
