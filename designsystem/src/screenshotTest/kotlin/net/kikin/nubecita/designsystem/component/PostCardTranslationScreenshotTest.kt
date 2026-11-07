package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import kotlin.time.Instant

/**
 * Baselines for the translation states of [PostCard].
 *
 * Four fixtures, and each one discriminates something a single fixture could
 * not:
 *
 * - **offered** — the chip exists at all, and the body is still the original.
 *   A regression that dropped the chip, or that translated eagerly, changes it.
 * - **in progress** — the shimmer sits *over* the original text rather than
 *   replacing it with placeholder bars. If someone swaps that, the body here
 *   stops being Japanese and the diff is obvious.
 * - **translated** — the body really did swap, and the attribution line and
 *   "Show original" are both present. ML Kit's terms require the credit, so a
 *   baseline that could not tell attribution from no attribution would be
 *   worth nothing.
 * - **waiting for Wi-Fi** — a distinct, non-tappable pill. Rendered identically
 *   to the offer it would be indistinguishable from a chip that silently does
 *   nothing, which is the exact failure the state exists to prevent.
 *
 * The Japanese body is the post that motivated the feature, and the English is
 * what ML Kit actually produced for it on device — rough, which is the point:
 * these baselines should not flatter the engine.
 */
@PreviewTest
@Preview(name = "post-card-translate-offered-light")
@Preview(name = "post-card-translate-offered-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardTranslateOfferedPreview() {
    PostCard(post = japanesePost(), translation = PostTranslationUi.Offered("ja"))
}

@PreviewTest
@Preview(name = "post-card-translate-progress-light")
@Preview(name = "post-card-translate-progress-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardTranslateInProgressPreview() {
    PostCard(post = japanesePost(), translation = PostTranslationUi.InProgress("ja"))
}

@PreviewTest
@Preview(name = "post-card-translated-light")
@Preview(name = "post-card-translated-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardTranslatedPreview() {
    PostCard(
        post = japanesePost(),
        translation =
            PostTranslationUi.Shown(
                sourceLanguage = "ja",
                text = ML_KIT_OUTPUT,
                facets = persistentListOf(),
            ),
    )
}

@PreviewTest
@Preview(name = "post-card-translate-waiting-wifi-light")
@Preview(name = "post-card-translate-waiting-wifi-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardTranslateWaitingWifiPreview() {
    PostCard(post = japanesePost(), translation = PostTranslationUi.WaitingForWifi("ja"))
}

@PreviewTest
@Preview(name = "post-card-translate-downloading-light")
@Preview(name = "post-card-translate-downloading-dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostCardTranslateDownloadingPreview() {
    PostCard(post = japanesePost(), translation = PostTranslationUi.Downloading("ja"))
}

private const val JAPANESE_BODY =
    "MNPしすぎて残価あり分割の審査に通らないので、17eをペイディ後払いで公式で買って4年使うか途中で売却して元を取ろう"

/** What ML Kit actually returned for [JAPANESE_BODY] on device. */
private const val ML_KIT_OUTPUT =
    "Because it does not pass to the examination of the remaining price, the 17E is officially " +
        "buying and it will be used for 4 years, and it will be sold out on the way."

private fun japanesePost() =
    PostUi(
        id = "at://did:plc:fake/app.bsky.feed.post/translate",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = AuthorUi("did:plc:fake", "z-z.world", "めも", null),
        createdAt = Instant.parse("2026-04-25T12:00:00Z"),
        text = JAPANESE_BODY,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 0, repostCount = 5, likeCount = 15),
        viewer = ViewerStateUi(),
        repostedBy = null,
        langs = persistentListOf("ja"),
    )
