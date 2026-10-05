package app.lanceur

import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey

/** Fabrique une appli de test : la clé est dérivée du nom, sauf si `id` est donné. */
fun app(
    label: String,
    id: String = label.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "x" },
    serial: Long = 0,
    isPrivate: Boolean = false,
): AppEntry = AppEntry(AppKey("app.$id", "app.$id.Main", serial), label, isPrivate)
