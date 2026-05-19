package net.kikin.nubecita.core.common.time

import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal class RelativeTimeTest {
    private val now: Instant = Instant.parse("2026-04-25T12:00:00Z")
    private val utc: TimeZone = TimeZone.UTC
    private val locale: Locale = Locale.ENGLISH
    private val strings: RelativeTimeStrings = RelativeTimeStrings.English

    private fun format(then: Instant): String = formatRelativeTime(now, then, strings, utc, locale)

    @Test
    fun `exactly now is now`() {
        assertEquals("now", format(now))
    }

    @Test
    fun `30 seconds ago is now`() {
        assertEquals("now", format(now - 30.seconds))
    }

    @Test
    fun `59 seconds ago is now (sub-minute)`() {
        assertEquals("now", format(now - 59.seconds))
    }

    @Test
    fun `exactly 60 seconds ago is 1m`() {
        assertEquals("1m", format(now - 60.seconds))
    }

    @Test
    fun `90 seconds ago is 1m (truncated, not rounded)`() {
        assertEquals("1m", format(now - 90.seconds))
    }

    @Test
    fun `5 minutes ago is 5m`() {
        assertEquals("5m", format(now - 5.minutes))
    }

    @Test
    fun `59 minutes ago is 59m`() {
        assertEquals("59m", format(now - 59.minutes))
    }

    @Test
    fun `exactly 1 hour ago is 1h`() {
        assertEquals("1h", format(now - 1.hours))
    }

    @Test
    fun `23 hours ago is 23h`() {
        assertEquals("23h", format(now - 23.hours))
    }

    @Test
    fun `exactly 24 hours ago is 1d`() {
        assertEquals("1d", format(now - 24.hours))
    }

    @Test
    fun `6 days ago is 6d`() {
        assertEquals("6d", format(now - 6.days))
    }

    @Test
    fun `exactly 7 days ago crosses to absolute date (same year)`() {
        // now is 2026-04-25; 7 days back is 2026-04-18.
        assertEquals("Apr 18", format(now - 7.days))
    }

    @Test
    fun `30 days ago renders absolute date in same year`() {
        // 2026-04-25 minus 30 days = 2026-03-26
        assertEquals("Mar 26", format(now - 30.days))
    }

    @Test
    fun `cross-year boundary renders date with year`() {
        // 2026-04-25 minus 200 days = 2025-10-07
        assertEquals("Oct 7, 2025", format(now - 200.days))
    }

    @Test
    fun `future timestamp from clock skew clamps to now`() {
        assertEquals("now", format(now + 5.minutes))
    }

    @Test
    fun `slightly future timestamp clamps to now`() {
        assertEquals("now", format(now + 1.seconds))
    }

    @Test
    fun `formats different locale correctly for absolute date`() {
        // Sanity check: passing a non-English locale produces a different month label.
        val french = formatRelativeTime(now, now - 30.days, strings, utc, Locale.FRENCH)
        // French abbreviates March as "mars". The exact form differs across JDK
        // releases (some emit "mars", some "mars."), but the prefix is stable.
        assertTrue(french.startsWith("mars", ignoreCase = true))
    }

    @Test
    fun `custom RelativeTimeStrings is honored`() {
        // Locks in the i18n contract: the helper sources every user-visible label
        // from the strings parameter, never from a hardcoded fallback.
        val spanish =
            RelativeTimeStrings(
                now = "ahora",
                minutes = { "hace $it min" },
                hours = { "hace $it h" },
                days = { "hace $it d" },
                yesterday = "ayer",
            )
        assertEquals("ahora", formatRelativeTime(now, now, spanish, utc, locale))
        assertEquals("ahora", formatRelativeTime(now, now - 30.seconds, spanish, utc, locale))
        assertEquals("hace 5 min", formatRelativeTime(now, now - 5.minutes, spanish, utc, locale))
        assertEquals("hace 3 h", formatRelativeTime(now, now - 3.hours, spanish, utc, locale))
        assertEquals("hace 5 d", formatRelativeTime(now, now - 5.days, spanish, utc, locale))
    }

    // --- formatChatRelativeTime --------------------------------------------------
    // The chat formatter uses calendar-aware buckets (Yesterday, weekday names)
    // distinct from the Twitter-style compact form above. Calendar comparisons
    // pin to `utc` for these tests so day-flip behavior is deterministic.

    private fun chat(then: Instant): String = formatChatRelativeTime(now, then, strings, utc, locale)

    @Test
    fun `chat - sub-minute is now`() {
        assertEquals("now", chat(now - 30.seconds))
    }

    @Test
    fun `chat - 5 minutes ago is 5m`() {
        assertEquals("5m", chat(now - 5.minutes))
    }

    @Test
    fun `chat - 59 minutes ago is 59m`() {
        assertEquals("59m", chat(now - 59.minutes))
    }

    @Test
    fun `chat - same calendar day shows Nh`() {
        // now is 2026-04-25T12:00:00Z; 3h earlier is still 2026-04-25.
        assertEquals("3h", chat(now - 3.hours))
    }

    @Test
    fun `chat - 11 hours ago on the same calendar day shows 11h`() {
        // 2026-04-25T01:00:00Z is still the same calendar day in UTC.
        assertEquals("11h", chat(now - 11.hours))
    }

    @Test
    fun `chat - 13 hours ago crosses into previous day and shows Yesterday`() {
        // 2026-04-25T12:00 - 13h = 2026-04-24T23:00 — previous calendar day.
        assertEquals("Yesterday", chat(now - 13.hours))
    }

    @Test
    fun `chat - 28 hours ago shows Yesterday`() {
        // Unambiguously the prior calendar day.
        assertEquals("Yesterday", chat(now - 28.hours))
    }

    @Test
    fun `chat - 2 days ago shows weekday`() {
        // 2026-04-25 is a Saturday; 2026-04-23 is a Thursday.
        assertEquals("Thu", chat(now - 2.days))
    }

    @Test
    fun `chat - 6 days ago shows weekday`() {
        // 2026-04-25 - 6 days = 2026-04-19 (Sunday).
        assertEquals("Sun", chat(now - 6.days))
    }

    @Test
    fun `chat - 7 days ago crosses into absolute date (same year)`() {
        // 2026-04-25 - 7 days = 2026-04-18.
        assertEquals("Apr 18", chat(now - 7.days))
    }

    @Test
    fun `chat - 30 days ago shows absolute date in same year`() {
        // 2026-04-25 - 30 days = 2026-03-26.
        assertEquals("Mar 26", chat(now - 30.days))
    }

    @Test
    fun `chat - prior year crosses to MMM d, yyyy`() {
        // 2026-04-25 - 200 days = 2025-10-07.
        assertEquals("Oct 7, 2025", chat(now - 200.days))
    }

    @Test
    fun `chat - future timestamp clamps to now`() {
        assertEquals("now", chat(now + 5.minutes))
    }

    @Test
    fun `chat - weekday is locale-aware`() {
        // 2026-04-23 (Thursday) in French is "jeu." — the exact suffix is
        // JDK-dependent; assert the prefix is stable across JDK releases.
        val french = formatChatRelativeTime(now, now - 2.days, strings, utc, Locale.FRENCH)
        assertTrue(french.startsWith("jeu", ignoreCase = true))
    }
}
