package app.lanceur.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.util.SizeF
import android.widget.FrameLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/** Affiche un vrai widget Android ; sa taille réelle lui est transmise pour qu'il choisisse sa mise en page. */
@Composable
fun HostedWidget(slot: WidgetSlot, host: WidgetHost, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val size = SizeF(maxWidth.value, slot.size.heightDp.toFloat())
        key(slot.appWidgetId) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context -> host.createView(context, slot.appWidgetId) ?: FrameLayout(context) },
                update = { view ->
                    // Ne prévenir le widget que si sa taille change vraiment
                    if (view is AppWidgetHostView && view.tag != size) {
                        view.tag = size
                        view.updateAppWidgetSize(Bundle(), listOf(size))
                    }
                },
            )
        }
    }
}
