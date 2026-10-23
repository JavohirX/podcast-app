package dev.javohir.podcast.data.download

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadFileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun cacheFile(episodeId: Long): File =
        File(context.cacheDir, "downloads/$episodeId.part")

    fun exists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(path)
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 0L } == true
            } else {
                val file = File(path)
                file.exists() && file.length() > 0L
            }
        } catch (_: Exception) {
            false
        }
    }

    fun size(path: String?): Long {
        if (path.isNullOrBlank()) return 0L
        return try {
            val uri = Uri.parse(path)
            if (uri.scheme == "content") {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { desc ->
                    if (desc.statSize > 0L) desc.statSize
                    else querySize(uri)
                } ?: 0L
            } else {
                val file = File(path)
                if (file.exists()) file.length() else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun delete(path: String?): FileDeleteResult {
        if (path.isNullOrBlank()) return FileDeleteResult.AlreadyGone
        return try {
            val existed = exists(path)
            if (!existed) return FileDeleteResult.AlreadyGone
            val succeeded = performDelete(path)
            interpretFileDelete(existed = true, deleteSucceeded = succeeded)
        } catch (e: Exception) {
            if (e is FileNotFoundException || e.cause is FileNotFoundException) {
                FileDeleteResult.AlreadyGone
            } else {
                FileDeleteResult.Failed
            }
        }
    }

    fun deleteCache(episodeId: Long) {
        val file = cacheFile(episodeId)
        if (file.exists()) file.delete()
        File(file.parentFile, file.name + ".parallel").delete()
    }

    private fun performDelete(path: String): Boolean {
        val uri = Uri.parse(path)
        return if (uri.scheme == "content") {
            context.contentResolver.delete(uri, null, null) > 0 || !exists(path)
        } else {
            val file = File(path)
            file.delete() || !file.exists()
        }
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        return 0L
    }
}
