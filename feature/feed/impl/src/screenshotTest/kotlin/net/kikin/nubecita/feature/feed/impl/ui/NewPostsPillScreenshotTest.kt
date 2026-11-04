package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Baselines for the new-posts pill at one, two and three faces, light and
 * dark.
 *
 * The three counts are separate cases because the facepile's overlap is what
 * makes the pill read as "several people posted" rather than "one did", and
 * that geometry is the part a careless change to `AvatarGroup` would break
 * without failing anything else.
 *
 * Avatars are null here, so each face renders its initial-and-hue fallback —
 * deliberate, since a screenshot test must not load an image over the network.
 */
@PreviewTest
@Preview(name = "pill-one-light", showBackground = true)
@Preview(name = "pill-one-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewPostsPillOneScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        NewPostsPill(authors = authors(1), onClick = {})
    }
}

@PreviewTest
@Preview(name = "pill-two-light", showBackground = true)
@Preview(name = "pill-two-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewPostsPillTwoScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        NewPostsPill(authors = authors(2), onClick = {})
    }
}

@PreviewTest
@Preview(name = "pill-three-light", showBackground = true)
@Preview(name = "pill-three-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewPostsPillThreeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        NewPostsPill(authors = authors(3), onClick = {})
    }
}

private fun authors(count: Int): ImmutableList<AuthorUi> =
    persistentListOf(
        AuthorUi(did = "did:plc:nova", handle = "nova.astro", displayName = "Nova Reyes", avatarUrl = null),
        AuthorUi(did = "did:plc:maria", handle = "maria.cooks", displayName = "María Lindqvist", avatarUrl = null),
        AuthorUi(did = "did:plc:theo", handle = "theo.builds", displayName = "Theo Adeyemi", avatarUrl = null),
    ).subList(0, count).let { persistentListOf(*it.toTypedArray()) }
