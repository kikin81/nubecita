package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * A single account the viewer has blocked, as shown in the blocked-accounts
 * list. [blockUri] is the AT URI of the viewer's own `app.bsky.graph.block`
 * record (from the profile's `viewer.blocking`) — it's what an unblock deletes,
 * so a row is only listable when it's present.
 *
 * [avatarHue] is the deterministic placeholder hue (degrees, 0–359) used when
 * [avatarUrl] is null, mirroring the convention elsewhere (`ConvoListItemUi`).
 */
@Immutable
public data class BlockedAccount(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val avatarHue: Int,
    val blockUri: String,
)
