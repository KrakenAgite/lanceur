package app.lanceur.lock

/**
 * Lecture du réglage d'Android qui retient les services d'accessibilité activés
 * (`enabled_accessibility_services` : « paquet/classe » séparés par « : », classe parfois abrégée en « .Nom »).
 */
object LockServiceSetting {
    fun isEnabledIn(setting: String?, packageName: String, className: String): Boolean =
        setting.orEmpty().split(':').any { entry ->
            val parts = entry.trim().split('/')
            if (parts.size != 2) return@any false
            val pkg = parts[0]
            val cls = if (parts[1].startsWith(".")) pkg + parts[1] else parts[1]
            pkg.equals(packageName, ignoreCase = true) && cls.equals(className, ignoreCase = true)
        }
}
