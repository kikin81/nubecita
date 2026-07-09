package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.app.bsky.actor.VerificationState
import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.profile.canViewerMessage
import net.kikin.nubecita.data.models.toVerifiedBadge
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.VerifierRef
import net.kikin.nubecita.feature.profile.impl.ViewerModerationState
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

/**
 * Maps the atproto wire model [ProfileViewDetailed] into the UI-ready
 * [ProfileHeaderUi].
 *
 * Inlined in `:feature:profile:impl` because Profile is the only
 * consumer of this mapping (per the YAGNI rule from `CLAUDE.md`).
 * Future consumers (a hover card, a notification author chip) can
 * promote the function to `:core:feed-mapping` or its own
 * `:core:profile-mapping` module — see `openspec/.../design.md`
 * Decision 4.
 *
 * Unwraps the atproto runtime value classes (`Did`, `Handle`, `Uri`,
 * `Datetime`) into raw strings so consumers downstream of this point
 * never see atproto wire types — the boundary is here.
 */
internal fun ProfileViewDetailed.toProfileHeaderUi(): ProfileHeaderUi =
    ProfileHeaderUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeUnless { it.isBlank() },
        avatarUrl = avatar?.raw,
        bannerUrl = banner?.raw,
        bio = description?.takeUnless { it.isBlank() },
        location = null, // No location field on profileViewDetailed yet — atproto-side stretch.
        website = website?.raw,
        joinedDisplay = createdAt?.raw?.toJoinedDisplay(),
        postsCount = postsCount ?: 0L,
        followersCount = followersCount ?: 0L,
        followsCount = followsCount ?: 0L,
        canMessage = canViewerMessage(associated, viewer),
        viewerModeration = viewer.toViewerModerationState(),
        verifiedBadge = verification.toVerifiedBadge(),
        verifierRefs = verification.toVerifierRefs(),
    )

/**
 * Extract the profile's **valid** verifications as unresolved [VerifierRef]s
 * (issuer DID + date). Invalid entries are dropped, and any whose `createdAt`
 * won't parse are skipped. The DIDs are resolved to names lazily by the VM when
 * the verification sheet opens — this mapper deliberately does no network work.
 */
internal fun VerificationState?.toVerifierRefs(): ImmutableList<VerifierRef> =
    this
        ?.verifications
        ?.asSequence()
        ?.filter { it.isValid }
        ?.mapNotNull { view ->
            val instant = runCatching { Instant.parse(view.createdAt.raw) }.getOrNull() ?: return@mapNotNull null
            VerifierRef(did = view.issuer.raw, verifiedAt = instant)
        }?.toList()
        ?.toImmutableList()
        ?: persistentListOf()

/**
 * Projects the mute / block-direction flags off `viewer` into the
 * UI-layer [ViewerModerationState]. Treats `null` `viewer` (an
 * unauthenticated request, or an appview that omitted the block) as
 * the all-defaults "no moderation in play" baseline — matches the
 * same posture as [toViewerRelationship].
 *
 * `viewer.blocking` is an `AtUri?` on the wire; we capture `.raw` so
 * the UI layer never sees the runtime value class. `viewer.muted` and
 * `viewer.blockedBy` are nullable `Boolean?` — null is treated as the
 * default `false` for both.
 */
private fun ViewerState?.toViewerModerationState(): ViewerModerationState {
    if (this == null) return ViewerModerationState()
    return ViewerModerationState(
        isMutedByViewer = muted == true,
        blockUri = blocking?.raw,
        isBlockingViewer = blockedBy == true,
    )
}

/**
 * Render the wire `createdAt` (an RFC3339 timestamp string) into the
 * profile's `Joined <Month YYYY>` display. Returns null when the
 * string can't be parsed — the meta row simply hides the joined-date
 * line in that case (per the spec's `Meta row hides absent optional
 * fields` scenario).
 */
private fun String.toJoinedDisplay(): String? =
    runCatching {
        ZonedDateTime.parse(this).format(JOINED_FORMATTER)
    }.getOrNull()

// `LLLL` = full month name (locale-aware); `yyyy` = 4-digit year.
private val JOINED_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("'Joined' LLLL yyyy", Locale.getDefault())

/**
 * Pair of the UI-ready header and the viewer's relationship to the
 * subject profile, both derived from a single [ProfileViewDetailed]
 * wire response.
 *
 * Bead F separates `viewerRelationship` from [ProfileHeaderUi] so a
 * future follow-up bd (epic 7.3 — real Follow / Unfollow writes) can
 * mutate the relationship without invalidating the header — the two
 * fields have independent lifetimes.
 */
data class ProfileHeaderWithViewer(
    val header: ProfileHeaderUi,
    val viewerRelationship: ViewerRelationship,
)

/**
 * Composes [toProfileHeaderUi] with [viewer]-derived relationship.
 *
 * `viewer.following: AtUri?` is the AtUri of *the requesting user's*
 * follow record pointing at this profile. Non-null → Following.
 * Null but `viewer` itself non-null → NotFollowing. `viewer` null →
 * None (unauthed-style fallback; shouldn't happen post-login since
 * profile screens only mount past the splash routing gate).
 *
 * Own-profile (`route.handle == null`) overrides the result to
 * [ViewerRelationship.Self] at the ViewModel layer — the mapper itself
 * doesn't know about own/other-user. See
 * [net.kikin.nubecita.feature.profile.impl.ProfileViewModel.launchHeaderLoad].
 */
internal fun ProfileViewDetailed.toProfileHeaderWithViewer(): ProfileHeaderWithViewer =
    ProfileHeaderWithViewer(
        header = toProfileHeaderUi(),
        viewerRelationship = viewer.toViewerRelationship(),
    )

private fun ViewerState?.toViewerRelationship(): ViewerRelationship {
    if (this == null) return ViewerRelationship.None
    val uri = following ?: return ViewerRelationship.NotFollowing()
    return ViewerRelationship.Following(followUri = uri.raw)
}
