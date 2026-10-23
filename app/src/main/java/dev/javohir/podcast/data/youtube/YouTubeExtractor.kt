package dev.javohir.podcast.data.youtube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeExtractor @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val initialized = AtomicBoolean(false)

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(OkHttpDownloader.create(okHttpClient), Localization("en"))
        }
    }

    suspend fun getBestAudioStreamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        ensureInit()
        var lastException: Exception? = null
        
        repeat(3) { attempt ->
            try {
                // Use withTimeout to prevent infinite hang
                return@withContext withTimeout(45_000) {
                    val service = ServiceList.YouTube
                    val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                    extractor.fetchPage()
                    
                    if (extractor.audioStreams.isEmpty()) {
                        throw IllegalStateException("No audio streams found for this video. It might be age-restricted, private, or blocked by YouTube.")
                    }

                    // Prefer m4a/aac streams for better compatibility
                    val bestAudio = extractor.audioStreams
                        .filter { 
                            val formatName = it.format?.name?.lowercase() ?: ""
                            formatName.contains("m4a") || formatName.contains("aac")
                        }
                        .maxByOrNull { it.averageBitrate }
                        ?: extractor.audioStreams.maxByOrNull { it.averageBitrate }

                    bestAudio?.content ?: throw IllegalStateException("Could not find a valid content URL in the audio streams.")
                }
            } catch (e: Exception) {
                lastException = e
                val message = e.message ?: ""
                Log.e("YouTubeExtractor", "Extraction failed (attempt ${attempt + 1}): $message")
                if (message.contains("reloaded") || message.contains("s4.b")) {
                    // Wait a bit before retrying
                    delay(1000L * (attempt + 1))
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: IllegalStateException("Extraction failed after multiple attempts")
    }
}
