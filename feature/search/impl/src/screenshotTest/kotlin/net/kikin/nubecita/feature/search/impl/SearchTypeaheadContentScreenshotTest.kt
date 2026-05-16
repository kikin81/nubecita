package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme

private fun previewActor(
    did: String,
    handle: String,
    displayName: String?,
): ActorUi =
    ActorUi(
        did = did,
        handle = handle,
        displayName = displayName,
        avatarUrl = null,
    )

@PreviewTest
@Preview(name = "typeahead-loading-light", showBackground = true, heightDp = 400)
@Preview(name = "typeahead-loading-dark", showBackground = true, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchTypeaheadLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchTypeaheadContent(
                query = "alice",
                status = SearchTypeaheadStatus.Loading("alice"),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "typeahead-suggestions-top-only-light", showBackground = true, heightDp = 400)
@Preview(
    name = "typeahead-suggestions-top-only-dark",
    showBackground = true,
    heightDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchTypeaheadSuggestionsTopOnlyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchTypeaheadContent(
                query = "alice",
                status =
                    SearchTypeaheadStatus.Suggestions(
                        query = "alice",
                        topMatch = previewActor("did:plc:alice", "alice.bsky.social", "Alice Chen"),
                        people = persistentListOf(),
                    ),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "typeahead-suggestions-top-and-people-light", showBackground = true, heightDp = 600)
@Preview(
    name = "typeahead-suggestions-top-and-people-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchTypeaheadSuggestionsTopAndPeopleScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchTypeaheadContent(
                query = "al",
                status =
                    SearchTypeaheadStatus.Suggestions(
                        query = "al",
                        topMatch = previewActor("did:plc:alice", "alice.bsky.social", "Alice Chen"),
                        people =
                            persistentListOf(
                                previewActor("did:plc:alex", "alex.bsky.social", "Alex Park"),
                                previewActor("did:plc:albert", "albert.bsky.social", null),
                                previewActor("did:plc:alma", "alma.bsky.social", "Alma Rivera"),
                            ),
                    ),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "typeahead-no-results-light", showBackground = true, heightDp = 400)
@Preview(name = "typeahead-no-results-dark", showBackground = true, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchTypeaheadNoResultsScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchTypeaheadContent(
                query = "zxyqq",
                status = SearchTypeaheadStatus.NoResults("zxyqq"),
                onCommitQuery = {},
                onEvent = {},
            )
        }
    }
}
