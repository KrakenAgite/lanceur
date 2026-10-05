package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerCatalogTest {
    private fun entry(pkg: String, cls: String, app: String, widget: String, kind: ProfileKind = ProfileKind.MAIN, serial: Long = 0) =
        ProviderEntry(AppKey(pkg, cls, serial), kind, app, widget, 110)

    private val meteoJour = entry("app.meteo", "Jour", "Météo", "Prévisions du jour")
    private val meteoSemaine = entry("app.meteo", "Semaine", "Météo", "Semaine")
    private val agenda = entry("app.agenda", "Mois", "Agenda", "Mois")
    private val editions = entry("app.editions", "Une", "Éditions", "À la une")
    private val coffre = entry("app.coffre", "W", "Coffre", "Solde", ProfileKind.PRIVATE, 11)
    private val inconnu = entry("app.inconnu", "W", "Inconnu", "Widget", ProfileKind.UNKNOWN, 12)
    private val all = listOf(meteoSemaine, editions, coffre, agenda, inconnu, meteoJour)

    @Test
    fun groups_by_app_sorted_in_french_without_private_or_unknown_profiles() {
        val groups = PickerCatalog.build(all, hidden = emptySet(), query = "")
        assertEquals(listOf("Agenda", "Éditions", "Météo"), groups.map { it.appLabel })
        assertEquals(listOf("Prévisions du jour", "Semaine"), groups.last().entries.map { it.widgetLabel })
    }

    @Test
    fun hidden_apps_offer_no_widget() {
        val hiddenApp = AppKey("app.meteo", "app.meteo.MainActivity", 0)
        val groups = PickerCatalog.build(all, hidden = setOf(hiddenApp), query = "")
        assertEquals(listOf("Agenda", "Éditions"), groups.map { it.appLabel })
    }

    @Test
    fun filter_matches_app_or_widget_names_without_accents() {
        assertEquals(listOf("Météo"), PickerCatalog.build(all, emptySet(), "meteo").map { it.appLabel })
        assertEquals(listOf("Éditions"), PickerCatalog.build(all, emptySet(), "a la UNE").map { it.appLabel })
        assertTrue(PickerCatalog.build(all, emptySet(), "zzz").isEmpty())
    }

    @Test
    fun the_same_app_in_the_work_profile_is_a_separate_group() {
        val workAgenda = entry("app.agenda", "Mois", "Agenda", "Mois", ProfileKind.OTHER, 10)
        val groups = PickerCatalog.build(listOf(agenda, workAgenda), emptySet(), "")
        assertEquals(listOf(false, true), groups.map { it.isWork })
    }

    @Test
    fun lanceur_group_comes_first_in_enum_order() {
        val groups = PickerCatalog.build(all, emptySet(), "", app.lanceur.builtin.BuiltinSlots.pickerEntries())
        assertEquals(listOf("Lanceur", "Agenda", "Éditions", "Météo"), groups.map { it.appLabel })
        assertEquals(
            app.lanceur.builtin.BuiltinKind.entries.map { it.label },
            groups.first().entries.map { it.widgetLabel },
        )
    }

    @Test
    fun lanceur_widgets_follow_the_filter() {
        val groups = PickerCatalog.build(all, emptySet(), "batterie", app.lanceur.builtin.BuiltinSlots.pickerEntries())
        assertEquals(listOf("Lanceur"), groups.map { it.appLabel })
        assertEquals(2, groups.single().entries.size)
    }
}
