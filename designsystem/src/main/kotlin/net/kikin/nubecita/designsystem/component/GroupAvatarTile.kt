package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi

/**
 * A **fixed-footprint** avatar tile for a group, laid out as quadrants of one
 * square: 1 member fills it, 2 split it, 3 sit two-over-one, and 4-or-more fill
 * all four quadrants.
 *
 * Why this exists alongside [AvatarGroup]: the facepile's width is a function of
 * member count, so in a list's leading slot every row indents its text by a
 * different amount, and a wide facepile can starve the trailing slots of space.
 * That is not hypothetical — a 5-member group row squeezed the supporting slot
 * to 257dp and clipped an action button out of existence entirely
 * (`nubecita-mpgs`). A constant outer size makes the row's remaining width
 * independent of who is in the conversation.
 *
 * [AvatarGroup] is still the right choice for INLINE use — "Ana and 3 others
 * liked your post" — where the cluster sits in a sentence rather than a slot and
 * an overlapping row reads more naturally than a tile.
 *
 * There is deliberately no "+N" overflow pill. The tile caps at four faces and
 * says nothing about the rest; a group's exact size is surfaced as a member
 * count next to its name, and cramming a count into a 22dp quadrant would be
 * illegible at this scale. (The AT Protocol chat lexicon has no group image of
 * any kind — `chat.bsky.convo.defs#groupConvo` carries only a name and counts —
 * so a composed tile is the only identity a group has.)
 *
 * [contentDescription] is caller-supplied — the design system does not own the
 * domain's plural strings — and merged over the whole tile so assistive tech
 * announces one object rather than up to four unlabelled images.
 */
@Composable
fun GroupAvatarTile(
    members: ImmutableList<AuthorUi>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_GROUP_TILE_SIZE,
) {
    val faces = members.take(MAX_TILE_FACES)
    val tileModifier =
        if (contentDescription != null) {
            modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        } else {
            modifier
        }

    Box(modifier = tileModifier.size(size), contentAlignment = Alignment.Center) {
        when (faces.size) {
            0 -> Unit
            1 -> Face(faces[0], size)
            else -> {
                // Quadrant size, not half the tile: the gap has to come out of the
                // faces or the grid overflows its fixed footprint — which is the
                // whole point of the tile.
                val quadrant = (size - QUADRANT_GAP) / 2
                Column(verticalArrangement = Arrangement.spacedBy(QUADRANT_GAP)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(QUADRANT_GAP)) {
                        Face(faces[0], quadrant)
                        Face(faces[1], quadrant)
                    }
                    if (faces.size > 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(QUADRANT_GAP)) {
                            Face(faces[2], quadrant)
                            // Three members leave the fourth quadrant empty rather
                            // than re-centring the bottom row: a stable grid makes
                            // the tile read as one object across counts, which a
                            // shifting third face would undo.
                            if (faces.size > 3) Face(faces[3], quadrant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Face(
    member: AuthorUi,
    size: Dp,
) {
    NubecitaAvatar(
        model = member.avatarUrl,
        // The tile carries one merged description; per-face labels would make a
        // screen reader read four images where the user sees one group.
        contentDescription = null,
        size = size,
        fallback =
            avatarFallbackFor(
                did = member.did,
                handle = member.handle,
                displayName = member.displayName,
            ),
    )
}

/**
 * Matches the single-avatar size used by conversation rows, so a direct row and
 * a group row indent their text identically.
 */
val DEFAULT_GROUP_TILE_SIZE: Dp = 48.dp

private val QUADRANT_GAP: Dp = 2.dp

/** Four faces is the grid's capacity; beyond that the member count carries the size. */
private const val MAX_TILE_FACES = 4
