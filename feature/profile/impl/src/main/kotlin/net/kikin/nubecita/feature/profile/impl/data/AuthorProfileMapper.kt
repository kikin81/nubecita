package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewDetailed
import net.kikin.nubecita.feature.profile.impl.ProfileHeaderUi
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

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
 * Deterministic hue in `0..360` derived from `did + first char of
 * handle`. Used as the fallback gradient input by
 * [net.kikin.nubecita.designsystem.hero.BoldHeroGradient] when no
 * banner is set (and as the synchronous initial gradient while
 * Palette extraction is in flight for users who DO have a banner).
 *
 * Hashing `did` alone would mean two users with similar DIDs map to
 * the same hue; mixing in `handle.first()` spreads the hash across
 * the alphabet too. `abs(hashCode) mod 360` is intentionally cheap —
 * the spec only asks for "deterministic", not "perceptually uniform".
 */
internal fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return abs(seed.hashCode()) % 360
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
