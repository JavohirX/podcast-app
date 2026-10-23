package dev.javohir.podcast.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.javohir.podcast.domain.RefreshFeedsUseCase

@HiltWorker
class RefreshFeedsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val refreshFeedsUseCase: RefreshFeedsUseCase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            refreshFeedsUseCase()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
