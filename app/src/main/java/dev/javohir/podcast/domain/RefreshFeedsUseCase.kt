package dev.javohir.podcast.domain

import dev.javohir.podcast.data.db.dao.FeedDao
import dev.javohir.podcast.data.repository.FeedRepository
import dev.javohir.podcast.data.repository.RefreshResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class RefreshFeedsUseCase @Inject constructor(
    private val feedRepository: FeedRepository,
    private val feedDao: FeedDao
) {
    suspend operator fun invoke(): Map<Long, RefreshResult> = coroutineScope {
        val feeds = feedDao.observeAll().first()
        feeds.map { feed ->
            async { feed.id to feedRepository.refresh(feed.id) }
        }.awaitAll().toMap()
    }
}
