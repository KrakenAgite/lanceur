package app.lanceur.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppIcon(key: AppKey, loader: IconLoader, size: Dp = 40.dp) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val version = loader.version
    val bitmap by produceState(initialValue = loader.cached(key), key, sizePx, version) {
        // Après un changement de style, le cache est vide : on recharge (sinon c'est immédiat)
        value = loader.load(key, sizePx)
    }
    Box(Modifier.size(size)) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}
