package net.kikin.nubecita.feature.profile.impl.di

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.IntentActionFilter
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
import net.kikin.nubecita.core.common.navigation.uriDeepLinkMatcher
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.isValidActor
import net.kikin.nubecita.feature.profile.impl.isValidRkey

/**
 * Registers the deep-link matchers that translate URLs under the
 * `/profile/` path prefix into the corresponding NavKeys.
 *
 * Two URL shapes are covered, each in three scheme/host variants:
 *
 * 1. `/profile/{handle}` → [Profile] (the Profile screen). kf6k.2.
 * 2. `/profile/{handle}/post/{rkey}` → [PostDeepLinkKey] (intermediate
 *    transport key; `MainActivity.handleIntent` converts it to
 *    `PostDetailRoute(postUri = "at://<handle>/app.bsky.feed.post/<rkey>")`
 *    before publication to the deep-link router). kf6k.3.
 *
 * Six matchers total — one per (URL-shape × scheme/host) pair. The
 * alpha03 [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher]
 * requires exact scheme + authority matching (see kf6k.4), so a single
 * matcher cannot cover the three schemes. Per-shape ordering between
 * profile (2 path segments) and post (4 path segments) is handled by
 * the activity-side `sortedByDescending { it.patternSpecificity }`
 * scan — the post matchers outrank the profile matchers for any
 * `/profile/{h}/post/{r}` URL.
 *
 * Scheme/host variants:
 * - `https://nubecita.app/profile/...` is the verified App Link
 *   (autoVerify=true in the manifest). The OS routes these URLs to
 *   Nubecita without a chooser; used by surfaces WE control (push
 *   notifications, email links, etc).
 * - `https://bsky.app/profile/...` is the chooser-only path. We
 *   don't control bsky.app's assetlinks.json so verification isn't
 *   available; Nubecita registers as a candidate, never the default.
 * - `nubecita://profile/...` is the unambiguous custom scheme for
 *   in-app links / widgets / future share extensions.
 *
 * All matchers:
 * - Filter on `Intent.ACTION_VIEW` at the request boundary — non-VIEW
 *   actions fall through to the unmatched-link log. (kf6k.5 §"Matcher
 *   filters").
 * - Validate the extracted `{handle}` against AT Protocol handle / DID
 *   grammar via [isValidActor], and the `{rkey}` against TID grammar
 *   via [isValidRkey], before publishing the NavKey. Malformed input
 *   is rejected at the matcher boundary so it never reaches
 *   `ProfileViewModel` / `PostDetailViewModel` / their XRPCs. (kf6k.5
 *   §"Input validation at the ViewModel boundary").
 */
@Module
@InstallIn(SingletonComponent::class)
internal object ProfileDeepLinkModule {
    @Provides
    @IntoSet
    fun provideNubecitaAppProfileDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "https://nubecita.app/profile/{handle}",
            serializer = Profile.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { profile -> isValidActor(profile.handle) },
        )

    @Provides
    @IntoSet
    fun provideHttpsProfileDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "https://bsky.app/profile/{handle}",
            serializer = Profile.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { profile -> isValidActor(profile.handle) },
        )

    @Provides
    @IntoSet
    fun provideNubecitaProfileDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "nubecita://profile/{handle}",
            serializer = Profile.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { profile -> isValidActor(profile.handle) },
        )

    @Provides
    @IntoSet
    fun provideNubecitaAppPostDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "https://nubecita.app/profile/{handle}/post/{rkey}",
            serializer = PostDeepLinkKey.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { key -> isValidActor(key.handle) && isValidRkey(key.rkey) },
        )

    @Provides
    @IntoSet
    fun provideHttpsPostDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "https://bsky.app/profile/{handle}/post/{rkey}",
            serializer = PostDeepLinkKey.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { key -> isValidActor(key.handle) && isValidRkey(key.rkey) },
        )

    @Provides
    @IntoSet
    fun provideNubecitaPostDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "nubecita://profile/{handle}/post/{rkey}",
            serializer = PostDeepLinkKey.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { key -> isValidActor(key.handle) && isValidRkey(key.rkey) },
        )
}
