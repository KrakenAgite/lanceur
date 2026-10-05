package app.lanceur.apps

/** Identifie une activité de lancement dans un profil. Forme texte : `paquet/activité#série-du-profil`. */
data class AppKey(val packageName: String, val className: String, val userSerial: Long) {
    fun encode(): String = "$packageName/$className#$userSerial"

    companion object {
        fun decode(value: String): AppKey? {
            val slash = value.indexOf('/')
            val hash = value.lastIndexOf('#')
            if (slash <= 0 || hash <= slash + 1 || hash == value.lastIndex) return null
            val serial = value.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(value.substring(0, slash), value.substring(slash + 1, hash), serial)
        }
    }
}
