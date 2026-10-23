package dev.javohir.podcast.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.javohir.podcast.data.db.dao.FolderDao
import dev.javohir.podcast.data.db.entities.WatchedFolder
import dev.javohir.podcast.data.scanner.FolderScanner
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderDao: FolderDao,
    private val folderScanner: FolderScanner
) {
    companion object {
        val SCAN_THROTTLE_MS = TimeUnit.MINUTES.toMillis(15)
    }

    fun observeAll(): Flow<List<WatchedFolder>> = folderDao.observeAll()

    suspend fun getById(id: Long): WatchedFolder? = folderDao.getById(id)

    suspend fun addFolder(treeUri: Uri, displayName: String): Long {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val folder = WatchedFolder(treeUri = treeUri.toString(), displayName = displayName)
        val id = folderDao.insert(folder)
        if (id > 0) {
            scanFolder(id, force = true)
        }
        return id
    }

    suspend fun scanFolder(folderId: Long, force: Boolean = false) {
        val folder = folderDao.getById(folderId) ?: return
        val now = System.currentTimeMillis()
        if (!force && folder.lastScanAt != null && now - folder.lastScanAt < SCAN_THROTTLE_MS) return
        folderScanner.scanFolder(folder)
    }

    suspend fun scanAll(force: Boolean = false) {
        for (folder in folderDao.getAll()) {
            scanFolder(folder.id, force)
        }
    }

    suspend fun removeFolder(folderId: Long) {
        val folder = folderDao.getById(folderId) ?: return
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}
        folderDao.delete(folderId)
    }
}
