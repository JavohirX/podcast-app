package dev.javohir.podcast.data.repository

import dev.javohir.podcast.data.db.dao.EpisodeDao
import dev.javohir.podcast.data.db.dao.FeedDao
import dev.javohir.podcast.data.db.entities.DownloadStatus
import dev.javohir.podcast.data.db.entities.Episode
import dev.javohir.podcast.data.db.entities.Feed
import dev.javohir.podcast.data.db.entities.SourceType
import dev.javohir.podcast.data.rss.FetchOutcome
import dev.javohir.podcast.data.rss.RssClient
import dev.javohir.podcast.data.rss.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class RefreshResult {
    data class Success(val newEpisodes: Int) : RefreshResult()
    data class Error(val message: String) : RefreshResult()
    object NotModified : RefreshResult()
}

@Singleton
class FeedRepository @Inject constructor(
    private val feedDao: FeedDao,
    private val episodeDao: EpisodeDao,
    private val rssClient: RssClient,
    private val rssParser: RssParser
) {

    fun observeAll(): Flow<List<Feed>> = feedDao.observeAll()

    suspend fun getById(id: Long): Feed? = feedDao.getById(id)

    suspend fun subscribe(url: String): Long? = withContext(Dispatchers.IO) {
        val existing = feedDao.getByUrl(url)
        if (existing != null) return@withContext existing.id

        val outcome = rssClient.fetch(url)
        if (outcome !is FetchOutcome.Success) return@withContext null

        val parsed = rssParser.parse(outcome.result.body) ?: return@withContext null

        val feed = Feed(
            url = outcome.result.finalUrl,
            title = parsed.title,
            description = parsed.description,
            author = parsed.author,
            imageUrl = parsed.imageUrl,
            link = parsed.link,
            etag = outcome.result.etag,
            lastModified = outcome.result.lastModified,
            lastFetchAt = System.currentTimeMillis()
        )
        val feedId = feedDao.insert(feed)
        if (feedId <= 0) return@withContext null

        val now = System.currentTimeMillis()
        for (item in parsed.items) {
            val guid = item.guid ?: continue
            val episode = Episode(
                sourceType = SourceType.RSS,
                feedId = feedId,
                guid = guid,
                title = item.title ?: "Untitled",
                description = item.description,
                author = item.author,
                publishedAt = item.pubDate ?: now,
                durationMs = item.durationMs,
                artworkUrl = item.imageUrl ?: parsed.imageUrl,
                enclosureUrl = item.enclosureUrl,
                enclosureSize = item.enclosureSize,
                downloadStatus = DownloadStatus.NONE
            )
            episodeDao.insert(episode)
        }

        feedId
    }

    suspend fun refresh(feedId: Long): RefreshResult = withContext(Dispatchers.IO) {
        val feed = feedDao.getById(feedId) ?: return@withContext RefreshResult.Error("Feed not found")

        val outcome = rssClient.fetch(feed.url, feed.etag, feed.lastModified)
        return@withContext when (outcome) {
            is FetchOutcome.NotModified -> {
                feedDao.update(feed.copy(lastFetchAt = System.currentTimeMillis(), lastFetchError = null))
                RefreshResult.NotModified
            }
            is FetchOutcome.Error -> {
                feedDao.update(feed.copy(lastFetchError = outcome.message))
                RefreshResult.Error(outcome.message)
            }
            is FetchOutcome.Success -> {
                val parsed = rssParser.parse(outcome.result.body)
                    ?: return@withContext RefreshResult.Error("Failed to parse feed")

                val finalUrl = outcome.result.finalUrl
                feedDao.update(
                    feed.copy(
                        url = finalUrl,
                        title = parsed.title,
                        description = parsed.description,
                        author = parsed.author,
                        imageUrl = parsed.imageUrl,
                        link = parsed.link,
                        etag = outcome.result.etag,
                        lastModified = outcome.result.lastModified,
                        lastFetchAt = System.currentTimeMillis(),
                        lastFetchError = null
                    )
                )

                val now = System.currentTimeMillis()
                var newCount = 0
                val seenGuids = mutableSetOf<String>()

                for (item in parsed.items) {
                    val guid = item.guid ?: continue
                    if (!seenGuids.add(guid)) continue  // dedupe within doc

                    val existing = episodeDao.getByGuid(feedId, guid)
                    if (existing == null) {
                        episodeDao.insert(
                            Episode(
                                sourceType = SourceType.RSS,
                                feedId = feedId,
                                guid = guid,
                                title = item.title ?: "Untitled",
                                description = item.description,
                                author = item.author,
                                publishedAt = item.pubDate ?: now,
                                durationMs = item.durationMs,
                                artworkUrl = item.imageUrl ?: parsed.imageUrl,
                                enclosureUrl = item.enclosureUrl,
                                enclosureSize = item.enclosureSize,
                                downloadStatus = DownloadStatus.NONE
                            )
                        )
                        newCount++
                    } else {
                        // Update metadata but NEVER touch playback state
                        episodeDao.updateMetadata(
                            feedId = feedId,
                            guid = guid,
                            title = item.title ?: existing.title,
                            description = item.description,
                            author = item.author,
                            publishedAt = item.pubDate ?: existing.publishedAt,
                            durationMs = item.durationMs,
                            artworkUrl = item.imageUrl ?: parsed.imageUrl,
                            enclosureUrl = item.enclosureUrl,
                            enclosureSize = item.enclosureSize
                        )
                    }
                }
                RefreshResult.Success(newCount)
            }
        }
    }

    suspend fun unsubscribe(feedId: Long) {
        feedDao.delete(feedId)
    }
}
