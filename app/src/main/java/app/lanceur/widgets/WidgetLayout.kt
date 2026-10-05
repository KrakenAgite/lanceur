package app.lanceur.widgets

import app.lanceur.builtin.BuiltinSlots

/** Hauteur et tailles proposées d'une carte : celles du type intégré, sinon celles des widgets Android. */
object WidgetLayout {
    fun heightDp(slot: WidgetSlot): Int = BuiltinSlots.kindOf(slot)?.heightDp(slot.size) ?: slot.size.heightDp

    fun sizes(slot: WidgetSlot): List<WidgetSize> = BuiltinSlots.kindOf(slot)?.sizes ?: WidgetSize.entries

    fun displaySize(slot: WidgetSlot): WidgetSize = BuiltinSlots.kindOf(slot)?.effectiveSize(slot.size) ?: slot.size
}
