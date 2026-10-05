package app.lanceur.search

import android.Manifest
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsProvider(private val context: Context, private val limit: Int = 5) : SearchProvider {
    override val delayMs = 80L

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (!SearchPermissions.granted(context, Manifest.permission.READ_CONTACTS)) return@withContext emptyList<SearchResult>()
        val uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.HAS_PHONE_NUMBER)
        val results = mutableListOf<SearchResult>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            while (results.size < limit && cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(2)
                if (name != null) {
                    val phone = if (cursor.getInt(3) > 0) firstPhone(id) else null
                    results += SearchResult.Contact(Contacts.getLookupUri(id, cursor.getString(1)).toString(), name, phone)
                }
            }
        }
        results
    }

    private fun firstPhone(contactId: Long): String? = context.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone.NUMBER),
        "${Phone.CONTACT_ID} = ?",
        arrayOf(contactId.toString()),
        "${Phone.IS_SUPER_PRIMARY} DESC",
    )?.use { if (it.moveToFirst()) it.getString(0) else null }
}
