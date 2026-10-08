package app.lanceur.search

import app.lanceur.i18n.L10n
import app.lanceur.i18n.Lang
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConverterTest {
    // Mercredi 7 octobre 2026, 10:00 à Paris (UTC+2)
    private val now = ZonedDateTime.of(2026, 10, 7, 10, 0, 0, 0, ZoneId.of("Europe/Paris"))

    @After fun backToFrench() { L10n.lang = Lang.FR }

    private fun value(q: String) = Converter.convert(q, now)?.value

    @Test
    fun converts_units_with_or_without_a_target() {
        assertEquals("8,047 km", value("5 miles en km"))
        assertEquals("8,047 km", value("5mi to km"))
        assertEquals("3,107 mi", value("5 km"))
        assertEquals("2,205 lb", value("1 kg"))
        assertEquals("180,3 cm", value("71 pouces en cm"))
        assertEquals("1,5 L", value("1500 ml en l"))
        assertEquals("2000 Mo", value("2 Go en Mo"))
        assertEquals("62,14 mph", value("100 km/h"))
        assertEquals("300 min", value("5 h en min"))
        assertEquals("2,5 ha", value("25000 m2 en ha"))
        assertEquals("709,8 ml", value("3 tasses en ml"))
        assertEquals("118,3 ml", value("4 fl oz"))
    }

    @Test
    fun converts_temperatures_both_ways() {
        assertEquals("21,11 °C", value("70°F"))
        assertEquals("21,11 °C", value("70 f en c"))
        assertEquals("98,6 °F", value("37 °C"))
        assertEquals("-40 °F", value("-40 c"))
        assertEquals("0 °C", value("273,15 K"))
    }

    @Test
    fun ignores_what_is_not_a_conversion() {
        assertNull(value("chrome"))
        assertNull(value("5g network"))
        assertNull(value("12*8"))
        assertNull(value("5 km en kg"))
        assertNull(value("5 km en km"))
        assertNull(value("2 s"))
        assertNull(value("15h"))
        assertNull(value("15h à Atlantis"))
    }

    @Test
    fun converts_a_time_here_to_another_city_and_tells_the_gap() {
        val tokyo = Converter.convert("15h à Tokyo", now)!!
        assertEquals("22:00", tokyo.value)
        assertEquals("15:00 ici → Tokyo (+7 h)", tokyo.detail)
        assertEquals("02:30 (demain)", value("19h30 tokyo"))
        assertEquals("19:00 (la veille)", value("1h New York"))
        assertEquals("19:00", value("19:00 à Paris"))
        assertEquals("19:00 ici → Paris (même heure)", Converter.convert("19:00 à Paris", now)!!.detail)
    }

    @Test
    fun tells_the_current_time_in_a_city() {
        val c = Converter.convert("heure à New York", now)!!
        assertEquals("04:00", c.value)
        assertEquals("Maintenant à New York (−6 h)", c.detail)
        assertEquals("04:00", value("new york time"))
    }

    @Test
    fun english_symbols_follow_the_language() = runTest {
        L10n.lang = Lang.EN
        assertEquals(listOf(SearchResult.Convert("2 GB → MB", "2000 MB")), ConvertProvider { now }.search("2 gb in mb"))
        assertEquals("15:00 here → Tokyo (+7 h)", Converter.convert("15h in Tokyo", now)!!.detail)
    }
}
