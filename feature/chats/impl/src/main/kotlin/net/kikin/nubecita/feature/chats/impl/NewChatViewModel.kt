package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presenter for the NewChat recipient-picker screen.
 *
 * On a blank query, shows the most-recently-seen actors from the local cache
 * (self-excluded at the repo layer via [selfDid]). While the user types,
 * debounces 250ms then issues a network typeahead search; self is filtered
 * from search results. Selecting a recipient emits [NewChatEffect.OpenChat].
 *
 * # Text-field ownership
 *
 * [queryFieldState] is exposed as a public val — the search field is an editor
 * surface and follows the sanctioned MVI editor exception (CLAUDE.md). The VM
 * observes it via `snapshotFlow` and does NOT route keystrokes through
 * [handleEvent] / [setState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewChatViewModel
    @Inject
    constructor(
        private val actorRepository: ActorRepository,
        sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<NewChatState, NewChatEvent, NewChatEffect>(NewChatState()) {
        /** Editor-exception: the VM owns the search field's text + selection. */
        val queryFieldState: TextFieldState = TextFieldState()

        // Stable for the session — NewChat is only reachable inside MainShell (SignedIn).
        private val selfDid: String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        // extraBufferCapacity = 1 + tryEmit: at most one retry is buffered; additional
        // taps while one is queued are dropped (not DROP_OLDEST — there is no stale-token
        // concept here, a retry just re-runs the current query).
        private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        init {
            recipientSearchResults(
                queries =
                    merge(
                        snapshotFlow { queryFieldState.text.toString() },
                        retryTrigger.map { queryFieldState.text.toString() },
                    ),
                actorRepository = actorRepository,
                selfDid = selfDid,
                debounce = DEBOUNCE,
                // Messaging yourself is not a thing, so self is dropped from the
                // 1:1 picker's results. The group pickers keep self in the list
                // and filter it at selection time instead.
                excludeSelfFromResults = true,
            ).map { result ->
                when (result) {
                    is RecipientSearchResult.Recent -> NewChatStatus.Recent(result.actors)
                    RecipientSearchResult.Searching -> NewChatStatus.Searching
                    is RecipientSearchResult.Results -> NewChatStatus.Results(result.actors)
                    RecipientSearchResult.NoResults -> NewChatStatus.NoResults
                    RecipientSearchResult.Error -> NewChatStatus.Error
                }
            }.onEach { status -> setState { copy(status = status) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: NewChatEvent) {
            when (event) {
                is NewChatEvent.RecipientSelected -> sendEffect(NewChatEffect.OpenChat(event.otherUserDid))
                NewChatEvent.RetryClicked -> retryTrigger.tryEmit(Unit)
            }
        }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
