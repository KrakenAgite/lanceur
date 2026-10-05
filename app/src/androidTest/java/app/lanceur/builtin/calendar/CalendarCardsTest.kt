package app.lanceur.builtin.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarCardsTest {
    @get:Rule val rule = createComposeRule()

    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(paris).toInstant().toEpochMilli()
    private val dentiste = SummaryEvent(1, "Dentiste", at(14, 14), at(14, 15), false, 0xFF3366CC.toInt())
    private val sport = SummaryEvent(2, "Sport", at(7, 18), at(7, 19), false)
    private val load = CalendarLoad(true, listOf(dentiste, sport))

    @Test
    fun month_shows_the_title_and_a_tapped_day_lists_its_events() {
        var shown: YearMonth? = null
        var opened: SummaryEvent? = null
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(400.dp)) {
                    MonthCard(today, WidgetSize.MEDIUM, load, onMonthShown = { shown = it }, actions = CalendarCardActions(openEvent = { opened = it }), zone = paris)
                }
            }
        }
        rule.onNodeWithText("octobre 2026").assertIsDisplayed()
        rule.onNodeWithTag("day-2026-10-14").performClick()
        rule.onNodeWithText("Dentiste").performClick()
        assertEquals(dentiste, opened)
        rule.onNodeWithContentDescription("Mois suivant").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("novembre 2026").assertIsDisplayed()
        assertEquals(YearMonth.of(2026, 11), shown)
    }

    @Test
    fun month_without_permission_offers_to_allow() {
        var asked = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(400.dp)) {
                    MonthCard(today, WidgetSize.MEDIUM, CalendarLoad(false, emptyList()), {}, CalendarCardActions(requestCalendar = { asked = true }), zone = paris)
                }
            }
        }
        rule.onNodeWithText("Autoriser l'agenda").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun week_shows_title_events_and_opens_a_day() {
        var day: LocalDate? = null
        var week: LocalDate? = null
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(200.dp)) {
                    WeekCard(today, today, WidgetSize.MEDIUM, load, onWeekChange = { week = it }, actions = CalendarCardActions(openDay = { day = it }), zone = paris)
                }
            }
        }
        rule.onNodeWithText("5 – 11 octobre").assertIsDisplayed()
        rule.onNodeWithText("Sport").assertIsDisplayed()
        rule.onNodeWithTag("week-2026-10-07").performClick()
        assertEquals(LocalDate.of(2026, 10, 7), day)
        rule.onNodeWithContentDescription("Semaine suivante").performClick()
        assertEquals(LocalDate.of(2026, 10, 12), week)
    }
}
