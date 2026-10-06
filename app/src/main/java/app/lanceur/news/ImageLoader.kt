package app.lanceur.news

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.lanceur.net.NetResult
import app.lanceur.net.NetRules
import app.lanceur.net.Network
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Images des articles : téléchargées par `Network` (HTTPS, 1 Mo), décodées réduites, gardées en mémoire et sur disque. */
class ImageLoader(context: Context, private val network: Network) {
    // Taille en octets, pas en nombre d'images : un huitième de la mémoire de l'appli au plus
    private val memory = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val dir = File(context.cacheDir, "news-images")

    suspend fun load(url: String, targetWidth: Int): ImageBitmap? {
        memory.get(url)?.let { return it }
        if (!NetRules.allowed(url)) return null
        val file = File(dir, key(url))
        val cached = withContext(Dispatchers.IO) {
            file.takeIf { it.exists() }?.let { it.setLastModified(System.currentTimeMillis()); runCatching { it.readBytes() }.getOrNull() }
        }
        val bytes = cached ?: (network.get(url) as? NetResult.Ok)?.bytes?.also { downloaded ->
            withContext(Dispatchers.IO) { runCatching { dir.mkdirs(); file.writeBytes(downloaded); trim() } }
        } ?: return null
        val bitmap = withContext(Dispatchers.Default) { decode(bytes, targetWidth) } ?: return null
        memory.put(url, bitmap)
        return bitmap
    }

    private fun decode(bytes: ByteArray, targetWidth: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // Dimensions illisibles : on ne décode pas à l'aveugle en taille réelle
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, targetWidth) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }.getOrNull()

    private fun trim() {
        val files = dir.listFiles().orEmpty().map { ImageSizing.CachedFile(it.name, it.length(), it.lastModified()) }
        ImageSizing.toDelete(files, MAX_DISK_BYTES).forEach { File(dir, it).delete() }
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_DISK_BYTES = 50L * 1024 * 1024
    }
}

/** Image d'article à la largeur de l'écran ; chargement annulé quand la carte quitte l'écran. Échec : fond discret. */
@Composable
fun NewsImage(url: String, loader: ImageLoader, modifier: Modifier = Modifier) {
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, url) { value = loader.load(url, width) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}
