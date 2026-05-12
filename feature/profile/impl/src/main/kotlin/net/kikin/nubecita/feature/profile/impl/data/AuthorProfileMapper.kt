package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import io.github.kikin81.atproto.app.bsky.actor.ViewerState
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import net.kikin.nubecita.feature.profile.impl.ViewerRelationship
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        avatarHue = avatarHueFor(did.raw, handle.raw),
        bio = description?.takeUnless { it.isBlank() },
        location = null, // No location field on profileViewDetailed yet — atproto-side stretch.
        website = website?.raw,
        joinedDisplay = createdAt?.raw?.toJoinedDisplay(),
        postsCount = postsCount ?: 0L,
        followersCount = followersCount ?: 0L,
        followsCount = followsCount ?: 0L,
    )

/**
 * Deterministic hue in `0..359` derived from `did + first char of
 * handle`. Used as the fallback gradient input by
 * [net.kikin.nubecita.designsystem.hero.BoldHeroGradient] when no
 * banner is set (and as the synchronous initial gradient while
 * Palette extraction is in flight for users who DO have a banner).
 *
 * Hashing `did` alone would mean two users with similar DIDs map to
 * the same hue; mixing in `handle.first()` spreads the hash across
 * the alphabet too. `Math.floorMod` is used (not `abs % 360`) because
 * `abs(Int.MIN_VALUE)` is still `Int.MIN_VALUE` — `floorMod` returns
 * a non-negative result for every input.
 */
internal fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
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
internal data class ProfileHeaderWithViewer(
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

private fun ViewerState?.toViewerRelationship(): ViewerRelationship =
    when {
        this == null -> ViewerRelationship.None
        following != null -> ViewerRelationship.Following
        else -> ViewerRelationship.NotFollowing
    }
