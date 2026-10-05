package app.lanceur.apps

/** Type d'un profil selon le système. `UNKNOWN` : type illisible, ce pourrait être l'Espace privé. */
enum class ProfileKind { MAIN, PRIVATE, OTHER, UNKNOWN }

/** Ce qu'une lecture du système a trouvé pour un profil. */
data class ProfileSnapshot(val kind: ProfileKind, val isQuiet: Boolean, val apps: List<AppEntry>)

data class CatalogState(val apps: List<AppEntry>, val privateSpace: PrivateSpaceState)

object CatalogBuilder {
    fun build(profiles: List<ProfileSnapshot>): CatalogState {
        val private = profiles.firstOrNull { it.kind == ProfileKind.PRIVATE }
        val apps = profiles.flatMap { profile ->
            when (profile.kind) {
                ProfileKind.MAIN, ProfileKind.OTHER -> profile.apps.map { it.copy(isPrivateSpace = false) }
                ProfileKind.PRIVATE -> if (profile.isQuiet) emptyList() else profile.apps.map { it.copy(isPrivateSpace = true) }
                // Par prudence : on n'affiche jamais un profil dont on ne connaît pas le type
                ProfileKind.UNKNOWN -> emptyList()
            }
        }
        val privateSpace = when {
            private == null -> PrivateSpaceState.ABSENT
            private.isQuiet -> PrivateSpaceState.LOCKED
            else -> PrivateSpaceState.UNLOCKED
        }
        return CatalogState(apps, privateSpace)
    }
}
