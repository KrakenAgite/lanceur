# Widgets intégrés, lot 3 — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter les widgets intégrés **Météo** (ville choisie ou position approximative) et **Flux RSS**. Ce sont les seuls qui accèdent à Internet, et ils y accèdent par un point de sortie unique et protégé.

**Architecture:** `net/Network` est la seule classe qui ouvre des connexions : HTTPS seulement, 10 s, 1 Mo, 3 redirections. `NetRules`, pur, porte les décisions. Un test JVM lit les sources et échoue si une connexion apparaît ailleurs. Les modèles sont purs : `Forecast`, `Geocoding`, `WeatherConfig`, `Feed`, `RssConfig`, `Freshness`. Ils sont testés sur des réponses enregistrées, avec le vrai `org.json` en `testImplementation`. Les sources (`WeatherSource`, `RssSource`) gardent la dernière réponse dans les données du widget et ne rappellent le réseau qu'après 30 min. Les cartes sont sans état métier. Les feuilles de réglages reçoivent la recherche de ville, la localisation et la vérification des flux par `BuiltinSettingsServices`.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, Material 3, `HttpURLConnection`, `org.json` (Android ; `org.json:json:20240303` pour les tests JVM), `javax.xml.parsers`, `LocationManager`. Tests : JUnit 4, coroutines-test, Compose UI test v2.

**Spec:** `docs/superpowers/specs/2026-10-05-widgets-integres-lot3-design.md`

## Global Constraints

- minSdk 35, compile/targetSdk 37 ; permissions ajoutées : `android.permission.INTERNET` et `android.permission.ACCESS_COARSE_LOCATION`, et aucune autre.
- **Point de sortie unique** : `HttpURLConnection`, `openConnection` et `URL(` n'apparaissent que dans `app/src/main/java/app/lanceur/net/Network.kt`. Seuls `builtin/weather/*`, `builtin/rss/*` et `AppContainer.kt` mentionnent `Network`.
- HTTPS uniquement (même après redirection) ; délais de 10 s ; réponse de 1 Mo au plus ; 3 redirections au plus ; seul en-tête ajouté : `User-Agent: Lanceur`.
- Coordonnées envoyées arrondies à 0,01° ; nouvel appel seulement si les données ont plus de 30 min ; jamais en arrière-plan.
- La localisation n'est demandée qu'au choix de « Ma position » ; si elle est refusée, le mode reste « Ville ».
- Nouvelles entrées `BuiltinKind` en fin d'enum : `WEATHER("Météo", "🌤", S120/M220/L340, M, configurable)`, `RSS("Flux RSS", "📰", S160/M280/L420, M, configurable)`.
- Style : `cardBackground()`, `CardShape`, `CardLabel`. Textes en français.
- Commandes : `JAVA_HOME=/opt/android-studio/jbr ./gradlew …` ; tests JVM `:app:testDebugUnitTest` ; tests d'interface d'une classe `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<classe>` (téléphone déverrouillé).
- Ne jamais afficher ni committer `keystore.properties` ou `*.jks` ; mettre à jour le téléphone avec `adb install -r`.

## Review Focus

1. **Flux XML piégé.** Un flux avec une `DOCTYPE` ou des entités externes ne doit rien lire de local ni planter. `Feed.parse` désactive les DTD (quand le parseur le permet) et attrape les erreurs. Le test `FeedTest.doctype_does_not_crash` le couvre (Tâche 4).
2. **Redirection vers du HTTP.** Une redirection 301 vers `http://` doit être refusée. Le test `NetRulesTest.redirect_to_http_is_refused` le couvre (Tâche 1).
3. **Réponse énorme.** Plus de 1 Mo doit donner une erreur sans épuiser la mémoire. `Network.readLimited` lit au plus `MAX_BYTES + 1` octets ; à vérifier à la lecture du code.
4. **Fuite vers d'autres modules.** Aucune autre partie de Lanceur ne doit pouvoir appeler le réseau. Le test `NetworkGuardTest` le couvre (Tâche 1).
5. **Boucle de rafraîchissement.** Enregistrer le cache recompose la carte ; cela ne doit pas relancer un appel. L'effet n'est relancé que par `refresh` (retour sur la page) ou un changement de réglage, et le cache est encore frais. À vérifier à la lecture du code (Tâche 3).

---

### Task 1: Point de sortie réseau

**Files:**
- Create: `app/src/main/java/app/lanceur/net/Network.kt`
- Modify: `app/src/main/AndroidManifest.xml` (INTERNET, ACCESS_COARSE_LOCATION), `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Test: `app/src/test/java/app/lanceur/net/NetRulesTest.kt`, `app/src/test/java/app/lanceur/net/NetworkGuardTest.kt`

**Interfaces:**
- Produces: `sealed interface NetResult { Ok(bytes: ByteArray) { text() }; Failed(reason: String) }` ; `object NetRules { MAX_BYTES; MAX_REDIRECTS; allowed(url); next(current, location): String? }` ; `class Network { suspend fun get(url: String): NetResult }`.

- [ ] **Step 1: Write the failing tests**

`NetRulesTest.kt` :

```kotlin
package app.lanceur.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetRulesTest {
    @Test
    fun only_https_is_allowed() {
        assertTrue(NetRules.allowed("https://www.lemonde.fr/rss/une.xml"))
        assertTrue(NetRules.allowed("HTTPS://EXAMPLE.ORG"))
        assertFalse(NetRules.allowed("http://example.org/feed"))
        assertFalse(NetRules.allowed("file:///etc/hosts"))
        assertFalse(NetRules.allowed("pas une adresse"))
    }

    @Test
    fun relative_redirects_are_resolved() {
        assertEquals("https://example.org/b/feed", NetRules.next("https://example.org/a/feed", "/b/feed"))
        assertEquals("https://other.org/x", NetRules.next("https://example.org/a", "https://other.org/x"))
    }

    @Test
    fun redirect_to_http_is_refused() {
        assertNull(NetRules.next("https://example.org/a", "http://example.org/a"))
        assertNull(NetRules.next("https://example.org/a", null))
    }
}
```

`NetworkGuardTest.kt` :

```kotlin
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
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*NetRulesTest*' --tests '*NetworkGuardTest*'`
Expected: FAIL at compilation, "Unresolved reference 'NetRules'".

- [ ] **Step 3: Write `Network.kt`, manifest, test dependency**

```kotlin
package app.lanceur.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface NetResult {
    class Ok(val bytes: ByteArray) : NetResult {
        fun text(): String = bytes.toString(Charsets.UTF_8)
    }

    data class Failed(val reason: String) : NetResult
}

/** Décisions pures du point de sortie : HTTPS seulement, y compris après redirection. */
object NetRules {
    const val MAX_BYTES = 1_000_000
    const val MAX_REDIRECTS = 3
    const val TIMEOUT_MS = 10_000

    fun allowed(url: String): Boolean = runCatching { URI(url.trim()).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)

    /** Adresse suivante d'une redirection, ou `null` si elle est absente ou sort du HTTPS. */
    fun next(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val resolved = runCatching { URI(current).resolve(location.trim()).toString() }.getOrNull() ?: return null
        return resolved.takeIf(::allowed)
    }
}

/**
 * Seul endroit de Lanceur qui ouvre une connexion (vérifié par `NetworkGuardTest`). Utilisé uniquement par la météo
 * et le RSS ; aucun cookie ni identifiant n'est envoyé.
 */
class Network {
    suspend fun get(url: String): NetResult = withContext(Dispatchers.IO) { fetch(url) }

    private fun fetch(start: String): NetResult {
        var current = start
        repeat(NetRules.MAX_REDIRECTS + 1) {
            if (!NetRules.allowed(current)) return NetResult.Failed("Adresse HTTPS requise")
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = NetRules.TIMEOUT_MS
                readTimeout = NetRules.TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "Lanceur")
            }
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> return NetResult.Ok(connection.inputStream.use(::readLimited))
                    301, 302, 303, 307, 308 ->
                        current = NetRules.next(current, connection.getHeaderField("Location")) ?: return NetResult.Failed("Redirection refusée")
                    else -> return NetResult.Failed("Erreur $code")
                }
            } catch (e: IOException) {
                return NetResult.Failed(e.message ?: "Réseau indisponible")
            } finally {
                connection.disconnect()
            }
        }
        return NetResult.Failed("Trop de redirections")
    }

    /** Lit au plus `MAX_BYTES` : au-delà, erreur sans tout charger en mémoire. */
    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > NetRules.MAX_BYTES) throw IOException("Réponse trop grosse")
        }
        return out.toByteArray()
    }
}
```

`AndroidManifest.xml`, avec les autres `<uses-permission>` :

```xml
    <!-- Météo et RSS uniquement, par net/Network (voir NetworkGuardTest) -->
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Demandée seulement si « Ma position » est choisie pour la météo -->
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

`gradle/libs.versions.toml` : dans `[versions]`, `orgJson = "20240303"` ; dans `[libraries]`,
`org-json = { group = "org.json", name = "json", version.ref = "orgJson" }`.
`app/build.gradle.kts`, après `testImplementation(libs.kotlinx.coroutines.test)` :
`testImplementation(libs.org.json)` (le vrai `org.json` sur la JVM ; dans l'appli, celui d'Android).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM.

- [ ] **Step 5: Commit**

```bash
git add app gradle/libs.versions.toml
git commit -m "Point de sortie réseau unique (HTTPS, limites) et garde-fou ; permissions Internet et localisation approximative"
```

---

### Task 2: Météo — logique pure

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/Freshness.kt`
- Create: `app/src/main/java/app/lanceur/builtin/weather/WeatherCode.kt`, `Forecast.kt`, `Geocoding.kt`, `WeatherConfig.kt`, `WeatherView.kt`
- Test: `app/src/test/java/app/lanceur/builtin/FreshnessTest.kt`, `app/src/test/java/app/lanceur/builtin/weather/WeatherTest.kt`

**Interfaces:**
- Produces: `Freshness.isStale(fetchedAt, now)`, `Freshness.label(fetchedAt, now, zone)`, `Freshness.ago(time, now)` ; `WeatherCode.icon(code)`, `WeatherCode.label(code)` ; `Forecast(...)`, `HourForecast`, `DayForecast`, `Forecast.parse(json, now: Instant): Forecast?` ; `Place(name, region, latitude, longitude)` avec `label`, `Geocoding.parse(json)` ; `WeatherQuery.forecastUrl(lat, lon)`, `geocodeUrl(query)`, `searchUrl(placeName)` ; `WeatherConfig(usePosition, place)`, `WeatherCache(raw, fetchedAt)`, `WeatherData.encode(config, cache)`, `fromData` ; `WeatherViewState` et `WeatherView.state(config, cache, outcome, now, zone)` ; `enum WeatherOutcome { FRESH, CACHED, FAILED, NO_POSITION }`.

- [ ] **Step 1: Write the failing tests**

`FreshnessTest.kt` :

```kotlin
package app.lanceur.builtin

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val t0 = LocalDateTime.of(2026, 10, 5, 14, 5).atZone(paris).toInstant().toEpochMilli()
    private val min = 60_000L

    @Test
    fun stale_after_thirty_minutes() {
        assertFalse(Freshness.isStale(t0, t0 + 29 * min))
        assertTrue(Freshness.isStale(t0, t0 + 31 * min))
        assertTrue(Freshness.isStale(null, t0))
    }

    @Test
    fun labels() {
        assertEquals("Mis à jour à 14:05", Freshness.label(t0, t0 + 20 * min, paris))
        assertEquals("Mis à jour il y a 2 h", Freshness.label(t0, t0 + 125 * min, paris))
        assertEquals("Mis à jour il y a 3 j", Freshness.label(t0, t0 + 3 * 24 * 60 * min, paris))
        assertEquals("à l'instant", Freshness.ago(t0, t0 + 30_000))
        assertEquals("il y a 25 min", Freshness.ago(t0, t0 + 25 * min))
        assertEquals("il y a 3 h", Freshness.ago(t0, t0 + 190 * min))
        assertEquals("il y a 2 j", Freshness.ago(t0, t0 + 2 * 24 * 60 * min))
    }
}
```

`WeatherTest.kt` :

```kotlin
package app.lanceur.builtin.weather

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    /** Réponse Open-Meteo réduite, au format réel (heures locales, `utc_offset_seconds`). */
    private val response: String = run {
        val hours = (0 until 48).map { h -> "\"2026-10-0${5 + h / 24}T%02d:00\"".format(h % 24) }
        val temps = (0 until 48).map { 10 + it % 10 }
        val codes = (0 until 48).map { if (it % 2 == 0) 0 else 61 }
        """
        {"latitude":47.22,"longitude":-1.56,"utc_offset_seconds":7200,"timezone":"Europe/Paris",
         "current":{"time":"2026-10-05T14:00","temperature_2m":16.4,"apparent_temperature":15.1,"weather_code":2,
                    "wind_speed_10m":12.3,"precipitation_probability":20},
         "hourly":{"time":[${hours.joinToString()}],"temperature_2m":[${temps.joinToString()}],
                   "weather_code":[${codes.joinToString()}],"precipitation_probability":[${(0 until 48).joinToString()}]},
         "daily":{"time":["2026-10-05","2026-10-06","2026-10-07","2026-10-08","2026-10-09","2026-10-10"],
                  "weather_code":[2,61,3,95,71,0],"temperature_2m_max":[17.4,15,14,13,9,18],
                  "temperature_2m_min":[9.2,8,7,6,1,10],"precipitation_probability_max":[20,80,30,90,60,0]}}
        """.trimIndent()
    }
    private val now = Instant.parse("2026-10-05T12:30:00Z") // 14:30 à Nantes

    @Test
    fun current_conditions_and_today() {
        val f = Forecast.parse(response, now)!!
        assertEquals(16, f.temp)
        assertEquals(15, f.apparent)
        assertEquals("⛅", f.icon)
        assertEquals("Éclaircies", f.condition)
        assertEquals(12, f.wind)
        assertEquals(20, f.rain)
        assertEquals(9, f.min)
        assertEquals(17, f.max)
    }

    @Test
    fun next_six_hours_and_five_days() {
        val f = Forecast.parse(response, now)!!
        assertEquals(listOf("15 h", "16 h", "17 h", "18 h", "19 h", "20 h"), f.hours.map { it.label })
        assertEquals(15, f.hours.first().temp)
        assertEquals("🌧", f.hours.first().icon)
        assertEquals(listOf("Mar.", "Mer.", "Jeu.", "Ven.", "Sam."), f.days.map { it.label })
        assertEquals(DayForecast("Jeu.", "⛈", 6, 13, 90), f.days[2])
    }

    @Test
    fun broken_responses_give_null() {
        assertNull(Forecast.parse("{}", now))
        assertNull(Forecast.parse("pas du json", now))
    }

    @Test
    fun weather_codes() {
        assertEquals("☀", WeatherCode.icon(0))
        assertEquals("🌫", WeatherCode.icon(45))
        assertEquals("🌦", WeatherCode.icon(53))
        assertEquals("🌧", WeatherCode.icon(81))
        assertEquals("❄", WeatherCode.icon(75))
        assertEquals("⛈", WeatherCode.icon(99))
        assertEquals("🌡", WeatherCode.icon(42))
        assertEquals("Orage", WeatherCode.label(95))
    }

    @Test
    fun geocoding_and_urls() {
        val places = Geocoding.parse(
            """{"results":[{"name":"Nantes","latitude":47.21725,"longitude":-1.55336,"admin1":"Pays de la Loire","country":"France"},
                           {"name":"Nant","latitude":44.02,"longitude":3.3,"country":"France"}]}""",
        )
        assertEquals(listOf("Nantes, Pays de la Loire", "Nant, France"), places.map { it.label })
        assertTrue(Geocoding.parse("""{"generationtime_ms":0.2}""").isEmpty())
        assertTrue(WeatherQuery.forecastUrl(47.21725, -1.55336).startsWith("https://api.open-meteo.com/v1/forecast?latitude=47.22&longitude=-1.55&"))
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Saint-%C3%89tienne&count=8&language=fr", WeatherQuery.geocodeUrl("Saint-Étienne"))
    }

    @Test
    fun data_round_trip_and_view_states() {
        val config = WeatherConfig(usePosition = false, place = Place("Nantes", "Pays de la Loire", 47.21, -1.55))
        val cache = WeatherCache(response, fetchedAt = now.toEpochMilli())
        assertEquals(config, WeatherData.config(WeatherData.encode(config, cache)))
        assertEquals(cache, WeatherData.cache(WeatherData.encode(config, cache)))
        assertNull(WeatherData.cache(WeatherData.encode(config, null)))
        val zone = ZoneId.of("Europe/Paris")
        val ready = WeatherView.state(config, cache, WeatherOutcome.CACHED, now.toEpochMilli(), zone)
        assertTrue(ready is WeatherViewState.Ready && ready.placeName == "Nantes")
        assertEquals(WeatherViewState.Loading, WeatherView.state(config, null, null, 0, zone))
        assertEquals(WeatherViewState.Unavailable, WeatherView.state(config, null, WeatherOutcome.FAILED, 0, zone))
        assertEquals(WeatherViewState.NoPosition, WeatherView.state(config.copy(usePosition = true), null, WeatherOutcome.NO_POSITION, 0, zone))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*FreshnessTest*' --tests '*WeatherTest*'`
Expected: FAIL at compilation, "Unresolved reference 'Freshness'" / "'Forecast'".

- [ ] **Step 3: Write the pure code**

`Freshness.kt` :

```kotlin
package app.lanceur.builtin

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ancienneté des données réseau (météo, RSS). */
object Freshness {
    const val MAX_AGE_MS = 30 * 60_000L
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

    fun isStale(fetchedAt: Long?, now: Long): Boolean = fetchedAt == null || now - fetchedAt > MAX_AGE_MS

    /** Moins d'une heure : « Mis à jour à 14:05 » ; sinon « Mis à jour il y a 2 h » (données gardées hors réseau). */
    fun label(fetchedAt: Long, now: Long, zone: ZoneId): String =
        if (now - fetchedAt < 3_600_000) "Mis à jour à ${TIME.format(Instant.ofEpochMilli(fetchedAt).atZone(zone))}"
        else "Mis à jour ${ago(fetchedAt, now)}"

    fun ago(time: Long, now: Long): String {
        val minutes = (now - time).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / (24 * 60)} j"
        }
    }
}
```

`WeatherCode.kt` :

```kotlin
package app.lanceur.builtin.weather

/** Codes météo WMO d'Open-Meteo. */
object WeatherCode {
    fun icon(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..57 -> "🌦"
        in 61..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⛈"
        else -> "🌡"
    }

    fun label(code: Int): String = when (code) {
        0 -> "Ensoleillé"
        1, 2 -> "Éclaircies"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"
        in 61..67, in 80..82 -> "Pluie"
        in 71..77, 85, 86 -> "Neige"
        in 95..99 -> "Orage"
        else -> "Météo"
    }
}
```

`Forecast.kt` :

```kotlin
package app.lanceur.builtin.weather

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class HourForecast(val label: String, val icon: String, val temp: Int)

data class DayForecast(val label: String, val icon: String, val min: Int, val max: Int, val rain: Int?)

data class Forecast(
    val temp: Int,
    val apparent: Int,
    val icon: String,
    val condition: String,
    val wind: Int,
    val rain: Int?,
    val min: Int,
    val max: Int,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
) {
    companion object {
        /** Heures et jours à l'heure locale de la ville (`utc_offset_seconds`) ; `null` si la réponse est inutilisable. */
        fun parse(json: String, now: Instant): Forecast? = runCatching {
            val root = JSONObject(json)
            val offset = ZoneOffset.ofTotalSeconds(root.getInt("utc_offset_seconds"))
            val localNow = now.atOffset(offset).toLocalDateTime()
            val current = root.getJSONObject("current")
            val hourly = root.getJSONObject("hourly")
            val daily = root.getJSONObject("daily")
            val hourTimes = hourly.getJSONArray("time").strings().map(LocalDateTime::parse)
            val hourTemps = hourly.getJSONArray("temperature_2m")
            val hourCodes = hourly.getJSONArray("weather_code")
            val firstHour = hourTimes.indexOfFirst { it.isAfter(localNow) }.takeIf { it >= 0 } ?: hourTimes.size
            val hours = (firstHour until minOf(firstHour + 6, hourTimes.size)).map { i ->
                HourForecast("${hourTimes[i].hour} h", WeatherCode.icon(hourCodes.getInt(i)), hourTemps.getDouble(i).roundToInt())
            }
            val dayTimes = daily.getJSONArray("time").strings().map(LocalDate::parse)
            val today = dayTimes.indexOf(localNow.toLocalDate()).coerceAtLeast(0)
            val dayCodes = daily.getJSONArray("weather_code")
            val maxs = daily.getJSONArray("temperature_2m_max")
            val mins = daily.getJSONArray("temperature_2m_min")
            val rains = daily.optJSONArray("precipitation_probability_max")
            val days = (today + 1 until minOf(today + 6, dayTimes.size)).map { i ->
                val name = dayTimes[i].dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).replaceFirstChar { it.titlecase(Locale.FRENCH) }
                DayForecast(name, WeatherCode.icon(dayCodes.getInt(i)), mins.getDouble(i).roundToInt(), maxs.getDouble(i).roundToInt(), rains?.optIntOrNull(i))
            }
            val code = current.getInt("weather_code")
            Forecast(
                temp = current.getDouble("temperature_2m").roundToInt(),
                apparent = current.getDouble("apparent_temperature").roundToInt(),
                icon = WeatherCode.icon(code),
                condition = WeatherCode.label(code),
                wind = current.getDouble("wind_speed_10m").roundToInt(),
                rain = if (current.has("precipitation_probability") && !current.isNull("precipitation_probability")) current.getInt("precipitation_probability") else null,
                min = mins.getDouble(today).roundToInt(),
                max = maxs.getDouble(today).roundToInt(),
                hours = hours,
                days = days,
            )
        }.getOrNull()

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

        private fun JSONArray.optIntOrNull(i: Int): Int? = if (i < length() && !isNull(i)) getInt(i) else null
    }
}
```

`Geocoding.kt` :

```kotlin
package app.lanceur.builtin.weather

import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

data class Place(val name: String, val region: String, val latitude: Double, val longitude: Double) {
    val label: String get() = if (region.isBlank()) name else "$name, $region"
}

object Geocoding {
    /** Résultats de la recherche de ville d'Open-Meteo ; aucun résultat ou réponse illisible : liste vide. */
    fun parse(json: String): List<Place> = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            Place(
                name = r.getString("name"),
                region = r.optString("admin1").ifBlank { r.optString("country") },
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
            )
        }
    }.getOrDefault(emptyList())
}

object WeatherQuery {
    /** Coordonnées arrondies à 0,01° (environ 1 km) : c'est tout ce que reçoit le service météo. */
    fun forecastUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coord(latitude)}&longitude=${coord(longitude)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation_probability" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=6"

    fun geocodeUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${encode(query.trim())}&count=8&language=fr"

    fun searchUrl(placeName: String): String = "https://www.google.com/search?q=${encode("météo $placeName")}"

    private fun coord(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
```

`WeatherConfig.kt` :

```kotlin
package app.lanceur.builtin.weather

import app.lanceur.builtin.WidgetData

/** `usePosition` : position approximative ; sinon `place`, la ville choisie. */
data class WeatherConfig(val usePosition: Boolean, val place: Place?)

/** Dernière réponse d'Open-Meteo, gardée pour l'affichage hors réseau. */
data class WeatherCache(val raw: String, val fetchedAt: Long)

object WeatherData {
    fun encode(config: WeatherConfig, cache: WeatherCache?): String = WidgetData.encode(
        buildMap {
            put("mode", if (config.usePosition) "position" else "city")
            config.place?.let { p ->
                put("name", p.name)
                put("region", p.region)
                put("lat", p.latitude.toString())
                put("lon", p.longitude.toString())
            }
            cache?.let {
                put("cache", it.raw)
                put("fetchedAt", it.fetchedAt.toString())
            }
        },
    )

    fun config(data: String?): WeatherConfig? {
        val v = WidgetData.decode(data)
        val mode = v["mode"] ?: return null
        val lat = v["lat"]?.toDoubleOrNull()
        val lon = v["lon"]?.toDoubleOrNull()
        val place = if (lat != null && lon != null) Place(v["name"].orEmpty(), v["region"].orEmpty(), lat, lon) else null
        return WeatherConfig(usePosition = mode == "position", place = place)
    }

    fun cache(data: String?): WeatherCache? {
        val v = WidgetData.decode(data)
        val raw = v["cache"] ?: return null
        return WeatherCache(raw, v["fetchedAt"]?.toLongOrNull() ?: return null)
    }
}
```

`WeatherView.kt` :

```kotlin
package app.lanceur.builtin.weather

import app.lanceur.builtin.Freshness
import java.time.Instant
import java.time.ZoneId

enum class WeatherOutcome { FRESH, CACHED, FAILED, NO_POSITION }

sealed interface WeatherViewState {
    data object Loading : WeatherViewState
    data object Unavailable : WeatherViewState
    data object NoPosition : WeatherViewState
    data class Ready(val placeName: String, val forecast: Forecast, val freshness: String) : WeatherViewState
}

object WeatherView {
    /** Des données gardées s'affichent toujours, même si le dernier appel a échoué. */
    fun state(config: WeatherConfig?, cache: WeatherCache?, outcome: WeatherOutcome?, now: Long, zone: ZoneId): WeatherViewState {
        val forecast = cache?.let { Forecast.parse(it.raw, Instant.ofEpochMilli(now)) }
        if (cache != null && forecast != null) {
            val name = if (config?.usePosition == true) "Ma position" else config?.place?.name.orEmpty()
            return WeatherViewState.Ready(name, forecast, Freshness.label(cache.fetchedAt, now, zone))
        }
        return when (outcome) {
            WeatherOutcome.NO_POSITION -> WeatherViewState.NoPosition
            WeatherOutcome.FAILED -> WeatherViewState.Unavailable
            else -> WeatherViewState.Loading
        }
    }
}
```

- [ ] **Step 4: Run them to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Météo : lecture d'Open-Meteo, codes, recherche de ville, cache et fraîcheur (logique pure)"
```

---

### Task 3: Météo — source, carte, réglages, branchement

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/weather/WeatherSource.kt`, `WeatherCard.kt`, `WeatherSettings.kt`
- Modify: `BuiltinKind.kt` (`WEATHER`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `BuiltinSettings.kt` (`BuiltinSettingsServices`), `AppContainer.kt`, `AppRoot.kt`
- Test: `app/src/androidTest/java/app/lanceur/builtin/weather/WeatherCardsTest.kt`

**Interfaces:**
- Consumes: `Network` (Tâche 1) ; tout le pur de la Tâche 2.
- Produces: `class WeatherSource(context, network) { suspend fun search(query): List<Place>; suspend fun refresh(config, cache, now): Pair<WeatherOutcome, WeatherCache?> }` ; `@Composable WeatherCard(state, size, onOpen, onChooseCity, modifier)` ; `@Composable WeatherSettings(initial, search, locationGranted, requestLocation, onSave)` ; `class BuiltinSettingsServices(searchPlaces, locationGranted, requestLocation, checkFeed)` ; `BuiltinServices.weather`, `BuiltinServices.openUrl`.

- [ ] **Step 1: Write the failing UI test**

```kotlin
package app.lanceur.builtin.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeatherCardsTest {
    @get:Rule val rule = createComposeRule()

    private val forecast = Forecast(
        temp = 16, apparent = 15, icon = "⛅", condition = "Éclaircies", wind = 12, rain = 20, min = 9, max = 17,
        hours = (15..20).map { HourForecast("$it h", "🌧", it) },
        days = listOf("Mar.", "Mer.", "Jeu.", "Ven.", "Sam.").map { DayForecast(it, "☁", 8, 15, 30) },
    )
    private val ready = WeatherViewState.Ready("Nantes", forecast, "Mis à jour à 14:05")

    @Test
    fun large_card_shows_now_hours_and_days() {
        var opened = false
        rule.setContent { MaterialTheme { Box(Modifier.height(340.dp)) { WeatherCard(ready, WidgetSize.LARGE, onOpen = { opened = true }, onChooseCity = {}) } } }
        rule.onNodeWithText("16°").assertIsDisplayed()
        rule.onNodeWithText("Min 9° · Max 17°").assertIsDisplayed()
        rule.onNodeWithText("15 h").assertIsDisplayed()
        rule.onNodeWithText("Sam.").assertIsDisplayed()
        rule.onNodeWithText("16°").performClick()
        assertTrue(opened)
    }

    @Test
    fun no_position_offers_a_city() {
        var chose = false
        rule.setContent { MaterialTheme { Box(Modifier.height(220.dp)) { WeatherCard(WeatherViewState.NoPosition, WidgetSize.MEDIUM, onOpen = {}, onChooseCity = { chose = true }) } } }
        rule.onNodeWithText("Choisir une ville").performClick()
        assertTrue(chose)
    }

    @Test
    fun settings_pick_a_found_city() {
        var saved: String? = null
        rule.setContent {
            MaterialTheme {
                WeatherSettings(
                    initial = null,
                    search = { listOf(Place("Nantes", "Pays de la Loire", 47.21, -1.55)) },
                    locationGranted = false,
                    requestLocation = {},
                    onSave = { saved = it },
                )
            }
        }
        rule.onNodeWithText("Enregistrer").assertIsNotEnabled()
        rule.onNodeWithTag("place-search").performTextInput("nan")
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Nantes, Pays de la Loire")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Nantes, Pays de la Loire").performClick()
        rule.onNodeWithText("Enregistrer").performClick()
        assertEquals("Nantes", WeatherData.config(saved)!!.place!!.name)
    }

    @Test
    fun choosing_position_asks_for_location() {
        var asked = false
        rule.setContent {
            MaterialTheme { WeatherSettings(initial = null, search = { emptyList() }, locationGranted = false, requestLocation = { asked = true }, onSave = {}) }
        }
        rule.onNodeWithText("Ma position").performClick()
        assertTrue(asked)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.weather.WeatherCardsTest`
Expected: FAIL at compilation, "Unresolved reference 'WeatherCard'".

- [ ] **Step 3: Source**

`WeatherSource.kt` :

```kotlin
package app.lanceur.builtin.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import app.lanceur.search.SearchPermissions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class WeatherSource(private val context: Context, private val network: Network) {
    suspend fun search(query: String): List<Place> =
        when (val result = network.get(WeatherQuery.geocodeUrl(query))) {
            is NetResult.Ok -> Geocoding.parse(result.text())
            is NetResult.Failed -> emptyList()
        }

    /** Ne rappelle le réseau que si le cache a plus de 30 min. Renvoie le résultat et le cache à garder. */
    suspend fun refresh(config: WeatherConfig, cache: WeatherCache?, now: Long): Pair<WeatherOutcome, WeatherCache?> {
        if (!app.lanceur.builtin.Freshness.isStale(cache?.fetchedAt, now)) return WeatherOutcome.CACHED to cache
        val coords = if (config.usePosition) position() else config.place?.let { it.latitude to it.longitude }
        if (coords == null) return (if (config.usePosition) WeatherOutcome.NO_POSITION else WeatherOutcome.FAILED) to cache
        return when (val result = network.get(WeatherQuery.forecastUrl(coords.first, coords.second))) {
            is NetResult.Ok -> {
                val raw = result.text()
                if (Forecast.parse(raw, java.time.Instant.ofEpochMilli(now)) != null) WeatherOutcome.FRESH to WeatherCache(raw, now)
                else WeatherOutcome.FAILED to cache
            }
            is NetResult.Failed -> WeatherOutcome.FAILED to cache
        }
    }

    /** Dernière position de moins d'1 h, sinon une demande ponctuelle (15 s au plus) ; `null` sans permission. */
    @SuppressLint("MissingPermission")
    private suspend fun position(): Pair<Double, Double>? {
        if (!SearchPermissions.granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        val manager = context.getSystemService(LocationManager::class.java)
        if (!manager.isLocationEnabled) return null
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.hasProvider(it) }
        providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            ?.takeIf { System.currentTimeMillis() - it.time < 3_600_000 }
            ?.let { return it.latitude to it.longitude }
        val provider = providers.firstOrNull() ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    manager.getCurrentLocation(provider, signal, context.mainExecutor) { location ->
                        if (cont.isActive) cont.resume(location?.let { it.latitude to it.longitude })
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }
}
```

- [ ] **Step 4: Card and settings**

`WeatherCard.kt` :

```kotlin
package app.lanceur.builtin.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

@Composable
fun WeatherCard(state: WeatherViewState, size: WidgetSize, onOpen: () -> Unit, onChooseCity: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val base = modifier.fillMaxSize().background(cardBackground())
    when (state) {
        WeatherViewState.Loading -> Message(base, "Chargement de la météo…")
        WeatherViewState.Unavailable -> Message(base, "Météo indisponible, réessaie plus tard")
        WeatherViewState.NoPosition -> Column(base.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Position indisponible", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onChooseCity) { Text("Choisir une ville") }
        }
        is WeatherViewState.Ready -> {
            val f = state.forecast
            Column(base.clickable(onClick = onOpen).padding(horizontal = 22.dp, vertical = 14.dp)) {
                CardLabel(state.placeName)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.icon, fontSize = 36.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("${f.temp}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(f.condition, style = MaterialTheme.typography.titleMedium)
                        Text("Min ${f.min}° · Max ${f.max}°", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                if (size != WidgetSize.SMALL) {
                    Text(
                        listOfNotNull("Ressenti ${f.apparent}°", "Vent ${f.wind} km/h", f.rain?.let { "Pluie $it %" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        f.hours.forEach { h ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(h.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                Text(h.icon)
                                Text("${h.temp}°", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (size == WidgetSize.LARGE) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        f.days.forEach { d ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(d.label, Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
                                Text(d.icon, Modifier.width(32.dp))
                                Text(d.rain?.let { "$it %" }.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                Text("${d.min}° / ${d.max}°", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(state.freshness, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Message(modifier: Modifier, text: String) {
    Column(modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CardLabel("Météo")
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

`WeatherSettings.kt` :

```kotlin
package app.lanceur.builtin.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Ville (recherche Open-Meteo) ou position approximative. Choisir « Ma position » sans autorisation la demande ;
 * tant qu'elle n'est pas accordée, le mode reste « Ville ».
 */
@Composable
fun WeatherSettings(
    initial: WeatherConfig?,
    search: suspend (String) -> List<Place>,
    locationGranted: Boolean,
    requestLocation: () -> Unit,
    onSave: (String) -> Unit,
) {
    var usePosition by remember { mutableStateOf(initial?.usePosition == true && locationGranted) }
    var wantsPosition by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf(initial?.place) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Place>()) }
    var searched by remember { mutableStateOf(false) }
    LaunchedEffect(locationGranted, wantsPosition) { if (wantsPosition && locationGranted) usePosition = true }
    LaunchedEffect(query) {
        if (query.trim().length < 2) return@LaunchedEffect
        delay(400)
        results = search(query)
        searched = true
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Météo", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().clickable { usePosition = false; wantsPosition = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !usePosition, onClick = null)
            Text("Ville", Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.fillMaxWidth().clickable {
                if (locationGranted) usePosition = true else { wantsPosition = true; requestLocation() }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = usePosition, onClick = null)
            Column(Modifier.padding(start = 8.dp)) {
                Text("Ma position")
                Text("Position approximative, envoyée arrondie au kilomètre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!usePosition) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(place?.label ?: "Chercher une ville") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("place-search"),
            )
            if (searched && results.isEmpty()) Text("Aucune ville trouvée", color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { place = p; query = ""; results = emptyList(); searched = false }.padding(vertical = 8.dp)) {
                    Text(p.label)
                }
            }
        }
        Button(
            onClick = { onSave(WeatherData.encode(WeatherConfig(usePosition, if (usePosition) null else place), cache = null)) },
            enabled = usePosition || place != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }
    }
}
```

- [ ] **Step 5: Wire it**

`BuiltinKind`, après `STORAGE(...)` :

```kotlin
    WEATHER("Météo", "🌤", mapOf(SMALL to 120, MEDIUM to 220, LARGE to 340), MEDIUM, configurable = true),
```

`BuiltinSettings.kt` : ajouter avant `BuiltinSettingsSheet` (la Tâche 5 y ajoutera `checkFeed`)

```kotlin
/** Ce dont les feuilles ont besoin hors d'elles-mêmes (réseau, autorisation de localisation). */
class BuiltinSettingsServices(
    val searchPlaces: suspend (String) -> List<app.lanceur.builtin.weather.Place> = { emptyList() },
    val locationGranted: Boolean = false,
    val requestLocation: () -> Unit = {},
)
```

Changer la signature en `fun BuiltinSettingsSheet(kind: BuiltinKind, initial: String?, onSave: (String) -> Unit, onDismiss: () -> Unit, services: BuiltinSettingsServices = BuiltinSettingsServices())`
et ajouter au `when` :

```kotlin
            BuiltinKind.WEATHER -> WeatherSettings(WeatherData.config(initial), services.searchPlaces, services.locationGranted, services.requestLocation, onSave)
```

`AppContainer` : `val network = Network()` et `val weather = WeatherSource(appContext, network)` (imports `app.lanceur.net.Network`, `app.lanceur.builtin.weather.WeatherSource`).

`BuiltinServices` : `val weather: WeatherSource? = null,` et `val openUrl: (String) -> Unit = {},`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.WEATHER -> {
            val id = slot.appWidgetId
            val data = services.data(id)
            val config = WeatherData.config(data)
            val cache = WeatherData.cache(data)
            var outcome by remember(id) { mutableStateOf<WeatherOutcome?>(null) }
            // Relancé au retour sur la page (`refresh`) ou quand les réglages changent ; enregistrer le cache ne relance rien
            LaunchedEffect(id, services.refresh, config) {
                val source = services.weather ?: return@LaunchedEffect
                if (config == null) return@LaunchedEffect
                val (result, kept) = source.refresh(config, cache, System.currentTimeMillis())
                outcome = result
                if (result == WeatherOutcome.FRESH && kept != null) services.saveData(id, WeatherData.encode(config, kept))
            }
            val now = rememberMinuteClock()
            val state = WeatherView.state(config, cache, outcome, now, java.time.ZoneId.systemDefault())
            WeatherCard(
                state,
                size,
                onOpen = { (state as? WeatherViewState.Ready)?.let { services.openUrl(WeatherQuery.searchUrl(it.placeName.takeUnless { n -> n == "Ma position" } ?: "")) } },
                onChooseCity = { services.openSettings(slot) },
                modifier = modifier,
            )
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.WEATHER -> WeatherCard(
            WeatherViewState.Ready(
                "Nantes",
                Forecast(16, 15, "⛅", "Éclaircies", 12, 20, 9, 17, (15..20).map { HourForecast("$it h", if (it < 18) "⛅" else "🌧", it) }, emptyList()),
                "Mis à jour à 14:05",
            ),
            kind.defaultSize,
            onOpen = {},
            onChooseCity = {},
            modifier = modifier,
        )
```

`AppRoot` :
- après `permissionLauncher` :

```kotlin
    var locationGranted by remember { mutableStateOf(SearchPermissions.granted(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locationGranted = it }
```

- dans `BuiltinServices(...)` : `weather = container.weather,` et `openUrl = { url -> container.appLauncher.startSafely(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) },`
- dans l'appel `BuiltinSettingsSheet(...)`, ajouter
  `services = BuiltinSettingsServices(searchPlaces = { container.weather.search(it) }, locationGranted = locationGranted, requestLocation = { locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) }),`
  (import `app.lanceur.builtin.BuiltinSettingsServices`).

- [ ] **Step 6: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` (dont `NetworkGuardTest`) puis la classe `app.lanceur.builtin.weather.WeatherCardsTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src
git commit -m "Widget Météo (ville ou position approximative)"
```

---

### Task 4: RSS — logique pure

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/rss/Feed.kt`, `RssConfig.kt`
- Test: `app/src/test/java/app/lanceur/builtin/rss/FeedTest.kt`

**Interfaces:**
- Produces: `Article(title, link, source, published: Long?)` ; `FeedResult(title, articles)` ; `Feed.parse(bytes, fallbackTitle): FeedResult?`, `Feed.clean(text)`, `Feed.merge(results, limit)` ; `sealed interface FeedCheck { Ok(title, count); Failed(message) }` ; `RssConfig(urls)` avec `validate(url)`, `RssSuggestions.all` ; `RssCache(articles, fetchedAt)` ; `RssData.encode(config, cache)`, `config(data)`, `cache(data)` ; `RssViewState` et `RssView.state(cache, failed)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lanceur.builtin.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {
    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>Le Monde</title>
          <item><title><![CDATA[Élections : <b>les résultats</b> &amp; analyses]]></title><link>https://lemonde.fr/a</link>
            <pubDate>Mon, 05 Oct 2026 12:00:00 +0200</pubDate></item>
          <item><title>L&#39;économie</title><link>https://lemonde.fr/b</link><dc:date>2026-10-05T11:00:00Z</dc:date></item>
          <item><title>Sans date</title><link>https://lemonde.fr/c</link></item>
        </channel></rss>
    """.trimIndent().toByteArray()

    private val atom = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Korben</title>
          <entry><title>Astuce Linux</title><link rel="alternate" href="https://korben.info/x"/><updated>2026-10-05T13:30:00+02:00</updated></entry>
          <entry><title>Doublon</title><link href="https://lemonde.fr/a"/><published>2026-10-04T08:00:00Z</published></entry>
        </feed>
    """.trimIndent().toByteArray()

    @Test
    fun rss_items_are_cleaned_and_dated() {
        val feed = Feed.parse(rss, "secours")!!
        assertEquals("Le Monde", feed.title)
        assertEquals(listOf("Élections : les résultats & analyses", "L'économie", "Sans date"), feed.articles.map { it.title })
        assertEquals(java.time.Instant.parse("2026-10-05T10:00:00Z").toEpochMilli(), feed.articles[0].published)
        assertEquals(java.time.Instant.parse("2026-10-05T11:00:00Z").toEpochMilli(), feed.articles[1].published)
        assertNull(feed.articles[2].published)
        assertEquals("Le Monde", feed.articles[0].source)
    }

    @Test
    fun atom_entries() {
        val feed = Feed.parse(atom, "secours")!!
        assertEquals("Korben", feed.title)
        assertEquals("https://korben.info/x", feed.articles[0].link)
        assertEquals(java.time.Instant.parse("2026-10-05T11:30:00Z").toEpochMilli(), feed.articles[0].published)
    }

    @Test
    fun merge_sorts_newest_first_dedups_and_limits() {
        val merged = Feed.merge(listOf(Feed.parse(rss, "")!!, Feed.parse(atom, "")!!), limit = 3)
        assertEquals(listOf("Astuce Linux", "L'économie", "Élections : les résultats & analyses"), merged.map { it.title })
        val all = Feed.merge(listOf(Feed.parse(rss, "")!!, Feed.parse(atom, "")!!), limit = 10)
        assertEquals(4, all.size)
        assertEquals("Sans date", all.last().title)
    }

    @Test
    fun not_a_feed_and_doctype_do_not_crash() {
        assertNull(Feed.parse("<html><body>coucou</body></html>".toByteArray(), "x"))
        assertNull(Feed.parse("pas du xml".toByteArray(), "x"))
    }

    @Test
    fun doctype_does_not_crash() {
        val evil = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>&x;</title></channel></rss>"""
        val result = runCatching { Feed.parse(evil.toByteArray(), "x") }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.title?.contains("root") != true)
    }

    @Test
    fun config_cache_and_validation() {
        assertEquals("Adresse HTTPS requise", RssConfig.validate("http://example.org/feed"))
        assertNull(RssConfig.validate("https://example.org/feed"))
        val config = RssConfig(listOf("https://www.lemonde.fr/rss/une.xml"))
        val cache = RssCache(Feed.parse(rss, "")!!.articles, fetchedAt = 42)
        val data = RssData.encode(config, cache)
        assertEquals(config, RssData.config(data))
        assertEquals(cache, RssData.cache(data))
        assertTrue(RssSuggestions.all.all { it.url.startsWith("https://") })
        assertEquals(RssViewState.Loading, RssView.state(null, failed = false))
        assertEquals(RssViewState.Unavailable, RssView.state(null, failed = true))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*FeedTest*'`
Expected: FAIL at compilation, "Unresolved reference 'Feed'".

- [ ] **Step 3: Write `Feed.kt` and `RssConfig.kt`**

`Feed.kt` :

```kotlin
package app.lanceur.builtin.rss

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class Article(val title: String, val link: String, val source: String, val published: Long?)

data class FeedResult(val title: String, val articles: List<Article>)

sealed interface FeedCheck {
    data class Ok(val title: String, val count: Int) : FeedCheck
    data class Failed(val message: String) : FeedCheck
}

/** RSS 2.0 (et RDF) et Atom, sans réseau. Les DTD et entités externes sont refusées. */
object Feed {
    fun parse(bytes: ByteArray, fallbackTitle: String): FeedResult? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Selon le parseur (Android ou JVM), certaines options n'existent pas : on applique celles qui existent
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
            ).forEach { (feature, value) -> runCatching { setFeature(feature, value) } }
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
        when (root.localName ?: root.nodeName) {
            "rss", "RDF" -> rss(root, fallbackTitle)
            "feed" -> atom(root, fallbackTitle)
            else -> null
        }
    }.getOrNull()

    private fun rss(root: Element, fallback: String): FeedResult {
        val channel = root.children("channel").firstOrNull() ?: root
        val title = clean(channel.childText("title")).ifBlank { fallback }
        val items = (channel.children("item") + root.children("item")).mapNotNull { item ->
            val link = item.childText("link").trim()
            if (link.isEmpty()) null
            else Article(clean(item.childText("title")).ifBlank { link }, link, title, date(item.childText("pubDate")) ?: date(item.childText("date")))
        }
        return FeedResult(title, items)
    }

    private fun atom(root: Element, fallback: String): FeedResult {
        val title = clean(root.childText("title")).ifBlank { fallback }
        val entries = root.children("entry").mapNotNull { entry ->
            val links = entry.children("link")
            val link = (links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull())
                ?.getAttribute("href")?.trim().orEmpty()
            if (link.isEmpty()) null
            else Article(clean(entry.childText("title")).ifBlank { link }, link, title, date(entry.childText("published")) ?: date(entry.childText("updated")))
        }
        return FeedResult(title, entries)
    }

    /** Plus récents d'abord (sans date à la fin), un seul article par lien. */
    fun merge(results: List<FeedResult>, limit: Int): List<Article> =
        results.flatMap { it.articles }
            .distinctBy { it.link }
            .sortedWith(compareBy<Article> { it.published == null }.thenByDescending { it.published ?: 0 })
            .take(limit)

    /** Balises retirées, entités décodées, espaces resserrés. */
    fun clean(text: String): String {
        val noTags = text.replace(Regex("<[^>]*>"), " ")
        val decoded = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(noTags) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ENTITIES[e] ?: m.value
            }
        }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }

    private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "rsquo" to "’", "lsquo" to "‘", "hellip" to "…", "laquo" to "«", "raquo" to "»")

    private fun date(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
    }

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { (it.localName ?: it.nodeName) == name }
    }

    private fun Element.childText(name: String): String = children(name).firstOrNull()?.textContent.orEmpty()
}
```

`RssConfig.kt` :

```kotlin
package app.lanceur.builtin.rss

import app.lanceur.builtin.WidgetData
import app.lanceur.net.NetRules

data class RssConfig(val urls: List<String>) {
    companion object {
        const val MAX = 3

        /** Message d'erreur, ou `null` si l'adresse est acceptable. */
        fun validate(url: String): String? = if (NetRules.allowed(url)) null else "Adresse HTTPS requise"
    }
}

data class RssSuggestion(val name: String, val url: String)

object RssSuggestions {
    val all = listOf(
        RssSuggestion("Le Monde", "https://www.lemonde.fr/rss/une.xml"),
        RssSuggestion("France Info", "https://www.francetvinfo.fr/titres.rss"),
        RssSuggestion("Libération", "https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml"),
        RssSuggestion("Numerama", "https://www.numerama.com/feed/"),
        RssSuggestion("Les Numériques", "https://www.lesnumeriques.com/rss.xml"),
        RssSuggestion("Korben", "https://korben.info/feed"),
    )
}

data class RssCache(val articles: List<Article>, val fetchedAt: Long)

object RssData {
    fun encode(config: RssConfig, cache: RssCache?): String = WidgetData.encode(
        buildMap {
            put("urls", WidgetData.list(config.urls))
            cache?.let { c ->
                put("fetchedAt", c.fetchedAt.toString())
                // Chaque article : date|source|lien|titre (le titre peut contenir « | »)
                put("articles", WidgetData.list(c.articles.map { "${it.published ?: ""}|${it.source.replace("|", " ")}|${it.link}|${it.title}" }))
            }
        },
    )

    fun config(data: String?): RssConfig? {
        val v = WidgetData.decode(data)
        val urls = WidgetData.unlist(v["urls"]).filter { it.isNotBlank() }
        return if (v.containsKey("urls")) RssConfig(urls) else null
    }

    fun cache(data: String?): RssCache? {
        val v = WidgetData.decode(data)
        val fetchedAt = v["fetchedAt"]?.toLongOrNull() ?: return null
        val articles = WidgetData.unlist(v["articles"]).mapNotNull { e ->
            val p = e.split('|', limit = 4)
            if (p.size == 4) Article(p[3], p[2], p[1], p[0].toLongOrNull()) else null
        }
        return RssCache(articles, fetchedAt)
    }
}

sealed interface RssViewState {
    data object Loading : RssViewState
    data object Unavailable : RssViewState
    data class Ready(val articles: List<Article>, val fetchedAt: Long, val failed: Boolean) : RssViewState
}

object RssView {
    fun state(cache: RssCache?, failed: Boolean): RssViewState = when {
        cache != null -> RssViewState.Ready(cache.articles, cache.fetchedAt, failed)
        failed -> RssViewState.Unavailable
        else -> RssViewState.Loading
    }
}
```

Note : un lien contenant `|` serait mal relu ; les liens d'articles n'en contiennent pas en pratique (caractère réservé, encodé `%7C`).

- [ ] **Step 4: Run it to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "RSS : lecture RSS/Atom, nettoyage, fusion, réglages et cache (logique pure)"
```

---

### Task 5: RSS — source, carte, réglages, branchement

**Files:**
- Create: `app/src/main/java/app/lanceur/builtin/rss/RssSource.kt`, `RssCard.kt`, `RssSettings.kt`
- Modify: `BuiltinKind.kt` (`RSS`), `BuiltinWidget.kt`, `BuiltinPreview.kt`, `BuiltinSettings.kt` (`checkFeed`), `AppContainer.kt`, `AppRoot.kt`
- Test: `app/src/androidTest/java/app/lanceur/builtin/rss/RssCardsTest.kt`

**Interfaces:**
- Consumes: `Network`, `Feed`, `RssConfig`, `RssData`, `RssView`, `Freshness` (Tâches 1, 2, 4).
- Produces: `class RssSource(network) { suspend fun check(url): FeedCheck; suspend fun refresh(config, cache, now): Pair<Boolean /*échec*/, RssCache?> }` ; `@Composable RssCard(state, size, now, onOpen: (String) -> Unit, modifier)` ; `@Composable RssSettings(initial, check, onSave)` ; `BuiltinServices.rss`.

- [ ] **Step 1: Write the failing UI test**

```kotlin
package app.lanceur.builtin.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RssCardsTest {
    @get:Rule val rule = createComposeRule()
    private val now = 1_000_000_000L
    private val articles = listOf(
        Article("Élections : les résultats", "https://lemonde.fr/a", "Le Monde", now - 25 * 60_000),
        Article("Astuce Linux", "https://korben.info/x", "Korben", now - 3 * 3_600_000),
    )

    @Test
    fun lists_articles_and_opens_one() {
        var opened: String? = null
        rule.setContent {
            MaterialTheme { Box(Modifier.height(280.dp)) { RssCard(RssViewState.Ready(articles, now, failed = false), WidgetSize.MEDIUM, now, onOpen = { opened = it }) } }
        }
        rule.onNodeWithText("Le Monde · il y a 25 min").assertIsDisplayed()
        rule.onNodeWithText("Astuce Linux").performClick()
        assertEquals("https://korben.info/x", opened)
    }

    @Test
    fun unavailable_message() {
        rule.setContent { MaterialTheme { RssCard(RssViewState.Unavailable, WidgetSize.MEDIUM, now, onOpen = {}) } }
        rule.onNodeWithText("Flux indisponibles").assertIsDisplayed()
    }

    @Test
    fun settings_check_and_save_a_suggestion() {
        var saved: String? = null
        rule.setContent { MaterialTheme { RssSettings(initial = null, check = { FeedCheck.Ok("Le Monde", 20) }, onSave = { saved = it }) } }
        rule.onNodeWithText("Le Monde").performClick()
        rule.onNodeWithText("Vérifier et enregistrer").performClick()
        rule.waitUntil(3_000) { saved != null }
        assertEquals(listOf("https://www.lemonde.fr/rss/une.xml"), RssData.config(saved)!!.urls)
    }

    @Test
    fun settings_refuse_http() {
        var saved: String? = null
        rule.setContent { MaterialTheme { RssSettings(initial = null, check = { FeedCheck.Ok("x", 1) }, onSave = { saved = it }) } }
        rule.onNodeWithTag("feed-url-0").performTextInput("http://example.org/feed")
        rule.onNodeWithText("Vérifier et enregistrer").performClick()
        rule.onNodeWithText("✗ Adresse HTTPS requise").assertIsDisplayed()
        assertNull(saved)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.builtin.rss.RssCardsTest`
Expected: FAIL at compilation, "Unresolved reference 'RssCard'".

- [ ] **Step 3: Source, card, settings**

`RssSource.kt` :

```kotlin
package app.lanceur.builtin.rss

import app.lanceur.builtin.Freshness
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RssSource(private val network: Network) {
    suspend fun check(url: String): FeedCheck {
        RssConfig.validate(url)?.let { return FeedCheck.Failed(it) }
        return when (val result = network.get(url.trim())) {
            is NetResult.Failed -> FeedCheck.Failed("Flux injoignable")
            is NetResult.Ok -> Feed.parse(result.bytes, hostOf(url))?.let { FeedCheck.Ok(it.title, it.articles.size) }
                ?: FeedCheck.Failed("Pas un flux RSS/Atom")
        }
    }

    /** Flux téléchargés en parallèle si le cache a plus de 30 min. Un flux en erreur est ignoré. Renvoie (échec, cache). */
    suspend fun refresh(config: RssConfig, cache: RssCache?, now: Long): Pair<Boolean, RssCache?> {
        if (!Freshness.isStale(cache?.fetchedAt, now)) return false to cache
        val results = coroutineScope {
            config.urls.map { url ->
                async { (network.get(url) as? NetResult.Ok)?.let { Feed.parse(it.bytes, hostOf(url)) } }
            }.awaitAll()
        }.filterNotNull()
        if (results.isEmpty()) return true to cache
        return false to RssCache(Feed.merge(results, KEPT), now)
    }

    private fun hostOf(url: String) = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull().orEmpty()

    private companion object {
        const val KEPT = 20
    }
}
```

`RssCard.kt` :

```kotlin
package app.lanceur.builtin.rss

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.Freshness
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

@Composable
fun RssCard(state: RssViewState, size: WidgetSize, now: Long, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 20.dp, vertical = 14.dp)) {
        CardLabel("Actualités")
        Spacer(Modifier.height(6.dp))
        when (state) {
            RssViewState.Loading -> Text("Chargement des flux…", color = colors.onSurfaceVariant)
            RssViewState.Unavailable -> Text("Flux indisponibles", color = colors.onSurfaceVariant)
            is RssViewState.Ready -> {
                val count = when (size) {
                    WidgetSize.SMALL -> 3
                    WidgetSize.MEDIUM -> 6
                    WidgetSize.LARGE -> 10
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.articles.isEmpty()) Text("Aucun article", color = colors.onSurfaceVariant)
                    state.articles.take(count).forEach { a ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(a.link) }.padding(vertical = 6.dp)) {
                            Text(a.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(a.source.ifBlank { null }, a.published?.let { Freshness.ago(it, now) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.failed) Text("Hors ligne · ${Freshness.ago(state.fetchedAt, now)}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
```

`RssSettings.kt` :

```kotlin
package app.lanceur.builtin.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Jusqu'à 3 flux ; chacun est vérifié avant d'enregistrer. */
@Composable
fun RssSettings(initial: RssConfig?, check: suspend (String) -> FeedCheck, onSave: (String) -> Unit) {
    val urls = remember { mutableStateListOf(*Array(RssConfig.MAX) { initial?.urls?.getOrNull(it).orEmpty() }) }
    var statuses by remember { mutableStateOf(emptyMap<Int, String>()) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Flux RSS", style = MaterialTheme.typography.headlineSmall)
        urls.indices.forEach { i ->
            OutlinedTextField(
                value = urls[i],
                onValueChange = { urls[i] = it; statuses = statuses - i },
                label = { Text("Adresse du flux ${i + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("feed-url-$i"),
            )
            statuses[i]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        RssSuggestions.all.forEach { s ->
            val index = urls.indexOf(s.url)
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (index >= 0) urls[index] = ""
                    else urls.indexOfFirst { it.isBlank() }.takeIf { it >= 0 }?.let { urls[it] = s.url }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = index >= 0, onCheckedChange = null)
                Text(s.name, Modifier.padding(start = 8.dp))
            }
        }
        Button(
            onClick = {
                checking = true
                scope.launch {
                    val filled = urls.withIndex().filter { it.value.isNotBlank() }
                    val results = filled.associate { (i, url) -> i to (RssConfig.validate(url)?.let { FeedCheck.Failed(it) } ?: check(url.trim())) }
                    statuses = results.mapValues { (_, r) ->
                        when (r) {
                            is FeedCheck.Ok -> "✓ ${r.title} — ${r.count} articles"
                            is FeedCheck.Failed -> "✗ ${r.message}"
                        }
                    }
                    checking = false
                    if (results.values.all { it is FeedCheck.Ok }) onSave(RssData.encode(RssConfig(filled.map { it.value.trim() }), cache = null))
                }
            },
            enabled = !checking && urls.any { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (checking) "Vérification…" else "Vérifier et enregistrer") }
    }
}
```

- [ ] **Step 4: Wire it**

`BuiltinKind`, après `WEATHER(...)` :

```kotlin
    RSS("Flux RSS", "📰", mapOf(SMALL to 160, MEDIUM to 280, LARGE to 420), MEDIUM, configurable = true),
```

`BuiltinSettings.kt` : ajouter à `BuiltinSettingsServices`
`val checkFeed: suspend (String) -> FeedCheck = { FeedCheck.Failed("Indisponible") },` (import `app.lanceur.builtin.rss.FeedCheck`) et au `when` :

```kotlin
            BuiltinKind.RSS -> RssSettings(RssData.config(initial), services.checkFeed, onSave)
```

`AppContainer` : `val rss = RssSource(network)`.

`BuiltinServices` : `val rss: RssSource? = null,`.

`BuiltinWidget`, au `when (kind)` :

```kotlin
        BuiltinKind.RSS -> {
            val id = slot.appWidgetId
            val data = services.data(id)
            val config = RssData.config(data)
            val cache = RssData.cache(data)
            var failed by remember(id) { mutableStateOf(false) }
            LaunchedEffect(id, services.refresh, config) {
                val source = services.rss ?: return@LaunchedEffect
                if (config == null || config.urls.isEmpty()) return@LaunchedEffect
                val (error, kept) = source.refresh(config, cache, System.currentTimeMillis())
                failed = error
                if (!error && kept != null && kept != cache) services.saveData(id, RssData.encode(config, kept))
            }
            RssCard(RssView.state(cache, failed), size, rememberMinuteClock(), onOpen = services.openUrl, modifier = modifier)
        }
```

`BuiltinPreview.SampleCard` :

```kotlin
        BuiltinKind.RSS -> {
            val now = System.currentTimeMillis()
            RssCard(
                RssViewState.Ready(
                    listOf(
                        Article("Élections : les premiers résultats", "a", "Le Monde", now - 25 * 60_000),
                        Article("Une astuce Linux pour gagner du temps", "b", "Korben", now - 3 * 3_600_000),
                        Article("Le budget 2027 présenté", "c", "France Info", now - 5 * 3_600_000),
                    ),
                    now,
                    failed = false,
                ),
                kind.defaultSize,
                now,
                onOpen = {},
                modifier = modifier,
            )
        }
```

`AppRoot` : `rss = container.rss,` dans `BuiltinServices(...)` ; dans `BuiltinSettingsServices(...)`, ajouter `checkFeed = { container.rss.check(it) },`.

- [ ] **Step 5: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest` (dont `NetworkGuardTest`) puis la classe `app.lanceur.builtin.rss.RssCardsTest` sur le téléphone.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Widget Flux RSS"
```

---

### Task 6: README, suites complètes, installation et vérification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README**

Dans la section « Widgets intégrés », ajouter Météo et Flux RSS à la liste, puis :

```markdown
- **Météo / Flux RSS** : les seuls widgets qui utilisent Internet, par un point de sortie unique (HTTPS seulement,
  vérifié par un test). Ils se connectent quand la page de widgets s'affiche, au plus toutes les 30 minutes, jamais en
  arrière-plan. La météo (Open-Meteo, sans compte) ne reçoit que des coordonnées arrondies au kilomètre ; la position
  approximative n'est demandée que si tu choisis « Ma position ». Sans réseau, les dernières données restent affichées.
```

Dans « Limites d'Android » / vie privée du README, remplacer toute mention « aucune permission Internet » par
« Internet uniquement pour la Météo et le RSS ».

- [ ] **Step 2: Suites complètes et release**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Installer**

Run: `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`.

- [ ] **Step 4: Vérification à la main (avec l'utilisateur)**

1. Météo, mode Ville : chercher « Nantes », enregistrer ; S / M / L ; toucher ouvre la recherche Google.
2. Météo, mode Ma position : la permission est demandée ; la météo s'affiche (« Ma position »).
3. RSS : cocher Le Monde, vérifier et enregistrer ; ouvrir un article.
4. Mode avion, revenir sur la page : dernières données avec leur ancienneté.
5. `adb logcat -d -b crash | grep lanceur` : aucun plantage.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "README : widgets Météo et RSS, accès Internet encadré"
```
