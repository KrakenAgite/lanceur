package app.lanceur.builtin

import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSlot

/**
 * Un widget intégré est un `WidgetSlot` ordinaire : identifiant négatif (Android n'en attribue jamais) et fournisseur
 * `app.lanceur.builtin/<TYPE>`. Rien ne change donc dans le stockage, l'ordre ou les tailles.
 */
object BuiltinSlots {
    const val PACKAGE = "app.lanceur.builtin"

    fun isBuiltin(key: AppKey): Boolean = key.packageName == PACKAGE

    fun isBuiltin(slot: WidgetSlot): Boolean = isBuiltin(slot.provider)

    /** `null` pour un widget Android, ou pour un type inconnu (enregistré par une version plus récente). */
    fun kindOf(key: AppKey): BuiltinKind? =
        if (isBuiltin(key)) BuiltinKind.entries.firstOrNull { it.name == key.className } else null

    fun kindOf(slot: WidgetSlot): BuiltinKind? = kindOf(slot.provider)

    fun provider(kind: BuiltinKind): AppKey = AppKey(PACKAGE, kind.name, 0)

    fun nextId(slots: List<WidgetSlot>): Int = minOf(0, slots.minOfOrNull { it.appWidgetId } ?: 0) - 1

    fun create(kind: BuiltinKind, slots: List<WidgetSlot>): WidgetSlot =
        WidgetSlot(nextId(slots), provider(kind), kind.defaultSize)
}
