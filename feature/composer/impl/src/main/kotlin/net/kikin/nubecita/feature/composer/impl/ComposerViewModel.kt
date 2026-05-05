package net.kikin.nubecita.feature.composer.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyRefs
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.internal.GraphemeCounter
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus

/**
 * Presenter for the unified composer screen. Drives both new-post
 * and reply modes from one VM / one state machine, using only the
 * route's `replyToUri` argument to disambiguate.
 *
 * Lifecycle:
 * - Constructor receives the [ComposerRoute] via Hilt assisted
 *   injection (Nav3 canonical pattern — see `:feature:postdetail:impl`'s
 *   `PostDetailViewModel.Factory` for the precedent). The route
 *   carries `replyToUri: String?`; null means new-post mode, non-null
 *   means reply mode.
 * - In reply mode, `init` kicks off a parent fetch via
 *   [ParentFetchSource]. State transitions Loading → Loaded or
 *   Loading → Failed; submit is blocked until Loaded.
 *
 * Forward-compatibility: append-only constructor contract. V1 ships
 * with three injected dependencies (the assisted [ComposerRoute] +
 * [PostingRepository] + [ParentFetchSource]). The future `:core:drafts`
 * adds `DraftRepository` as the next param — no reorder, no rename.
 *
 * Process death: V1 does NOT survive process death (no
 * [androidx.lifecycle.SavedStateHandle] plumbing — explicit non-goal
 * per the unified-composer spec). If the process is killed mid-
 * compose, the user's draft is lost. The `:core:drafts` follow-up
 * adds disk-backed draft persistence which addresses this case for
 * non-empty drafts.
 *
 * The character counter, the `isOverLimit` flag, the
 * AddAttachments cap, and every reducer's logic are unit-testable
 * as pure state transitions — see `ComposerViewModelTest`.
 */
@HiltViewModel(assistedFactory = ComposerViewModel.Factory::class)
class ComposerViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: ComposerRoute,
        private val postingRepository: PostingRepository,
        private val parentFetchSource: ParentFetchSource,
    ) : MviViewModel<ComposerState, ComposerEvent, ComposerEffect>(
            initialState = ComposerState(replyToUri = route.replyToUri),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: ComposerRoute): ComposerViewModel
        }

        init {
            uiState.value.replyToUri?.let { uri ->
                launchParentFetch(uri)
            }
        }

        override fun handleEvent(event: ComposerEvent) {
            // Drafts mutate the current state; a submit in flight has
            // already snapshotted text + attachments + replyTo into
            // the createPost call. Letting drafts continue to mutate
            // the visible state would mean the UI shows content
            // different from what's actually being posted — and on
            // Success the composer closes leaving the user thinking
            // they posted the new content. Block draft mutations
            // (text/attachments) and parent-fetch retries while
            // submitting; only Submit itself is allowed through (and
            // is gated by canSubmit, which itself rejects when
            // submitInFlight).
            val submitInFlight = uiState.value.submitStatus is ComposerSubmitStatus.Submitting
            when (event) {
                is ComposerEvent.TextChanged -> if (!submitInFlight) handleTextChanged(event.text)
                is ComposerEvent.AddAttachments -> if (!submitInFlight) handleAddAttachments(event.attachments)
                is ComposerEvent.RemoveAttachment -> if (!submitInFlight) handleRemoveAttachment(event.index)
                ComposerEvent.Submit -> handleSubmit()
                ComposerEvent.RetryParentLoad -> if (!submitInFlight) handleRetryParentLoad()
            }
        }

        private fun handleTextChanged(text: String) {
            val count = GraphemeCounter.count(text)
            setState {
                copy(
                    text = text,
                    graphemeCount = count,
                    isOverLimit = count > MAX_GRAPHEMES,
                )
            }
        }

        private fun handleAddAttachments(incoming: List<net.kikin.nubecita.core.posting.ComposerAttachment>) {
            setState {
                val remaining = (MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)
                if (remaining == 0 || incoming.isEmpty()) {
                    this
                } else {
                    val accepted = incoming.take(remaining)
                    copy(attachments = (attachments + accepted).toImmutableList())
                }
            }
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

        private fun handleSubmit() {
            val current = uiState.value
            if (!canSubmit(current)) return

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
                        text = current.text,
                        attachments = current.attachments.toList(),
                        replyTo = replyTo,
                    )
                result.fold(
                    onSuccess = { uri ->
                        setState { copy(submitStatus = ComposerSubmitStatus.Success) }
                        sendEffect(ComposerEffect.OnSubmitSuccess(uri))
                    },
                    onFailure = { throwable ->
                        val cause = (throwable as? ComposerError) ?: ComposerError.RecordCreationFailed(throwable)
                        setState { copy(submitStatus = ComposerSubmitStatus.Error(cause)) }
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
        private fun canSubmit(state: ComposerState): Boolean {
            val hasContent = state.text.isNotBlank() || state.attachments.isNotEmpty()
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
            /** AT Protocol post-text limit per `app.bsky.richtext.facet.MAX_GRAPHEMES`. */
            const val MAX_GRAPHEMES = 300

            /** Lexicon cap for `app.bsky.embed.images`. */
            const val MAX_ATTACHMENTS = 4
        }
    }
