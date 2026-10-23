package dev.javohir.podcast.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies a finished cache file into Music/Podcasts and returns a path that can be opened later.
 * The MediaStore row is published with IS_PENDING cleared. A row that cannot be reopened is deleted.
 */
@Singleton
class AudioFilePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileStore: DownloadFileStore
) {
    private companion object {
        const val COPY_BUFFER = 1024 * 1024
    }
    fun publish(cacheFile: File, displayName: String, mimeType: String): String? {
        if (!cacheFile.exists() || cacheFile.length() <= 0L) return null
        val published = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(cacheFile, displayName, mimeType)
        } else {
            publishToPublicMusic(cacheFile, displayName)
        }
        if (published != null && !fileStore.exists(published)) {
            fileStore.delete(published)
            return null
        }
        return published
    }

    private fun publishToMediaStore(cacheFile: File, displayName: String, mimeType: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Podcasts")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                cacheFile.inputStream().use { input -> input.copyTo(out, COPY_BUFFER) }
            } ?: throw IllegalStateException("Could not open the download destination")
            val finish = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, finish, null, null)
            uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun publishToPublicMusic(cacheFile: File, displayName: String): String? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Podcasts"
        ).also { it.mkdirs() }
        val dest = File(dir, displayName)
        cacheFile.inputStream().use { input ->
            dest.outputStream().use { out -> input.copyTo(out, COPY_BUFFER) }
        }
        return dest.absolutePath.takeIf { dest.exists() && dest.length() > 0L }
    }
}
