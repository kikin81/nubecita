package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Whether a "remove" affordance applies (there's an image to clear). */
internal fun ImageSlot.isRemovable(): Boolean =
    when (this) {
        is ImageSlot.Original -> url != null
        is ImageSlot.Cropped -> true
        ImageSlot.Removed -> false
    }

private val BANNER_OVERHANG = 36.dp
private val AVATAR_SIZE = 88.dp

/**
 * The avatar + banner editor header, matching `ProfileHero`'s overlap: a wide
 * 3:1 banner with the circular avatar overhanging its bottom-start edge. Each
 * image is tap-to-pick (a camera badge signals it) with a remove badge when an
 * image is present. The [BANNER_OVERHANG] of avatar overhang is reserved by the
 * caller (a spacer below) so the avatar doesn't collide with the form.
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
    Box(modifier = modifier.fillMaxWidth()) {
        EditableImage(
            model = banner.model(),
            removable = banner.isRemovable(),
            onPick = onPickBanner,
            onRemove = onRemoveBanner,
            contentDescription = stringResource(R.string.edit_profile_banner_content_description),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f)
                    .clip(RoundedCornerShape(12.dp)),
        )

        EditableImage(
            model = avatar.model(),
            removable = avatar.isRemovable(),
            onPick = onPickAvatar,
            onRemove = onRemoveAvatar,
            contentDescription = stringResource(R.string.edit_profile_avatar_content_description),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .offset(y = BANNER_OVERHANG)
                    .size(AVATAR_SIZE)
                    .clip(CircleShape),
        )
    }
}

/**
 * One tappable, editable image. Renders the image (or a placeholder), a
 * centered camera badge to signal tap-to-change, and a top-end remove badge
 * when [removable]. The caller supplies the size + clip via [modifier].
 */
@Composable
private fun EditableImage(
    model: Any?,
    removable: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            NubecitaAsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
            )
        }
        // Camera badge — on a translucent scrim disc so it reads over any image.
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
        ) {
            NubecitaIcon(
                name = NubecitaIconName.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.padding(6.dp).size(22.dp),
            )
        }
        if (removable) {
            RemoveBadge(onRemove = onRemove, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
    }
}

@Composable
private fun BoxScope.RemoveBadge(
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onRemove,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Close,
            contentDescription = stringResource(R.string.edit_profile_remove_image_content_description),
            modifier = Modifier.padding(4.dp).size(18.dp),
        )
    }
}
