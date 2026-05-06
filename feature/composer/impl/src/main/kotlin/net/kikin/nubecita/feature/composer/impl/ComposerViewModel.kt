package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.posting.ActorTypeaheadRepository
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyRefs
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.internal.GraphemeCounter
import net.kikin.nubecita.feature.composer.impl.internal.findActiveMentionStart
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.TypeaheadStatus
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presenter for the unified composer screen. Drives both new-post
 * and reply modes from one VM / one state machine, using only the
 * route's `replyToUri` argument to disambiguate.
 *
 * # Text-input ownership exception
 *
 * The composer's text and selection live in [textFieldState], a
 * Compose `TextFieldState` exposed as a public `val`. The screen
 * Composable wires the `OutlinedTextField(state = ...)` overload
 * directly; the IME's writes are local to the field and never
 * round-trip through `handleEvent` / `setState`. The VM observes
 * the field via `snapshotFlow { textFieldState.text.toString() to
 * textFieldState.selection }` collected from `init` and uses each
 * snapshot to:
 *
 * 1. Update [ComposerState.graphemeCount] / [ComposerState.isOverLimit]
 *    via the existing [GraphemeCounter].
 * 2. Detect the active `@`-mention token (via
 *    `currentMentionToken`) and feed the typeahead pipeline.
 *
 * This is a deliberate, **editor-only** departure from the repo's
 * MVI baseline (which says the VM owns canonical state and the UI
 * is a pure projection). The rationale is documented in
 * `openspec/changes/add-composer-mention-typeahead/design.md`:
 * the value/onValueChange round-trip is the canonical source of
 * cursor-jump bugs once the reducer does any non-trivial work, and
 * the typeahead feature requires non-trivial work on every
 * keystroke. The exception does NOT generalize to other screens.
 *
 * # Lifecycle
 *
 * - Constructor receives the [ComposerRoute] via Hilt assisted
 *   injection (Nav3 canonical pattern — see `:feature:postdetail:impl`'s
 *   `PostDetailViewModel.Factory` for the precedent). The route
 *   carries `replyToUri: String?`; null means new-post mode, non-null
 *   means reply mode.
 * - In reply mode, `init` kicks off a parent fetch via
 *   [ParentFetchSource]. State transitions Loading → Loaded or
 *   Loading → Failed; submit is blocked until Loaded.
 * - `init` also constructs [textFieldState] and launches the
 *   `snapshotFlow` collector + the typeahead `MutableSharedFlow`
 *   pipeline (debounce 150ms → distinctUntilChanged → mapLatest).
 *
 * # Constructor contract (append-only)
 *
 * V1 ships with four injected dependencies (the assisted
 * [ComposerRoute] + [PostingRepository] + [ParentFetchSource] +
 * [ActorTypeaheadRepository]). The future `:core:drafts` adds
 * `DraftRepository` as the next param — no reorder, no rename.
 *
 * # Process death
 *
 * V1 does NOT survive process death. Neither the [textFieldState]
 * nor the rest of the composer's working state is persisted via
 * [androidx.lifecycle.SavedStateHandle] (explicit non-goal per the
 * unified-composer spec). If the process is killed mid-compose, the
 * draft is lost. The `:core:drafts` follow-up adds disk-backed
 * draft persistence which addresses this for non-empty drafts.
 *
 * The character counter, the `isOverLimit` flag, the
 * AddAttachments cap, and every reducer's logic remain unit-testable
 * — see `ComposerViewModelTest`. The new typeahead pipeline lands
 * its own dedicated tests in `ComposerViewModelTypeaheadTest` (next
 * commit).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel(assistedFactory = ComposerViewModel.Factory::class)
class ComposerViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: ComposerRoute,
        private val postingRepository: PostingRepository,
        private val parentFetchSource: ParentFetchSource,
        private val actorTypeaheadRepository: ActorTypeaheadRepository,
    ) : MviViewModel<ComposerState, ComposerEvent, ComposerEffect>(
            initialState = ComposerState(replyToUri = route.replyToUri),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: ComposerRoute): ComposerViewModel
        }

        /**
         * Canonical text and selection for the composer. Constructed
         * once in init (default empty); never re-assigned. The
         * screen's `OutlinedTextField(state = textFieldState)` reads
         * and writes directly; the IME never round-trips through the
         * VM reducer. See class Kdoc for the rationale.
         */
        val textFieldState: TextFieldState = TextFieldState()

        /**
         * Per-VM hot stream driving the typeahead lookup. The
         * `snapshotFlow` collector below emits the active
         * `@`-mention token here on every text/selection change;
         * the pipeline downstream debounces, dedupes, and `mapLatest`s
         * the result through [actorTypeaheadRepository]. The empty
         * string is a sentinel meaning "no active token" — it
         * cancels any in-flight query via `mapLatest` and resolves
         * to [TypeaheadStatus.Idle].
         *
         * `extraBufferCapacity = 1` so a synchronous `tryEmit` from
         * the snapshot collector never drops events; replays
         * nothing.
         */
        private val queryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

        init {
            uiState.value.replyToUri?.let { uri ->
                launchParentFetch(uri)
            }

            // Snapshot collector — drives the grapheme counter and
            // feeds the typeahead query flow. Runs once per snapshot
            // frame after a write to textFieldState.text or
            // textFieldState.selection.
            snapshotFlow { textFieldState.text.toString() to textFieldState.selection.end }
                .onEach { (text, cursor) ->
                    val count = GraphemeCounter.count(text)
                    setState { copy(graphemeCount = count, isOverLimit = count > MAX_GRAPHEMES) }

                    val tokenStart = findActiveMentionStart(text, cursor)
                    if (tokenStart == null) {
                        // Synchronous Idle dismissal so the dropdown
                        // disappears immediately when the cursor leaves
                        // the active token. The "" sentinel below
                        // also cancels any in-flight query via
                        // mapLatest so a stale Suggestions write
                        // can't race in after the dismissal.
                        setState { copy(typeahead = TypeaheadStatus.Idle) }
                        queryFlow.tryEmit("")
                    } else {
                        val token = text.substring(tokenStart + 1, cursor)
                        queryFlow.tryEmit(token)
                    }
                }.launchIn(viewModelScope)

            // Typeahead lookup pipeline — debounce + dedupe +
            // mapLatest. Failures collapse to Idle (hidden dropdown)
            // per the design's hide-on-error decision; surfacing a
            // snackbar on every flap of a flaky connection during
            // typing would be more annoying than helpful.
            queryFlow
                .debounce(TYPEAHEAD_DEBOUNCE)
                .distinctUntilChanged()
                .mapLatest { query ->
                    if (query.isEmpty()) {
                        TypeaheadStatus.Idle
                    } else {
                        setState { copy(typeahead = TypeaheadStatus.Querying(query)) }
                        actorTypeaheadRepository.searchTypeahead(query).fold(
                            onSuccess = { actors ->
                                if (actors.isEmpty()) {
                                    TypeaheadStatus.NoResults(query)
                                } else {
                                    TypeaheadStatus.Suggestions(query, actors.toImmutableList())
                                }
                            },
                            onFailure = { TypeaheadStatus.Idle },
                        )
                    }
                }.onEach { status -> setState { copy(typeahead = status) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: ComposerEvent) {
            // Drafts mutate the current state; a submit in flight has
            // already snapshotted text + attachments + replyTo into
            // the createPost call. Letting drafts continue to mutate
            // the visible state would mean the UI shows content
            // different from what's actually being posted — and on
            // Success the composer closes leaving the user thinking
            // they posted the new content. Block draft mutations
            // (attachments, typeahead inserts) and parent-fetch
            // retries while submitting; only Submit itself is allowed
            // through (and is gated by canSubmit, which itself
            // rejects when submitInFlight). Text input is gated at
            // the UI layer via OutlinedTextField's `enabled = false`
            // — the IME can't write to a disabled field, so no
            // snapshot fires.
            val submitInFlight = uiState.value.submitStatus is ComposerSubmitStatus.Submitting
            when (event) {
                is ComposerEvent.AddAttachments -> if (!submitInFlight) handleAddAttachments(event.attachments)
                is ComposerEvent.RemoveAttachment -> if (!submitInFlight) handleRemoveAttachment(event.index)
                ComposerEvent.Submit -> handleSubmit()
                ComposerEvent.RetryParentLoad -> if (!submitInFlight) handleRetryParentLoad()
                is ComposerEvent.TypeaheadResultClicked ->
                    if (!submitInFlight) handleTypeaheadResultClicked(event.actor)
            }
        }

        private fun handleAddAttachments(incoming: List<net.kikin.nubecita.core.posting.ComposerAttachment>) {
            Timber.tag(TAG).d(
                "handleAddAttachments() — incoming=%d, currentAttachments=%d",
                incoming.size,
                uiState.value.attachments.size,
            )
            setState {
                val remaining = (MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)
                if (remaining == 0 || incoming.isEmpty()) {
                    this
                } else {
                    val accepted = incoming.take(remaining)
                    copy(attachments = (attachments + accepted).toImmutableList())
                }
            }
            Timber.tag(TAG).d(
                "handleAddAttachments() done — attachments now=%d",
                uiState.value.attachments.size,
            )
        }

        private fun handleRemoveAttachment(index: Int) {
            setState {
                if (index !in attachments.indices) {
                    this
                } else {
                    copy(attachments = attachments.toMutableList().apply { removeAt(index) }.toImmutableList())
                }
            }
        }

        /**
         * Atomically replaces the active `@`-mention substring with
         * the canonical handle followed by a trailing space, and
         * places the cursor at the end of the insertion. The trailing
         * space ensures the next-typed character starts a new word
         * — without it, the next char would append to the canonical
         * handle and immediately invalidate it.
         *
         * No-op when the `@`-position can't be re-located (the user
         * moved the cursor between suggestion-arrival and click);
         * dropping the click is safer than corrupting the text with
         * a stale insertion.
         *
         * The next snapshot emission sees the trailing whitespace
         * boundary and `findActiveMentionStart` returns null, so
         * the pipeline transitions `state.typeahead` back to Idle.
         */
        private fun handleTypeaheadResultClicked(actor: ActorTypeaheadUi) {
            val text = textFieldState.text
            val cursor = textFieldState.selection.end
            val atPos = findActiveMentionStart(text, cursor) ?: return
            val insertion = "@${actor.handle} "
            textFieldState.edit {
                replace(atPos, cursor, insertion)
                placeCursorBeforeCharAt(atPos + insertion.length)
            }
        }

        private fun handleSubmit() {
            val current = uiState.value
            val text = textFieldState.text.toString()
            // `replyToUri` is a full parent AT-URI carrying a third-
            // party DID. Per the repo's redaction policy (see
            // :core:auth DefaultXrpcClientProvider.redactDid + the
            // rkey-only postdetail logging), log just the rkey so a
            // future crash-reporter tree can't surface the full DID.
            // For diagnostics, "submitting in reply mode to <rkey>" is
            // sufficient — the parent identity is recoverable from
            // the navigation route argument if needed.
            Timber.tag(TAG).d(
                "handleSubmit() — text.len=%d, attachments=%d, replyToRkey=%s, submitStatus=%s",
                text.length,
                current.attachments.size,
                current.replyToUri?.substringAfterLast('/') ?: "null",
                current.submitStatus::class.simpleName,
            )
            if (!canSubmit(current, text)) {
                Timber.tag(TAG).w("handleSubmit() blocked by canSubmit gate — no-op")
                return
            }

            // Resolve reply refs upfront — `Loaded` is required by canSubmit
            // when replyToUri is set, so the cast is safe.
            val replyTo: ReplyRefs? =
                current.replyParentLoad?.let { status ->
                    val loaded = status as ParentLoadStatus.Loaded
                    ReplyRefs(parent = loaded.post.parentRef, root = loaded.post.rootRef)
                }

            setState { copy(submitStatus = ComposerSubmitStatus.Submitting) }

            viewModelScope.launch {
                val result =
                    postingRepository.createPost(
                        text = text,
                        attachments = current.attachments.toList(),
                        replyTo = replyTo,
                    )
                result.fold(
                    onSuccess = { uri ->
                        // The full AtUri carries the signed-in user's
                        // DID (`at://did:plc:.../app.bsky.feed.post/<rkey>`).
                        // Log just the rkey to match the redaction
                        // pattern used in :feature:postdetail:impl and
                        // the repository's parallel breadcrumb at
                        // DefaultPostingRepository's createRecord-ok
                        // log site. See :core:auth's
                        // DefaultXrpcClientProvider.redactDid for the
                        // canonical reasoning.
                        Timber.tag(TAG).d(
                            "createPost() success rkey=%s",
                            uri.raw.substringAfterLast('/'),
                        )
                        setState { copy(submitStatus = ComposerSubmitStatus.Success) }
                        sendEffect(ComposerEffect.OnSubmitSuccess(uri))
                    },
                    onFailure = { throwable ->
                        Timber.tag(TAG).e(throwable, "createPost() failed")
                        val cause = (throwable as? ComposerError) ?: ComposerError.RecordCreationFailed(throwable)
                        setState { copy(submitStatus = ComposerSubmitStatus.Error(cause)) }
                        // Sticky state already records the typed cause for an
                        // inline retry affordance; the snackbar is the
                        // user-facing surface for "your post didn't go" and
                        // the screen maps the variant to a localized string.
                        // Parent-fetch failure deliberately does NOT emit
                        // ShowError — that error is shown as inline reply-
                        // header UI keyed off `replyParentLoad = Failed`.
                        sendEffect(ComposerEffect.ShowError(cause))
                    },
                )
            }
        }

        private fun handleRetryParentLoad() {
            val uri = uiState.value.replyToUri ?: return
            // Ignore retry if already loading.
            if (uiState.value.replyParentLoad is ParentLoadStatus.Loading) return
            launchParentFetch(uri)
        }

        private fun launchParentFetch(rawUri: String) {
            setState { copy(replyParentLoad = ParentLoadStatus.Loading) }
            viewModelScope.launch {
                val result = parentFetchSource.fetchParent(AtUri(rawUri))
                result.fold(
                    onSuccess = { post ->
                        setState { copy(replyParentLoad = ParentLoadStatus.Loaded(post)) }
                    },
                    onFailure = { throwable ->
                        val cause = (throwable as? ComposerError) ?: ComposerError.RecordCreationFailed(throwable)
                        setState { copy(replyParentLoad = ParentLoadStatus.Failed(cause)) }
                    },
                )
            }
        }

        /**
         * Submit is allowed iff:
         * - There's content (text or attachments).
         * - We're not over the grapheme limit.
         * - Submission is currently `Idle` or `Error` (not in flight,
         *   not already succeeded).
         * - In reply mode, the parent has fully loaded.
         */
        private fun canSubmit(
            state: ComposerState,
            text: String,
        ): Boolean {
            val hasContent = text.isNotBlank() || state.attachments.isNotEmpty()
            if (!hasContent) return false
            if (state.isOverLimit) return false
            val submitInFlight =
                state.submitStatus is ComposerSubmitStatus.Submitting ||
                    state.submitStatus is ComposerSubmitStatus.Success
            if (submitInFlight) return false
            // Reply mode requires the parent to be Loaded.
            if (state.replyToUri != null && state.replyParentLoad !is ParentLoadStatus.Loaded) return false
            return true
        }

        companion object {
            private const val TAG = "ComposerVM"

            /** AT Protocol post-text limit per `app.bsky.richtext.facet.MAX_GRAPHEMES`. */
            const val MAX_GRAPHEMES = 300

            /** Lexicon cap for `app.bsky.embed.images`. */
            const val MAX_ATTACHMENTS = 4

            /**
             * Debounce window for the typeahead query pipeline. 150ms
             * matches Bluesky's mobile clients' feel; below ~200ms
             * keeps the dropdown responsive, above ~100ms keeps the
             * API quiet during burst typing.
             */
            private val TYPEAHEAD_DEBOUNCE = 150.milliseconds
        }
    }
