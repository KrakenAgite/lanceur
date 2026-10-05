package app.lanceur.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppKeyTest {
    @Test
    fun encode_then_decode_gives_the_same_key() {
        val key = AppKey("com.exemple", "com.exemple.Main", 10)
        assertEquals("com.exemple/com.exemple.Main#10", key.encode())
        assertEquals(key, AppKey.decode(key.encode()))
    }

    @Test
    fun decode_rejects_malformed_values() {
        listOf("", "com.a", "com.a/Main", "com.a/Main#", "/Main#0", "com.a/#0", "com.a/Main#x").forEach {
            assertNull(it, AppKey.decode(it))
        }
    }
}
