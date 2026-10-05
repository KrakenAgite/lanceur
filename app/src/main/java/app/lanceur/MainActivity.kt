package app.lanceur

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.lanceur.home.LauncherViewModel
import app.lanceur.search.SearchPermissions
import app.lanceur.search.SearchViewModel
import app.lanceur.ui.theme.LanceurTheme
import app.lanceur.vault.VaultEvent

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as LanceurApp).container }

    private val launcherVm: LauncherViewModel by viewModels { LauncherViewModel.factory(container) }

    private val searchVm: SearchViewModel by viewModels {
        viewModelFactory {
            initializer {
                // Copies locales : les lambdas gardées par le ViewModel ne retiennent pas l'activité
                val launcher = launcherVm
                val appContext = applicationContext
                SearchViewModel(
                    container.searchEngine(
                        visibleApps = { launcher.lists.value.allVisible },
                        showPermissionHint = {
                            !launcher.prefs.value.permissionHintDismissed && !SearchPermissions.allGranted(appContext)
                        },
                    ),
                )
            }
        }
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = launcherVm.vaultEvent(VaultEvent.ScreenOff)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        setContent {
            LanceurTheme { AppRoot(launcherVm, searchVm, container) }
        }
    }

    /** Bouton Accueil (ou geste) alors que Lanceur est déjà lancé. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) launcherVm.goHome()
    }

    override fun onResume() {
        super.onResume()
        launcherVm.vaultEvent(VaultEvent.Resumed)
    }

    override fun onStop() {
        super.onStop()
        launcherVm.vaultEvent(VaultEvent.Backgrounded)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }
}
