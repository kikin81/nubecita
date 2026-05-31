package net.kikin.nubecita.feature.profile.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for the EditProfile form ([EditProfileContent]) — the
 * text-first slice's visual states. Renders against fixture state (no Hilt, no
 * ViewModel): empty (new account), populated (existing profile), and
 * name-over-limit (error tone on the field + grapheme counter).
 *
 * Each fixture renders Light + Dark.
 */
@PreviewTest
@Preview(name = "edit-profile-empty-light", showBackground = true)
@Preview(name = "edit-profile-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditProfileEmptyPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileContent(
            state = EditProfileViewState(),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
        )
    }
}

@PreviewTest
@Preview(name = "edit-profile-populated-light", showBackground = true)
@Preview(name = "edit-profile-populated-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditProfilePopulatedPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileContent(
            state =
                EditProfileViewState(
                    // Plain ASCII only — emoji glyphs render differently on the
                    // CI (Linux) image vs a dev Mac and drift the baseline.
                    displayName = "Alice",
                    description = "Coffee, Kotlin, and cloud-watching.",
                    displayNameGraphemes = 5,
                    descriptionGraphemes = 35,
                    isDirty = true,
                ),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
        )
    }
}

@PreviewTest
@Preview(name = "edit-profile-over-limit-light", showBackground = true)
@Preview(name = "edit-profile-over-limit-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditProfileOverLimitPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileContent(
            state =
                EditProfileViewState(
                    displayName = "a".repeat(70),
                    description = "short bio",
                    displayNameGraphemes = 70,
                    descriptionGraphemes = 9,
                    isDirty = true,
                ),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
        )
    }
}

@PreviewTest
@Preview(name = "edit-profile-with-images-light", showBackground = true)
@Preview(name = "edit-profile-with-images-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditProfileWithImagesPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileContent(
            // Original(url) slots render the camera + remove badges over the
            // async-image placeholder (the URLs don't resolve in screenshot tests).
            state =
                EditProfileViewState(
                    displayName = "Alice",
                    description = "Coffee, Kotlin, and cloud-watching.",
                    displayNameGraphemes = 5,
                    descriptionGraphemes = 35,
                    avatar = ImageSlot.Original("https://example.test/avatar.jpg"),
                    banner = ImageSlot.Original("https://example.test/banner.jpg"),
                ),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
        )
    }
}
