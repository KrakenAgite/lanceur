package app.lanceur.builtin.media

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Lecteurs actifs (Spotify, YouTube Music…) et commandes ⏮ ⏯ ⏭ sur celui que la carte montre. */
class NowPlayingSource(private val context: Context) {
    private val component = ComponentName(context, MediaListener::class.java)
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    @Volatile private var current: MediaController? = null

    fun hasAccess(): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)

    val state: Flow<NowPlayingState> = callbackFlow {
        if (!hasAccess()) {
            trySend(NowPlayingState.NoAccess)
            awaitClose { }
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())
        val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
        var controllers = emptyList<MediaController>()

        fun publish() {
            val state = NowPlaying.pick(controllers.map(::snapshot))
            current = (state as? NowPlayingState.Active)?.let { active -> controllers.firstOrNull { it.packageName == active.media.packageName } }
            trySend(state)
        }

        fun watch(list: List<MediaController>?) {
            callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            callbacks.clear()
            controllers = list.orEmpty()
            controllers.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                }
                controller.registerCallback(callback, handler)
                callbacks[controller] = callback
            }
            publish()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }
        try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            watch(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            // Accès retiré entre la vérification et l'appel
            Log.w("Lanceur", "Accès aux lecteurs refusé", e)
            trySend(NowPlayingState.NoAccess)
        }
        awaitClose {
            runCatching { manager.removeOnActiveSessionsChangedListener(listener) }
            callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
            current = null
        }
    }.conflate()

    fun playPause() {
        val controller = current ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
    }

    fun next() { current?.transportControls?.skipToNext() }

    fun previous() { current?.transportControls?.skipToPrevious() }

    fun seekTo(ms: Long) { current?.transportControls?.seekTo(ms) }

    /** Intent de l'appli qui joue, ou `null`. */
    fun openIntent(): Intent? = current?.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())

    private fun snapshot(controller: MediaController): MediaSnapshot {
        val metadata = controller.metadata
        val playback = controller.playbackState
        val actions = playback?.actions ?: 0L
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.description?.iconBitmap
        return MediaSnapshot(
            packageName = controller.packageName,
            appLabel = appLabel(controller.packageName),
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: metadata?.description?.title?.toString(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.description?.subtitle?.toString(),
            art = art?.asImageBitmap(),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            positionMs = playback?.position ?: 0L,
            updatedAtElapsed = playback?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed ?: 0f,
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING || playback?.state == PlaybackState.STATE_BUFFERING,
            canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
    }

    private fun appLabel(packageName: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
