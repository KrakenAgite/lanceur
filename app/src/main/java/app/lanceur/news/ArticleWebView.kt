package app.lanceur.news

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lanceur.i18n.tr
import app.lanceur.ui.CardLabel

/**
 * Article ouvert dans Lanceur : la page du site dans une vue web, sous la barre du lanceur (Retour, source,
 * ↗ navigateur). Retour remonte d'abord dans les pages visitées. Seuls les liens web s'ouvrent ici ;
 * pas de cookies tiers, pas d'accès aux fichiers, aucun téléchargement.
 */
@Composable
fun ArticleWebView(
    url: String,
    source: String,
    onClose: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var view by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(url) }
    BackHandler { if (canGoBack) view?.goBack() else onClose() }

    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().testTag("article-web")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onClose, modifier = Modifier.testTag("article-close")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Fermer", "Close"))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                CardLabel(source)
                Text(
                    Uri.parse(currentUrl).host?.removePrefix("www.").orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = { onOpenInBrowser(currentUrl) },
                modifier = Modifier.testTag("article-browser").semantics { contentDescription = tr("Ouvrir dans le navigateur", "Open in browser") },
            ) { Text("↗", style = MaterialTheme.typography.titleMedium) }
        }
        Box(Modifier.fillMaxSize()) {
            if (!LocalInspectionMode.current) {
                AndroidView(
                    factory = { context -> createWebView(context, onProgress = { progress = it }, onPage = { u, b -> currentUrl = u; canGoBack = b }).also { it.loadUrl(url); view = it } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (progress < 1f) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().testTag("article-progress"))
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            view?.apply {
                stopLoading()
                destroy()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: android.content.Context,
    onProgress: (Float) -> Unit,
    onPage: (url: String, canGoBack: Boolean) -> Unit,
): WebView = WebView(context).apply {
    settings.apply {
        // La plupart des sites d'information ne s'affichent pas sans JavaScript
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        setSupportMultipleWindows(false)
        // Mode sombre du téléphone appliqué aux pages qui n'en ont pas
        isAlgorithmicDarkeningAllowed = true
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
    webViewClient = object : WebViewClient() {
        /** Liens web : dans la vue ; le reste (mailto:, intent:, applis…) est ignoré. */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.scheme?.lowercase() !in setOf("https", "http")

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPage(url, view.canGoBack())

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = onPage(url, view.canGoBack())
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress / 100f)
    }
    setDownloadListener { _, _, _, _, _ -> } // aucun téléchargement depuis un article
}
