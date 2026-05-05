package net.kikin.nubecita.feature.composer.impl.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.posting.ComposerAttachment

/**
 * Flat, UI-ready state for the unified composer screen.
 *
 * Per the repo's MVI conventions:
 * - **Independent flags** ([text], [graphemeCount], [isOverLimit],
 *   [attachments]) live as flat fields. Composables read them
 *   directly without a `when` on a sum-type wrapper.
 * - **Mutually-exclusive lifecycles** ([submitStatus],
 *   [replyParentLoad]) are sealed status sums — the type system
 *   forbids invalid combinations like "Submitting AND Error".
 *
 * No `Async<T>` / `Result<T>` wrappers. No remote-data sum at the UI
 * boundary. Concrete fields the composable can read with zero
 * branching.
 *
 * @property text The current composer text. Defaults to empty.
 * @property graphemeCount Grapheme-cluster count of [text] — what the
 *   counter renders. Recomputed on every `TextChanged` event using
 *   `java.text.BreakIterator.getCharacterInstance()`. NOT
 *   `text.length` (UTF-16 code units) and NOT `text.codePointCount`
 *   (Unicode codepoints) — both miscount emoji ZWJ sequences.
 * @property isOverLimit `true` iff [graphemeCount] > 300 (the AT
 *   Protocol post-text limit). Derived from [graphemeCount]; reducer
 *   keeps both consistent on every update.
 * @property attachments Up to 4 image attachments. Defaults to empty.
 *   Cap enforced by the reducer (the picker also caps via
 *   `maxItems`).
 * @property replyToUri Copied from `ComposerRoute.replyToUri` at VM
 *   init time. `null` in new-post mode; non-null in reply mode.
 *   Stored as `String` (matching the NavKey's String shape) — the
 *   eventual `ParentFetchSource` lifts to `AtUri` at the call site
 *   to the atproto runtime.
 * @property replyParentLoad Reply-mode parent-fetch lifecycle.
 *   `null` in new-post mode; non-null and starts in
 *   `ParentLoadStatus.Loading` in reply mode, transitioning to
 *   `Loaded` or `Failed`.
 * @property submitStatus Submit-flow lifecycle. Defaults to
 *   `ComposerSubmitStatus.Idle`.
 */
data class ComposerState(
    val text: String = "",
    val graphemeCount: Int = 0,
    val isOverLimit: Boolean = false,
    val attachments: ImmutableList<ComposerAttachment> = persistentListOf(),
    val replyToUri: String? = null,
    val replyParentLoad: ParentLoadStatus? = null,
    val submitStatus: ComposerSubmitStatus = ComposerSubmitStatus.Idle,
) : UiState
