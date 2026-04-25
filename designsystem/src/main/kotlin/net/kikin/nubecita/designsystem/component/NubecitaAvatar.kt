package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Circular avatar primitive. Wraps [NubecitaAsyncImage] with the
 * standard size + circular clip used across feed, profile, and
 * notifications.
 *
 * `model` accepts anything Coil can resolve (URL string, Uri, Drawable,
 * etc.) — the AT Protocol author avatar field is a `String?` URL, so
 * passing it directly is the common path.
 */
@Composable
fun NubecitaAvatar(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_AVATAR_SIZE,
) {
    NubecitaAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier =
            modifier
                .size(size)
                .clip(CircleShape),
        contentScale = ContentScale.Crop,
    )
}

private val DEFAULT_AVATAR_SIZE = 40.dp

@Preview(name = "Avatar — placeholder", showBackground = true)
@Composable
private fun NubecitaAvatarPreview() {
    NubecitaTheme {
        NubecitaAvatar(model = null, contentDescription = null)
    }
}
