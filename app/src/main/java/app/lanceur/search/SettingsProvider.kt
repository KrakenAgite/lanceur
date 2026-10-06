package app.lanceur.search

import app.lanceur.i18n.tr
import app.lanceur.text.TextNormalizer

data class SettingShortcut(val action: String, val label: String, val keywords: List<String>)

/** Raccourcis vers les réglages Android. Les actions sont écrites en clair pour rester testables sur la JVM. */
object SettingsProvider : SearchProvider {
    val SHORTCUTS: List<SettingShortcut> get() = listOf(
        SettingShortcut("android.settings.SETTINGS", tr("Paramètres", "Settings"), listOf("paramètres", "réglages", "settings")),
        SettingShortcut("android.settings.WIFI_SETTINGS", tr("Wi-Fi", "Wi-Fi"), listOf("wifi", "wi-fi", "sans fil", "internet", "wireless")),
        SettingShortcut("android.settings.BLUETOOTH_SETTINGS", tr("Bluetooth", "Bluetooth"), listOf("bluetooth", "écouteurs", "casque", "headphones", "earbuds")),
        SettingShortcut("android.settings.DISPLAY_SETTINGS", tr("Affichage", "Display"), listOf("écran", "luminosité", "mode sombre", "veille", "display", "screen", "brightness", "dark mode")),
        SettingShortcut("android.intent.action.POWER_USAGE_SUMMARY", tr("Batterie", "Battery"), listOf("batterie", "autonomie", "économie", "battery", "power")),
        SettingShortcut("android.settings.SOUND_SETTINGS", tr("Son et vibreur", "Sound & vibration"), listOf("son", "volume", "sonnerie", "vibreur", "sound", "volume", "ringtone", "vibration")),
        SettingShortcut("android.settings.MANAGE_APPLICATIONS_SETTINGS", tr("Applis", "Apps"), listOf("applications", "applis", "apps")),
        SettingShortcut("android.settings.ALL_APPS_NOTIFICATION_SETTINGS", tr("Notifications", "Notifications"), listOf("notifications", "alertes", "notifications")),
        SettingShortcut("android.settings.LOCATION_SOURCE_SETTINGS", tr("Localisation", "Location"), listOf("localisation", "gps", "position", "location")),
        SettingShortcut("android.settings.SECURITY_SETTINGS", tr("Sécurité et confidentialité", "Security & privacy"), listOf("sécurité", "confidentialité", "empreinte", "verrouillage", "espace privé", "security", "privacy", "fingerprint", "screen lock", "private space")),
        SettingShortcut("android.settings.INTERNAL_STORAGE_SETTINGS", tr("Stockage", "Storage"), listOf("stockage", "mémoire", "espace", "storage", "memory")),
        SettingShortcut("android.settings.WIRELESS_SETTINGS", tr("Réseau et Internet", "Internet & network"), listOf("réseau", "données mobiles", "avion", "sim", "network", "mobile data", "airplane")),
        SettingShortcut("android.settings.DATE_SETTINGS", tr("Date et heure", "Date & time"), listOf("date", "heure", "fuseau", "time", "time zone")),
        SettingShortcut("android.settings.ACCESSIBILITY_SETTINGS", tr("Accessibilité", "Accessibility"), listOf("accessibilité", "taille du texte", "accessibility", "font size")),
        SettingShortcut("android.settings.DEVICE_INFO_SETTINGS", tr("À propos du téléphone", "About phone"), listOf("à propos", "version", "android", "about")),
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
