package dev.javohir.podcast.data.scanner

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val durationMs: Long?,
    val artworkCachePath: String?
)

@Singleton
class AudioMetadataReader @Inject constructor(@ApplicationContext private val context: Context) {

    private val artworkDir: File by lazy {
        File(context.filesDir, "artwork").also { it.mkdirs() }
    }

    fun read(uri: Uri, artworkKey: String?): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull()

            val artworkPath = if (artworkKey != null) {
                try {
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        val file = File(artworkDir, "$artworkKey.jpg")
                        FileOutputStream(file).use { it.write(art) }
                        Uri.fromFile(file).toString()
                    } else null
                } catch (_: Exception) { null }
            } else null

            AudioMetadata(
                title = title,
                artist = artist,
                durationMs = durationMs,
                artworkCachePath = artworkPath
            )
        } catch (_: Exception) {
            AudioMetadata(null, null, null, null)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
