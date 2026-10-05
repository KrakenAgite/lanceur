package app.lanceur.apps

import java.text.Collator
import java.util.Locale

/** Ordre alphabétique français, sans tenir compte des accents ni de la casse ; la clé départage les homonymes. */
object LabelOrder : Comparator<AppEntry> {
    private val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    override fun compare(a: AppEntry, b: AppEntry): Int {
        val byLabel = synchronized(collator) { collator.compare(a.label, b.label) }
        return if (byLabel != 0) byLabel else a.key.encode().compareTo(b.key.encode())
    }
}
