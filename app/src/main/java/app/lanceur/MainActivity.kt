package app.lanceur

import android.graphics.Color
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.lanceur.home.LauncherViewModel
import app.lanceur.search.SearchPermissions
import app.lanceur.search.SearchViewModel
import app.lanceur.ui.theme.LanceurTheme
import app.lanceur.vault.VaultEvent
import app.lanceur.widgets.ProviderEntry
import app.lanceur.widgets.WidgetAddFlow
import app.lanceur.widgets.WidgetSlot

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

    /** Fenêtre d'Android « Autoriser Lanceur à créer des widgets… ». */
    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = launcherVm.widgetAddState as? WidgetAddFlow.State.Binding
        val granted = result.resultCode == RESULT_OK
        val needsConfiguration = granted && pending != null && container.widgetHost.needsConfiguration(pending.id)
        runWidgetEvent(WidgetAddFlow.Event.BindResult(granted, needsConfiguration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barre des 3 boutons sans voile : le fond d'écran continue jusqu'en bas, comme sur l'accueil du Pixel
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))
        window.isNavigationBarContrastEnforced = false
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        setContent {
            LanceurTheme {
                AppRoot(launcherVm, searchVm, container, WidgetHostActions(add = ::addWidget, reconfigure = ::reconfigureWidget))
            }
        }
    }

    /** Bouton Accueil (ou geste) alors que Lanceur est déjà lancé. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) launcherVm.goHome()
    }

    override fun onStart() {
        super.onStart()
        if (launcherVm.prefs.value.widgetPageEnabled) container.widgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()
        launcherVm.vaultEvent(VaultEvent.Resumed)
    }

    override fun onStop() {
        super.onStop()
        container.widgetHost.stopListening()
        launcherVm.vaultEvent(VaultEvent.Backgrounded)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    private fun addWidget(entry: ProviderEntry) {
        val host = container.widgetHost
        val id = host.allocateId()
        val bound = host.bindIfAllowed(id, entry.provider)
        runWidgetEvent(WidgetAddFlow.Event.Start(id, entry, bound, bound && host.needsConfiguration(id)))
    }

    private fun reconfigureWidget(slot: WidgetSlot) {
        container.widgetHost.startConfiguration(this, slot.appWidgetId, REQUEST_RECONFIGURE)
    }

    private fun runWidgetEvent(event: WidgetAddFlow.Event) {
        val (next, effects) = WidgetAddFlow.reduce(launcherVm.widgetAddState, event)
        launcherVm.widgetAddState = next
        effects.forEach(::runWidgetEffect)
    }

    private fun runWidgetEffect(effect: WidgetAddFlow.Effect) {
        val host = container.widgetHost
        when (effect) {
            is WidgetAddFlow.Effect.LaunchBind -> bindLauncher.launch(host.bindIntent(effect.id, effect.provider))
            is WidgetAddFlow.Effect.LaunchConfigure -> {
                if (!host.startConfiguration(this, effect.id, REQUEST_CONFIGURE)) runWidgetEvent(WidgetAddFlow.Event.ConfigureResult(false))
            }
            // Un identifiant qu'Android ne connaît plus (Lanceur relancé entre-temps) n'est jamais enregistré
            is WidgetAddFlow.Effect.Save -> {
                if (host.info(effect.slot.appWidgetId) != null) launcherVm.addWidget(effect.slot) else host.deleteId(effect.slot.appWidgetId)
            }
            is WidgetAddFlow.Effect.Delete -> host.deleteId(effect.id)
        }
    }

    /** `startAppWidgetConfigureActivityForResult` ne renvoie son résultat que par cette méthode. */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) runWidgetEvent(WidgetAddFlow.Event.ConfigureResult(resultCode == RESULT_OK))
    }

    private companion object {
        const val REQUEST_CONFIGURE = 41
        const val REQUEST_RECONFIGURE = 42
    }
}
