package app.lanceur.news

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSizingTest {
    @Test
    fun sample_size_keeps_at_least_the_target_width() {
        assertEquals(2, ImageSizing.sampleSize(4000, 3000, 1080))
        assertEquals(1, ImageSizing.sampleSize(1200, 800, 1080))
        assertEquals(1, ImageSizing.sampleSize(800, 600, 1080))
        assertEquals(8, ImageSizing.sampleSize(10_000, 10_000, 1080))
        assertEquals(1, ImageSizing.sampleSize(0, 0, 1080))
    }

    @Test
    fun oldest_files_go_first_beyond_the_limit() {
        val files = listOf(
            ImageSizing.CachedFile("a", size = 30, lastUsed = 3),
            ImageSizing.CachedFile("b", size = 30, lastUsed = 1),
            ImageSizing.CachedFile("c", size = 30, lastUsed = 2),
        )
        assertEquals(listOf("b"), ImageSizing.toDelete(files, maxBytes = 70))
        assertEquals(emptyList<String>(), ImageSizing.toDelete(files, maxBytes = 90))
        assertEquals(listOf("c", "b"), ImageSizing.toDelete(files, maxBytes = 30))
    }

    @Test
    fun very_tall_images_are_reduced_too() {
        // 2000 × 6000 sur un écran de 1080 : la largeur seule ne réduirait pas, le nombre de pixels si
        val sample = ImageSizing.sampleSize(2000, 6000, 1080)
        assertEquals(true, (2000 / sample) * (6000 / sample) <= ImageSizing.MAX_PIXELS)
        assertEquals(2, sample)
    }
}
