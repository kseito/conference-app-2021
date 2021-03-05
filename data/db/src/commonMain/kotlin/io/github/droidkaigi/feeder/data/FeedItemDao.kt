package io.github.droidkaigi.feeder.data

import io.github.droidkaigi.feeder.Author
import io.github.droidkaigi.feeder.FeedItem
import io.github.droidkaigi.feeder.Image
import io.github.droidkaigi.feeder.Media
import io.github.droidkaigi.feeder.MultiLangText
import io.github.droidkaigi.feeder.Speaker
import kotlinx.datetime.Instant

interface FeedItemDao {
    fun selectAll(): List<FeedItem>
    fun insert(feeds: List<FeedItem>)
    fun deleteAll()
}

internal class FeedItemDaoImpl(database: Database) : FeedItemDao {
    private val blogQueries: FeedItemBlogQueries = database.feedItemBlogQueries
    private val podcastQueries: FeedItemPodcastQueries = database.feedItemPodcastQueries
    private val videoQueries: FeedItemVideoQueries = database.feedItemVideoQueries
    private val podcastSpeakerQueries: FeedItemPodcastSpeakerQueries =
        database.feedItemPodcastSpeakerQueries

    override fun selectAll(): List<FeedItem> {
        val blogFeeds = blogQueries.selectAll(blogQueriesMapper).executeAsList()
        val podcastFeeds = podcastQueries.selectAll().executeAsList().toPodcastItems()
        val videoFeeds = videoQueries.selectAll(videoQueriesMapper).executeAsList()

        return blogFeeds + podcastFeeds + videoFeeds
    }

    override fun insert(feeds: List<FeedItem>) {
        feeds.forEach { item ->
            when (item) {
                is FeedItem.Blog -> blogQueries.insert(item)
                is FeedItem.Podcast -> {
                    podcastQueries.insert(item)
                    item.speakers.forEach { speaker ->
                        podcastSpeakerQueries.insert(
                            item.id,
                            speaker
                        )
                    }
                }
                is FeedItem.Video -> videoQueries.insert(item)
            }
        }
    }

    override fun deleteAll() {
        blogQueries.deleteAll()
        podcastSpeakerQueries.deleteAll()
        podcastQueries.deleteAll()
        videoQueries.deleteAll()
    }

    companion object {
        private val blogQueriesMapper = {
            id: String,
            publishedAt: Long,
            imageSmallUrl: String,
            imageStandardUrl: String,
            imageLargeUrl: String,
            media: String,
            jaTitle: String,
            enTitle: String,
            jaSummary: String,
            enSummary: String,
            link: String,
            language: String,
            authorName: String,
            authorLink: String,
            ->
            FeedItem.Blog(
                id = id,
                publishedAt = Instant.fromEpochMilliseconds(publishedAt),
                image = Image(
                    smallUrl = imageSmallUrl, standardUrl = imageStandardUrl,
                    largeUrl =
                        imageLargeUrl
                ),
                media = Media.parse(media),
                title = MultiLangText(jaTitle = jaTitle, enTitle = enTitle),
                summary = MultiLangText(jaTitle = jaSummary, enTitle = enSummary),
                link = link,
                language = language,
                author = Author(name = authorName, link = authorLink)
            )
        }

        private val videoQueriesMapper = {
            id: String,
            publishedAt: Long,
            imageSmallUrl: String,
            imageStandardUrl: String,
            imageLargeUrl: String,
            media: String,
            jaTitle: String,
            enTitle: String,
            jaSummary: String,
            enSummary: String,
            link: String,
            ->
            FeedItem.Video(
                id = id,
                publishedAt = Instant.fromEpochMilliseconds(publishedAt),
                image = Image(
                    smallUrl = imageSmallUrl, standardUrl = imageStandardUrl,
                    largeUrl =
                        imageLargeUrl
                ),
                media = Media.parse(media),
                title = MultiLangText(jaTitle = jaTitle, enTitle = enTitle),
                summary = MultiLangText(jaTitle = jaSummary, enTitle = enSummary),
                link = link
            )
        }
    }
}

private fun FeedItemBlogQueries.insert(blog: FeedItem.Blog) {
    this.insert(
        FeedItemBlog(
            id = blog.id,
            publishedAt = blog.publishedAt.toEpochMilliseconds(),
            imageSmallUrl = blog.image.smallUrl,
            imageStandardUrl = blog.image.standardUrl,
            imageLargeUrl = blog.image.largeUrl,
            media = blog.media.text,
            jaTitle = blog.title.jaTitle,
            enTitle = blog.title.enTitle,
            jaSummary = blog.summary.jaTitle,
            enSummary = blog.summary.enTitle,
            link = blog.link,
            language = blog.language,
            authorName = blog.author.name,
            authorLink = blog.author.link,
        )
    )
}

private fun FeedItemPodcastQueries.insert(podcast: FeedItem.Podcast) {
    this.insert(
        FeedItemPodcast(
            id = podcast.id,
            publishedAt = podcast.publishedAt.toEpochMilliseconds(),
            imageSmallUrl = podcast.image.smallUrl,
            imageStandardUrl = podcast.image.standardUrl,
            imageLargeUrl = podcast.image.largeUrl,
            media = podcast.media.text,
            jaTitle = podcast.title.jaTitle,
            enTitle = podcast.title.enTitle,
            jaSummary = podcast.summary.jaTitle,
            enSummary = podcast.summary.enTitle,
            link = podcast.link,
        )
    )
}

private fun FeedItemPodcastSpeakerQueries.insert(podcastId: String, speaker: Speaker) {
    this.insert(
        FeedItemPodcastSpeaker(
            feedItemPodcastId = podcastId,
            name = speaker.name,
            iconUrl = speaker.iconUrl
        )
    )
}

private fun FeedItemVideoQueries.insert(podcast: FeedItem.Video) {
    this.insert(
        FeedItemVideo(
            id = podcast.id,
            publishedAt = podcast.publishedAt.toEpochMilliseconds(),
            imageSmallUrl = podcast.image.smallUrl,
            imageStandardUrl = podcast.image.standardUrl,
            imageLargeUrl = podcast.image.largeUrl,
            media = podcast.media.text,
            jaTitle = podcast.title.jaTitle,
            enTitle = podcast.title.enTitle,
            jaSummary = podcast.summary.jaTitle,
            enSummary = podcast.summary.enTitle,
            link = podcast.link,
        )
    )
}

private fun List<SelectAll>.toPodcastItems(): List<FeedItem.Podcast> {
    return this.foldRight(mapOf<String, FeedItem.Podcast>()) { row, acc ->
        val feedItem = if (acc.containsKey(row.id)) {
            val oldFeedItem = acc.getValue(row.id)
            oldFeedItem.copy(
                speakers = oldFeedItem.speakers + Speaker(
                    name = row.speakerName,
                    iconUrl = row.speakerIconUrl
                )
            )
        } else {
            FeedItem.Podcast(
                id = row.id,
                publishedAt = Instant.fromEpochMilliseconds(row.publishedAt),
                image = Image(
                    smallUrl = row.imageSmallUrl,
                    standardUrl = row.imageStandardUrl,
                    largeUrl = row.imageLargeUrl
                ),
                media = Media.parse(row.media),
                title = MultiLangText(jaTitle = row.jaTitle, enTitle = row.enTitle),
                summary = MultiLangText(jaTitle = row.jaSummary, enTitle = row.enSummary),
                link = row.link,
                speakers = listOf(Speaker(name = row.speakerName, iconUrl = row.speakerIconUrl))
            )
        }
        acc + mapOf(row.id to feedItem)
    }.values.toList()
}

fun fakeFeedItemDao(): FeedItemDao = object : FeedItemDao {
    override fun selectAll(): List<FeedItem> {
        return emptyList()
    }

    override fun insert(feeds: List<FeedItem>) {
        // nothing to do.
    }

    override fun deleteAll() {
        // nothing to do.
    }
}
