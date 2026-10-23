package net.kikin.nubecita.feature.profile.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.data.EditableProfileLink

/**
 * Screenshot baselines for Edit Profile's Links section
 * ([EditProfileLinksSection]) across its load states and list sizes, and for
 * the add / edit form body ([LinkEditorForm]) across its validation states.
 * Icons are null so the globe fallback renders deterministically.
 */
private fun link(
    n: Int,
    url: String,
    title: String? = null,
) = EditableProfileLink(
    localId = "l$n",
    url = url,
    title = title,
    uri = "at://did:plc:alice/app.bsky.actor.link/l$n",
    cid = "cid$n",
)

private val THREE =
    listOf(
        link(1, "https://www.patreon.com/alice", "Support my work"),
        link(2, "https://alice.example.com"),
        link(3, "https://news.example.com/a-very-long-newsletter-path-that-ellipsizes", "My newsletter"),
    )

private val TEN = (1..10).map { link(it, "https://site$it.example.com", "Link $it") }

@Composable
private fun SectionFixture(state: EditProfileViewState) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Box(modifier = Modifier.width(360.dp).padding(16.dp)) {
                EditProfileLinksSection(state = state, onEvent = {})
            }
        }
    }
}

@Composable
private fun FormFixture(editor: LinkEditorState) {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Box(modifier = Modifier.width(320.dp).padding(16.dp)) {
                LinkEditorForm(editor = editor, onEvent = {})
            }
        }
    }
}

@PreviewTest
@Preview(name = "links-section-loaded-light", showBackground = true)
@Preview(name = "links-section-loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinksSectionLoadedScreenshot() {
    SectionFixture(EditProfileViewState(links = THREE.toImmutableList(), linksStatus = LinksLoadStatus.Loaded))
}

@PreviewTest
@Preview(name = "links-section-empty-light", showBackground = true)
@Preview(name = "links-section-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinksSectionEmptyScreenshot() {
    SectionFixture(EditProfileViewState(linksStatus = LinksLoadStatus.Loaded))
}

@PreviewTest
@Preview(name = "links-section-full-light", showBackground = true)
@Preview(name = "links-section-full-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinksSectionFullScreenshot() {
    SectionFixture(EditProfileViewState(links = TEN.toImmutableList(), linksStatus = LinksLoadStatus.Loaded))
}

@PreviewTest
@Preview(name = "links-section-loading-light", showBackground = true)
@Preview(name = "links-section-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinksSectionLoadingScreenshot() {
    SectionFixture(EditProfileViewState(linksStatus = LinksLoadStatus.Loading))
}

@PreviewTest
@Preview(name = "links-section-failed-light", showBackground = true)
@Preview(name = "links-section-failed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinksSectionFailedScreenshot() {
    SectionFixture(EditProfileViewState(linksStatus = LinksLoadStatus.Failed))
}

@PreviewTest
@Preview(name = "link-form-valid-light", showBackground = true)
@Preview(name = "link-form-valid-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkFormValidScreenshot() {
    FormFixture(LinkEditorState(urlInput = "patreon.com/alice", titleInput = "Support my work", titleGraphemes = 15))
}

@PreviewTest
@Preview(name = "link-form-invalid-light", showBackground = true)
@Preview(name = "link-form-invalid-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkFormInvalidScreenshot() {
    FormFixture(LinkEditorState(urlInput = "not a url"))
}

@PreviewTest
@Preview(name = "link-form-duplicate-light", showBackground = true)
@Preview(name = "link-form-duplicate-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkFormDuplicateScreenshot() {
    FormFixture(LinkEditorState(urlInput = "alice.example.com", isDuplicate = true))
}

@PreviewTest
@Preview(name = "link-form-warnings-light", showBackground = true)
@Preview(name = "link-form-warnings-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkFormWarningsScreenshot() {
    // A shortener over http triggers both non-blocking warnings at once.
    FormFixture(LinkEditorState(urlInput = "http://bit.ly/abc"))
}

@PreviewTest
@Preview(name = "link-form-title-over-limit-light", showBackground = true)
@Preview(name = "link-form-title-over-limit-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LinkFormTitleOverLimitScreenshot() {
    val title = "This title is far too long for a profile link pill"
    FormFixture(LinkEditorState(urlInput = "example.com", titleInput = title, titleGraphemes = title.length))
}
