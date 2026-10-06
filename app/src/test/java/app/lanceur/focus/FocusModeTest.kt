package app.lanceur.focus

import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusModeTest {
    private val zone = ZoneId.of("Europe/Paris")
    // Lundi 5 octobre 2026
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone)
    private val work = FocusSchedule(setOf(MONDAY, TUESDAY, FRIDAY), LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val night = FocusSchedule(setOf(MONDAY), LocalTime.of(22, 0), LocalTime.of(7, 0))

    private fun app(pkg: String) = AppEntry(AppKey(pkg, "$pkg.Main", 0L), pkg, false)
    private val apps = listOf(app("insta"), app("mail"), app("maps"), app("tiktok"))

    @Test
    fun off_by_default_and_on_when_started_by_hand() {
        assertFalse(FocusMode().isActive(at(5, 10)))
        assertTrue(FocusMode(manual = true).isActive(at(5, 10)))
    }

    @Test
    fun schedules_follow_days_and_hours_including_overnight() {
        val focus = FocusMode(schedules = listOf(work, night))
        assertTrue(focus.isActive(at(5, 9)))
        assertFalse(focus.isActive(at(5, 18)))
        assertFalse(focus.isActive(at(7, 10))) // mercredi
        assertTrue(focus.isActive(at(5, 23)))
        assertTrue(focus.isActive(at(6, 6, 59))) // la nuit de lundi déborde sur mardi
        assertFalse(focus.isActive(at(6, 7)))
        assertFalse(focus.isActive(at(10, 10))) // samedi
        assertEquals(at(5, 18), focus.activeUntil(at(5, 10)))
        assertEquals(at(6, 7), focus.activeUntil(at(5, 23)))
        assertNull(FocusMode(manual = true).activeUntil(at(5, 10)))
    }

    @Test
    fun stopping_during_a_schedule_pauses_it_until_its_end() {
        val focus = FocusMode(manual = true, schedules = listOf(work)).stop(at(5, 10))
        assertFalse(focus.manual)
        assertFalse(focus.isActive(at(5, 17)))
        assertTrue(focus.isActive(at(6, 9))) // le lendemain, la plage reprend
    }

    @Test
    fun hide_mode_removes_the_checked_apps_and_show_mode_keeps_only_them() {
        val hide = FocusMode(manual = true, filter = FocusFilter.HIDE, apps = setOf("insta" to 0L, "tiktok" to 0L))
        assertEquals(listOf("mail", "maps"), hide.visible(apps, at(5, 10)).map { it.key.packageName })
        val show = FocusMode(manual = true, filter = FocusFilter.SHOW_ONLY, apps = setOf("mail" to 0L))
        assertEquals(listOf("mail"), show.visible(apps, at(5, 10)).map { it.key.packageName })
        // Inactif : tout reste visible
        assertEquals(apps, show.copy(manual = false).visible(apps, at(5, 10)))
    }

    @Test
    fun encoding_round_trips_and_survives_garbage() {
        val focus = FocusMode(true, FocusFilter.SHOW_ONLY, setOf("mail" to 0L, "work.app" to 10L), listOf(work, night), pausedUntil = 123L)
        assertEquals(focus, FocusMode.decode(focus.encode()))
        assertEquals(FocusMode(), FocusMode.decode(null))
        assertEquals(FocusMode(), FocusMode.decode("n'importe quoi"))
    }

    @Test
    fun schedule_label_is_readable() {
        assertEquals("lun., mar., ven. · 09:00–18:00", work.label(java.util.Locale.FRENCH))
    }
}

class FocusVisibleAppsTest {
    private fun app(pkg: String) = AppEntry(AppKey(pkg, "$pkg.Main", 0L), pkg, false)

    @Test
    fun focus_filters_the_lists_but_not_the_apps_to_check() {
        val catalog = listOf(app("insta"), app("mail"), app("app.lanceur"))
        val prefs = app.lanceur.prefs.LauncherPrefs(
            favorites = listOf(AppKey("insta", "insta.Main", 0L), AppKey("mail", "mail.Main", 0L)),
            focus = FocusMode(manual = true, filter = FocusFilter.SHOW_ONLY, apps = setOf("mail" to 0L)),
        )
        val lists = app.lanceur.prefs.VisibleApps.compute(catalog, prefs, ZonedDateTime.now())
        assertTrue(lists.focusActive)
        assertEquals(listOf("mail"), lists.allVisible.map { it.key.packageName })
        assertEquals(listOf("mail"), lists.favorites.map { it.key.packageName })
        assertEquals(listOf("insta", "mail"), lists.focusCandidates.map { it.key.packageName })
    }
}
