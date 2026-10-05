package app.lanceur.builtin.contacts

import android.Manifest
import android.content.Context
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import androidx.compose.ui.graphics.asImageBitmap
import app.lanceur.search.SearchPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Contacts marqués d'une étoile dans l'appli Contacts, suivis en direct. */
class FavoriteContactsSource(private val context: Context) {
    /** Un nouveau flux à chaque appel : la permission est revérifiée (accordée entre-temps). */
    fun favorites(): Flow<FavoritesState> = callbackFlow {
        if (!SearchPermissions.granted(context, Manifest.permission.READ_CONTACTS)) {
            trySend(FavoritesState.NoPermission)
            awaitClose { }
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch { send(load()) }
            }
        }
        context.contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        send(load())
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    private suspend fun load(): FavoritesState = withContext(Dispatchers.IO) {
        FavoritesState.Loaded(runCatching { query() }.getOrDefault(emptyList()))
    }

    private fun query(): List<FavoriteContact> {
        val resolver = context.contentResolver
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI, Contacts.HAS_PHONE_NUMBER)
        val out = mutableListOf<FavoriteContact>()
        resolver.query(Contacts.CONTENT_URI, projection, "${Contacts.STARRED} = 1", null, "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
            while (c.moveToNext() && out.size < MAX) {
                val id = c.getLong(0)
                val phone = if (c.getInt(4) == 1) firstPhone(id) else null
                out += FavoriteContact(
                    lookupUri = Contacts.getLookupUri(id, c.getString(1)).toString(),
                    name = c.getString(2).orEmpty(),
                    phone = phone,
                    photo = c.getString(3)?.let { photo(Uri.parse(it)) },
                )
            }
        }
        return out
    }

    /** Le numéro principal s'il y en a un, sinon le premier. */
    private fun firstPhone(contactId: Long): String? =
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER),
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            "${Phone.IS_SUPER_PRIMARY} DESC, ${Phone.IS_PRIMARY} DESC",
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun photo(uri: Uri) = runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()

    private companion object {
        const val MAX = 8
    }
}
