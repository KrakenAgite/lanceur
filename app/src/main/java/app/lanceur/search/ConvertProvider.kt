package app.lanceur.search

import java.time.ZonedDateTime

/** Conversions d'unités et de fuseaux horaires, sans réseau (voir [Converter]). */
class ConvertProvider(private val now: () -> ZonedDateTime = ZonedDateTime::now) : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> =
        listOfNotNull(Converter.convert(query, now())?.let { SearchResult.Convert(it.detail, it.value) })
}
