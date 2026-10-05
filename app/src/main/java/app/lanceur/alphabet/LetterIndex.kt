package app.lanceur.alphabet

import app.lanceur.apps.AppEntry
import app.lanceur.text.TextNormalizer

data class LetterSection(val letter: Char, val apps: List<AppEntry>) {
    val isEmpty: Boolean get() = apps.isEmpty()
}

object LetterIndex {
    /** A à Z, puis `#` pour tout ce qui ne commence pas par une lettre latine. */
    val LETTERS: List<Char> = ('A'..'Z').toList() + '#'

    fun letterOf(label: String): Char {
        val first = TextNormalizer.fold(label.trim()).firstOrNull() ?: return '#'
        return if (first in 'A'..'Z') first else '#'
    }

    /** Une section par lettre de [LETTERS] ; `sortedApps` doit déjà être trié avec `LabelOrder`. */
    fun build(sortedApps: List<AppEntry>): List<LetterSection> {
        val grouped = sortedApps.groupBy { letterOf(it.label) }
        return LETTERS.map { LetterSection(it, grouped[it].orEmpty()) }
    }
}
