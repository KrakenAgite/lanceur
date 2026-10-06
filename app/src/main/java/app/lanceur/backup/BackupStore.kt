package app.lanceur.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import app.lanceur.prefs.Backup
import app.lanceur.prefs.BackupFile
import app.lanceur.prefs.PrefsRepo
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Écrit et lit les fichiers de sauvegarde : un fichier choisi à la main, ou le dossier des sauvegardes automatiques. */
class BackupStore(context: Context, private val prefs: PrefsRepo) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Garde l'accès au dossier choisi après redémarrage. */
    fun keepAccess(folder: Uri) {
        resolver.takePersistableUriPermission(folder, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    fun folderName(folder: String?): String? = folder?.let { Uri.parse(it).lastPathSegment?.substringAfterLast(':')?.ifEmpty { null } }

    suspend fun current(): String = Backup.encode(prefs.snapshot(), System.currentTimeMillis())

    /** Sauvegarde manuelle dans le fichier choisi. */
    suspend fun exportTo(file: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = current()
            resolver.openOutputStream(file, "wt")!!.use { it.write(text.toByteArray()) }
            prefs.setBackupLast(System.currentTimeMillis())
        }.onFailure { Log.w("Lanceur", "Sauvegarde impossible", it) }.isSuccess
    }

    suspend fun read(file: Uri): BackupFile? = withContext(Dispatchers.IO) {
        runCatching { resolver.openInputStream(file)!!.use { Backup.decode(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull()
    }

    suspend fun restore(backup: BackupFile) = prefs.restore(backup.values)

    /** Sauvegarde automatique : un nouveau fichier daté dans le dossier, puis seuls les [Backup.KEEP] plus récents restent. */
    suspend fun writeToFolder(folder: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val tree = Uri.parse(folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val file = DocumentsContract.createDocument(resolver, parent, "application/json", Backup.fileName(LocalDateTime.now()))!!
            val text = current()
            resolver.openOutputStream(file, "wt")!!.use { it.write(text.toByteArray()) }
            prune(tree)
            prefs.setBackupLast(System.currentTimeMillis())
        }.onFailure { Log.w("Lanceur", "Sauvegarde automatique impossible", it) }.isSuccess
    }

    private fun prune(tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = mutableListOf<Pair<String, String>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (Backup.isBackupName(name)) found += c.getString(0) to name
            }
        }
        // Les noms sont datés : l'ordre alphabétique est l'ordre chronologique
        found.sortedByDescending { it.second }.drop(Backup.KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}
