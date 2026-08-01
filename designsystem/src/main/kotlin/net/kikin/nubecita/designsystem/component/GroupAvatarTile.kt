package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.AuthorUi

/**
 * A **fixed-footprint** avatar tile for a group: one square, laid out as
 * quadrants. A single member fills the whole tile; from two members up the
 * geometry is always the same 2x2 grid and only the filled cells change — two
 * take the diagonal, three take three cells, four or more fill it.
 *
 * The diagonal for a pair is not decoration. A single top row would leave the
 * bottom half empty, so a two-member tile would read as top-heavy beside a full
 * one, and the first two faces would jump vertically the moment a third member
 * joined. Sharing one grid keeps `members[0]` in the top-left at every count.
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

    Box(modifier = tileModifier.size(size)) {
        if (faces.isEmpty()) return@Box
        if (faces.size == 1) {
            Face(faces[0], size)
            return@Box
        }
        // Every count of two or more uses the SAME 2x2 geometry; only which
        // quadrants are filled changes. Two members take the diagonal so the tile
        // still reads as balanced in a square instead of a top-heavy single row,
        // and face[0] stays in the top-left at every count — a face does not move
        // just because someone joined the group.
        //
        //   2 -> TL . / . BR      3 -> TL TR / BL .      4+ -> TL TR / BL BR
        val quadrant = (size - QUADRANT_GAP) / 2
        val isPair = faces.size == 2
        val topRight = if (isPair) null else faces[1]
        val bottomLeft = if (isPair) null else faces.getOrNull(2)
        val bottomRight = if (isPair) faces[1] else faces.getOrNull(3)
        Column(verticalArrangement = Arrangement.spacedBy(QUADRANT_GAP)) {
            Row(horizontalArrangement = Arrangement.spacedBy(QUADRANT_GAP)) {
                Quadrant(faces[0], quadrant)
                Quadrant(topRight, quadrant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(QUADRANT_GAP)) {
                Quadrant(bottomLeft, quadrant)
                Quadrant(bottomRight, quadrant)
            }
        }
    }
}

/** One grid cell: a face, or an empty box holding the slot open. */
@Composable
private fun Quadrant(
    member: AuthorUi?,
    size: Dp,
) {
    if (member == null) Box(Modifier.size(size)) else Face(member, size)
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
