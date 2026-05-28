package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.collections.immutable.persistentListOf
import javax.inject.Inject

/**
 * Bench-flavor stand-in for [FeedRepository]. Returns an empty
 * [TimelinePage] on every call so the bench APK's `FeedScreen` lands on
 * the empty-state UI rather than the `InitialError` branch that the
 * production [DefaultFeedRepository] would surface under the bench
 * `FakeXrpcClientProvider` (which throws `NoSessionException` on every
 * call to `authenticated()`).
 *
 * The asset-backed JSON timeline loader — reading
 * `app/src/bench/assets/timeline.json` once via a `@Singleton` lazy parser
 * — lands in a follow-up commit (crmi.6 Section A2 Phase 3 — see
 * `bd show nubecita-xh99`). This skeleton is deliberately scope-minimal
 * so the build-infra commit that flips the flavor dimension on
 * `:feature:feed:impl` stays bisectable.
 *
 * Matches the production [DefaultFeedRepository]'s constructor-injection
 * shape (no `@Singleton`) — the repository is stateless under bench
 * until the lazy parser lands.
 */
internal class FakeFeedRepository
    @Inject
    constructor() : FeedRepository {
        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> = Result.success(EMPTY_PAGE)

        private companion object {
            val EMPTY_PAGE =
                TimelinePage(
                    feedItems = persistentListOf(),
                    nextCursor = null,
                    wirePosts = persistentListOf(),
                )
        }
    }
