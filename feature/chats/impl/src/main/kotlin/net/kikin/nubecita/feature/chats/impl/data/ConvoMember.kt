package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import net.kikin.nubecita.data.models.AuthorUi

/**
 * Wire member → UI [AuthorUi]. `AuthorUi.displayName` is non-null, so fall back to
 * the handle when the wire displayName is null or blank.
 */
internal fun ProfileViewBasic.toAuthorUi(): AuthorUi =
    AuthorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeUnless { it.isBlank() } ?: handle.raw,
        avatarUrl = avatar?.raw,
    )
