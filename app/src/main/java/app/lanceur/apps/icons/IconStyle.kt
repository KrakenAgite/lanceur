package app.lanceur.apps.icons

import app.lanceur.i18n.tr

/** Forme imposée aux icônes adaptatives d'Android ; [SYSTEM] garde celle du téléphone. */
enum class IconShape(private val fr: String, private val en: String) {
    SYSTEM("Système", "System"),
    CIRCLE("Rond", "Circle"),
    SQUIRCLE("Galet", "Squircle"),
    ROUNDED("Carré arrondi", "Rounded square"),
    TEARDROP("Goutte", "Teardrop"),
    ;

    val label: String get() = tr(fr, en)

    companion object {
        fun decode(name: String?): IconShape = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** [pack] : paquet du pack d'icônes choisi, ou null pour les icônes du système. */
data class IconStyle(val pack: String? = null, val shape: IconShape = IconShape.SYSTEM, val themed: Boolean = false)
