package net.kikin.nubecita.feature.search.impl.pane

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import net.kikin.nubecita.feature.search.impl.data.actorFixture
import net.kikin.nubecita.feature.search.impl.data.feedFixture
import net.kikin.nubecita.feature.search.impl.data.packFixture
import net.kikin.nubecita.feature.search.impl.data.searchPostFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SummaryPaneLogicTest {
    private val search = SubmittedSearch(query = "kotlin", fromRecent = false)
    private val loaded = SummarySectionStatus.Loaded

    private fun summary(
        posts: SummarySectionStatus = loaded,
        people: SummarySectionStatus = loaded,
        feeds: SummarySectionStatus = loaded,
        packs: SummarySectionStatus = loaded,
    ) = SearchPaneMode.Summary(search, postsStatus = posts, peopleStatus = people, feedsStatus = feeds, packsStatus = packs)

    @Test
    fun `no results only when all four loaded and all are empty`() {
        assertEquals(true, summary().hasNoResults())
    }

    @Test
    fun `a section still loading or failed is not no results`() {
        assertEquals(false, summary(posts = SummarySectionStatus.Loading).hasNoResults())
        assertEquals(false, summary(people = SummarySectionStatus.Error).hasNoResults())
        assertEquals(false, summary(feeds = SummarySectionStatus.Loading).hasNoResults())
        assertEquals(false, summary(packs = SummarySectionStatus.Loading).hasNoResults())
        assertEquals(false, summary(packs = SummarySectionStatus.Error).hasNoResults())
    }

    @Test
    fun `any one section with content is not no results`() {
        assertEquals(false, summary().copy(topPost = searchPostFixture("at://p/1", "x")).hasNoResults())
        assertEquals(false, summary().copy(people = persistentListOf(actorFixture())).hasNoResults())
        assertEquals(false, summary().copy(feeds = persistentListOf(feedFixture())).hasNoResults())
        assertEquals(false, summary().copy(packs = persistentListOf(packFixture())).hasNoResults())
    }
}
