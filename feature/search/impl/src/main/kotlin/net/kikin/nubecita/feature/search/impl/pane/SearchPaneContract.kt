package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.feature.search.impl.data.DateFilter
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.ReplyFilter
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.data.SearchTab
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import net.kikin.nubecita.feature.search.impl.ui.filterChipState
import java.util.Locale

/** A lazy list's scroll position, as the two numbers `LazyListState` is created from. */
@Immutable
internal data class ScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
)

/** What the second pane is showing. */
@Immutable
internal sealed interface SearchPaneMode {
    /** Nothing is searched: the suggestion cards. */
    data object Explore : SearchPaneMode

    /**
     * A search is submitted: what the list pane's search found, in brief. Each of the four
     * sections loads on its own and carries its own status; every field here belongs to
     * [search] (a result for any other search is dropped, never shown under this header).
     */
    data class Summary(
        val search: SubmittedSearch,
        /** The server's estimate of the post count; null when it did not say. */
        val hitsTotal: Long? = null,
        /** The first post of the Posts first page. */
        val topPost: FeedItemUi.Single? = null,
        /** The first [SUMMARY_ROWS] people, with the viewer's own follow actions applied. */
        val people: ImmutableList<ActorUi> = persistentListOf(),
        /** The first [SUMMARY_ROWS] feeds, with the viewer's pins applied. */
        val feeds: ImmutableList<FeedGeneratorUi> = persistentListOf(),
        /** The first [SUMMARY_PACK_ROWS] starter packs. */
        val packs: ImmutableList<StarterPackUi> = persistentListOf(),
        val postsStatus: SummarySectionStatus = SummarySectionStatus.Loading,
        val peopleStatus: SummarySectionStatus = SummarySectionStatus.Loading,
        val feedsStatus: SummarySectionStatus = SummarySectionStatus.Loading,
        val packsStatus: SummarySectionStatus = SummarySectionStatus.Loading,
    ) : SearchPaneMode
}

/** How many people and feeds the Summary lists (starter packs: [SUMMARY_PACK_ROWS]). */
internal const val SUMMARY_ROWS = 3

/** How many starter packs the Summary lists. */
internal const val SUMMARY_PACK_ROWS = 2

/** Where one Summary section's request stands. A section with no results is Loaded and empty. */
internal enum class SummarySectionStatus { Loading, Loaded, Error }

/** The four requests behind a Summary, each retried on its own. */
internal enum class SummarySection { Posts, People, Feeds, Packs }

/** A filter the Summary shows as a removable chip. */
internal enum class SummaryFilter { Sort, Following, Media, Video, Replies, Language, Date }

/**
 * The filters in [this] that differ from their defaults, in the order the filter row lists
 * them. Media and Video are one field: a video-only search shows the Video chip alone.
 */
internal fun PostSearchFilters.activeSummaryFilters(): List<SummaryFilter> {
    // The same rules as the filter row's chips; only the language's display name is not needed here.
    val chips = filterChipState(this, Locale.ROOT)
    return buildList {
        if (chips.sortSelected) add(SummaryFilter.Sort)
        if (chips.followingSelected) add(SummaryFilter.Following)
        if (chips.videoSelected) {
            add(SummaryFilter.Video)
        } else if (chips.mediaSelected) {
            add(SummaryFilter.Media)
        }
        // Replies have no chip in the row (only the sheet), so there is no row rule to reuse.
        if (replies != ReplyFilter.INCLUDE) add(SummaryFilter.Replies)
        if (chips.languageSelected) add(SummaryFilter.Language)
        if (chips.dateSelected) add(SummaryFilter.Date)
    }
}

/** These filters with [filter] back at its default. */
internal fun PostSearchFilters.without(filter: SummaryFilter): PostSearchFilters =
    when (filter) {
        SummaryFilter.Sort -> copy(sort = SearchPostsSort.TOP)
        SummaryFilter.Following -> copy(followingOnly = false)
        SummaryFilter.Media, SummaryFilter.Video -> copy(media = MediaFilter.ANY)
        SummaryFilter.Replies -> copy(replies = ReplyFilter.INCLUDE)
        SummaryFilter.Language -> copy(language = null)
        SummaryFilter.Date -> copy(date = DateFilter.AllTime)
    }

/**
 * What the Summary's scroll position belongs to: the searched query and filters. It does not
 * change with the selected result tab, the filter-sheet flag or a re-submit of the same text.
 */
@Immutable
internal data class SummaryKey(
    val query: String,
    val filters: PostSearchFilters,
)

/** Which mode's scroll position a [SearchPaneEvent.ScrollSaved] belongs to. */
internal enum class ScrollTarget { Explore, Summary }

/**
 * State of the Search tab's second pane.
 *
 * The pane is composed outside the Search entry, where `rememberSaveable` is lost whenever
 * the pane leaves composition, so everything that must outlive that lives here: the scroll
 * position of each mode and the "Show more" toggles.
 */
@Immutable
internal data class SearchPaneState(
    val mode: SearchPaneMode = SearchPaneMode.Explore,
    val exploreScroll: ScrollPosition = ScrollPosition(),
    val summaryScroll: ScrollPosition = ScrollPosition(),
    /** The key [summaryScroll] belongs to; null in Explore. The Summary's list state is keyed on it. */
    val summaryKey: SummaryKey? = null,
    /** Whether the suggested-feeds grid shows every loaded feed instead of the first few. */
    val feedsExpanded: Boolean = false,
    /** Whether the people grid shows every loaded account instead of the first few. */
    val accountsExpanded: Boolean = false,
    /** Whether the starter-pack grid shows every loaded pack instead of the first few. */
    val packsExpanded: Boolean = false,
    /** The signed-in account's DID; its own row in the Summary's people offers no Follow. */
    val viewerDid: String? = null,
) : UiState

internal sealed interface SearchPaneEvent : UiEvent {
    /**
     * The list for a mode left composition at [position].
     *
     * [target] names the mode the list belonged to; `null` means the mode currently shown. A
     * list that leaves composition because the mode just changed must name its own mode, or
     * its position would be stored under the new one.
     */
    data class ScrollSaved(
        val position: ScrollPosition,
        val target: ScrollTarget? = null,
        /**
         * For a Summary list: the key it was created for. A save whose key is no longer the
         * current one is dropped, so a list replaced because the search changed cannot undo
         * the reset.
         */
        val summaryKey: SummaryKey? = null,
    ) : SearchPaneEvent

    data object FeedsExpansionToggled : SearchPaneEvent

    data object AccountsExpansionToggled : SearchPaneEvent

    data object PacksExpansionToggled : SearchPaneEvent

    /** A removable filter chip was tapped: that filter goes back to its default, the tab does not change. */
    data class FilterRemoved(
        val filter: SummaryFilter,
    ) : SearchPaneEvent

    /** "Edit filters": opens the filter sheet the Search screen hosts; the tab does not change. */
    data object EditFiltersClicked : SearchPaneEvent

    /** "See all" under a section: moves the list pane to [tab]. */
    data class SeeAllClicked(
        val tab: SearchTab,
    ) : SearchPaneEvent

    /** Reloads one section whose request failed; the others are left alone. */
    data class RetrySection(
        val section: SummarySection,
    ) : SearchPaneEvent

    data class PostTapped(
        val uri: String,
    ) : SearchPaneEvent

    data class ActorTapped(
        val handle: String,
    ) : SearchPaneEvent

    data class FeedTapped(
        val uri: String,
        val displayName: String,
    ) : SearchPaneEvent

    data class FollowTapped(
        val did: String,
    ) : SearchPaneEvent

    data class PinTapped(
        val uri: String,
    ) : SearchPaneEvent

    data class PackTapped(
        val uri: String,
    ) : SearchPaneEvent
}

/**
 * One-shot effects of the Summary. Explore's own navigation and errors belong to the pane's
 * `DiscoverViewModel`; these are the Summary's rows.
 */
internal sealed interface SearchPaneEffect : UiEffect {
    /** Opens the post's thread in the detail pane. */
    data class NavigateToPost(
        val uri: String,
    ) : SearchPaneEffect

    data class NavigateToProfile(
        val handle: String,
    ) : SearchPaneEffect

    data class NavigateToFeed(
        val uri: String,
        val displayName: String,
    ) : SearchPaneEffect

    data class NavigateToPack(
        val uri: String,
    ) : SearchPaneEffect

    data class FollowFailed(
        val wasUnfollow: Boolean,
    ) : SearchPaneEffect

    data class PinFailed(
        val wasUnpin: Boolean,
    ) : SearchPaneEffect
}
