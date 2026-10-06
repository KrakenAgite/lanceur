package app.lanceur.net

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Garde-fou : le réseau ne sort que par `Network`, utilisé seulement par la météo et le RSS. */
class NetworkGuardTest {
    private val root = File("src/main/java/app/lanceur")
    private val sources = root.walkTopDown().filter { it.extension == "kt" }.toList()
    private fun relative(file: File) = file.relativeTo(root).invariantSeparatorsPath

    @Test
    fun sources_are_found() {
        // Sinon les autres tests passeraient sans rien vérifier (mauvais dossier de travail)
        org.junit.Assert.assertTrue(sources.size > 50)
    }

    @Test
    fun connections_are_opened_only_in_network() {
        val offenders = sources.filter { relative(it) != "net/Network.kt" }
            .filter { file -> listOf("HttpURLConnection", "openConnection", "openStream", "URL(", "toURL(", "Socket(", "DownloadManager", "okhttp", "ktor", ".connect(").any { it in file.readText() } }
            .map(::relative)
        assertEquals(emptyList<String>(), offenders)
    }

    /** La vue web des articles est la seule exception : elle charge la page que l'on a touchée, rien d'autre. */
    @Test
    fun webview_is_used_only_by_the_article_view() {
        val users = sources.filter { "android.webkit" in it.readText() }.map(::relative)
        assertEquals(listOf("news/ArticleWebView.kt"), users)
    }

    @Test
    fun network_is_used_only_by_weather_rss_and_the_container() {
        val allowed = listOf("net/", "builtin/weather/", "builtin/rss/", "news/", "AppContainer.kt")
        val users = sources.filter { Regex("""\bNetwork\b""").containsMatchIn(it.readText()) }
            .map(::relative)
            .filterNot { path -> allowed.any { path.startsWith(it) } }
        assertEquals(emptyList<String>(), users)
    }
}
