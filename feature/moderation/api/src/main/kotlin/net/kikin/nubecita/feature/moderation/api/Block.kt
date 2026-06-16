package net.kikin.nubecita.feature.moderation.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Block-account confirmation dialog.
 *
 * Like [Report], the dialog is a sub-route pushed onto whichever top-level tab
 * the user is on (e.g. the Chats list's contextual "Block account"), rendered as
 * a Material 3 `ModalBottomSheet` by `:feature:moderation:impl`'s `@MainShell`
 * entry provider. On confirm the dialog's ViewModel creates an
 * `app.bsky.graph.block` record via `:core:actors`' `BlockRepository`.
 *
 * Carries both [did] (the block subject — the record's `subject`) and [handle]
 * (for the confirmation copy, e.g. "Block @alice.bsky.social?") so the dialog
 * needs no profile-resolve round-trip; the call site already has both. Lives in
 * `:feature:moderation:api` so cross-feature callers (`:feature:chats:impl`, and
 * later `:feature:profile:impl`) can construct + push the key without depending
 * on `:feature:moderation:impl`'s runtime graph.
 */
@Serializable
data class Block(
    val did: String,
    val handle: String,
) : NavKey {
    companion object {
        /** Build a `Block` NavKey targeting an account by [did] (with its [handle] for display). */
        fun forAccount(
            did: String,
            handle: String,
        ): Block = Block(did = did, handle = handle)
    }
}
