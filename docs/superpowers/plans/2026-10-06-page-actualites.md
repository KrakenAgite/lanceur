# Page Actualités et pages réordonnables — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter une page plein écran d'actualités RSS : des bulles de filtre en haut, les articles en cartes avec l'image du flux. Les pages (Actualités, Widgets, Accueil) s'activent et se réordonnent dans les réglages.

**Architecture:** L'ordre et l'activation des pages sont des réglages (`pageOrder`, `newsEnabled`, plus l'ancien `widgetPageEnabled` repris tel quel). `PageLayout`, en code pur, calcule les pages actives. `HomePager` accepte une liste de pages ; l'ancienne signature reste et délègue. La page Actualités garde son état (`NewsState` : flux et derniers articles) sous une clé DataStore `news`, modifiée en transaction. `NewsSource` actualise en parallèle par le point de sortie `Network` du lot 3. Une actualisation qui revient se fusionne avec l'état courant (`NewsState.merge`), pour qu'un flux ajouté ou retiré entre-temps ne soit pas perdu. `Feed` du lot 3 apprend à repérer l'image d'un article. `ImageLoader` télécharge par `Network`, décode en réduisant (`ImageSizing`, pur) et garde un cache mémoire et disque.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, Material 3 (`PullToRefreshBox`, `ModalBottomSheet`), `HttpURLConnection` via `net/Network`, `BitmapFactory`, `android.util.LruCache`. Tests : JUnit 4, coroutines-test, Compose UI test v2.

**Spec:** `docs/superpowers/specs/2026-10-06-page-actualites-design.md`

## Global Constraints

- Branche `page-actualites`, créée depuis `widgets-integres-lot3` : `Network`, `NetRules`, `Feed`, `FeedCheck`, `RssSuggestions`, `RssSource.check`, `Freshness`, `cardBackground`, `CardShape` et `CardLabel` existent déjà.
- Réseau : uniquement par `net/Network` (HTTPS, 10 s, 1 Mo) ; `NetworkGuardTest` autorise `news/` en plus ; les liens ouverts sont des liens web uniquement (`openUrl` d'AppRoot).
- Actualisation : à l'affichage de la page si un flux a plus de 30 min, ou forcée par ⟳ / tirer vers le bas ; jamais en arrière-plan.
- 30 articles au plus par flux ; images au format 16:9 recadrées ; cache disque `cacheDir/news-images` de 50 Mo au plus ; cache mémoire de 40 images.
- Pages : `NEWS` (désactivée par défaut), `WIDGETS` (reprend `widgetPageEnabled`), `HOME` (toujours active, non désactivable) ; ordre par défaut `NEWS · WIDGETS · HOME`.
- Style : `cardBackground()`, `CardShape` (28 dp), `CardLabel` ; coins concentriques (28 dp − marge). Textes en français.
- Commandes : `JAVA_HOME=/opt/android-studio/jbr ./gradlew …` ; JVM `:app:testDebugUnitTest` ; interface `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<classe>` (téléphone déverrouillé).
- Ne jamais afficher ni committer `keystore.properties` ou `*.jks` ; installer avec `adb install -r`.

**Ruling de conception (réglages) :** la spec parle d'une « poignée » pour réordonner les pages. Avec trois pages seulement, des boutons ↑ / ↓ par ligne sont plus sûrs au toucher et plus simples à tester. C'est ce que fait la Tâche 6.

## Review Focus

1. **Course entre une actualisation et une modification des flux.** Un flux ajouté ou retiré pendant qu'une actualisation est en cours ne doit être ni perdu ni ressuscité. `NewsState.merge` et le test `NewsStateTest.merge_keeps_feeds_added_or_removed_meanwhile` le couvrent (Tâche 3).
2. **Mémoire des images.** Une image de 4000 × 3000 ne doit jamais être décodée en taille réelle. `ImageSizing.sampleSize` et son test le couvrent (Tâche 4) ; `ImageLoader` décode avec `inSampleSize`.
3. **Images hors écran.** Le chargement d'une carte sortie de l'écran doit s'arrêter (`produceState` lié à la composition, `LazyColumn`). À vérifier à la lecture du code.
4. **Pager à trois pages et bouton Accueil.** Depuis la page la plus éloignée, Accueil ramène à `HOME` et Retour aussi. Le test `HomePagerTest.three_pages_reach_news_and_home_returns` le couvre (Tâche 6).
5. **Migration des réglages.** Un utilisateur qui avait désactivé la page de widgets la retrouve désactivée, et Actualités désactivée. Le test `PrefsRepoTest.pages_default_and_migration` le couvre (Tâche 1).

---

### Task 1: Pages — logique pure et réglages

**Files:**
- Create: `app/src/main/java/app/lanceur/home/PageLayout.kt`
- Modify: `app/src/main/java/app/lanceur/prefs/LauncherPrefs.kt`, `prefs/PrefsRepo.kt`, `home/LauncherViewModel.kt`
- Test: `app/src/test/java/app/lanceur/home/PageLayoutTest.kt`, `app/src/test/java/app/lanceur/prefs/PrefsRepoTest.kt`

**Interfaces:**
- Produces: `enum class PageKind(label, subtitle) { NEWS, WIDGETS, HOME }` ; `PageLayout.DEFAULT_ORDER`, `normalize(order)`, `active(order, widgetsEnabled, newsEnabled)`, `move(order, kind, delta)`, `encode(order)`, `decode(text)` ; `LauncherPrefs.newsEnabled`, `pageOrder`, `news: String?` ; `PrefsRepo.setNewsEnabled`, `setPageOrder`, `updateNews(transform: (String?) -> String)` ; VM `setNewsEnabled`, `movePage(kind, delta)`, `updateNews(transform)`.

- [ ] **Step 1: Write the failing tests**

`PageLayoutTest.kt` :

```kotlin
package app.lanceur.home

import app.lanceur.home.PageKind.HOME
import app.lanceur.home.PageKind.NEWS
import app.lanceur.home.PageKind.WIDGETS
import org.junit.Assert.assertEquals
import org.junit.Test

class PageLayoutTest {
    @Test
    fun active_pages_follow_the_order_and_home_is_always_there() {
        assertEquals(listOf(WIDGETS, HOME), PageLayout.active(PageLayout.DEFAULT_ORDER, widgetsEnabled = true, newsEnabled = false))
        assertEquals(listOf(NEWS, WIDGETS, HOME), PageLayout.active(PageLayout.DEFAULT_ORDER, widgetsEnabled = true, newsEnabled = true))
        assertEquals(listOf(WIDGETS, HOME, NEWS), PageLayout.active(listOf(WIDGETS, HOME, NEWS), widgetsEnabled = true, newsEnabled = true))
        assertEquals(listOf(HOME), PageLayout.active(PageLayout.DEFAULT_ORDER, widgetsEnabled = false, newsEnabled = false))
    }

    @Test
    fun broken_orders_are_repaired() {
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.normalize(emptyList()))
        assertEquals(listOf(HOME, NEWS, WIDGETS), PageLayout.normalize(listOf(HOME, HOME, NEWS)))
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.decode(null))
        assertEquals(listOf(WIDGETS, HOME, NEWS), PageLayout.decode(PageLayout.encode(listOf(WIDGETS, HOME, NEWS))))
        assertEquals(listOf(HOME, NEWS, WIDGETS), PageLayout.decode("HOME,PLANETE,NEWS"))
    }

    @Test
    fun move_shifts_one_place_within_bounds() {
        assertEquals(listOf(NEWS, HOME, WIDGETS), PageLayout.move(PageLayout.DEFAULT_ORDER, HOME, -1))
        assertEquals(listOf(WIDGETS, NEWS, HOME), PageLayout.move(PageLayout.DEFAULT_ORDER, NEWS, +1))
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.move(PageLayout.DEFAULT_ORDER, NEWS, -1))
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.move(PageLayout.DEFAULT_ORDER, HOME, +1))
    }
}
```

Ajouter à `PrefsRepoTest` :

```kotlin
    @Test
    fun pages_default_and_migration() = runTest {
        val repo = PrefsRepo(store())
        val initial = repo.prefs.first()
        assertEquals(app.lanceur.home.PageLayout.DEFAULT_ORDER, initial.pageOrder)
        assertFalse(initial.newsEnabled)
        repo.setWidgetPageEnabled(false)
        repo.setNewsEnabled(true)
        repo.setPageOrder(listOf(app.lanceur.home.PageKind.WIDGETS, app.lanceur.home.PageKind.HOME, app.lanceur.home.PageKind.NEWS))
        val prefs = repo.prefs.first()
        assertFalse(prefs.widgetPageEnabled)
        assertTrue(prefs.newsEnabled)
        assertEquals(listOf(app.lanceur.home.PageKind.WIDGETS, app.lanceur.home.PageKind.HOME, app.lanceur.home.PageKind.NEWS), prefs.pageOrder)
        repo.updateNews { (it ?: "") + "x" }
        repo.updateNews { (it ?: "") + "y" }
        assertEquals("xy", repo.prefs.first().news)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*PageLayoutTest*' --tests '*PrefsRepoTest*'`
Expected: FAIL at compilation, "Unresolved reference 'PageLayout'".

- [ ] **Step 3: Write `PageLayout.kt` and the settings**

```kotlin
package app.lanceur.home

/** Pages du défilement horizontal. Le nom de chaque entrée est enregistré : ne jamais le renommer. */
enum class PageKind(val label: String, val subtitle: String) {
    NEWS("Actualités", "Tes flux RSS, avec images"),
    WIDGETS("Widgets", "Résumé du jour et widgets"),
    HOME("Accueil", "Toujours affichée"),
}

object PageLayout {
    val DEFAULT_ORDER = listOf(PageKind.NEWS, PageKind.WIDGETS, PageKind.HOME)

    /** Chaque page une seule fois, les manquantes ajoutées à la fin dans l'ordre par défaut. */
    fun normalize(order: List<PageKind>): List<PageKind> = (order.distinct() + DEFAULT_ORDER).distinct()

    fun active(order: List<PageKind>, widgetsEnabled: Boolean, newsEnabled: Boolean): List<PageKind> =
        normalize(order).filter {
            when (it) {
                PageKind.HOME -> true
                PageKind.WIDGETS -> widgetsEnabled
                PageKind.NEWS -> newsEnabled
            }
        }

    /** Décale `kind` d'une place (`delta` = -1 vers la gauche, +1 vers la droite), sans sortir de la liste. */
    fun move(order: List<PageKind>, kind: PageKind, delta: Int): List<PageKind> {
        val list = normalize(order).toMutableList()
        val from = list.indexOf(kind)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return list
        list.removeAt(from)
        list.add(to, kind)
        return list
    }

    fun encode(order: List<PageKind>): String = normalize(order).joinToString(",") { it.name }

    fun decode(text: String?): List<PageKind> =
        normalize(text.orEmpty().split(',').mapNotNull { name -> PageKind.entries.firstOrNull { it.name == name.trim() } })
}
```

`LauncherPrefs.kt` : ajouter à la fin du constructeur

```kotlin
    val newsEnabled: Boolean = false,
    /** Ordre de gauche à droite des pages ; `widgetPageEnabled` et `newsEnabled` disent lesquelles sont affichées. */
    val pageOrder: List<PageKind> = PageLayout.DEFAULT_ORDER,
    /** État de la page Actualités (`NewsState` encodé). */
    val news: String? = null,
```

(imports `app.lanceur.home.PageKind`, `app.lanceur.home.PageLayout`).

`PrefsRepo.kt` :
- clés : `val NEWS_ENABLED = booleanPreferencesKey("news_enabled")`, `val PAGE_ORDER = stringPreferencesKey("page_order")`, `val NEWS = stringPreferencesKey("news")` ;
- `decode` : `newsEnabled = stored[NEWS_ENABLED] ?: false, pageOrder = PageLayout.decode(stored[PAGE_ORDER]), news = stored[NEWS],` ;
- `encode` : `out[NEWS_ENABLED] = prefs.newsEnabled`, `out[PAGE_ORDER] = PageLayout.encode(prefs.pageOrder)`, et `if (prefs.news != null) out[NEWS] = prefs.news else out.remove(NEWS)` ;
- fonctions :

```kotlin
    suspend fun setNewsEnabled(enabled: Boolean) = update { it.copy(newsEnabled = enabled) }

    suspend fun setPageOrder(order: List<PageKind>) = update { it.copy(pageOrder = PageLayout.normalize(order)) }

    /** Transformation dans la transaction : une actualisation et un ajout de flux ne s'écrasent pas. */
    suspend fun updateNews(transform: (String?) -> String) = update { it.copy(news = transform(it.news)) }
```

`LauncherViewModel.kt` :

```kotlin
    fun setNewsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setNewsEnabled(enabled) }
    }

    fun movePage(kind: PageKind, delta: Int) {
        viewModelScope.launch { prefsRepo.setPageOrder(PageLayout.move(prefs.value.pageOrder, kind, delta)) }
    }

    fun updateNews(transform: (String?) -> String) {
        viewModelScope.launch { prefsRepo.updateNews(transform) }
    }
```

- [ ] **Step 4: Run them to verify they pass**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS, toute la suite JVM (dont `defaults_when_nothing_is_stored`).

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Pages : ordre et activation (Actualités, Widgets, Accueil), état des actualités dans les réglages"
```

---

### Task 2: Images des articles dans `Feed`

**Files:**
- Modify: `app/src/main/java/app/lanceur/builtin/rss/Feed.kt`
- Test: `app/src/test/java/app/lanceur/builtin/rss/FeedImageTest.kt`

**Interfaces:**
- Produces: `Article.image: String? = null` (dernier paramètre, défaut `null` : les appels existants ne changent pas).

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lanceur.builtin.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedImageTest {
    private fun images(items: String) = Feed.parse(
        """<rss xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>T</title>$items</channel></rss>""".toByteArray(),
        "",
    )!!.articles.map { it.image }

    @Test
    fun media_enclosure_and_html_images() {
        val result = images(
            """
            <item><title>A</title><link>https://s.fr/a</link><media:content url="https://img.fr/a.jpg" medium="image"/></item>
            <item><title>B</title><link>https://s.fr/b</link><media:thumbnail url="https://img.fr/b.jpg"/></item>
            <item><title>C</title><link>https://s.fr/c</link><enclosure url="https://img.fr/c.png" type="image/png"/></item>
            <item><title>D</title><link>https://s.fr/d</link><description>&lt;p&gt;&lt;img src="https://img.fr/d.webp" /&gt;Texte&lt;/p&gt;</description></item>
            <item><title>E</title><link>https://s.fr/e</link><enclosure url="https://s.fr/e.mp3" type="audio/mpeg"/></item>
            """,
        )
        assertEquals(listOf("https://img.fr/a.jpg", "https://img.fr/b.jpg", "https://img.fr/c.png", "https://img.fr/d.webp", null), result)
    }

    @Test
    fun relative_images_are_resolved_and_http_refused() {
        val result = images(
            """
            <item><title>A</title><link>https://s.fr/2026/a.html</link><content:encoded><![CDATA[<img src="/img/a.jpg">]]></content:encoded></item>
            <item><title>B</title><link>https://s.fr/b</link><media:content url="http://img.fr/b.jpg" medium="image"/></item>
            """,
        )
        assertEquals(listOf("https://s.fr/img/a.jpg", null), result)
    }

    @Test
    fun atom_entries_find_their_image() {
        val feed = """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/"><title>K</title>
            <entry><title>A</title><link href="https://k.fr/a"/><media:thumbnail url="https://k.fr/a.jpg"/></entry>
            <entry><title>B</title><link href="https://k.fr/b"/><content type="html">&lt;img src="https://k.fr/b.jpg"&gt;</content></entry></feed>"""
        assertEquals(listOf("https://k.fr/a.jpg", "https://k.fr/b.jpg"), Feed.parse(feed.toByteArray(), "")!!.articles.map { it.image })
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*FeedImageTest*'`
Expected: FAIL at compilation, "Unresolved reference 'image'".

- [ ] **Step 3: Implement**

Dans `Feed.kt` :
- `data class Article(val title: String, val link: String, val source: String, val published: Long?, val image: String? = null)` ;
- dans `rss(...)`, construire `Article(..., date(...) ?: date(...), image = image(item, link))` ; dans `atom(...)`, `image = image(entry, link)` ;
- ajouter :

```kotlin
    private const val MEDIA_NS = "search.yahoo.com/mrss"
    private val IMG_SRC = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /**
     * Image de l'article : `media:content` / `media:thumbnail`, puis `enclosure` d'image, puis premier `<img>` du
     * contenu HTML. Lien relatif résolu par rapport à l'article ; HTTPS exigé, sinon pas d'image.
     */
    private fun image(item: Element, base: String): String? {
        val media = (item.children("content") + item.children("thumbnail")).firstOrNull { el ->
            el.namespaceURI?.contains(MEDIA_NS) == true &&
                (el.localName == "thumbnail" || el.getAttribute("medium") == "image" || el.getAttribute("type").startsWith("image/"))
        }?.getAttribute("url")
        val enclosure = item.children("enclosure").firstOrNull { it.getAttribute("type").startsWith("image/") }?.getAttribute("url")
        val html = listOf("description", "encoded", "content", "summary").joinToString(" ") { item.childText(it) }
        val inHtml = IMG_SRC.find(html)?.groupValues?.get(1)
        val raw = listOfNotNull(media, enclosure, inHtml).firstOrNull { it.isNotBlank() } ?: return null
        val resolved = runCatching { java.net.URI(base).resolve(raw.trim().replace("&amp;", "&")).toString() }.getOrNull() ?: return null
        return resolved.takeIf(NetRules::allowed)
    }
```

(import `app.lanceur.net.NetRules`). `children("content")` couvre `media:content` et le `<content>` d'Atom : le filtre d'espace de noms ne retient que celui de Media RSS ; le texte HTML du `<content>` Atom passe par `childText("content")`.

- [ ] **Step 4: Run it to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS (dont les anciens `FeedTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Flux : image de chaque article (media, enclosure, <img>), HTTPS exigé"
```

---

### Task 3: État et fil des actualités (pur) et source

**Files:**
- Create: `app/src/main/java/app/lanceur/news/NewsState.kt`, `NewsFeed.kt`, `NewsSource.kt`
- Modify: `app/src/test/java/app/lanceur/net/NetworkGuardTest.kt` (`news/` autorisé)
- Test: `app/src/test/java/app/lanceur/news/NewsStateTest.kt`

**Interfaces:**
- Consumes: `Article`, `Feed`, `Freshness`, `Network`, `WidgetData`.
- Produces: `FeedSnapshot(url, title, articles, fetchedAt: Long?, failed)` ; `NewsState(feeds)` avec `add(url, title)`, `remove(url)`, `encode()`, `NewsState.decode(String?)`, `NewsState.merge(current, refreshed)` ; `NewsChip(url, label, failed)` ; `NewsFeed.PER_FEED`, `articles(state, filter)`, `chips(state)`, `validFilter(state, filter)`, `footer(state, now, zone)`, `allFailedEmpty(state)` ; `NewsSource(network).refresh(state, now, force): NewsState`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lanceur.news

import app.lanceur.builtin.rss.Article
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsStateTest {
    private val lemonde = "https://www.lemonde.fr/rss/une.xml"
    private val korben = "https://korben.info/feed"
    private fun art(title: String, link: String, at: Long?, image: String? = null) = Article(title, link, "", at, image)

    private val state = NewsState(
        listOf(
            FeedSnapshot(lemonde, "Le Monde", listOf(art("A", "https://l/a", 300, "https://i/a.jpg"), art("B", "https://l/b", 100)), fetchedAt = 1_000, failed = false),
            FeedSnapshot(korben, "Korben", listOf(art("K", "https://k/k", 200), art("Dup", "https://l/a", 50), art("Sans date", "https://k/s", null)), fetchedAt = 2_000, failed = true),
        ),
    )

    @Test
    fun articles_merge_sort_dedup_filter_and_take_the_feed_name() {
        assertEquals(listOf("A", "K", "B", "Sans date"), NewsFeed.articles(state, null).map { it.title })
        assertEquals("Le Monde", NewsFeed.articles(state, null).first().source)
        assertEquals("https://i/a.jpg", NewsFeed.articles(state, null).first().image)
        assertEquals(listOf("K", "Dup", "Sans date"), NewsFeed.articles(state, korben).map { it.title })
        val many = NewsState(listOf(FeedSnapshot(lemonde, "LM", (1..40).map { art("$it", "https://l/$it", it.toLong()) })))
        assertEquals(NewsFeed.PER_FEED, NewsFeed.articles(many, null).size)
    }

    @Test
    fun chips_disambiguate_same_names_and_flag_failures() {
        assertEquals(listOf(NewsChip(lemonde, "Le Monde", false), NewsChip(korben, "Korben", true)), NewsFeed.chips(state))
        val twins = NewsState(listOf(FeedSnapshot("https://a.fr/rss", "Actu"), FeedSnapshot("https://www.b.com/rss", "Actu")))
        assertEquals(listOf("Actu · a.fr", "Actu · b.com"), NewsFeed.chips(twins).map { it.label })
    }

    @Test
    fun add_remove_and_filter_reset() {
        val added = state.add("https://x.fr/rss", "X").add(lemonde, "doublon")
        assertEquals(3, added.feeds.size)
        val removed = added.remove(korben)
        assertEquals(listOf(lemonde, "https://x.fr/rss"), removed.feeds.map { it.url })
        assertNull(NewsFeed.validFilter(removed, korben))
        assertEquals(lemonde, NewsFeed.validFilter(removed, lemonde))
    }

    @Test
    fun encode_round_trip() {
        assertEquals(state, NewsState.decode(state.encode()))
        assertEquals(NewsState(), NewsState.decode(null))
        assertEquals(NewsState(), NewsState.decode("n'importe quoi"))
    }

    @Test
    fun merge_keeps_feeds_added_or_removed_meanwhile() {
        val refreshed = NewsState(state.feeds.map { it.copy(fetchedAt = 9_000, failed = false) })
        val current = state.remove(korben).add("https://x.fr/rss", "X")
        val merged = NewsState.merge(current, refreshed)
        assertEquals(listOf(lemonde, "https://x.fr/rss"), merged.feeds.map { it.url })
        assertEquals(9_000L, merged.feeds.first().fetchedAt)
        assertNull(merged.feeds.last().fetchedAt)
    }

    @Test
    fun footer_texts() {
        val paris = ZoneId.of("Europe/Paris")
        val at = LocalDateTime.of(2026, 10, 6, 14, 5).atZone(paris).toInstant().toEpochMilli()
        val ok = NewsState(listOf(FeedSnapshot(lemonde, "LM", fetchedAt = at)))
        assertEquals("Mis à jour à 14:05", NewsFeed.footer(ok, at + 60_000, paris))
        val offline = NewsState(listOf(FeedSnapshot(lemonde, "LM", fetchedAt = at, failed = true)))
        assertEquals("Hors ligne · il y a 2 h", NewsFeed.footer(offline, at + 2 * 3_600_000, paris))
        assertNull(NewsFeed.footer(NewsState(), at, paris))
        assertTrue(NewsFeed.allFailedEmpty(NewsState(listOf(FeedSnapshot(lemonde, "LM", failed = true)))))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*NewsStateTest*'`
Expected: FAIL at compilation, "Unresolved reference 'NewsState'".

- [ ] **Step 3: Write `NewsState.kt`, `NewsFeed.kt`, `NewsSource.kt`**

`NewsState.kt` :

```kotlin
package app.lanceur.news

import app.lanceur.builtin.WidgetData
import app.lanceur.builtin.rss.Article

/** Un flux de la page : son nom, ses derniers articles et l'état du dernier essai. */
data class FeedSnapshot(
    val url: String,
    val title: String,
    val articles: List<Article> = emptyList(),
    val fetchedAt: Long? = null,
    val failed: Boolean = false,
)

data class NewsState(val feeds: List<FeedSnapshot> = emptyList()) {
    fun add(url: String, title: String) = if (feeds.any { it.url == url }) this else copy(feeds = feeds + FeedSnapshot(url, title))

    fun remove(url: String) = copy(feeds = feeds.filterNot { it.url == url })

    /** Champs d'un article séparés par U+001E (jamais dans un titre : `Feed.clean` retire les caractères de contrôle). */
    fun encode(): String = WidgetData.encode(
        buildMap {
            put("feeds", WidgetData.list(feeds.map { it.url }))
            feeds.forEachIndexed { i, f ->
                put("title.$i", f.title)
                f.fetchedAt?.let { put("fetched.$i", it.toString()) }
                if (f.failed) put("failed.$i", "1")
                put("articles.$i", WidgetData.list(f.articles.map { a -> listOf(a.published?.toString().orEmpty(), a.image.orEmpty(), a.link, a.title).joinToString(FIELD) }))
            }
        },
    )

    companion object {
        private const val FIELD = "\u001E"

        fun decode(data: String?): NewsState {
            val v = WidgetData.decode(data)
            val urls = WidgetData.unlist(v["feeds"]).filter { it.isNotBlank() }
            return NewsState(
                urls.mapIndexed { i, url ->
                    FeedSnapshot(
                        url = url,
                        title = v["title.$i"].orEmpty(),
                        articles = WidgetData.unlist(v["articles.$i"]).mapNotNull { e ->
                            val p = e.split(FIELD)
                            if (p.size == 4) Article(p[3], p[2], "", p[0].toLongOrNull(), p[1].ifEmpty { null }) else null
                        },
                        fetchedAt = v["fetched.$i"]?.toLongOrNull(),
                        failed = v["failed.$i"] == "1",
                    )
                },
            )
        }

        /** Résultat d'une actualisation appliqué à l'état courant : un flux ajouté ou retiré entre-temps est respecté. */
        fun merge(current: NewsState, refreshed: NewsState): NewsState {
            val byUrl = refreshed.feeds.associateBy { it.url }
            return NewsState(current.feeds.map { byUrl[it.url] ?: it })
        }
    }
}
```

`NewsFeed.kt` :

```kotlin
package app.lanceur.news

import app.lanceur.builtin.Freshness
import app.lanceur.builtin.rss.Article
import java.time.ZoneId

data class NewsChip(val url: String, val label: String, val failed: Boolean)

object NewsFeed {
    const val PER_FEED = 30

    /** Articles du filtre (`null` = tous), récents d'abord, sans doublon de lien ; la source est le nom du flux. */
    fun articles(state: NewsState, filter: String?): List<Article> =
        state.feeds.filter { filter == null || it.url == filter }
            .flatMap { feed -> feed.articles.take(PER_FEED).map { it.copy(source = feed.title) } }
            .distinctBy { it.link }
            .sortedWith(compareBy<Article> { it.published == null }.thenByDescending { it.published ?: 0 })

    /** Deux flux du même nom : le domaine les distingue. */
    fun chips(state: NewsState): List<NewsChip> {
        val counts = state.feeds.groupingBy { it.title }.eachCount()
        return state.feeds.map { f ->
            val label = if ((counts[f.title] ?: 0) > 1) "${f.title} · ${host(f.url)}" else f.title.ifBlank { host(f.url) }
            NewsChip(f.url, label, f.failed)
        }
    }

    fun validFilter(state: NewsState, filter: String?): String? = filter?.takeIf { f -> state.feeds.any { it.url == f } }

    /** « Mis à jour à 14:05 », ou « Hors ligne · il y a 2 h » si tous les flux ont échoué au dernier essai. */
    fun footer(state: NewsState, now: Long, zone: ZoneId): String? {
        val latest = state.feeds.mapNotNull { it.fetchedAt }.maxOrNull() ?: return null
        return if (state.feeds.all { it.failed }) "Hors ligne · ${Freshness.ago(latest, now)}" else Freshness.label(latest, now, zone)
    }

    fun allFailedEmpty(state: NewsState): Boolean = state.feeds.isNotEmpty() && state.feeds.all { it.failed && it.articles.isEmpty() }

    private fun host(url: String) = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull().orEmpty()
}
```

`NewsSource.kt` :

```kotlin
package app.lanceur.news

import app.lanceur.builtin.Freshness
import app.lanceur.builtin.rss.Feed
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Actualise les flux en parallèle ; un flux en erreur garde ses articles et est marqué en échec. */
class NewsSource(private val network: Network) {
    suspend fun refresh(state: NewsState, now: Long, force: Boolean): NewsState = coroutineScope {
        NewsState(
            state.feeds.map { feed ->
                async { if (!force && !Freshness.isStale(feed.fetchedAt, now)) feed else fetch(feed, now) }
            }.awaitAll(),
        )
    }

    private suspend fun fetch(feed: FeedSnapshot, now: Long): FeedSnapshot {
        val parsed = (network.get(feed.url) as? NetResult.Ok)?.let { ok -> withContext(Dispatchers.Default) { Feed.parse(ok.bytes, feed.title) } }
            ?: return feed.copy(failed = true)
        return feed.copy(
            title = parsed.title.ifBlank { feed.title },
            articles = parsed.articles.take(NewsFeed.PER_FEED),
            fetchedAt = now,
            failed = false,
        )
    }
}
```

`NetworkGuardTest` : dans `network_is_used_only_by_weather_rss_and_the_container`, ajouter `"news/"` à `allowed`.

- [ ] **Step 4: Run it to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Actualités : état des flux, fil fusionné et filtré, bulles, source d'actualisation"
```

---

### Task 4: Chargement des images

**Files:**
- Create: `app/src/main/java/app/lanceur/news/ImageSizing.kt`, `ImageLoader.kt`
- Test: `app/src/test/java/app/lanceur/news/ImageSizingTest.kt`

**Interfaces:**
- Produces: `ImageSizing.sampleSize(width, height, targetWidth): Int`, `ImageSizing.CachedFile(name, size, lastUsed)`, `ImageSizing.toDelete(files, maxBytes): List<String>` ; `class ImageLoader(context, network) { suspend fun load(url, targetWidth): ImageBitmap? }` ; `@Composable NewsImage(url, loader, modifier)`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.lanceur.news

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {
    @Test
    fun sample_size_keeps_at_least_the_target_width() {
        assertEquals(2, ImageSizing.sampleSize(4000, 3000, 1080))
        assertEquals(1, ImageSizing.sampleSize(1200, 800, 1080))
        assertEquals(1, ImageSizing.sampleSize(800, 600, 1080))
        assertEquals(8, ImageSizing.sampleSize(10_000, 10_000, 1080))
        assertEquals(1, ImageSizing.sampleSize(0, 0, 1080))
    }

    @Test
    fun oldest_files_go_first_beyond_the_limit() {
        val files = listOf(
            ImageSizing.CachedFile("a", size = 30, lastUsed = 3),
            ImageSizing.CachedFile("b", size = 30, lastUsed = 1),
            ImageSizing.CachedFile("c", size = 30, lastUsed = 2),
        )
        assertEquals(listOf("b"), ImageSizing.toDelete(files, maxBytes = 70))
        assertEquals(emptyList<String>(), ImageSizing.toDelete(files, maxBytes = 90))
        assertEquals(listOf("c", "b"), ImageSizing.toDelete(files, maxBytes = 30))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest --tests '*ImageSizingTest*'`
Expected: FAIL at compilation, "Unresolved reference 'ImageSizing'".

- [ ] **Step 3: Write `ImageSizing.kt` and `ImageLoader.kt`**

`ImageSizing.kt` :

```kotlin
package app.lanceur.news

object ImageSizing {
    /** Plus grande puissance de 2 qui garde une largeur ≥ `targetWidth` : jamais d'image décodée en taille réelle inutilement. */
    fun sampleSize(width: Int, height: Int, targetWidth: Int): Int {
        if (width <= 0 || height <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    data class CachedFile(val name: String, val size: Long, val lastUsed: Long)

    /** Garde les plus récemment utilisés dans `maxBytes` ; renvoie les autres, du plus récent au plus ancien. */
    fun toDelete(files: List<CachedFile>, maxBytes: Long): List<String> {
        var total = 0L
        return files.sortedByDescending { it.lastUsed }.filter { file ->
            total += file.size
            total > maxBytes
        }.map { it.name }
    }
}
```

`ImageLoader.kt` :

```kotlin
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
    private val memory = LruCache<String, ImageBitmap>(MEMORY_IMAGES)
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
        const val MEMORY_IMAGES = 40
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
```

- [ ] **Step 4: Run it to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest`
Expected: PASS (dont `NetworkGuardTest` : `ImageLoader` est dans `news/` et n'ouvre aucune connexion lui-même).

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "Actualités : chargement des images réduit, caches mémoire et disque"
```

---

### Task 5: Page Actualités et feuille d'ajout

**Files:**
- Create: `app/src/main/java/app/lanceur/news/NewsPage.kt`, `NewsFeedSheet.kt`
- Test: `app/src/androidTest/java/app/lanceur/news/NewsPageTest.kt`

**Interfaces:**
- Consumes: `NewsChip`, `Article`, `Freshness`, `FeedCheck`, `RssSuggestions`, `RssConfig.validate`.
- Produces: `class NewsActions(filter: (String?) -> Unit, add, remove: (String) -> Unit, open: (String) -> Unit, refresh)` ; `@Composable NewsPage(chips, filter, articles, footer, unavailable, refreshing, now, actions, image, modifier)` ; `@Composable NewsFeedForm(existing, check, onAdd: (url, title) -> Unit)` ; `@Composable NewsFeedSheet(existing, check, onAdd, onDismiss)`.

- [ ] **Step 1: Write the failing UI test**

```kotlin
package app.lanceur.news

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.lanceur.builtin.rss.Article
import app.lanceur.builtin.rss.FeedCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewsPageTest {
    @get:Rule val rule = createComposeRule()
    private val now = 10_000_000L
    private val chips = listOf(NewsChip("https://lm/rss", "Le Monde", false), NewsChip("https://k/feed", "Korben", true))
    private val articles = listOf(
        Article("Élections : les résultats", "https://lm/a", "Le Monde", now - 25 * 60_000, image = "https://img/a.jpg"),
        Article("Astuce Linux", "https://k/x", "Korben", now - 3 * 3_600_000),
    )

    private fun show(actions: NewsActions, chips: List<NewsChip> = this.chips, articles: List<Article> = this.articles, unavailable: Boolean = false) =
        rule.setContent {
            MaterialTheme {
                NewsPage(chips, filter = null, articles = articles, footer = "Mis à jour à 14:05", unavailable = unavailable, refreshing = false, now = now, actions = actions, image = { url, m -> androidx.compose.foundation.layout.Box(m) { androidx.compose.material3.Text("IMAGE $url") } })
            }
        }

    @Test
    fun bubbles_filter_and_articles_open() {
        var filter: String? = "?"
        var opened: String? = null
        show(NewsActions(filter = { filter = it }, open = { opened = it }))
        rule.onNodeWithText("Korben").performClick()
        assertEquals("https://k/feed", filter)
        rule.onNodeWithText("Tout").performClick()
        assertEquals(null, filter)
        rule.onNodeWithText("IMAGE https://img/a.jpg").assertIsDisplayed()
        rule.onNodeWithText("LE MONDE").assertIsDisplayed()
        rule.onNodeWithText("Astuce Linux").performClick()
        assertEquals("https://k/x", opened)
    }

    @Test
    fun long_press_removes_and_plus_adds() {
        var removed: String? = null
        var added = false
        show(NewsActions(remove = { removed = it }, add = { added = true }))
        rule.onNodeWithText("Le Monde").performTouchInput { longClick() }
        rule.onNodeWithText("Retirer ce flux").performClick()
        assertEquals("https://lm/rss", removed)
        rule.onNodeWithTag("news-add").performClick()
        assertTrue(added)
    }

    @Test
    fun empty_and_unavailable_states() {
        var added = false
        show(NewsActions(add = { added = true }), chips = emptyList(), articles = emptyList())
        rule.onNodeWithText("Ajoute tes premiers flux").performClick()
        assertTrue(added)
    }

    @Test
    fun unavailable_offers_retry() {
        var refreshed = false
        show(NewsActions(refresh = { refreshed = true }), articles = emptyList(), unavailable = true)
        rule.onNodeWithText("Réessayer").performClick()
        assertTrue(refreshed)
    }

    @Test
    fun form_checks_then_adds_a_suggestion() {
        var added: Pair<String, String>? = null
        rule.setContent { MaterialTheme { NewsFeedForm(existing = setOf("https://korben.info/feed"), check = { FeedCheck.Ok("Le Monde", 20) }, onAdd = { u, t -> added = u to t }) } }
        rule.onNodeWithText("Le Monde").performClick()
        rule.waitUntil(3_000) { added != null }
        assertEquals("https://www.lemonde.fr/rss/une.xml" to "Le Monde", added)
    }

    @Test
    fun form_refuses_http() {
        rule.setContent { MaterialTheme { NewsFeedForm(existing = emptySet(), check = { FeedCheck.Ok("x", 1) }, onAdd = { _, _ -> }) } }
        rule.onNodeWithTag("news-url").performTextInput("http://x.fr/rss")
        rule.onNodeWithText("Vérifier et ajouter").performClick()
        rule.onNodeWithText("✗ Adresse HTTPS requise").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.news.NewsPageTest`
Expected: FAIL at compilation, "Unresolved reference 'NewsPage'".

- [ ] **Step 3: Write `NewsPage.kt`**

```kotlin
package app.lanceur.news

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.Freshness
import app.lanceur.builtin.rss.Article
import app.lanceur.ui.CardLabel
import app.lanceur.ui.CardShape
import app.lanceur.ui.cardBackground

class NewsActions(
    val filter: (String?) -> Unit = {},
    val add: () -> Unit = {},
    val remove: (String) -> Unit = {},
    val open: (String) -> Unit = {},
    val refresh: () -> Unit = {},
)

private val CARD_PADDING = 18.dp

/** Page Actualités : bulles de filtre, puis les articles en cartes (image du flux sous le titre). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPage(
    chips: List<NewsChip>,
    filter: String?,
    articles: List<Article>,
    footer: String?,
    unavailable: Boolean,
    refreshing: Boolean,
    now: Long,
    actions: NewsActions,
    image: @Composable (String, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.surface.copy(alpha = 0.15f), 1f to colors.surface.copy(alpha = 0.65f)))
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Actualités", Modifier.weight(1f))
            IconButton(onClick = actions.refresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualiser") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Bubble("Tout", selected = filter == null, failed = false, onClick = { actions.filter(null) }) }
            items(chips, key = { it.url }) { chip ->
                Bubble(chip.label, selected = filter == chip.url, failed = chip.failed, onClick = { actions.filter(chip.url) }, onRemove = { actions.remove(chip.url) })
            }
            item { Bubble("+", selected = false, failed = false, onClick = actions.add, modifier = Modifier.testTag("news-add")) }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = actions.refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                chips.isEmpty() -> Message("Ajoute tes premiers flux", "Le Monde, Numerama, Korben… ou l'adresse de ton choix", onClick = actions.add)
                unavailable && articles.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Flux indisponibles", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = actions.refresh) { Text("Réessayer") }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(articles, key = { it.link }) { article -> ArticleCard(article, now, actions.open, image) }
                    footer?.let { text ->
                        item { Text(text, Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(label: String, selected: Boolean, failed: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) colors.secondaryContainer else cardBackground())
                .border(1.dp, if (selected) colors.secondaryContainer else colors.outlineVariant, CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onRemove?.let { { menu = true } })
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                if (failed) "$label ⚠" else label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            )
        }
        if (onRemove != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Retirer ce flux") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article, now: Long, open: (String) -> Unit, image: @Composable (String, Modifier) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).clickable { open(article.link) }.padding(CARD_PADDING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(article.source, Modifier.weight(1f, fill = false))
            article.published?.let {
                Text("  ·  ${Freshness.ago(it, now)}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        article.image?.let { url ->
            Spacer(Modifier.height(12.dp))
            // Coins concentriques : 28 dp de la carte moins sa marge
            image(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(28.dp - CARD_PADDING)))
        }
    }
}

@Composable
private fun Message(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).clickable(onClick = onClick).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

- [ ] **Step 4: Write `NewsFeedSheet.kt`**

```kotlin
package app.lanceur.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.rss.FeedCheck
import app.lanceur.builtin.rss.RssConfig
import app.lanceur.builtin.rss.RssSuggestions
import kotlinx.coroutines.launch

/** Ajout d'un flux à la page : adresse collée ou suggestion, vérifiée avant d'être ajoutée. */
@Composable
fun NewsFeedForm(existing: Set<String>, check: suspend (String) -> FeedCheck, onAdd: (url: String, title: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun tryAdd(candidate: String) {
        val clean = candidate.trim()
        RssConfig.validate(clean)?.let { status = "✗ $it"; return }
        checking = true
        status = "Vérification…"
        scope.launch {
            when (val result = check(clean)) {
                is FeedCheck.Ok -> { status = "✓ ${result.title} — ${result.count} articles"; onAdd(clean, result.title) }
                is FeedCheck.Failed -> status = "✗ ${result.message}"
            }
            checking = false
        }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ajouter un flux", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; status = null },
            label = { Text("Adresse du flux") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("news-url"),
        )
        Button(onClick = { tryAdd(url) }, enabled = !checking && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Vérifier et ajouter") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        RssSuggestions.all.filterNot { it.url in existing }.forEach { suggestion ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !checking) { tryAdd(suggestion.url) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(suggestion.name, Modifier.weight(1f))
                Text("Ajouter", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.padding(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedSheet(existing: Set<String>, check: suspend (String) -> FeedCheck, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NewsFeedForm(existing, check, onAdd)
    }
}
```

- [ ] **Step 5: Run the UI test to verify it passes**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.news.NewsPageTest`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "Page Actualités : bulles de filtre, cartes avec image, tirer pour actualiser, ajout d'un flux"
```

---

### Task 6: Pager à N pages, réglages « Pages », branchement

**Files:**
- Modify: `app/src/main/java/app/lanceur/home/HomePager.kt`, `settings/SettingsScreen.kt`, `AppContainer.kt`, `AppRoot.kt`
- Test: `app/src/androidTest/java/app/lanceur/home/HomePagerTest.kt`, `app/src/androidTest/java/app/lanceur/settings/SettingsPagesTest.kt`

**Interfaces:**
- Consumes: tout ce qui précède.
- Produces: `HomePager(pages: List<PageKind>, homePageRequests, editMode, onExitEdit, onPageShown: (PageKind) -> Unit, widgetPage, home, newsPage = {}, backEnabled = true, modifier)` ; l'ancienne `HomePager(widgetsEnabled: Boolean, …)` délègue ; `SettingsScreen(…, pageOrder, widgetPageEnabled, newsEnabled, …)` ; `SettingsActions.setNewsEnabled`, `movePage`.

- [ ] **Step 1: Write the failing UI tests**

Ajouter à `HomePagerTest` :

```kotlin
    @Test
    fun three_pages_reach_news_and_home_returns() {
        var requests by mutableIntStateOf(0)
        rule.setContent {
            MaterialTheme {
                HomePager(
                    pages = listOf(PageKind.NEWS, PageKind.WIDGETS, PageKind.HOME),
                    homePageRequests = requests,
                    editMode = false,
                    onExitEdit = {},
                    onPageShown = {},
                    widgetPage = { Text("PAGE WIDGETS") },
                    newsPage = { Text("PAGE ACTUS") },
                    home = simpleHome(),
                )
            }
        }
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE ACTUS").assertIsDisplayed()
        requests++
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
    }

    @Test
    fun news_on_the_right_is_reached_by_swiping_left() {
        rule.setContent {
            MaterialTheme {
                HomePager(
                    pages = listOf(PageKind.WIDGETS, PageKind.HOME, PageKind.NEWS),
                    homePageRequests = 0, editMode = false, onExitEdit = {}, onPageShown = {},
                    widgetPage = { Text("PAGE WIDGETS") }, newsPage = { Text("PAGE ACTUS") }, home = simpleHome(),
                )
            }
        }
        rule.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        rule.onNodeWithText("PAGE ACTUS").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
    }
```

(import `androidx.compose.ui.test.swipeLeft`).

`SettingsPagesTest.kt` :

```kotlin
package app.lanceur.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
import app.lanceur.prefs.AlphabetSide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPagesTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pages_can_be_moved_and_switched_but_home_stays() {
        var moved: Pair<PageKind, Int>? = null
        var news: Boolean? = null
        rule.setContent {
            MaterialTheme {
                SettingsScreen(
                    favorites = emptyList(), side = AlphabetSide.RIGHT, isDefaultLauncher = true, permissionsGranted = true,
                    lockServiceEnabled = true, pageOrder = PageLayout.DEFAULT_ORDER, widgetPageEnabled = true, newsEnabled = false,
                    icon = {}, actions = SettingsActions(movePage = { k, d -> moved = k to d }, setNewsEnabled = { news = it }),
                )
            }
        }
        rule.onNodeWithContentDescription("Monter Accueil").performScrollTo().performClick()
        assertEquals(PageKind.HOME to -1, moved)
        rule.onNodeWithTag("page-switch-NEWS").performScrollTo().performClick()
        assertEquals(true, news)
        rule.onNodeWithTag("page-switch-HOME").performScrollTo().assertIsNotEnabled()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.lanceur.home.HomePagerTest`
Expected: FAIL at compilation, "No parameter with name 'pages'" / "Unresolved reference 'pageOrder'".

- [ ] **Step 3: Pager**

Dans `HomePager.kt`, remplacer la fonction existante par ces deux fonctions (même fichier, mêmes imports, plus `androidx.compose.runtime.key` déjà présent) :

```kotlin
/**
 * Pages dans l'ordre choisi (1 à 3), démarrage sur l'accueil. Un geste qui commence sur l'alphabet ne fait jamais
 * tourner la page : la barre consomme le toucher dès qu'on la pose.
 */
@Composable
fun HomePager(
    pages: List<PageKind>,
    homePageRequests: Int,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    onPageShown: (PageKind) -> Unit,
    widgetPage: @Composable () -> Unit,
    home: @Composable () -> Unit,
    newsPage: @Composable () -> Unit = {},
    /** `false` quand un écran est superposé (sélecteur…) : le geste retour lui appartient. */
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Changer les pages recrée l'état : on repart toujours de l'accueil
    key(pages) {
        val homeIndex = pages.indexOf(PageKind.HOME).coerceAtLeast(0)
        val state = rememberPagerState(initialPage = homeIndex) { pages.size }
        val scope = rememberCoroutineScope()
        val latestShown by rememberUpdatedState(onPageShown)

        // Seules les nouvelles demandes comptent (pas la valeur présente à la création), et toujours exécutées :
        // `currentPage` peut encore valoir l'accueil pendant un élan qui part vers une autre page
        var handledRequest by remember { mutableIntStateOf(homePageRequests) }
        var returningHome by remember { mutableStateOf(false) }
        LaunchedEffect(homePageRequests) {
            if (homePageRequests == handledRequest) return@LaunchedEffect
            handledRequest = homePageRequests
            returningHome = true
            try {
                state.animateScrollToPage(homeIndex)
            } finally {
                returningHome = false
            }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.settledPage }.collect { index -> pages.getOrNull(index)?.takeIf { it != PageKind.HOME }?.let(latestShown) }
        }
        BackHandler(enabled = backEnabled && state.currentPage != homeIndex) {
            if (editMode && pages.getOrNull(state.currentPage) == PageKind.WIDGETS) onExitEdit()
            else scope.launch { state.animateScrollToPage(homeIndex) }
        }

        HorizontalPager(
            state = state,
            modifier = modifier.fillMaxSize().testTag("pager"),
            beyondViewportPageCount = 1,
            // Pas de doigt pour interrompre le retour à l'accueil
            userScrollEnabled = !editMode && !returningHome,
            key = { index -> pages[index].name },
        ) { index ->
            when (pages[index]) {
                PageKind.WIDGETS -> widgetPage()
                PageKind.NEWS -> newsPage()
                PageKind.HOME -> home()
            }
        }
    }
}

/** Ancienne forme : page de widgets à gauche de l'accueil, ou accueil seul. */
@Composable
fun HomePager(
    widgetsEnabled: Boolean,
    homePageRequests: Int,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    onWidgetsShown: () -> Unit,
    widgetPage: @Composable () -> Unit,
    home: @Composable () -> Unit,
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) = HomePager(
    pages = if (widgetsEnabled) listOf(PageKind.WIDGETS, PageKind.HOME) else listOf(PageKind.HOME),
    homePageRequests = homePageRequests,
    editMode = editMode,
    onExitEdit = onExitEdit,
    onPageShown = { if (it == PageKind.WIDGETS) onWidgetsShown() },
    widgetPage = widgetPage,
    home = home,
    backEnabled = backEnabled,
    modifier = modifier,
)
```

- [ ] **Step 4: Réglages « Pages »**

`SettingsScreen.kt` :
- `SettingsActions` : ajouter `val setNewsEnabled: (Boolean) -> Unit = {},` et `val movePage: (PageKind, Int) -> Unit = { _, _ -> },` ;
- `SettingsScreen` : ajouter les paramètres `pageOrder: List<PageKind>`, `newsEnabled: Boolean` (après `widgetPageEnabled`) ;
- remplacer le `SwitchRow("Page de widgets à gauche", …)` par `PagesSection(pageOrder, widgetPageEnabled, newsEnabled, actions)` placé juste avant `SectionTitle("Côté de l'alphabet")`, et ajouter :

```kotlin
/** Pages de gauche à droite, comme quand on fait défiler ; l'Accueil ne se désactive pas. */
@Composable
private fun PagesSection(order: List<PageKind>, widgetsEnabled: Boolean, newsEnabled: Boolean, actions: SettingsActions) {
    SectionTitle("Pages")
    HintText("De gauche à droite, comme quand tu fais défiler")
    val pages = PageLayout.normalize(order)
    pages.forEachIndexed { index, kind ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.titleMedium)
                Text(kind.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { actions.movePage(kind, -1) }, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Monter ${kind.label}")
            }
            IconButton(onClick = { actions.movePage(kind, +1) }, enabled = index < pages.lastIndex) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Descendre ${kind.label}")
            }
            Switch(
                checked = when (kind) {
                    PageKind.HOME -> true
                    PageKind.WIDGETS -> widgetsEnabled
                    PageKind.NEWS -> newsEnabled
                },
                onCheckedChange = { on ->
                    when (kind) {
                        PageKind.WIDGETS -> actions.setWidgetPageEnabled(on)
                        PageKind.NEWS -> actions.setNewsEnabled(on)
                        PageKind.HOME -> Unit
                    }
                },
                enabled = kind != PageKind.HOME,
                modifier = Modifier.testTag("page-switch-${kind.name}"),
            )
        }
    }
}
```

(imports `app.lanceur.home.PageKind`, `app.lanceur.home.PageLayout`, `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.KeyboardArrowUp`, `androidx.compose.material.icons.filled.KeyboardArrowDown`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, `androidx.compose.ui.platform.testTag`). Supprimer `SwitchRow` s'il n'est plus utilisé.

- [ ] **Step 5: Branchement**

`AppContainer` : `val news = NewsSource(network)` et `val images = ImageLoader(appContext, network)` (imports `app.lanceur.news.NewsSource`, `app.lanceur.news.ImageLoader`).

`AppRoot.kt` (imports `app.lanceur.home.PageKind`, `app.lanceur.home.PageLayout`, `app.lanceur.news.*` utilisés) :
- avant `Box(Modifier.fillMaxSize())` :

```kotlin
    val pages = remember(prefs.pageOrder, prefs.widgetPageEnabled, prefs.newsEnabled) {
        PageLayout.active(prefs.pageOrder, prefs.widgetPageEnabled, prefs.newsEnabled)
    }
    val newsState = remember(prefs.news) { NewsState.decode(prefs.news) }
    var newsFilter by remember { mutableStateOf<String?>(null) }
    var newsRefreshing by remember { mutableStateOf(false) }
    var addingFeed by remember { mutableStateOf(false) }
    val newsNow = rememberMinuteClock()
    fun refreshNews(force: Boolean) {
        scope.launch {
            newsRefreshing = force
            val refreshed = container.news.refresh(newsState, System.currentTimeMillis(), force)
            vm.updateNews { current -> NewsState.merge(NewsState.decode(current), refreshed).encode() }
            newsRefreshing = false
        }
    }
```

(import `app.lanceur.builtin.rememberMinuteClock`).
- dans l'appel `HomePager(...)` : remplacer `widgetsEnabled = prefs.widgetPageEnabled,` par `pages = pages,` ; remplacer le paramètre `onWidgetsShown = { widgetRefresh++; reloadSummary() }` par

```kotlin
            onPageShown = { kind ->
                when (kind) {
                    PageKind.WIDGETS -> {
                        widgetRefresh++
                        reloadSummary()
                    }
                    PageKind.NEWS -> refreshNews(force = false)
                    PageKind.HOME -> Unit
                }
            },
```

  et ajouter, après `widgetPage = { … },` :

```kotlin
            newsPage = {
                val filter = NewsFeed.validFilter(newsState, newsFilter)
                NewsPage(
                    chips = NewsFeed.chips(newsState),
                    filter = filter,
                    articles = NewsFeed.articles(newsState, filter),
                    footer = NewsFeed.footer(newsState, newsNow, java.time.ZoneId.systemDefault()),
                    unavailable = NewsFeed.allFailedEmpty(newsState),
                    refreshing = newsRefreshing,
                    now = newsNow,
                    actions = NewsActions(
                        filter = { newsFilter = it },
                        add = { addingFeed = true },
                        remove = { url -> vm.updateNews { NewsState.decode(it).remove(url).encode() } },
                        open = builtinServices.openUrl,
                        refresh = { refreshNews(force = true) },
                    ),
                    image = { url, m -> NewsImage(url, container.images, m) },
                )
            },
```

- à la fin du `Box(Modifier.fillMaxSize())` (à côté de la feuille des widgets) :

```kotlin
        if (addingFeed) {
            NewsFeedSheet(
                existing = newsState.feeds.mapTo(HashSet()) { it.url },
                check = { container.rss.check(it) },
                onAdd = { url, title ->
                    vm.updateNews { NewsState.decode(it).add(url, title).encode() }
                    addingFeed = false
                },
                onDismiss = { addingFeed = false },
            )
        }
```

  Un flux tout juste ajouté n'a pas encore de `fetchedAt` : cet effet, placé près de `refreshNews`, le charge aussitôt
  (et ne relance rien quand seul le cache change, car la clé est la liste des adresses) :

```kotlin
    LaunchedEffect(newsState.feeds.map { it.url }) { if (newsState.feeds.any { it.fetchedAt == null }) refreshNews(force = false) }
```
- dans `SettingsScreen(...)` : ajouter `pageOrder = prefs.pageOrder,` et `newsEnabled = prefs.newsEnabled,` ; dans `SettingsActions(...)` : `setNewsEnabled = vm::setNewsEnabled,` et `movePage = vm::movePage,`.

- [ ] **Step 6: Run the tests**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`
Expected: PASS, toutes les suites (les anciens `HomePagerTest` passent par l'ancienne forme).

- [ ] **Step 7: Commit**

```bash
git add app/src
git commit -m "Pages réordonnables dans les réglages et page Actualités branchée dans le défilement"
```

---

### Task 7: README, installation et vérification

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README**

Ajouter une section après « Page de widgets » :

```markdown
## Pages et Actualités

Réglages › Pages : active ou désactive Actualités et Widgets, et choisis leur ordre de gauche à droite avec ↑ / ↓
(l'Accueil reste toujours là). Lanceur démarre sur l'Accueil ; le bouton Accueil et le geste Retour y ramènent.

La page **Actualités** rassemble tes flux RSS/Atom : bulles en haut pour filtrer (« Tout » ou un flux), « + » pour en
ajouter, appui long pour en retirer, puis les articles du plus récent au plus ancien, avec l'image fournie par le flux.
Tirer vers le bas ou ⟳ actualise ; sinon la page se met à jour à l'affichage, au plus toutes les 30 minutes, jamais
en arrière-plan. Tout passe par le même point de sortie réseau que la Météo et le RSS (HTTPS seulement) ; les images
sont gardées en cache (50 Mo au plus).
```

- [ ] **Step 2: Release**

Run: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease && ~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: BUILD SUCCESSFUL puis `Success`.

- [ ] **Step 3: Vérification à la main (avec l'utilisateur)**

1. Réglages › Pages : activer Actualités, la placer à droite de l'Accueil ; glisser vers la gauche depuis l'accueil.
2. « + » : ajouter Le Monde et Numerama ; les images apparaissent ; filtrer par bulle ; appui long → retirer.
3. Tirer pour actualiser ; mode avion : articles gardés et « Hors ligne ».
4. Remettre Actualités à gauche des Widgets ; Accueil et Retour ramènent à l'accueil.
5. `adb logcat -d -b crash | grep lanceur` : aucun plantage.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "README : pages réordonnables et page Actualités"
```
