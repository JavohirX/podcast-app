package dev.javohir.podcast.domain

import dev.javohir.podcast.data.download.DownloadCoordinator
import dev.javohir.podcast.data.repository.EpisodeRepository
import dev.javohir.podcast.data.settings.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class UpdateProgressUseCase @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val settingsStore: SettingsStore,
    private val downloadCoordinator: DownloadCoordinator
) {
    suspend operator fun invoke(episodeId: Long, positionMs: Long, durationMs: Long) {
        val pct = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        episodeRepository.updateProgress(episodeId, positionMs, pct)

        val threshold = settingsStore.settings.first().completionThreshold
        if (pct >= threshold) {
            episodeRepository.setListened(episodeId, true)

            val settings = settingsStore.settings.first()
            if (settings.autoDeleteListenedDownloads) {
                downloadCoordinator.deleteFile(episodeId)
            }
        }
    }
}
