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
