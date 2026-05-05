package net.kikin.nubecita.feature.composer.impl.data

import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.awaitCancellation
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi
import javax.inject.Inject

/**
 * Stub [ParentFetchSource] for wtq.3's skeleton VM. Suspends
 * indefinitely (`awaitCancellation()`), keeping the VM's reply-mode
 * lifecycle pinned at `ParentLoadStatus.Loading` until the parent
 * fetch is cancelled (e.g., user navigates away).
 *
 * Replaced in wtq.6 by an `app.bsky.feed.getPostThread`-backed
 * implementation. Until then:
 * - In production code paths, reply-mode entries to the composer
 *   show the loading skeleton forever — acceptable for a feature-
 *   gated build path that's not yet user-reachable (`MainShell`
 *   doesn't wire `ComposerRoute` into a feed reply affordance until
 *   wtq.10).
 * - In tests, the stub is replaced via the VM's constructor
 *   parameter (no Hilt at the test boundary) with controllable
 *   fakes that complete deterministically.
 */
internal class StubParentFetchSource
    @Inject
    constructor() : ParentFetchSource {
        override suspend fun fetchParent(uri: AtUri): Result<ParentPostUi> {
            awaitCancellation()
        }
    }
