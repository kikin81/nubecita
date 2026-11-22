package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * UI-ready projection of an `app.bsky.graph.defs#starterPackView` (or its basic
 * form): enough to draw a pack card or row and to open the pack.
 *
 * - [uri] is the pack's `at://…/app.bsky.graph.starterpack/…` URI, the key the
 *   pack screen loads by.
 * - [name] is the pack's name (from the pack record).
 * - [creatorHandle] is the creator's handle without a leading `@`.
 * - [memberCount] is the size of the pack's member list when the server said so;
 *   null when it did not, never a guess.
 * - [avatarUrls] are up to a handful of member avatars from the response's member
 *   sample, in server order; empty when the response carried no sample.
 */
@Immutable
public data class StarterPackUi(
    val uri: String,
    val name: String,
    val creatorHandle: String,
    val memberCount: Int?,
    val avatarUrls: ImmutableList<String>,
)
