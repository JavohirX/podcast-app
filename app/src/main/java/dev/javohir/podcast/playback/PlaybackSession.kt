package dev.javohir.podcast.playback

import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets a download deletion stop the episode that is currently loaded, including from a worker thread.
 * The playback service registers the stopper and clears it when it is destroyed.
 */
@Singleton
class PlaybackSession @Inject constructor() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var stopper: ((Long) -> Unit)? = null

    fun register(stopper: (Long) -> Unit) {
        this.stopper = stopper
    }

    fun unregister(stopper: (Long) -> Unit) {
        if (this.stopper === stopper) this.stopper = null
    }

    fun stopIfCurrent(episodeId: Long) {
        val action = stopper ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) action(episodeId)
        else main.post { action(episodeId) }
    }
}
