package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.tabs.PillTab

/**
 * Builds the immutable list of `PillTab<ProfileTab>` rendered by
 * `:designsystem.ProfilePillTabs` in the profile screen. Labels are
 * resolved via `stringResource` at composition time so locale changes
 * recompose the row; the resulting list is `remember`'d so identity
 * is stable across recompositions when the resolved strings haven't
 * changed.
 *
 * Order matches the spec: Posts, Replies, Media (left-to-right LTR).
 * Icon glyphs use the NubecitaIcons catalog — Article for posts,
 * Reply for replies, AddPhotoAlternate for media (the catalog does
 * not currently carry a plain `Image`/`Photo` glyph; the
 * photo-alternate codepoint is the closest available stand-in until
 * the catalog gains one).
 */
@Composable
internal fun rememberProfilePillTabs(): ImmutableList<PillTab<ProfileTab>> {
    val postsLabel = stringResource(R.string.profile_tab_posts)
    val repliesLabel = stringResource(R.string.profile_tab_replies)
    val mediaLabel = stringResource(R.string.profile_tab_media)
    return remember(postsLabel, repliesLabel, mediaLabel) {
        persistentListOf(
            PillTab(value = ProfileTab.Posts, label = postsLabel, iconName = NubecitaIconName.Article),
            PillTab(value = ProfileTab.Replies, label = repliesLabel, iconName = NubecitaIconName.Reply),
            PillTab(value = ProfileTab.Media, label = mediaLabel, iconName = NubecitaIconName.AddPhotoAlternate),
        )
    }
}
