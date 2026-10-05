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
    fun connections_are_opened_only_in_network() {
        val offenders = sources.filter { relative(it) != "net/Network.kt" }
            .filter { file -> listOf("HttpURLConnection", "openConnection", "URL(", "Socket(").any { it in file.readText() } }
            .map(::relative)
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun network_is_used_only_by_weather_rss_and_the_container() {
        val allowed = listOf("net/", "builtin/weather/", "builtin/rss/", "AppContainer.kt")
        val users = sources.filter { Regex("""\bNetwork\b""").containsMatchIn(it.readText()) }
            .map(::relative)
            .filterNot { path -> allowed.any { path.startsWith(it) } }
        assertEquals(emptyList<String>(), users)
    }
}
