package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ContentWarningCategory
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.MediaContentWarning
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for the NSFW media cover ([MediaContentWarningCover]) and
 * each media embed in its covered state. Covered media passes `model = null` to
 * Coil (no fetch), so these pin the cover scrim's layout + copy over the
 * placeholder, in light + dark. `dynamicColor = false` keeps colors deterministic.
 */
private fun overridableCover() = MediaCover(MediaContentWarning(ContentWarningCategory.ADULT_CONTENT, overridable = true), onReveal = {})

private fun nonOverridableCover() = MediaCover(MediaContentWarning(ContentWarningCategory.ADULT_CONTENT, overridable = false), onReveal = {})

private fun image(index: Int) =
    ImageUi(
        fullsizeUrl = "https://example.com/$index.jpg",
        thumbUrl = "https://example.com/$index.jpg",
        altText = "image $index",
        aspectRatio = 1.5f,
    )

@PreviewTest
@Preview(name = "cover — overridable, light", showBackground = true)
@Preview(name = "cover — overridable, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaCoverOverridablePreview() {
    NubecitaTheme(dynamicColor = false) {
        MediaContentWarningCover(overridableCover(), Modifier.size(320.dp, 200.dp))
    }
}

@PreviewTest
@Preview(name = "cover — non-overridable, light", showBackground = true)
@Preview(name = "cover — non-overridable, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MediaCoverNonOverridablePreview() {
    NubecitaTheme(dynamicColor = false) {
        MediaContentWarningCover(nonOverridableCover(), Modifier.size(320.dp, 200.dp))
    }
}

@PreviewTest
@Preview(name = "image single covered, light", showBackground = true)
@Preview(name = "image single covered, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageSingleCoveredPreview() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = persistentListOf(image(0)), cover = overridableCover())
    }
}

@PreviewTest
@Preview(name = "image multi covered, light", showBackground = true)
@Preview(name = "image multi covered, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImageMultiCoveredPreview() {
    NubecitaTheme(dynamicColor = false) {
        PostCardImageEmbed(items = persistentListOf(image(0), image(1), image(2)), cover = overridableCover())
    }
}

@PreviewTest
@Preview(name = "external covered, light", showBackground = true)
@Preview(name = "external covered, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExternalCoveredPreview() {
    NubecitaTheme(dynamicColor = false) {
        PostCardExternalEmbed(
            uri = "https://example.com/article",
            domain = "example.com",
            title = "A link with a sensitive preview image",
            description = "The thumbnail is covered; the link text stays readable.",
            thumbUrl = "https://example.com/thumb.jpg",
            onTap = null,
            cover = overridableCover(),
        )
    }
}

@PreviewTest
@Preview(name = "gif covered, light", showBackground = true)
@Preview(name = "gif covered, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GifCoveredPreview() {
    NubecitaTheme(dynamicColor = false) {
        PostCardGifEmbed(gifUrl = "https://static.klipy.com/x.gif", aspectRatio = 1.2f, alt = "gif", cover = overridableCover())
    }
}
