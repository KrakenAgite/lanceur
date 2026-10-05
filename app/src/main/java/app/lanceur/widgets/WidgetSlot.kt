package app.lanceur.widgets

import app.lanceur.apps.AppKey

enum class WidgetSize(val heightDp: Int, val shortLabel: String) {
    SMALL(120, "S"),
    MEDIUM(220, "M"),
    LARGE(340, "L"),
    ;

    companion object {
        /** Taille de départ d'après la hauteur minimale prévue par le widget. */
        fun fromMinHeightDp(minHeightDp: Int): WidgetSize = when {
            minHeightDp <= SMALL.heightDp -> SMALL
            minHeightDp <= MEDIUM.heightDp -> MEDIUM
            else -> LARGE
        }
    }
}

/** Un widget posé sur la page. `provider` : paquet et classe du fournisseur, et profil (même forme qu'une appli). */
data class WidgetSlot(val appWidgetId: Int, val provider: AppKey, val size: WidgetSize) {
    /** Forme texte : `id|paquet/classe#série|TAILLE`. */
    fun encode(): String = "$appWidgetId|${provider.encode()}|${size.name}"

    companion object {
        fun decode(value: String): WidgetSlot? {
            val parts = value.split('|')
            if (parts.size != 3) return null
            val id = parts[0].toIntOrNull() ?: return null
            val provider = AppKey.decode(parts[1]) ?: return null
            val size = WidgetSize.entries.firstOrNull { it.name == parts[2] } ?: return null
            return WidgetSlot(id, provider, size)
        }
    }
}
