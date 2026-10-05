package app.lanceur.apps

import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Accès au système, séparé pour que le catalogue soit testable sans Android. */
interface CatalogSource {
    /** `onChange` reçoit le paquet modifié, ou `null` pour un changement de profil. */
    fun start(onChange: (changedPackage: String?) -> Unit)

    suspend fun snapshot(): List<ProfileSnapshot>

    /** `true` si le système va afficher son propre verrou. */
    fun setPrivateSpaceLocked(locked: Boolean): Boolean
}

/** Liste des applis lançables de tous les profils, tenue à jour en direct. */
class AppCatalog(
    private val scope: CoroutineScope,
    private val source: CatalogSource,
    private val onPackageChanged: (String) -> Unit = {},
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onError: (Throwable) -> Unit = {},
) {
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** `null` tant que le premier chargement n'est pas terminé. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val _privateSpace = MutableStateFlow(PrivateSpaceState.ABSENT)
    val privateSpace: StateFlow<PrivateSpaceState> = _privateSpace.asStateFlow()

    private val generation = AtomicLong()
    private val publishLock = Any()

    fun start() {
        source.start { changedPackage ->
            if (changedPackage != null) onPackageChanged(changedPackage)
            reload()
        }
        reload()
    }

    /**
     * Seul le chargement le plus récent est publié : les appels système ne s'interrompent pas,
     * un ancien chargement plus lent ne doit donc jamais écraser un plus récent. En cas d'erreur,
     * la liste précédente reste affichée (et rien n'est nettoyé dans les préférences).
     */
    fun reload() {
        val ticket = generation.incrementAndGet()
        scope.launch(dispatcher) {
            val state = try {
                CatalogBuilder.build(source.snapshot())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                return@launch
            }
            synchronized(publishLock) {
                if (ticket != generation.get()) return@launch
                _apps.value = state.apps
                _privateSpace.value = state.privateSpace
            }
        }
    }

    fun setPrivateSpaceLocked(locked: Boolean): Boolean = source.setPrivateSpaceLocked(locked)
}
