package app.lanceur.news

import app.lanceur.i18n.L10n
import app.lanceur.i18n.Lang
import app.lanceur.i18n.tr
import app.lanceur.news.FeedRegion.FR
import app.lanceur.news.FeedRegion.UK
import app.lanceur.news.FeedRegion.US
import app.lanceur.news.FeedTheme.CULTURE
import app.lanceur.news.FeedTheme.ECONOMY
import app.lanceur.news.FeedTheme.ENVIRONMENT
import app.lanceur.news.FeedTheme.HEALTH
import app.lanceur.news.FeedTheme.POLITICS
import app.lanceur.news.FeedTheme.SCIENCE
import app.lanceur.news.FeedTheme.SPORT
import app.lanceur.news.FeedTheme.TECH
import app.lanceur.news.FeedTheme.TOP
import app.lanceur.news.FeedTheme.WORLD
import app.lanceur.text.TextNormalizer

enum class FeedRegion(val flag: String, private val fr: String, private val en: String) {
    FR("🇫🇷", "Français", "French"),
    UK("🇬🇧", "Royaume-Uni", "United Kingdom"),
    US("🇺🇸", "États-Unis", "United States"),
    ;

    val label: String get() = "$flag " + tr(fr, en)
}

enum class FeedTheme(private val fr: String, private val en: String) {
    TOP("À la une", "Top stories"),
    POLITICS("Politique", "Politics"),
    ECONOMY("Économie", "Business"),
    WORLD("International", "World"),
    TECH("Tech", "Tech"),
    SCIENCE("Sciences", "Science"),
    HEALTH("Santé", "Health"),
    CULTURE("Culture", "Culture"),
    SPORT("Sport", "Sport"),
    ENVIRONMENT("Environnement", "Environment"),
    ;

    val label: String get() = tr(fr, en)
}

/** Un flux du catalogue ; [name] vide quand la source n'a qu'un flux général. */
data class CatalogFeed(val region: FeedRegion, val theme: FeedTheme, val source: String, val name: String, val url: String) {
    /** Nom de la bulle sur la page : « franceinfo · Économie », ou la source seule. */
    val label: String get() = if (name.isEmpty()) source else "$source · $name"
}

/**
 * Flux français, britanniques et américains, rangés par langue et par thème.
 * Chaque adresse a été vérifiée (HTTPS, vrai RSS/Atom, moins d'1 Mo) le 2026-10-06.
 */
object FeedCatalog {
    private fun e(region: FeedRegion, theme: FeedTheme, source: String, name: String, url: String) = CatalogFeed(region, theme, source, name, url)

    val all: List<CatalogFeed> = listOf(
        e(FR, TOP, "20 Minutes", "À la une", "https://www.20minutes.fr/feeds/rss-une.xml"),
        e(FR, TOP, "franceinfo", "Les titres", "https://www.francetvinfo.fr/titres.rss"),
        e(FR, TOP, "franceinfo", "France", "https://www.francetvinfo.fr/france.rss"),
        e(FR, TOP, "Le Figaro", "À la une", "https://www.lefigaro.fr/rss/figaro_actualites.xml"),
        e(FR, TOP, "Le Monde", "À la une", "https://www.lemonde.fr/rss/une.xml"),
        e(FR, TOP, "Libération", "Toute l'actu", "https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml"),
        e(FR, TOP, "Ouest-France", "À la une", "https://www.ouest-france.fr/rss/une"),
        e(FR, POLITICS, "20 Minutes", "Politique", "https://www.20minutes.fr/feeds/rss-politique.xml"),
        e(FR, POLITICS, "franceinfo", "Politique", "https://www.francetvinfo.fr/politique.rss"),
        e(FR, POLITICS, "Le Figaro", "Politique", "https://www.lefigaro.fr/rss/figaro_politique.xml"),
        e(FR, POLITICS, "Le Figaro", "Élections", "https://www.lefigaro.fr/rss/figaro_elections.xml"),
        e(FR, POLITICS, "Le Monde", "Politique", "https://www.lemonde.fr/politique/rss_full.xml"),
        e(FR, POLITICS, "Libération", "Politique", "https://www.liberation.fr/arc/outboundfeeds/rss/category/politique/?outputType=xml"),
        e(FR, ECONOMY, "20 Minutes", "Économie", "https://www.20minutes.fr/feeds/rss-economie.xml"),
        e(FR, ECONOMY, "franceinfo", "Économie", "https://www.francetvinfo.fr/economie.rss"),
        e(FR, ECONOMY, "franceinfo", "Entreprises", "https://www.francetvinfo.fr/economie/entreprises.rss"),
        e(FR, ECONOMY, "La Tribune", "Économie", "https://www.latribune.fr/feed.xml"),
        e(FR, ECONOMY, "Le Figaro", "Économie", "https://www.lefigaro.fr/rss/figaro_economie.xml"),
        e(FR, ECONOMY, "Le Monde", "Économie", "https://www.lemonde.fr/economie/rss_full.xml"),
        e(FR, ECONOMY, "Libération", "Économie", "https://www.liberation.fr/arc/outboundfeeds/rss/category/economie/?outputType=xml"),
        e(FR, WORLD, "France 24", "Monde", "https://www.france24.com/fr/rss"),
        e(FR, WORLD, "franceinfo", "Monde", "https://www.francetvinfo.fr/monde.rss"),
        e(FR, WORLD, "Le Figaro", "International", "https://www.lefigaro.fr/rss/figaro_international.xml"),
        e(FR, WORLD, "Le Monde", "International", "https://www.lemonde.fr/international/rss_full.xml"),
        e(FR, WORLD, "Libération", "International", "https://www.liberation.fr/arc/outboundfeeds/rss/category/international/?outputType=xml"),
        e(FR, WORLD, "RFI", "Monde", "https://www.rfi.fr/fr/monde/rss"),
        e(FR, TECH, "01net", "Actualités", "https://www.01net.com/actualites/feed/"),
        e(FR, TECH, "franceinfo", "Internet", "https://www.francetvinfo.fr/internet.rss"),
        e(FR, TECH, "Frandroid", "", "https://www.frandroid.com/feed"),
        e(FR, TECH, "Korben", "", "https://korben.info/feed"),
        e(FR, TECH, "Le Figaro", "Tech", "https://www.lefigaro.fr/rss/figaro_secteur_high-tech.xml"),
        e(FR, TECH, "Le Monde", "Pixels", "https://www.lemonde.fr/pixels/rss_full.xml"),
        e(FR, TECH, "Les Numériques", "", "https://www.lesnumeriques.com/rss.xml"),
        e(FR, TECH, "Next", "", "https://next.ink/feed/"),
        e(FR, TECH, "Numerama", "", "https://www.numerama.com/feed/"),
        e(FR, SCIENCE, "CNRS Le journal", "", "https://lejournal.cnrs.fr/rss"),
        e(FR, SCIENCE, "franceinfo", "Sciences", "https://www.francetvinfo.fr/sciences.rss"),
        e(FR, SCIENCE, "Futura", "Sciences", "https://www.futura-sciences.com/rss/actualites.xml"),
        e(FR, SCIENCE, "Le Figaro", "Sciences", "https://www.lefigaro.fr/rss/figaro_sciences.xml"),
        e(FR, SCIENCE, "Le Monde", "Sciences", "https://www.lemonde.fr/sciences/rss_full.xml"),
        e(FR, SCIENCE, "Sciences et Avenir", "", "https://www.sciencesetavenir.fr/rss.xml"),
        e(FR, HEALTH, "20 Minutes", "Santé", "https://www.20minutes.fr/feeds/rss-sante.xml"),
        e(FR, HEALTH, "franceinfo", "Santé", "https://www.francetvinfo.fr/sante.rss"),
        e(FR, HEALTH, "Le Figaro", "Santé", "https://www.lefigaro.fr/rss/figaro_sante.xml"),
        e(FR, HEALTH, "Le Monde", "Santé", "https://www.lemonde.fr/sante/rss_full.xml"),
        e(FR, CULTURE, "Allociné", "Cinéma", "https://www.allocine.fr/rss/news.xml"),
        e(FR, CULTURE, "franceinfo", "Culture", "https://www.francetvinfo.fr/culture.rss"),
        e(FR, CULTURE, "Le Figaro", "Culture", "https://www.lefigaro.fr/rss/figaro_culture.xml"),
        e(FR, CULTURE, "Le Monde", "Culture", "https://www.lemonde.fr/culture/rss_full.xml"),
        e(FR, CULTURE, "Libération", "Culture", "https://www.liberation.fr/arc/outboundfeeds/rss/category/culture/?outputType=xml"),
        e(FR, CULTURE, "Télérama", "", "https://www.telerama.fr/rss/une.xml"),
        e(FR, SPORT, "20 Minutes", "Sport", "https://www.20minutes.fr/feeds/rss-sport.xml"),
        e(FR, SPORT, "franceinfo", "Sports", "https://www.francetvinfo.fr/sports.rss"),
        e(FR, SPORT, "L'Équipe", "", "https://dwh.lequipe.fr/api/edito/rss?path=/"),
        e(FR, SPORT, "L'Équipe", "Football", "https://dwh.lequipe.fr/api/edito/rss?path=/Football/"),
        e(FR, SPORT, "Le Figaro", "Sport", "https://www.lefigaro.fr/rss/figaro_sport.xml"),
        e(FR, SPORT, "Le Monde", "Sport", "https://www.lemonde.fr/sport/rss_full.xml"),
        e(FR, ENVIRONMENT, "franceinfo", "Environnement", "https://www.francetvinfo.fr/environnement.rss"),
        e(FR, ENVIRONMENT, "Le Monde", "Planète", "https://www.lemonde.fr/planete/rss_full.xml"),
        e(FR, ENVIRONMENT, "Libération", "Environnement", "https://www.liberation.fr/arc/outboundfeeds/rss/category/environnement/?outputType=xml"),
        e(FR, ENVIRONMENT, "Reporterre", "", "https://reporterre.net/spip.php?page=backend"),
        e(UK, TOP, "BBC News", "Top stories", "https://feeds.bbci.co.uk/news/rss.xml"),
        e(UK, TOP, "BBC News", "UK", "https://feeds.bbci.co.uk/news/uk/rss.xml"),
        e(UK, TOP, "Sky News", "Home", "https://feeds.skynews.com/feeds/rss/home.xml"),
        e(UK, TOP, "The Guardian", "UK", "https://www.theguardian.com/uk/rss"),
        e(UK, POLITICS, "BBC News", "Politics", "https://feeds.bbci.co.uk/news/politics/rss.xml"),
        e(UK, POLITICS, "Sky News", "Politics", "https://feeds.skynews.com/feeds/rss/politics.xml"),
        e(UK, POLITICS, "The Guardian", "Politics", "https://www.theguardian.com/politics/rss"),
        e(UK, ECONOMY, "BBC News", "Business", "https://feeds.bbci.co.uk/news/business/rss.xml"),
        e(UK, ECONOMY, "Financial Times", "Home", "https://www.ft.com/rss/home"),
        e(UK, ECONOMY, "Sky News", "Business", "https://feeds.skynews.com/feeds/rss/business.xml"),
        e(UK, ECONOMY, "The Economist", "Finance & economics", "https://www.economist.com/finance-and-economics/rss.xml"),
        e(UK, ECONOMY, "The Guardian", "Business", "https://www.theguardian.com/uk/business/rss"),
        e(UK, WORLD, "BBC News", "World", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        e(UK, WORLD, "Sky News", "World", "https://feeds.skynews.com/feeds/rss/world.xml"),
        e(UK, WORLD, "The Economist", "The world this week", "https://www.economist.com/the-world-this-week/rss.xml"),
        e(UK, WORLD, "The Guardian", "World", "https://www.theguardian.com/world/rss"),
        e(UK, TECH, "BBC News", "Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
        e(UK, TECH, "Sky News", "Technology", "https://feeds.skynews.com/feeds/rss/technology.xml"),
        e(UK, TECH, "The Guardian", "Technology", "https://www.theguardian.com/uk/technology/rss"),
        e(UK, TECH, "The Register", "Headlines", "https://www.theregister.com/headlines.atom"),
        e(UK, SCIENCE, "BBC News", "Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"),
        e(UK, SCIENCE, "New Scientist", "Home", "https://www.newscientist.com/feed/home/"),
        e(UK, SCIENCE, "The Guardian", "Science", "https://www.theguardian.com/science/rss"),
        e(UK, HEALTH, "BBC News", "Health", "https://feeds.bbci.co.uk/news/health/rss.xml"),
        e(UK, HEALTH, "NHS", "News", "https://www.england.nhs.uk/feed/"),
        e(UK, HEALTH, "The Guardian", "Society", "https://www.theguardian.com/society/rss"),
        e(UK, CULTURE, "BBC News", "Entertainment & arts", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml"),
        e(UK, CULTURE, "NME", "", "https://www.nme.com/feed"),
        e(UK, CULTURE, "The Guardian", "Culture", "https://www.theguardian.com/uk/culture/rss"),
        e(UK, SPORT, "BBC Sport", "All sport", "https://feeds.bbci.co.uk/sport/rss.xml"),
        e(UK, SPORT, "BBC Sport", "Football", "https://feeds.bbci.co.uk/sport/football/rss.xml"),
        e(UK, SPORT, "Sky Sports", "News", "https://www.skysports.com/rss/12040"),
        e(UK, SPORT, "The Guardian", "Sport", "https://www.theguardian.com/uk/sport/rss"),
        e(UK, ENVIRONMENT, "Carbon Brief", "", "https://www.carbonbrief.org/feed/"),
        e(UK, ENVIRONMENT, "The Guardian", "Environment", "https://www.theguardian.com/uk/environment/rss"),
        e(US, TOP, "ABC News", "Top stories", "https://abcnews.go.com/abcnews/topstories"),
        e(US, TOP, "CBS News", "Latest", "https://www.cbsnews.com/latest/rss/main"),
        e(US, TOP, "NPR", "News", "https://feeds.npr.org/1001/rss.xml"),
        e(US, TOP, "The New York Times", "Home page", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        e(US, TOP, "The Washington Post", "National", "https://feeds.washingtonpost.com/rss/national"),
        e(US, POLITICS, "CBS News", "Politics", "https://www.cbsnews.com/latest/rss/politics"),
        e(US, POLITICS, "NPR", "Politics", "https://feeds.npr.org/1014/rss.xml"),
        e(US, POLITICS, "Politico", "Politics", "https://rss.politico.com/politics-news.xml"),
        e(US, POLITICS, "The Hill", "News", "https://thehill.com/news/feed/"),
        e(US, POLITICS, "The New York Times", "Politics", "https://rss.nytimes.com/services/xml/rss/nyt/Politics.xml"),
        e(US, POLITICS, "The Washington Post", "Politics", "https://feeds.washingtonpost.com/rss/politics"),
        e(US, ECONOMY, "CNBC", "Top news", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114"),
        e(US, ECONOMY, "CNBC", "Economy", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=20910258"),
        e(US, ECONOMY, "MarketWatch", "Top stories", "https://feeds.content.dowjones.io/public/rss/mw_topstories"),
        e(US, ECONOMY, "NPR", "Business", "https://feeds.npr.org/1006/rss.xml"),
        e(US, ECONOMY, "The New York Times", "Business", "https://rss.nytimes.com/services/xml/rss/nyt/Business.xml"),
        e(US, ECONOMY, "The Wall Street Journal", "Markets", "https://feeds.content.dowjones.io/public/rss/RSSMarketsMain"),
        e(US, ECONOMY, "The Washington Post", "Business", "https://feeds.washingtonpost.com/rss/business"),
        e(US, WORLD, "NPR", "World", "https://feeds.npr.org/1004/rss.xml"),
        e(US, WORLD, "The New York Times", "World", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml"),
        e(US, WORLD, "The Wall Street Journal", "World", "https://feeds.content.dowjones.io/public/rss/RSSWorldNews"),
        e(US, WORLD, "The Washington Post", "World", "https://feeds.washingtonpost.com/rss/world"),
        e(US, TECH, "Ars Technica", "", "https://feeds.arstechnica.com/arstechnica/index"),
        e(US, TECH, "Engadget", "", "https://www.engadget.com/rss.xml"),
        e(US, TECH, "NPR", "Technology", "https://feeds.npr.org/1019/rss.xml"),
        e(US, TECH, "TechCrunch", "", "https://techcrunch.com/feed/"),
        e(US, TECH, "The New York Times", "Technology", "https://rss.nytimes.com/services/xml/rss/nyt/Technology.xml"),
        e(US, TECH, "The Verge", "", "https://www.theverge.com/rss/index.xml"),
        e(US, TECH, "Wired", "", "https://www.wired.com/feed/rss"),
        e(US, SCIENCE, "NASA", "News", "https://www.nasa.gov/news-release/feed/"),
        e(US, SCIENCE, "NPR", "Science", "https://feeds.npr.org/1007/rss.xml"),
        e(US, SCIENCE, "Science Daily", "Top news", "https://www.sciencedaily.com/rss/top.xml"),
        e(US, SCIENCE, "Scientific American", "", "https://www.scientificamerican.com/platform/syndication/rss/"),
        e(US, SCIENCE, "The New York Times", "Science", "https://rss.nytimes.com/services/xml/rss/nyt/Science.xml"),
        e(US, HEALTH, "NPR", "Health", "https://feeds.npr.org/1128/rss.xml"),
        e(US, HEALTH, "STAT", "", "https://www.statnews.com/feed/"),
        e(US, HEALTH, "The New York Times", "Health", "https://rss.nytimes.com/services/xml/rss/nyt/Health.xml"),
        e(US, CULTURE, "NPR", "Arts & life", "https://feeds.npr.org/1008/rss.xml"),
        e(US, CULTURE, "Rolling Stone", "", "https://www.rollingstone.com/feed/"),
        e(US, CULTURE, "The New York Times", "Arts", "https://rss.nytimes.com/services/xml/rss/nyt/Arts.xml"),
        e(US, CULTURE, "Variety", "", "https://variety.com/feed/"),
        e(US, SPORT, "ESPN", "Top headlines", "https://www.espn.com/espn/rss/news"),
        e(US, SPORT, "ESPN", "NBA", "https://www.espn.com/espn/rss/nba/news"),
        e(US, SPORT, "ESPN", "NFL", "https://www.espn.com/espn/rss/nfl/news"),
        e(US, ENVIRONMENT, "Grist", "", "https://grist.org/feed/"),
        e(US, ENVIRONMENT, "Inside Climate News", "", "https://insideclimatenews.org/feed/"),
        e(US, ENVIRONMENT, "NPR", "Climate", "https://feeds.npr.org/1167/rss.xml"),
        e(US, ENVIRONMENT, "The New York Times", "Climate", "https://rss.nytimes.com/services/xml/rss/nyt/Climate.xml"),
    )

    /** Langue proposée d'abord : celle de l'appli. */
    fun defaultRegion(): FeedRegion = if (L10n.lang == Lang.EN) UK else FR

    fun themes(region: FeedRegion): List<FeedTheme> = FeedTheme.entries.filter { t -> all.any { it.region == region && it.theme == t } }

    fun feeds(region: FeedRegion, theme: FeedTheme?): List<CatalogFeed> =
        all.filter { it.region == region && (theme == null || it.theme == theme) }

    /** Recherche dans toutes les langues, sur la source, le nom et le thème (sans accents ni casse). */
    fun search(query: String): List<CatalogFeed> {
        val words = TextNormalizer.fold(query.trim()).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return all.filter { feed ->
            val text = TextNormalizer.fold(listOf(feed.source, feed.name, feed.theme.label, feed.region.label).joinToString(" "))
            words.all { it in text }
        }
    }
}
