package net.kikin.nubecita.wear.complication

import net.kikin.nubecita.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnreadDmsComplicationTest {
    @Test
    fun the_count_is_shown_as_is_up_to_99() {
        assertEquals("0", unreadDmsLabel(0))
        assertEquals("7", unreadDmsLabel(7))
        assertEquals("99", unreadDmsLabel(99))
    }

    @Test
    fun counts_over_99_are_capped_so_they_fit_a_short_text_slot() {
        assertEquals("99+", unreadDmsLabel(100))
        assertEquals("99+", unreadDmsLabel(5_000))
    }

    @Test
    fun a_negative_count_from_a_bad_snapshot_reads_as_zero() {
        assertEquals("0", unreadDmsLabel(-3))
    }

    @Test
    fun the_icon_shows_an_unread_dot_only_when_something_is_unread() {
        assertEquals(R.drawable.ic_messages, unreadDmsIcon(0))
        assertEquals(R.drawable.ic_messages_unread, unreadDmsIcon(1))
    }
}
