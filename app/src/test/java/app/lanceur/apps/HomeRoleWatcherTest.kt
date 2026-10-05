package app.lanceur.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRoleWatcherTest {
    @Test
    fun reloads_once_when_the_launcher_becomes_the_default_home() {
        var reloads = 0
        val watcher = HomeRoleWatcher { reloads++ }
        watcher.update(false)
        watcher.update(true)
        watcher.update(true)
        assertEquals(1, reloads)
    }

    @Test
    fun already_default_at_start_needs_no_reload() {
        var reloads = 0
        val watcher = HomeRoleWatcher { reloads++ }
        watcher.update(true)
        assertEquals(0, reloads)
    }

    @Test
    fun losing_then_regaining_the_role_reloads_again() {
        var reloads = 0
        val watcher = HomeRoleWatcher { reloads++ }
        watcher.update(false)
        watcher.update(true)
        watcher.update(false)
        watcher.update(true)
        assertEquals(2, reloads)
    }
}
