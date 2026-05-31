package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/** What Coil should render for a slot: the remote URL, the cropped bytes, or nothing. */
internal fun ImageSlot.model(): Any? =
    when (this) {
        is ImageSlot.Original -> url
        is ImageSlot.Cropped -> bytes
        ImageSlot.Removed -> null
    }

/** Whether the slot currently shows an image (drives pencil-vs-＋ and the remove menu). */
internal fun ImageSlot.hasImage(): Boolean =
    when (this) {
        is ImageSlot.Original -> url != null
        is ImageSlot.Cropped -> true
        ImageSlot.Removed -> false
    }

private val AVATAR_SIZE = 88.dp
private val AVATAR_OVERHANG = 28.dp
private val BANNER_CORNER = 16.dp

/**
 * The avatar + banner editor, presented M3-Expressive style: a titled card with
 * an overflow menu for removals, a wide 3:1 banner, and the circular avatar
 * overhanging its bottom-start edge (matching `ProfileHero`). Each image carries
 * an overlapping pencil / ＋ edit button set in a card-colored "notch" so it
 * reads as cut into the image (the Google Contacts photo-editor pattern).
 */
@Composable
internal fun EditProfileImages(
    banner: ImageSlot,
    avatar: ImageSlot,
    onPickBanner: () -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveBanner: () -> Unit,
    onRemoveAvatar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.edit_profile_photos_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                ImagesOverflowMenu(
                    canRemoveAvatar = avatar.hasImage(),
                    canRemoveBanner = banner.hasImage(),
                    onRemoveAvatar = onRemoveAvatar,
                    onRemoveBanner = onRemoveBanner,
                )
            }

            Box(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                EditableImage(
                    model = banner.model(),
                    hasImage = banner.hasImage(),
                    onPick = onPickBanner,
                    imageShape = RoundedCornerShape(BANNER_CORNER),
                    contentDescription = stringResource(R.string.edit_profile_banner_content_description),
                    editContentDescription = stringResource(R.string.edit_profile_change_banner_content_description),
                    badgeAlignment = Alignment.TopEnd,
                    badgeOffsetX = (-8).dp,
                    badgeOffsetY = 8.dp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(3f),
                )

                EditableImage(
                    model = avatar.model(),
                    hasImage = avatar.hasImage(),
                    onPick = onPickAvatar,
                    imageShape = CircleShape,
                    contentDescription = stringResource(R.string.edit_profile_avatar_content_description),
                    editContentDescription = stringResource(R.string.edit_profile_change_avatar_content_description),
                    // Float the badge at the avatar's bottom-right edge (~4–5 o'clock),
                    // in the clear space below the banner rather than over it.
                    badgeAlignment = Alignment.BottomEnd,
                    badgeOffsetX = (-4).dp,
                    badgeOffsetY = (-4).dp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offset(y = AVATAR_OVERHANG)
                            .size(AVATAR_SIZE),
                )
            }
            // Reserve room for the avatar's overhang below the banner.
            Spacer(Modifier.height(AVATAR_OVERHANG))
        }
    }
}

@Composable
private fun ImagesOverflowMenu(
    canRemoveAvatar: Boolean,
    canRemoveBanner: Boolean,
    onRemoveAvatar: () -> Unit,
    onRemoveBanner: () -> Unit,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }, enabled = canRemoveAvatar || canRemoveBanner) {
            NubecitaIcon(
                name = NubecitaIconName.MoreVert,
                contentDescription = stringResource(R.string.edit_profile_more_options_content_description),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_profile_remove_profile_photo)) },
                enabled = canRemoveAvatar,
                onClick = {
                    expanded = false
                    onRemoveAvatar()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_profile_remove_banner_photo)) },
                enabled = canRemoveBanner,
                onClick = {
                    expanded = false
                    onRemoveBanner()
                },
            )
        }
    }
}

/**
 * One editable image. The image itself is the primary tap target — the whole
 * surface is clickable, so the ripple shows on the image (clipped to
 * [imageShape]). A small edit button floats over a corner as a secondary, more
 * obvious affordance; both trigger [onPick]. The image and the badge are
 * siblings in an unclipped box so the badge can float over the image edge
 * without being clipped (and the image's ripple doesn't bleed under the badge).
 */
@Composable
private fun EditableImage(
    model: Any?,
    hasImage: Boolean,
    onPick: () -> Unit,
    imageShape: Shape,
    contentDescription: String,
    editContentDescription: String,
    badgeAlignment: Alignment,
    badgeOffsetX: androidx.compose.ui.unit.Dp,
    badgeOffsetY: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(imageShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(onClick = onPick),
        ) {
            if (model != null) {
                NubecitaAsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        EditBadge(
            hasImage = hasImage,
            onClick = onPick,
            contentDescription = editContentDescription,
            modifier = Modifier.align(badgeAlignment).offset(x = badgeOffsetX, y = badgeOffsetY),
        )
    }
}

/**
 * The pencil (image set) / ＋ (no image) edit button — an M3-Expressive
 * extra-small [FilledTonalIconButton] (it morphs shape on press via
 * [IconButtonDefaults.shapes]). A card-colored ring sets it apart from the
 * image it floats over.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditBadge(
    hasImage: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.padding(2.dp).size(IconButtonDefaults.extraSmallContainerSize()),
        ) {
            NubecitaIcon(
                name = if (hasImage) NubecitaIconName.Edit else NubecitaIconName.Add,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconButtonDefaults.extraSmallIconSize),
            )
        }
    }
}
