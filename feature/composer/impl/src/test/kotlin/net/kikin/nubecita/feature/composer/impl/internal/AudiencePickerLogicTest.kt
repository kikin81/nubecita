package net.kikin.nubecita.feature.composer.impl.internal

import net.kikin.nubecita.core.posting.ReplyAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudiencePickerLogicTest {
    @Test
    fun `everyone is anyone and not nobody`() {
        assertTrue(ReplyAudience.Everyone.isAnyone)
        assertFalse(ReplyAudience.Everyone.isNobody)
    }

    @Test
    fun `nobody is nobody and not anyone`() {
        assertTrue(ReplyAudience.Nobody.isNobody)
        assertFalse(ReplyAudience.Nobody.isAnyone)
    }

    @Test
    fun `combination is neither anyone nor nobody`() {
        val combo = ReplyAudience.Combination(followers = true, following = false, mentioned = false)
        assertFalse(combo.isAnyone)
        assertFalse(combo.isNobody)
    }

    @Test
    fun `isChecked reads the group bits of a combination`() {
        val combo = ReplyAudience.Combination(followers = true, following = false, mentioned = true)
        assertTrue(combo.isChecked(ReplyGroup.FOLLOWERS))
        assertFalse(combo.isChecked(ReplyGroup.FOLLOWING))
        assertTrue(combo.isChecked(ReplyGroup.MENTIONED))
    }

    @Test
    fun `presets report no checked groups`() {
        ReplyGroup.entries.forEach { group ->
            assertFalse(ReplyAudience.Everyone.isChecked(group))
            assertFalse(ReplyAudience.Nobody.isChecked(group))
        }
    }

    @Test
    fun `toggling a group from a preset starts a fresh single-group combination`() {
        assertEquals(
            ReplyAudience.Combination(followers = true, following = false, mentioned = false),
            ReplyAudience.Everyone.toggle(ReplyGroup.FOLLOWERS),
        )
        assertEquals(
            ReplyAudience.Combination(followers = false, following = false, mentioned = true),
            ReplyAudience.Nobody.toggle(ReplyGroup.MENTIONED),
        )
    }

    @Test
    fun `toggling adds and removes individual groups within a combination`() {
        val start = ReplyAudience.Combination(followers = true, following = false, mentioned = false)
        val added = start.toggle(ReplyGroup.MENTIONED)
        assertEquals(ReplyAudience.Combination(followers = true, following = false, mentioned = true), added)
        val removed = added.toggle(ReplyGroup.FOLLOWERS)
        assertEquals(ReplyAudience.Combination(followers = false, following = false, mentioned = true), removed)
    }

    @Test
    fun `unchecking the last group falls back to anyone`() {
        val single = ReplyAudience.Combination(followers = false, following = true, mentioned = false)
        assertEquals(ReplyAudience.Everyone, single.toggle(ReplyGroup.FOLLOWING))
    }
}
