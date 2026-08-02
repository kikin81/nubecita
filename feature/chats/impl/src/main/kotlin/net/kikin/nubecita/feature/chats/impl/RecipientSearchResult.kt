package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.data.models.ActorUi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long to wait after the last keystroke before searching.
 *
 * One value rather than three per-screen constants: the three pickers are the
 * same surface wearing different hats, and they had all independently picked
 * 250ms. Tuning the picker's feel should be one edit, not three that can drift.
 * The composer's typeahead is a different surface with its own 150ms and is
 * deliberately not folded in here.
 */
private val DEFAULT_RECIPIENT_SEARCH_DEBOUNCE = 250.milliseconds

/**
 * The outcome of a recipient-picker search, before a screen maps it onto its
 * own status type.
 *
 * Deliberately NOT a shared `UiState` field: each picker keeps its own
 * per-screen sealed status (`NewChatStatus`, `NewGroupStatus`,
 * `AddMembersStatus`) as the MVI conventions require. This type exists only so
 * the three identical *pipelines* can collapse into one, and it dies at the
 * `map` on the way into each screen's state.
 */
internal sealed interface RecipientSearchResult {
    /** Blank query: most-recently-seen actors from the local cache. */
    data class Recent(
        val actors: ImmutableList<ActorUi>,
    ) : RecipientSearchResult

    /** Emitted immediately on a non-blank query, before the debounce. */
    data object Searching : RecipientSearchResult

    data class Results(
        val actors: ImmutableList<ActorUi>,
    ) : RecipientSearchResult

    data object NoResults : RecipientSearchResult

    data object Error : RecipientSearchResult
}

/**
 * The recipient-picker search pipeline, shared by the three pickers.
 *
 * `NewChatViewModel`, `NewGroupViewModel` and `AddGroupMembersViewModel` each
 * carried a byte-identical copy of this — their KDocs said so out loud
 * ("Forks NewChatViewModel's merge/debounce/flatMapLatest search pipeline"),
 * which is the copy-paste admitting itself. Three identical shapes is the
 * threshold CLAUDE.md sets for extracting a helper.
 *
 * Two properties are load-bearing and easy to lose in a rewrite:
 *
 *  - **`flatMapLatest`, not `mapLatest`.** The blank branch subscribes to
 *    [ActorRepository.recentActors], a *cold flow* that keeps emitting as the
 *    cache changes. `mapLatest` could only take one value from it.
 *  - **The delay sits after [RecipientSearchResult.Searching] and inside the
 *    latest-wins block.** The spinner appears at once, and a newer keystroke
 *    cancels the in-flight request *and* a still-pending debounce, so a stale
 *    result cannot land behind a newer one.
 *
 * A failed cache read degrades to [RecipientSearchResult.Error] via `catch`
 * rather than cancelling the pipeline, so the picker survives and the next
 * keystroke still searches.
 *
 * @param excludeSelfFromResults drops the signed-in user from *search* results.
 *   Only the 1:1 picker does this — messaging yourself is not a thing, whereas
 *   the group pickers filter self later, at selection time, via `pickable`.
 *   This mirrors the behaviour the three copies already had; it is not a new
 *   policy decision.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun recipientSearchResults(
    queries: Flow<String>,
    actorRepository: ActorRepository,
    selfDid: String?,
    debounce: Duration = DEFAULT_RECIPIENT_SEARCH_DEBOUNCE,
    excludeSelfFromResults: Boolean = false,
): Flow<RecipientSearchResult> =
    queries.flatMapLatest { raw ->
        val q = raw.trim()
        if (q.isEmpty()) {
            actorRepository
                .recentActors(selfDid)
                .map<List<ActorUi>, RecipientSearchResult> { actors ->
                    // Non-messageable actors are NOT filtered out — the picker shows
                    // them disabled with a "can't be messaged" label (RecipientRow
                    // reads ActorUi.canMessage), matching the official client.
                    RecipientSearchResult.Recent(actors.toImmutableList())
                }.catch { emit(RecipientSearchResult.Error) } // cache read failed; pipeline survives
        } else {
            flow {
                emit(RecipientSearchResult.Searching)
                delay(debounce)
                emit(
                    actorRepository.searchTypeahead(q).fold(
                        onSuccess = { actors ->
                            val visible =
                                if (excludeSelfFromResults) actors.filter { it.did != selfDid } else actors
                            if (visible.isEmpty()) {
                                RecipientSearchResult.NoResults
                            } else {
                                RecipientSearchResult.Results(visible.toImmutableList())
                            }
                        },
                        onFailure = { RecipientSearchResult.Error },
                    ),
                )
            }
        }
    }
