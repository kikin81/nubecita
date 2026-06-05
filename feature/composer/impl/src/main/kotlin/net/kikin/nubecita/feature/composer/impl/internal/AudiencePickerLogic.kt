package net.kikin.nubecita.feature.composer.impl.internal

import net.kikin.nubecita.core.posting.ReplyAudience

/**
 * The three reply groups a [ReplyAudience.Combination] can allow. Drives the
 * checkbox rows in [AudiencePickerContent].
 */
internal enum class ReplyGroup { FOLLOWERS, FOLLOWING, MENTIONED }

/** True when this is the wide-open "anyone can reply" preset. */
internal val ReplyAudience.isAnyone: Boolean get() = this == ReplyAudience.Everyone

/** True when this is the "no one can reply" preset. */
internal val ReplyAudience.isNobody: Boolean get() = this == ReplyAudience.Nobody

/** Whether [group]'s checkbox is checked — only a [ReplyAudience.Combination] checks groups. */
internal fun ReplyAudience.isChecked(group: ReplyGroup): Boolean =
    this is ReplyAudience.Combination &&
        when (group) {
            ReplyGroup.FOLLOWERS -> followers
            ReplyGroup.FOLLOWING -> following
            ReplyGroup.MENTIONED -> mentioned
        }

/**
 * Toggle one combination [group]. From a preset ([ReplyAudience.Everyone] /
 * [ReplyAudience.Nobody]) checking a group begins a fresh combination with only
 * that group set. Unchecking the last remaining group falls back to
 * [ReplyAudience.Everyone] — an all-false combination is not a representable
 * selection, and "no groups chosen" reads most naturally as "open to anyone".
 */
internal fun ReplyAudience.toggle(group: ReplyGroup): ReplyAudience {
    val base = this as? ReplyAudience.Combination ?: ReplyAudience.Combination(false, false, false)
    val next =
        when (group) {
            ReplyGroup.FOLLOWERS -> base.copy(followers = !base.followers)
            ReplyGroup.FOLLOWING -> base.copy(following = !base.following)
            ReplyGroup.MENTIONED -> base.copy(mentioned = !base.mentioned)
        }
    return if (next.followers || next.following || next.mentioned) next else ReplyAudience.Everyone
}
