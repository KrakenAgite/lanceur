package app.lanceur.apps

import android.content.ComponentName
import android.content.Intent
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppLauncherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = AppLauncher(context)

    @Test
    fun an_activity_refused_by_the_system_does_not_crash_the_launcher() {
        // Activité non exportée d'une autre appli : Android répond par une SecurityException
        val intent = Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.SubSettings"))
        assertFalse(launcher.startSafely(intent))
    }

    @Test
    fun uninstall_targets_the_profile_of_the_app() {
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val intent = launcher.uninstallIntent(AppKey("com.exemple", "com.exemple.Main", serial))!!
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("com.exemple", intent.data?.schemeSpecificPart)
        assertEquals(Process.myUserHandle(), intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java))
    }
}
