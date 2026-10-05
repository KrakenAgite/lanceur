package app.lanceur.text

import java.text.Normalizer
import java.util.Locale

/** Forme de comparaison d'un texte : majuscules, sans accents, ligatures « Œ » et « Æ » développées. */
object TextNormalizer {
    private val marks = Regex("\\p{Mn}+")

    fun fold(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
            .uppercase(Locale.ROOT)
            .replace("Œ", "OE")
            .replace("Æ", "AE")
}
