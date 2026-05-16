package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchActorsError
import net.kikin.nubecita.feature.search.impl.SearchActorsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchActorsState

@PreviewTest
@Preview(name = "people-tab-initial-loading-light", showBackground = true)
@Preview(name = "people-tab-initial-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleTabContentInitialLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleTabContent(
                state =
                    SearchActorsState(
                        loadStatus = SearchActorsLoadStatus.InitialLoading,
                        currentQuery = "alice",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "people-tab-empty-light", showBackground = true)
@Preview(name = "people-tab-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleTabContentEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleTabContent(
                state =
                    SearchActorsState(
                        loadStatus = SearchActorsLoadStatus.Empty,
                        currentQuery = "xyzqq",
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "people-tab-loaded-highlight-light", showBackground = true)
@Preview(name = "people-tab-loaded-highlight-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleTabContentLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleTabContent(
                state =
                    SearchActorsState(
                        currentQuery = "al",
                        loadStatus =
                            SearchActorsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        ActorUi(
                                            did = "did:plc:alice",
                                            handle = "alice.bsky.social",
                                            displayName = "Alice Chen",
                                            avatarUrl = null,
                                        ),
                                        ActorUi(
                                            did = "did:plc:alex",
                                            handle = "alex.bsky.social",
                                            displayName = "Alex Park",
                                            avatarUrl = null,
                                        ),
                                    ),
                                nextCursor = "c2",
                                endReached = false,
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "people-tab-loaded-appending-light", showBackground = true)
@Preview(name = "people-tab-loaded-appending-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleTabContentLoadedAppendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleTabContent(
                state =
                    SearchActorsState(
                        currentQuery = "al",
                        loadStatus =
                            SearchActorsLoadStatus.Loaded(
                                items =
                                    persistentListOf(
                                        ActorUi(
                                            did = "did:plc:alice",
                                            handle = "alice.bsky.social",
                                            displayName = "Alice Chen",
                                            avatarUrl = null,
                                        ),
                                    ),
                                nextCursor = "c2",
                                endReached = false,
                                isAppending = true,
                            ),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "people-tab-initial-error-network-light", showBackground = true)
@Preview(name = "people-tab-initial-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleTabContentInitialErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleTabContent(
                state =
                    SearchActorsState(
                        loadStatus =
                            SearchActorsLoadStatus.InitialError(error = SearchActorsError.Network),
                        currentQuery = "alice",
                    ),
                onEvent = {},
            )
        }
    }
}
