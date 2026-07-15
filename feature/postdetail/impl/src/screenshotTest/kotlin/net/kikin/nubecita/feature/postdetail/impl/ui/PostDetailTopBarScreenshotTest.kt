package net.kikin.nubecita.feature.postdetail.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for the stateless [PostDetailTopBar] at both ends of the
 * scroll-reactive swap: the resting "Post" title and the settled author block.
 *
 * These drive the **stateless** overload directly with a pinned `showAuthor`, so
 * `AnimatedContent` composes with its target already settled — there is no
 * in-flight spring to race, and the captures are deterministic across machines.
 * Do NOT add a fixture that drives the stateful (scroll-observing) overload; a
 * pre-scrolled `LazyListState` would capture a mid-animation frame.
 *
 * The resting/author pair is captured in light + dark (the title-vs-author
 * contrast is the visual contract). The long-name-truncation and avatarless
 * (initial-fallback) cases are layout/identity concerns that aren't
 * theme-sensitive, so they lock in light only.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@PreviewTest
@Preview(name = "topbar-post-title-light", showBackground = true)
@Preview(name = "topbar-post-title-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailTopBarPostTitleScreenshot() {
    NubecitaCanvasPreviewTheme {
        PostDetailTopBar(author = FIXTURE_AUTHOR, showAuthor = false, onBack = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@PreviewTest
@Preview(name = "topbar-author-light", showBackground = true)
@Preview(name = "topbar-author-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostDetailTopBarAuthorScreenshot() {
    NubecitaCanvasPreviewTheme {
        PostDetailTopBar(author = FIXTURE_AUTHOR, showAuthor = true, onBack = {})
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@PreviewTest
@Preview(name = "topbar-author-long-name-light", showBackground = true)
@Composable
private fun PostDetailTopBarLongNameScreenshot() {
    NubecitaCanvasPreviewTheme {
        PostDetailTopBar(
            author =
                FIXTURE_AUTHOR.copy(
                    displayName = "Bartholomew Fitzgerald-Montgomery the Third, Esq.",
                ),
            showAuthor = true,
            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@PreviewTest
@Preview(name = "topbar-author-avatarless-light", showBackground = true)
@Composable
private fun PostDetailTopBarAvatarlessScreenshot() {
    NubecitaCanvasPreviewTheme {
        // Null avatarUrl exercises NubecitaAvatar's deterministic initial-disc
        // fallback inside the bar.
        PostDetailTopBar(
            author = FIXTURE_AUTHOR.copy(avatarUrl = null),
            showAuthor = true,
            onBack = {},
        )
    }
}

private val FIXTURE_AUTHOR =
    AuthorUi(
        did = "did:plc:fixtureauthor",
        handle = "jane.bsky.social",
        displayName = "Jane Appleseed",
        // Non-null so the default fixtures exercise NubecitaAvatar's async-image
        // branch (the `topbar-author-avatarless` fixture overrides this to null
        // to lock the initial-disc fallback instead).
        avatarUrl = "https://cdn.example.com/avatars/jane.jpg",
    )
