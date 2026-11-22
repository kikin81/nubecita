package net.kikin.nubecita.feature.search.impl.ui

import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

class FilterLabelsTest {
    @Test
    fun `no filters selects nothing and shows default labels`() {
        val state = filterChipState(PostSearchFilters(), Locale.US)

        assertFalse(state.sortSelected)
        assertFalse(state.followingSelected)
        assertFalse(state.mediaSelected)
        assertFalse(state.videoSelected)
        assertNull(state.languageName)
        assertFalse(state.dateSelected)
        assertEquals(DateFilter.AllTime, state.date)
    }

    @Test
    fun `language set shows its display name and is selected`() {
        val state = filterChipState(PostSearchFilters(language = "es"), Locale.US)

        assertEquals("Spanish", state.languageName)
        assertTrue(state.languageSelected)
    }

    @Test
    fun `language name is shown in the display locale`() {
        val state = filterChipState(PostSearchFilters(language = "es"), Locale.forLanguageTag("es-419"))

        assertEquals("Español", state.languageName)
    }

    @Test
    fun `date preset shows its label`() {
        val state = filterChipState(PostSearchFilters(date = DateFilter.PastWeek), Locale.US)

        assertEquals(DateFilter.PastWeek, state.date)
        assertTrue(state.dateSelected)
    }

    @Test
    fun `video only selects Video and Media`() {
        val state = filterChipState(PostSearchFilters(media = MediaFilter.VIDEO_ONLY), Locale.US)

        assertTrue(state.videoSelected)
        assertTrue(state.mediaSelected)
    }

    @Test
    fun `has media selects Media but not Video`() {
        val state = filterChipState(PostSearchFilters(media = MediaFilter.HAS_MEDIA), Locale.US)

        assertTrue(state.mediaSelected)
        assertFalse(state.videoSelected)
    }

    @Test
    fun `sort is selected only when it is not the default`() {
        assertTrue(filterChipState(PostSearchFilters(sort = SearchPostsSort.LATEST), Locale.US).sortSelected)
    }

    @Test
    fun `count is activeCount`() {
        val filters =
            PostSearchFilters(
                sort = SearchPostsSort.LATEST,
                followingOnly = true,
                media = MediaFilter.VIDEO_ONLY,
                language = "en",
            )

        assertEquals(3, filterChipState(filters, Locale.US).activeCount)
        assertEquals(filters.activeCount, filterChipState(filters, Locale.US).activeCount)
    }

    @Test
    fun `media chip toggles between any and has media`() {
        assertEquals(MediaFilter.HAS_MEDIA, PostSearchFilters().toggleMedia().media)
        assertEquals(MediaFilter.ANY, PostSearchFilters(media = MediaFilter.HAS_MEDIA).toggleMedia().media)
        assertEquals(MediaFilter.ANY, PostSearchFilters(media = MediaFilter.VIDEO_ONLY).toggleMedia().media)
    }

    @Test
    fun `video chip toggles between any and video only`() {
        assertEquals(MediaFilter.VIDEO_ONLY, PostSearchFilters().toggleVideo().media)
        assertEquals(MediaFilter.VIDEO_ONLY, PostSearchFilters(media = MediaFilter.HAS_MEDIA).toggleVideo().media)
        assertEquals(MediaFilter.ANY, PostSearchFilters(media = MediaFilter.VIDEO_ONLY).toggleVideo().media)
    }

    @Test
    fun languageChoices_deduplicatesByLanguageCode() {
        val choices =
            languageChoices(
                deviceLocales = listOf(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("es-419"), Locale.forLanguageTag("en-GB")),
                selected = null,
                displayLocale = Locale.US,
            )

        assertEquals(listOf("en", "es"), choices.map { it.code })
        assertEquals(listOf("English", "Spanish"), choices.map { it.label })
    }

    @Test
    fun languageChoices_putsTheSelectedLanguageInTheListEvenIfNotADeviceLocale() {
        val choices =
            languageChoices(
                deviceLocales = listOf(Locale.forLanguageTag("en-US")),
                selected = "ja",
                displayLocale = Locale.US,
            )

        assertEquals(listOf("en", "ja"), choices.map { it.code })
        assertEquals("Japanese", choices.last().label)
    }

    @Test
    fun `languageChoices ignores blank language codes`() {
        val choices = languageChoices(listOf(Locale.ROOT, Locale.US), selected = null, displayLocale = Locale.US)

        assertEquals(listOf("en"), choices.map { it.code })
    }

    @Test
    fun `the count is shown only when it is positive`() {
        assertNull(shownHitsTotal(null))
        assertNull(shownHitsTotal(0L))
        assertEquals(1L, shownHitsTotal(1L))
        assertEquals(1_240L, shownHitsTotal(1_240L))
    }
}
