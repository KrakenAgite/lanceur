package app.lanceur.news

object ImageSizing {
    /** Plus grande puissance de 2 qui garde une largeur ≥ `targetWidth` : jamais d'image décodée en taille réelle inutilement. */
    /** Au plus 4 millions de pixels par image (environ 16 Mo), même pour une image très haute. */
    const val MAX_PIXELS = 4_000_000L

    fun sampleSize(width: Int, height: Int, targetWidth: Int): Int {
        if (width <= 0 || height <= 0 || targetWidth <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= targetWidth) sample *= 2
        while ((width / sample).toLong() * (height / sample) > MAX_PIXELS) sample *= 2
        return sample
    }

    data class CachedFile(val name: String, val size: Long, val lastUsed: Long)

    /** Garde les plus récemment utilisés dans `maxBytes` ; renvoie les autres, du plus récent au plus ancien. */
    fun toDelete(files: List<CachedFile>, maxBytes: Long): List<String> {
        var total = 0L
        return files.sortedByDescending { it.lastUsed }.filter { file ->
            total += file.size
            total > maxBytes
        }.map { it.name }
    }
}
