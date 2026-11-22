package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchPacksError
import net.kikin.nubecita.feature.search.impl.SearchPacksLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchPacksState

private val PACKS =
    persistentListOf(
        StarterPackUiFixtures.packWithAvatars(slug = "android", name = "Android developers", memberCount = 42),
        StarterPackUiFixtures.pack(slug = "science", name = "Science writers and a name long enough to need a second line", memberCount = 1),
        StarterPackUiFixtures.pack(slug = "unknown", name = "No member count", memberCount = null),
        StarterPackUiFixtures.packWithAvatars(count = 2, slug = "two", name = "Two avatars", memberCount = 2),
    )

@PreviewTest
@Preview(name = "packs-tab-loaded-light", showBackground = true)
@Preview(name = "packs-tab-loaded-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PacksTabContentLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PacksTabContent(
                state =
                    SearchPacksState(
                        currentQuery = "android",
                        loadStatus = SearchPacksLoadStatus.Loaded(items = PACKS, nextCursor = "c2", endReached = false),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "packs-tab-appending-light", showBackground = true)
@Composable
private fun PacksTabContentAppendingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PacksTabContent(
                state =
                    SearchPacksState(
                        currentQuery = "android",
                        loadStatus = SearchPacksLoadStatus.Loaded(items = PACKS, nextCursor = "c2", endReached = false, isAppending = true),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "packs-tab-loading-light", showBackground = true)
@Preview(name = "packs-tab-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PacksTabContentLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PacksTabContent(
                state = SearchPacksState(currentQuery = "android", loadStatus = SearchPacksLoadStatus.InitialLoading),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "packs-tab-empty-light", showBackground = true)
@Preview(name = "packs-tab-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PacksTabContentEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PacksTabContent(
                state = SearchPacksState(currentQuery = "xyzqq", loadStatus = SearchPacksLoadStatus.Empty),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "packs-tab-error-light", showBackground = true)
@Preview(name = "packs-tab-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PacksTabContentErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PacksTabContent(
                state =
                    SearchPacksState(
                        currentQuery = "android",
                        loadStatus = SearchPacksLoadStatus.InitialError(SearchPacksError.Network),
                    ),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "pack-row-with-avatars-light", showBackground = true)
@Preview(name = "pack-row-with-avatars-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StarterPackRowWithAvatarsScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { StarterPackRow(pack = StarterPackUiFixtures.packWithAvatars(), onClick = {}) }
    }
}

@PreviewTest
@Preview(name = "pack-row-bare-light", showBackground = true)
@Composable
private fun StarterPackRowBareScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { StarterPackRow(pack = StarterPackUiFixtures.pack(memberCount = null), onClick = {}) }
    }
}
