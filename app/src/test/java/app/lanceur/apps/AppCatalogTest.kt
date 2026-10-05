package app.lanceur.apps

import app.lanceur.app
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppCatalogTest {
    private val chrome = app("Chrome")
    private val maps = app("Maps")

    /** Chaque appel à `snapshot` consomme la réponse suivante. */
    private class FakeSource(vararg answers: suspend () -> List<ProfileSnapshot>) : CatalogSource {
        private val queue = ArrayDeque(answers.toList())
        override fun start(onChange: (changedPackage: String?) -> Unit) = Unit
        override suspend fun snapshot(): List<ProfileSnapshot> = queue.removeFirst()()
        override fun setPrivateSpaceLocked(locked: Boolean) = false
    }

    private fun main(vararg apps: AppEntry) = listOf(ProfileSnapshot(ProfileKind.MAIN, false, apps.toList()))

    @Test
    fun a_slow_older_load_never_overwrites_a_newer_one() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource({ gate.await(); main(chrome) }, { main(chrome, maps) })
        val catalog = AppCatalog(this, source, dispatcher = StandardTestDispatcher(testScheduler))
        catalog.reload()
        catalog.reload()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(chrome, maps), catalog.apps.value)
    }

    @Test
    fun a_failed_load_keeps_the_previous_list() = runTest {
        val errors = mutableListOf<Throwable>()
        val source = FakeSource({ main(chrome) }, { error("service système indisponible") })
        val catalog = AppCatalog(
            this,
            source,
            dispatcher = StandardTestDispatcher(testScheduler),
            onError = { errors += it },
        )
        catalog.reload()
        advanceUntilIdle()
        catalog.reload()
        advanceUntilIdle()
        assertEquals(listOf(chrome), catalog.apps.value)
        assertEquals(1, errors.size)
    }
}
