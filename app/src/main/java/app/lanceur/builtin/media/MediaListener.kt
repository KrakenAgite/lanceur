package app.lanceur.builtin.media

import android.service.notification.NotificationListenerService

/**
 * Ne lit aucune notification : sa seule présence, autorisée par l'utilisateur, ouvre l'accès aux lecteurs
 * (`MediaSessionManager.getActiveSessions`).
 */
class MediaListener : NotificationListenerService()
