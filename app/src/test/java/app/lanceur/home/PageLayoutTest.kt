package app.lanceur.home

import app.lanceur.home.PageKind.HOME
import app.lanceur.home.PageKind.NEWS
import app.lanceur.home.PageKind.WIDGETS
import org.junit.Assert.assertEquals
import org.junit.Test

class PageLayoutTest {
    @Test
    fun active_pages_follow_the_order_and_home_is_always_there() {
        assertEquals(listOf(WIDGETS, HOME), PageLayout.active(PageLayout.DEFAULT_ORDER, widgetsEnabled = true, newsEnabled = false))
        assertEquals(listOf(NEWS, WIDGETS, HOME), PageLayout.active(PageLayout.DEFAULT_ORDER, widgetsEnabled = true, newsEnabled = true))
        assertEquals(listOf(WIDGETS, HOME, NEWS), PageLayout.active(listOf(WIDGETS, HOME, NEWS), widgetsEnabled = true, newsEnabled = true))
        assertEquals(listOf(HOME), PageLayout.active(PageLayout.DEFAULT_ORDER, widgetsEnabled = false, newsEnabled = false))
    }

    @Test
    fun broken_orders_are_repaired() {
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.normalize(emptyList()))
        assertEquals(listOf(HOME, NEWS, WIDGETS), PageLayout.normalize(listOf(HOME, HOME, NEWS)))
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.decode(null))
        assertEquals(listOf(WIDGETS, HOME, NEWS), PageLayout.decode(PageLayout.encode(listOf(WIDGETS, HOME, NEWS))))
        assertEquals(listOf(HOME, NEWS, WIDGETS), PageLayout.decode("HOME,PLANETE,NEWS"))
    }

    @Test
    fun move_shifts_one_place_within_bounds() {
        assertEquals(listOf(NEWS, HOME, WIDGETS), PageLayout.move(PageLayout.DEFAULT_ORDER, HOME, -1))
        assertEquals(listOf(WIDGETS, NEWS, HOME), PageLayout.move(PageLayout.DEFAULT_ORDER, NEWS, +1))
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.move(PageLayout.DEFAULT_ORDER, NEWS, -1))
        assertEquals(PageLayout.DEFAULT_ORDER, PageLayout.move(PageLayout.DEFAULT_ORDER, HOME, +1))
    }
}
