package app.lanceur.i18n

import java.util.Locale

enum class Lang(val locale: Locale) { FR(Locale.FRENCH), EN(Locale.ENGLISH) }

/**
 * Langue de l'appli : anglais si la langue d'Android (ou celle choisie pour Lanceur) est l'anglais, sinon français.
 * Les textes vivent en paire dans le code avec [tr], pour qu'aucune traduction ne manque et que le code pur reste testable.
 */
object L10n {
    @Volatile var lang: Lang = Lang.FR

    /** Langue imposée quelle que soit celle du téléphone (tests d'interface). */
    @Volatile var forced: Lang? = null

    val locale: Locale get() = lang.locale

    fun langOf(locale: Locale?): Lang = if (locale?.language == "en") Lang.EN else Lang.FR

    fun apply(locale: Locale?) { lang = forced ?: langOf(locale) }
}

fun tr(fr: String, en: String): String = if (L10n.lang == Lang.EN) en else fr
