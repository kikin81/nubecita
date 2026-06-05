package net.kikin.nubecita.core.posting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PostAudienceTest {
    @Test
    fun default_isEveryoneCanReplyAndQuotesAllowed() {
        // The DEFAULT is the wide-open audience: it must map to NO
        // threadgate and NO postgate record (layer 3 relies on this).
        assertEquals(ReplyAudience.Everyone, PostAudience.DEFAULT.reply)
        assertTrue(PostAudience.DEFAULT.allowQuotes)
        assertEquals(PostAudience(ReplyAudience.Everyone, allowQuotes = true), PostAudience.DEFAULT)
    }

    @Test
    fun everyoneAndNobody_areDistinctSingletons() {
        // Singleton identity keeps `when` branches exhaustive and lets the
        // record mapper switch on the object without allocating.
        assertNotEquals(ReplyAudience.Everyone, ReplyAudience.Nobody)
        assertSame(ReplyAudience.Everyone, ReplyAudience.Everyone)
        assertSame(ReplyAudience.Nobody, ReplyAudience.Nobody)
    }

    @Test
    fun combination_carriesTheThreeIndependentToggles() {
        val onlyMentioned = ReplyAudience.Combination(followers = false, following = false, mentioned = true)
        // The three toggles are independent and read back as set — this is
        // the contract layer 3's record mapper relies on to pick exactly the
        // checked rules.
        assertFalse(onlyMentioned.followers)
        assertFalse(onlyMentioned.following)
        assertTrue(onlyMentioned.mentioned)

        // Distinct toggle sets are distinct values, so the composer reducer
        // can detect a real draft change (guards Combination staying a
        // value type rather than reference-identity).
        assertNotEquals(
            ReplyAudience.Combination(followers = true, following = false, mentioned = false),
            ReplyAudience.Combination(followers = false, following = true, mentioned = false),
        )
    }

    @Test
    fun nonDefaultAudience_isNotEqualToDefault() {
        // Drives the chip's "Visible to all" vs "Interaction limited" label.
        assertNotEquals(PostAudience.DEFAULT, PostAudience.DEFAULT.copy(allowQuotes = false))
        assertNotEquals(PostAudience.DEFAULT, PostAudience.DEFAULT.copy(reply = ReplyAudience.Nobody))
    }
}
