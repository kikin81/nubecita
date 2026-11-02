package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * State holder that synchronizes the active gallery page index for a post URI
 * between the full-screen media viewer and the feed's post image card.
 */
class GalleryStateSync(
    private val activeIndices: SnapshotStateMap<String, Int> = mutableStateMapOf(),
) {
    /**
     * Updates the active image index for [postUri].
     */
    fun setActiveIndex(
        postUri: String,
        index: Int,
    ) {
        if (postUri.isNotBlank()) {
            activeIndices[postUri] = index
        }
    }

    /**
     * Gets the active image index for [postUri], or null if not set.
     */
    fun getActiveIndex(postUri: String): Int? = activeIndices[postUri]

    /**
     * Clears synced index for [postUri].
     */
    fun clear(postUri: String) {
        activeIndices.remove(postUri)
    }
}

@Composable
fun rememberGalleryStateSync(): GalleryStateSync = remember { GalleryStateSync() }

@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalGalleryStateSync: ProvidableCompositionLocal<GalleryStateSync> =
    compositionLocalOf { GalleryStateSync() }
