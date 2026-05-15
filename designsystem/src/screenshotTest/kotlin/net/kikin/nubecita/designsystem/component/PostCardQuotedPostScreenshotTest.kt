package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for [PostCardQuotedPost] covering the four
 * non-video inner-embed shapes (text-only / images / external /
 * thread-chip) across light + dark themes — 8 baselines total. The
 * with-video baseline lands in task 5 alongside the coordinator
 * extension, since it requires the host-supplied
 * `quotedVideoEmbedSlot` lambda.
 *
 * The with-image baseline uses NubecitaAsyncImage's placeholder
 * painter (preview tooling doesn't hit the network), so the
 * baseline verifies card geometry + layout rather than actual
 * image content.
 */

private val FIXED_CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

private val FIXED_AUTHOR: AuthorUi =
    AuthorUi(
        did = "did:plc:fixed",
        handle = "acyn.bsky.social",
        displayName = "Acyn",
        avatarUrl = null,
    )

private const val FIXED_TEXT: String =
    "Quoted-post rendering needs to match the official Android client: " +
        "parent layout minus the action row, smaller avatar, full body " +
        "text without truncation, and inline embed dispatch."

private fun fixedQuoted(embed: QuotedEmbedUi): QuotedPostUi =
    QuotedPostUi(
        uri = "at://did:plc:fixed/app.bsky.feed.post/q",
        cid = "bafyreifixedquotedcid000000000000000000000000000",
        author = FIXED_AUTHOR,
        createdAt = FIXED_CREATED_AT,
        text = FIXED_TEXT,
        facets = persistentListOf(),
        embed = embed,
    )

@PreviewTest
@Preview(name = "text-only-light", showBackground = true)
@Preview(name = "text-only-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostTextOnlyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(quotedPost = fixedQuoted(QuotedEmbedUi.Empty))
    }
}

@PreviewTest
@Preview(name = "with-image-light", showBackground = true)
@Preview(name = "with-image-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostWithImageScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(
            quotedPost =
                fixedQuoted(
                    QuotedEmbedUi.Images(
                        items =
                            persistentListOf(
                                ImageUi(
                                    fullsizeUrl = "https://example.com/preview.jpg",
                                    thumbUrl = "https://example.com/preview.jpg",
                                    altText = null,
                                    aspectRatio = 16f / 9f,
                                ),
                            ),
                    ),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "with-external-light", showBackground = true)
@Preview(name = "with-external-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostWithExternalScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(
            quotedPost =
                fixedQuoted(
                    QuotedEmbedUi.External(
                        uri = "https://www.theverge.com/article",
                        domain = "theverge.com",
                        title = "An article headline goes here",
                        description = "Short description copy for the link card.",
                        thumbUrl = null,
                    ),
                ),
        )
    }
}

@PreviewTest
@Preview(name = "view-thread-light", showBackground = true)
@Preview(name = "view-thread-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostViewThreadScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(quotedPost = fixedQuoted(QuotedEmbedUi.QuotedThreadChip))
    }
}

// Guards against Modifier-ordering regressions when onTap is supplied:
// the clickable wraps Surface, NOT the inner Column, so the rendered
// layout must be byte-identical to the text-only baseline (clickable
// adds no draw). If a future refactor wraps the clickable inside the
// Surface's content slot or applies it after .padding(), the captured
// pixels would differ and these baselines would catch it.
@PreviewTest
@Preview(name = "tappable-light", showBackground = true)
@Preview(name = "tappable-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardQuotedPostTappableScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardQuotedPost(
            quotedPost = fixedQuoted(QuotedEmbedUi.Empty),
            onTap = {},
        )
    }
}
