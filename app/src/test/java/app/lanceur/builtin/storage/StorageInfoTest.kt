package app.lanceur.builtin.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageInfoTest {
    @Test
    fun texts_in_gigabytes() {
        assertEquals("87 / 128 Go", StorageInfo.text(Gauge(87_000_000_000, 128_000_000_000), decimals = 0))
        assertEquals("7,1 / 12,0 Go", StorageInfo.text(Gauge(7_100_000_000, 12_000_000_000), decimals = 1))
    }

    @Test
    fun warning_above_ninety_percent() {
        assertTrue(Gauge(91, 100).warning)
        assertFalse(Gauge(90, 100).warning)
        assertEquals(0f, Gauge(5, 0).fraction, 0f)
    }
}
