package app.lanceur.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun the_latest_release_is_read_from_github() {
        val json = """{"tag_name":"v1.5.0","name":"Lanceur 1.5.0","html_url":"https://github.com/KrakenAgite/lanceur/releases/tag/v1.5.0","draft":false,"prerelease":false,
            "assets":[{"name":"notes.txt","browser_download_url":"https://github.com/x/notes.txt"},
                      {"name":"lanceur-1.5.0.apk","size":3459177,"digest":"sha256:873d8ece","browser_download_url":"https://github.com/KrakenAgite/lanceur/releases/download/v1.5.0/lanceur-1.5.0.apk"}]}"""
        assertEquals(
            Release("1.5.0", "https://github.com/KrakenAgite/lanceur/releases/tag/v1.5.0", "https://github.com/KrakenAgite/lanceur/releases/download/v1.5.0/lanceur-1.5.0.apk", "873d8ece"),
            UpdateCheck.parse(json),
        )
        assertNull(UpdateCheck.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parse("pas du json"))
        assertNull(UpdateCheck.parse(json.replace("\"draft\":false", "\"draft\":true")))
    }

    @Test
    fun a_release_without_https_apk_or_digest_cannot_be_installed_by_itself() {
        val base = """{"tag_name":"v2.0","html_url":"https://github.com/KrakenAgite/lanceur/releases/tag/v2.0","assets":[ASSET]}"""
        assertNull(UpdateCheck.parse(base.replace("ASSET", ""))!!.apkUrl)
        assertNull(UpdateCheck.parse(base.replace("ASSET", """{"name":"a.apk","browser_download_url":"http://x/a.apk","digest":"sha256:ab"}"""))!!.apkUrl)
        assertNull(UpdateCheck.parse(base.replace("ASSET", """{"name":"a.apk","browser_download_url":"https://x/a.apk"}"""))!!.apkUrl)
    }

    @Test
    fun the_download_must_match_the_published_digest() {
        val bytes = "apk".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertTrue(UpdateCheck.matches(java.io.ByteArrayInputStream(bytes), sha))
        assertTrue(UpdateCheck.matches(java.io.ByteArrayInputStream(bytes), sha.uppercase()))
        assertFalse(UpdateCheck.matches(java.io.ByteArrayInputStream(bytes), "00"))
    }

    @Test
    fun versions_compare_number_by_number() {
        assertTrue(UpdateCheck.isNewer("1.5.0", "1.4.0"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.4.0", "1.4.0"))
        assertFalse(UpdateCheck.isNewer("1.3.9", "1.4.0"))
        assertFalse(UpdateCheck.isNewer("abc", "1.4.0"))
    }

    @Test
    fun checks_at_most_every_twelve_hours() {
        assertTrue(UpdateCheck.isDue(lastCheck = null, now = 0L))
        assertFalse(UpdateCheck.isDue(lastCheck = 0L, now = 11 * 3_600_000L))
        assertTrue(UpdateCheck.isDue(lastCheck = 0L, now = 12 * 3_600_000L))
    }
}
