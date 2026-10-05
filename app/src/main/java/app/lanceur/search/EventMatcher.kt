package app.lanceur.search

import app.lanceur.text.TextNormalizer

object EventMatcher {
    fun matches(title: String?, location: String?, query: String): Boolean {
        val q = TextNormalizer.fold(query.trim())
        if (q.isEmpty()) return false
        return listOfNotNull(title, location).any { TextNormalizer.fold(it).contains(q) }
    }
}
