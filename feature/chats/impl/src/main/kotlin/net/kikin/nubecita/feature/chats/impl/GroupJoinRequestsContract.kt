// Screen contract file (named after the join-requests screen, not its single type);
// State/Event/Effect land in a later task. See sibling *Contract.kt files.
@file:Suppress("ktlint:standard:filename")

package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

/** A pending group join request, projected for the join-requests screen. */
@Immutable
data class JoinRequestUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val requestedAt: Instant,
)
