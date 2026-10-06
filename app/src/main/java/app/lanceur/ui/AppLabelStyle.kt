package app.lanceur.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.lanceur.i18n.L10n

/** Affichage des applis dans les listes : avec ou sans icône, nom tel quel ou en majuscules. */
data class AppLabelStyle(val showIcons: Boolean = true, val uppercase: Boolean = false) {
    fun format(label: String): String = if (uppercase) label.uppercase(L10n.locale) else label
}

val LocalAppLabelStyle = staticCompositionLocalOf { AppLabelStyle() }
