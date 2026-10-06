package app.lanceur.news

import app.lanceur.i18n.L10n
import app.lanceur.i18n.tr
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
        return if (state.feeds.all { it.failed }) tr("Hors ligne · ", "Offline · ") + Freshness.ago(latest, now) else Freshness.label(latest, now, zone)
    }

    fun allFailedEmpty(state: NewsState): Boolean = state.feeds.isNotEmpty() && state.feeds.all { it.failed && it.articles.isEmpty() }

    private val DATE get() = java.time.format.DateTimeFormatter.ofPattern(tr("EEEE d MMMM", "EEEE, MMMM d"), L10n.locale)

    /** « Mardi 6 octobre », comme l'en-tête du résumé du jour. */
    fun dateLabel(now: Long, zone: ZoneId): String =
        DATE.format(java.time.Instant.ofEpochMilli(now).atZone(zone)).replaceFirstChar { it.titlecase(java.util.Locale.FRENCH) }

    private fun host(url: String) = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull().orEmpty()
}
