package app.lanceur.builtin.media

import android.os.UserManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import app.lanceur.LanceurApp
import app.lanceur.focus.FocusMode
import app.lanceur.focus.FocusNotifications
import app.lanceur.home.minuteTicks
import app.lanceur.prefs.VisibleApps
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Accès aux notifications, autorisé par l'utilisateur. Il sert à deux choses :
 * - piloter les lecteurs (`MediaSessionManager.getActiveSessions`) ;
 * - pendant la concentration, mettre en attente les notifications des applis masquées (si l'option est choisie) ;
 * - savoir quelles applis ont des notifications, pour leur pastille ([NotificationBadges]).
 * Le contenu des notifications n'est jamais lu : seuls comptent l'appli, le profil et le type (appel, alarme…).
 */
class MediaListener : NotificationListenerService() {
    private var scope: CoroutineScope? = null

    @Volatile private var focus = FocusMode()
    @Volatile private var launchable: Set<Pair<String, Long>> = emptySet()

    override fun onListenerConnected() {
        val container = (application as LanceurApp).container
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { s ->
            s.launch {
                combine(container.prefsRepo.prefs, container.catalog.apps.filterNotNull(), minuteTicks()) { prefs, apps, now ->
                    Triple(prefs.focus, VisibleApps.compute(apps, prefs, now).focusCandidates, now)
                }.collect { (newFocus, candidates, now) ->
                    focus = newFocus
                    launchable = candidates.mapTo(HashSet()) { it.key.packageName to it.key.userSerial }
                    // Concentration qui commence : celles déjà affichées attendent aussi
                    sweep(now)
                }
            }
        }
        refreshBadges()
    }

    override fun onListenerDisconnected() {
        scope?.cancel()
        scope = null
        NotificationBadges.set(emptySet())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        snoozeIfHidden(sbn, ZonedDateTime.now())
        refreshBadges()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshBadges()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        refreshBadges()
    }

    /** Notifications qu'on peut effacer et dont le canal accepte les pastilles (pas la musique, pas les appels en cours). */
    private fun refreshBadges() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val users = getSystemService(UserManager::class.java)
        NotificationBadges.set(
            active.filter { sbn ->
                !sbn.isOngoing && ranking?.let { map -> Ranking().takeIf { map.getRanking(sbn.key, it) }?.canShowBadge() } != false
            }.mapTo(HashSet()) { sbn ->
                sbn.packageName to runCatching { users.getSerialNumberForUser(sbn.user) }.getOrDefault(0L)
            },
        )
    }

    private fun sweep(now: ZonedDateTime) {
        if (!focus.isActive(now) || !focus.blockNotifications) return
        runCatching { activeNotifications }.getOrNull()?.forEach { snoozeIfHidden(it, now) }
    }

    private fun snoozeIfHidden(sbn: StatusBarNotification, now: ZonedDateTime) {
        val serial = runCatching { getSystemService(UserManager::class.java).getSerialNumberForUser(sbn.user) }.getOrDefault(0L)
        val duration = FocusNotifications.snoozeFor(focus, sbn.packageName, serial, sbn.isOngoing, sbn.notification.category, launchable, now) ?: return
        runCatching { snoozeNotification(sbn.key, duration) }.onFailure { Log.w("Lanceur", "Mise en attente impossible", it) }
    }
}
