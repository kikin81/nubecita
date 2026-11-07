package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.toVerifiedBadge

/** The handle the server reports for a deleted or deactivated account. */
internal const val DELETED_ACCOUNT_HANDLE = "missing.invalid"

internal fun ProfileView.toConnectionRowUi(viewerDid: String?): ConnectionRowUi {
    val isBlocked = viewer?.blocking != null || viewer?.blockedBy == true
    return ConnectionRowUi(
        actor =
            ActorUi(
                did = did.raw,
                handle = handle.raw,
                displayName = displayName?.takeIf { it.isNotBlank() },
                avatarUrl = avatar?.raw,
                verifiedBadge = verification.toVerifiedBadge(),
            ),
        followUri = viewer?.following?.raw,
        canFollow = did.raw != viewerDid && !isBlocked && handle.raw != DELETED_ACCOUNT_HANDLE,
    )
}
