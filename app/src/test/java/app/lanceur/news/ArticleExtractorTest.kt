package app.lanceur.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {
    private val lorem = "Le gouvernement a présenté mardi un plan détaillé pour relancer l'investissement dans les régions, " +
        "avec des mesures ciblées sur l'industrie, la formation et les transports publics du quotidien."

    private val page = """
        <html><head>
          <meta property="og:title" content="Un plan pour les régions">
          <meta property="og:site_name" content="Le Journal">
          <meta property="og:image" content="/img/hero.jpg">
          <meta name="author" content="Léa Martin">
          <meta property="article:published_time" content="2026-10-06T08:30:00+02:00">
          <title>Un plan pour les régions | Le Journal</title>
          <script>var tracking = "Ne pas lire ce script";</script>
        </head><body>
          <header><nav><a href="/">Accueil</a> <a href="/eco">Économie</a></nav></header>
          <aside class="sidebar"><p>Lisez aussi : un autre article très très long qui ne fait pas partie du texte principal de la page.</p></aside>
          <main><article>
            <h1>Un plan pour les régions</h1>
            <p>$lorem</p>
            <h2>Ce qui change</h2>
            <p>$lorem <a href="/x">un lien</a> au milieu.</p>
            <figure><img src="https://cdn.example.com/a.jpg" alt="Une usine"><figcaption>Une usine à Nantes</figcaption></figure>
            <img src="http://insecure.example.com/b.jpg">
            <blockquote>« Nous voulons agir vite », a déclaré la ministre.</blockquote>
            <ul><li>Industrie</li><li>Transports</li></ul>
            <p>$lorem</p>
            <div class="share">Partager sur Facebook</div>
            <ul class="tags"><li><a href="/t/eco">Économie</a></li></ul>
            <p>$lorem</p>
            <p><a href="/autre">À lire aussi : un autre sujet sans rapport avec celui-ci</a></p>
          </article></main>
          <footer><p>© Le Journal — mentions légales, cookies et conditions générales d'utilisation du site.</p></footer>
        </body></html>
    """.trimIndent()

    @Test
    fun metadata_is_read_from_the_page() {
        val article = ArticleExtractor.extract(page, "https://www.journal.fr/eco/plan.html")!!
        assertEquals("Un plan pour les régions", article.title)
        assertEquals("Le Journal", article.site)
        assertEquals("Léa Martin", article.byline)
        assertEquals("https://www.journal.fr/img/hero.jpg", article.image)
        assertEquals(java.time.OffsetDateTime.parse("2026-10-06T08:30:00+02:00").toInstant().toEpochMilli(), article.published)
    }

    @Test
    fun body_keeps_text_headings_quotes_lists_and_https_images_but_not_the_clutter() {
        val blocks = ArticleExtractor.extract(page, "https://www.journal.fr/eco/plan.html")!!.blocks
        assertEquals(ArticleBlock.Paragraph(lorem), blocks.first())
        assertTrue(ArticleBlock.Heading("Ce qui change") in blocks)
        assertTrue(ArticleBlock.Paragraph("$lorem un lien au milieu.") in blocks)
        assertTrue(ArticleBlock.Image("https://cdn.example.com/a.jpg", "Une usine à Nantes") in blocks)
        assertTrue(ArticleBlock.Quote("« Nous voulons agir vite », a déclaré la ministre.") in blocks)
        assertTrue(ArticleBlock.Bullet("Industrie") in blocks && ArticleBlock.Bullet("Transports") in blocks)
        val all = blocks.joinToString(" ") { it.toString() }
        listOf("tracking", "Lisez aussi", "mentions légales", "Accueil", "insecure", "Partager", "Bullet(text=Économie)", "lire aussi").forEach { assertFalse(it, it in all) }
        // Un paragraphe répété n'apparaît qu'une fois
        assertEquals(1, blocks.count { it == ArticleBlock.Paragraph(lorem) })
        // Le titre n'est pas répété dans le texte
        assertFalse(blocks.any { it == ArticleBlock.Heading("Un plan pour les régions") })
    }

    @Test
    fun without_article_tag_the_densest_block_of_paragraphs_wins() {
        val html = """<html><body><div class="menu"><p>Menu court</p></div>
            <div class="content"><p>1. $lorem</p><p>2. $lorem</p><p>3. $lorem</p></div>
            <div class="comments"><p>Super article !</p></div></body></html>"""
        val article = ArticleExtractor.extract(html, "https://site.fr/a")!!
        assertEquals(List(3) { ArticleBlock.Paragraph("${it + 1}. $lorem") }, article.blocks)
        assertEquals("", article.title)
        assertTrue(ArticleExtractor.isReadable(article))
    }

    @Test
    fun a_paywalled_teaser_is_not_readable() {
        val html = "<html><head><title>Réservé aux abonnés</title></head><body><article><p>Le début de l'article…</p></article></body></html>"
        val article = ArticleExtractor.extract(html, "https://site.fr/a")!!
        assertEquals("Réservé aux abonnés", article.title)
        assertFalse(ArticleExtractor.isReadable(article))
    }

    @Test
    fun garbage_does_not_crash() {
        assertFalse(ArticleExtractor.isReadable(ArticleExtractor.extract("<<<>>>", "https://site.fr/")!!))
        assertNull(ArticleExtractor.extract("<p>x</p>", "pas une adresse"))
    }
}
