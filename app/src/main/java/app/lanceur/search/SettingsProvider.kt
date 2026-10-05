package app.lanceur.search

import app.lanceur.text.TextNormalizer

data class SettingShortcut(val action: String, val label: String, val keywords: List<String>)

/** Raccourcis vers les réglages Android. Les actions sont écrites en clair pour rester testables sur la JVM. */
object SettingsProvider : SearchProvider {
    val SHORTCUTS = listOf(
        SettingShortcut("android.settings.SETTINGS", "Paramètres", listOf("paramètres", "réglages")),
        SettingShortcut("android.settings.WIFI_SETTINGS", "Wi-Fi", listOf("wifi", "wi-fi", "sans fil", "internet")),
        SettingShortcut("android.settings.BLUETOOTH_SETTINGS", "Bluetooth", listOf("bluetooth", "écouteurs", "casque")),
        SettingShortcut("android.settings.DISPLAY_SETTINGS", "Affichage", listOf("écran", "luminosité", "mode sombre", "veille")),
        SettingShortcut("android.intent.action.POWER_USAGE_SUMMARY", "Batterie", listOf("batterie", "autonomie", "économie")),
        SettingShortcut("android.settings.SOUND_SETTINGS", "Son et vibreur", listOf("son", "volume", "sonnerie", "vibreur")),
        SettingShortcut("android.settings.MANAGE_APPLICATIONS_SETTINGS", "Applis", listOf("applications", "applis")),
        SettingShortcut("android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "Notifications", listOf("notifications", "alertes")),
        SettingShortcut("android.settings.LOCATION_SOURCE_SETTINGS", "Localisation", listOf("localisation", "gps", "position")),
        SettingShortcut("android.settings.SECURITY_SETTINGS", "Sécurité et confidentialité", listOf("sécurité", "confidentialité", "empreinte", "verrouillage", "espace privé")),
        SettingShortcut("android.settings.INTERNAL_STORAGE_SETTINGS", "Stockage", listOf("stockage", "mémoire", "espace")),
        SettingShortcut("android.settings.WIRELESS_SETTINGS", "Réseau et Internet", listOf("réseau", "données mobiles", "avion", "sim")),
        SettingShortcut("android.settings.DATE_SETTINGS", "Date et heure", listOf("date", "heure", "fuseau")),
        SettingShortcut("android.settings.ACCESSIBILITY_SETTINGS", "Accessibilité", listOf("accessibilité", "taille du texte")),
        SettingShortcut("android.settings.DEVICE_INFO_SETTINGS", "À propos du téléphone", listOf("à propos", "version", "android")),
    )

    override suspend fun search(query: String): List<SearchResult> =
        match(query).map { SearchResult.Setting(it.action, it.label) }

    fun match(query: String, limit: Int = 3): List<SettingShortcut> {
        val q = TextNormalizer.fold(query.trim())
        if (q.length < 2) return emptyList()
        return SHORTCUTS.filter { shortcut ->
            TextNormalizer.fold(shortcut.label).contains(q) ||
                shortcut.keywords.any { keyword -> TextNormalizer.fold(keyword).split(' ', '-').any { it.startsWith(q) } }
        }.take(limit)
    }
}
