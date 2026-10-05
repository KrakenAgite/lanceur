package app.lanceur.apps

/**
 * Android n'accorde l'accès à l'Espace privé qu'à l'écran d'accueil par défaut : quand Lanceur le devient,
 * il faut relire le catalogue pour voir ce profil.
 */
class HomeRoleWatcher(private val onBecameHeld: () -> Unit) {
    private var held: Boolean? = null

    fun update(nowHeld: Boolean) {
        if (held == false && nowHeld) onBecameHeld()
        held = nowHeld
    }
}
