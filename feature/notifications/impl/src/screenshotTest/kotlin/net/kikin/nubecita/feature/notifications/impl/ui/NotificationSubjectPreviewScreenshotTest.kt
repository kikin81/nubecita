package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import kotlin.time.Instant

/**
 * Screenshot baselines for the mini subject-post preview embedded in
 * notification rows. Covers text-only and text+images variants —
 * other embed shapes (video, external, quoted record) are intentionally
 * omitted by the production renderer.
 */

private val PREVIEW_AUTHOR =
    AuthorUi(
        did = "did:plc:subject-preview",
        handle = "you.bsky.social",
        displayName = "You",
        avatarUrl = null,
    )

private fun previewPost(
    text: String = "A subject post used to baseline the mini preview shape.",
    embed: EmbedUi = EmbedUi.Empty,
): PostUi =
    PostUi(
        id = "at://did:plc:subject-preview/app.bsky.feed.post/preview",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = PREVIEW_AUTHOR,
        createdAt = Instant.parse("2026-04-26T10:00:00Z"),
        text = text,
        facets = persistentListOf(),
        embed = embed,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "subject-text-only-light", showBackground = true)
@Preview(name = "subject-text-only-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubjectTextOnlyScreenshot() {
    NotificationSubjectPreview(post = previewPost())
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "subject-long-text-light", showBackground = true)
@Composable
private fun SubjectLongTextScreenshot() {
    NotificationSubjectPreview(
        post =
            previewPost(
                text =
                    "A very long subject post that exceeds the two-line cap so we can " +
                        "verify the ellipsization behaviour kicks in cleanly. The preview " +
                        "should never balloon vertically when a thread starter writes a wall " +
                        "of text in their original post.",
            ),
    )
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "subject-with-images-light", showBackground = true)
@Composable
private fun SubjectWithImagesScreenshot() {
    NotificationSubjectPreview(
        post =
            previewPost(
                embed =
                    EmbedUi.Images(
                        items =
                            persistentListOf(
                                ImageUi(
                                    fullsizeUrl = "https://placeholder.example/full",
                                    thumbUrl = "https://placeholder.example/thumb",
                                    altText = "Preview image",
                                    aspectRatio = 1.5f,
                                ),
                            ),
                    ),
            ),
    )
}
