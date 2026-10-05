package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import app.lanceur.prefs.VisibleApps
import app.lanceur.text.TextNormalizer
import java.text.Collator
import java.util.Locale

/** Un widget installé, tel que le sélecteur le présente. */
data class ProviderEntry(
    val provider: AppKey,
    val profileKind: ProfileKind,
    val appLabel: String,
    val widgetLabel: String,
    val minHeightDp: Int,
)

data class PickerGroup(val appLabel: String, val packageName: String, val isWork: Boolean, val entries: List<ProviderEntry>)

object PickerCatalog {
    private val collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

    /** Jamais l'Espace privé ni un profil de type inconnu, jamais une appli cachée. */
    fun build(entries: List<ProviderEntry>, hidden: Set<AppKey>, query: String): List<PickerGroup> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        val q = TextNormalizer.fold(query.trim())
        entries
            .filter { it.profileKind == ProfileKind.MAIN || it.profileKind == ProfileKind.OTHER }
            .filter { it.provider.packageInProfile() !in hiddenPackages }
            .filter { q.isEmpty() || TextNormalizer.fold(it.appLabel).contains(q) || TextNormalizer.fold(it.widgetLabel).contains(q) }
            .groupBy { it.provider.packageInProfile() }
            .values
            .map { group ->
                val first = group.first()
                PickerGroup(
                    appLabel = first.appLabel,
                    packageName = first.provider.packageName,
                    isWork = first.profileKind == ProfileKind.OTHER,
                    entries = group.sortedWith(compareBy(collator) { it.widgetLabel }),
                )
            }
            .sortedWith(compareBy<PickerGroup, String>(collator) { it.appLabel }.thenBy { it.isWork })
    }
}
