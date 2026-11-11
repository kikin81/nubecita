package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalSharedImageSurface
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute

/**
 * Opens a post's images in the full-screen viewer, at the tapped index.
 *
 * Without this an image tap in search fell through to the card's tap and
 * opened post detail, so reaching the gallery took a second tap
 * (nubecita-mdmd.4). The viewer is an outer-shell route, so it goes on the
 * app navigator, not MainShell's back stack, and it carries this surface's
 * `contextKey` so the shared image transition matches the card it came from.
 */
@Composable
internal fun rememberOpenMediaViewer(): (postUri: String, imageIndex: Int) -> Unit {
    val appNavigator = LocalAppNavigator.current
    val surface = LocalSharedImageSurface.current
    return remember(appNavigator, surface) {
        { postUri, imageIndex ->
            appNavigator.goTo(MediaViewerRoute(postUri = postUri, imageIndex = imageIndex, contextKey = surface))
        }
    }
}
