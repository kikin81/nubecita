package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for the verification badge in [PostCard]'s author line
 * (task nubecita-vw45.2). Locks the glyph mapping (check_circle vs verified rosette),
 * the verified-blue tint, and that the badge hugs the display name ahead of the handle.
 * The unverified case is covered by the existing PostCard baselines, which stay
 * byte-identical because `VerificationBadge(None)` emits no layout node.
 */
private fun verifiedScreenshotPost(
    badge: VerifiedBadge,
    displayName: String = "Alice Chen",
): PostUi =
    PostUi(
        id = "screenshot-verified",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:fakedid000000000000000",
                handle = "alice.bsky.social",
                displayName = displayName,
                avatarUrl = null,
                verifiedBadge = badge,
            ),
        createdAt = Instant.parse("2025-10-15T12:00:00Z"),
        text = "Body text is constant so the only varying surface is the verification badge in the author line.",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
        viewer = ViewerStateUi(isLikedByViewer = true),
        repostedBy = null,
    )

@PreviewTest
@Preview(name = "postcard-verified-light", showBackground = true)
@Preview(name = "postcard-verified-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardVerifiedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(post = verifiedScreenshotPost(VerifiedBadge.Verified), callbacks = PostCallbacks.None)
    }
}

@PreviewTest
@Preview(name = "postcard-trusted-verifier-light", showBackground = true)
@Preview(name = "postcard-trusted-verifier-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardTrustedVerifierScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(post = verifiedScreenshotPost(VerifiedBadge.TrustedVerifier), callbacks = PostCallbacks.None)
    }
}

/**
 * A display name long enough to fill the whole author row (nubecita-vw45.5). Pins
 * the fix: the name ellipsizes and the badge stays visible ahead of the handle,
 * rather than the name squeezing the fixed badge to zero width.
 */
@PreviewTest
@Preview(name = "postcard-verified-long-name-light", showBackground = true)
@Preview(name = "postcard-verified-long-name-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardVerifiedLongNameScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post =
                verifiedScreenshotPost(
                    badge = VerifiedBadge.Verified,
                    displayName = "Alexandria Bartholomew Featherstonehaugh the Third, Esq.",
                ),
            callbacks = PostCallbacks.None,
        )
    }
}
