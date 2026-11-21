package net.kikin.nubecita.designsystem.component

import net.kikin.nubecita.data.models.PostStatsUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusPostCountsTest {
    @Test
    fun `focus counts omit zeros and keep reposts, quotes, likes, saves order`() {
        val stats = PostStatsUi(replyCount = 5, repostCount = 44, quoteCount = 0, likeCount = 193, bookmarkCount = 102)

        assertEquals(
            listOf(FocusCountKind.Reposts to 44, FocusCountKind.Likes to 193, FocusCountKind.Saves to 102),
            stats.focusCounts(),
        )
    }

    @Test
    fun `focus counts are empty when every count is zero`() {
        assertTrue(PostStatsUi(replyCount = 3).focusCounts().isEmpty(), "replies live on the action row, not the counts row")
    }
}
