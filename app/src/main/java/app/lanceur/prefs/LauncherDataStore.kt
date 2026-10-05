package app.lanceur.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Fabrique unique du stockage des préférences (l'app et les tests passent par elle). */
object LauncherDataStore {
    fun create(
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile: () -> File,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // Fichier corrompu : on repart des réglages par défaut plutôt que de planter à chaque démarrage
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )
}
