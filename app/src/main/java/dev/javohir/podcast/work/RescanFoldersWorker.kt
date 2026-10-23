package dev.javohir.podcast.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.javohir.podcast.data.repository.FolderRepository

@HiltWorker
class RescanFoldersWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val folderRepository: FolderRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            folderRepository.scanAll(force = false)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
