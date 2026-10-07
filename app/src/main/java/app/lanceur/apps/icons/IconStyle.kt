package app.lanceur.apps.icons

import app.lanceur.i18n.tr

/** Forme imposée aux icônes adaptatives d'Android ; [SYSTEM] garde celle du téléphone. */
enum class IconShape(private val fr: String, private val en: String) {
    SYSTEM("Système", "System"),
    CIRCLE("Rond", "Circle"),
    SQUIRCLE("Galet", "Squircle"),
    ROUNDED("Arrondi", "Rounded"),
    TEARDROP("Goutte", "Teardrop"),
    SQUARE("Carré", "Square"),
    SOFT_SQUARE("Carré doux", "Soft square"),
    LEAF("Feuille", "Leaf"),
    CYLINDER("Cylindre", "Cylinder"),
    ARCH("Arche", "Arch"),
    SHIELD("Écusson", "Shield"),
    GEM("Gemme", "Gem"),
    DIAMOND("Losange", "Diamond"),
    TRIANGLE("Triangle", "Triangle"),
    PENTAGON("Pentagone", "Pentagon"),
    HEXAGON("Hexagone", "Hexagon"),
    OCTAGON("Octogone", "Octagon"),
    COOKIE("Biscuit", "Cookie"),
    SUNNY("Soleil", "Sunny"),
    FLOWER("Fleur", "Flower"),
    CLOVER("Trèfle", "Clover"),
    STAR("Étoile", "Star"),
    BLOB("Bulle", "Blob"),
    HEART("Cœur", "Heart"),
    ;

    val label: String get() = tr(fr, en)

    companion object {
        fun decode(name: String?): IconShape = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** [pack] : paquet du pack d'icônes choisi, ou null pour les icônes du système. */
data class IconStyle(val pack: String? = null, val shape: IconShape = IconShape.SYSTEM, val themed: Boolean = false)
