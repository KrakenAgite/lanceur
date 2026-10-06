package app.lanceur.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockServiceSettingTest {
    private val pkg = "app.lanceur"
    private val cls = "app.lanceur.lock.LockScreenService"

    @Test
    fun the_saved_choice_counts_even_before_the_service_is_restarted() {
        assertTrue(LockServiceSetting.isEnabledIn("com.other/.Svc:app.lanceur/app.lanceur.lock.LockScreenService", pkg, cls))
        assertTrue(LockServiceSetting.isEnabledIn("app.lanceur/.lock.LockScreenService", pkg, cls))
        assertTrue(LockServiceSetting.isEnabledIn(" APP.LANCEUR/.lock.LockScreenService ", pkg, cls))
    }

    @Test
    fun other_services_or_nothing_mean_off() {
        assertFalse(LockServiceSetting.isEnabledIn(null, pkg, cls))
        assertFalse(LockServiceSetting.isEnabledIn("", pkg, cls))
        assertFalse(LockServiceSetting.isEnabledIn("com.other/.Svc", pkg, cls))
        assertFalse(LockServiceSetting.isEnabledIn("app.lanceur.debug/app.lanceur.lock.LockScreenService", pkg, cls))
    }
}
