package app.lanceur.apps.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

data class IconPackInfo(val packageName: String, val label: String)

/** Un pack chargé : ses ressources et ses correspondances. */
class LoadedPack(private val packageName: String, private val resources: Resources, val filter: AppFilter) {
    fun drawable(name: String, densityDpi: Int): Drawable? = runCatching {
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
        resources.getDrawableForDensity(id, densityDpi, null)
    }.getOrNull()
}

/** Packs d'icônes au format Nova / ADW (celui de presque tous les packs du Play Store). */
object IconPacks {
    /** Actions annoncées par les packs ; chacun en déclare au moins une. */
    val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme",
    )

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        return ACTIONS.flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL) }
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** Lit `appfilter.xml` (ressource xml ou fichier dans assets) ; null si le pack est introuvable ou illisible. */
    fun load(context: Context, packageName: String): LoadedPack? = runCatching {
        val resources = context.packageManager.getResourcesForApplication(packageName)
        val id = resources.getIdentifier("appfilter", "xml", packageName)
        val map = if (id != 0) read(resources.getXml(id)) else resources.assets.open("appfilter.xml").use { stream ->
            read(Xml.newPullParser().apply { setInput(stream, null) })
        }
        LoadedPack(packageName, resources, AppFilter(map))
    }.onFailure { Log.w("Lanceur", "Pack d'icônes illisible : $packageName", it) }.getOrNull()

    private fun read(parser: XmlPullParser): Map<Pair<String, String>, String> {
        val out = HashMap<Pair<String, String>, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val component = parser.getAttributeValue(null, "component")?.let(AppFilter::component) ?: continue
            val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
            out.putIfAbsent(component, drawable)
        }
        return out
    }
}
