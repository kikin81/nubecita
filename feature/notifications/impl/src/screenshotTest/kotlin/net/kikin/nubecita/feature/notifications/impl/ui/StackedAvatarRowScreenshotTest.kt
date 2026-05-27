package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [StackedAvatarRow] — covers the actor-count
 * sweep (1, 2, 5 at cap, 8 with +3 overflow) × light/dark.
 */

private fun fakeAuthors(count: Int): ImmutableList<AuthorUi> =
    (0 until count)
        .map { i ->
            AuthorUi(
                did = "did:plc:fixture-stack-$i",
                handle = "user$i.bsky.social",
                displayName = "User $i",
                avatarUrl = null,
            )
        }.toImmutableList()

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "stacked-avatar-row-1-light", showBackground = true)
@Composable
private fun StackedAvatarRowOneScreenshot() {
    StackedAvatarRow(actors = fakeAuthors(1))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "stacked-avatar-row-2-light", showBackground = true)
@Composable
private fun StackedAvatarRowTwoScreenshot() {
    StackedAvatarRow(actors = fakeAuthors(2))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "stacked-avatar-row-5-light", showBackground = true)
@Preview(name = "stacked-avatar-row-5-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StackedAvatarRowFiveScreenshot() {
    StackedAvatarRow(actors = fakeAuthors(5))
}

@PreviewTest
@PreviewWrapper(NubecitaComponentPreview::class)
@Preview(name = "stacked-avatar-row-8-light", showBackground = true)
@Preview(name = "stacked-avatar-row-8-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StackedAvatarRowEightScreenshot() {
    StackedAvatarRow(actors = fakeAuthors(8))
}
