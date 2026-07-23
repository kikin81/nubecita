package net.kikin.nubecita.core.videofeed

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import javax.inject.Provider

class VideoFeedSourceFactoryTest {
    private val trendingInstance = mockk<DefaultTrendingVideoSource>()
    private val authorInstance = mockk<AuthorVideoSource>()
    private val authorFactory = mockk<AuthorVideoSource.Factory> { every { create(any()) } returns authorInstance }
    private val factory = DefaultVideoFeedSourceFactory(Provider { trendingInstance }, authorFactory)

    @Test
    fun nullAuthor_returnsTrending() {
        assertSame(trendingInstance, factory.create(null))
    }

    @Test
    fun nonNullAuthor_returnsAuthorSourceForThatActor() {
        val source = factory.create("did:plc:abc")
        assertSame(authorInstance, source)
        io.mockk.verify { authorFactory.create("did:plc:abc") }
    }
}
