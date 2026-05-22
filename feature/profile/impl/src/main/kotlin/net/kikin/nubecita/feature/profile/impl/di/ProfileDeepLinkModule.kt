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
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.isValidActor

/**
 * Registers the deep-link matchers that translate `nubecita.app/profile/...`,
 * `bsky.app/profile/...`, and `nubecita://profile/...` URIs into [Profile]
 * NavKeys.
 *
 * Three matchers — one per scheme/host pair — all target the same
 * NavKey shape. The alpha03
 * [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher] requires
 * exact scheme + authority matching (see kf6k.4), so a single matcher
 * cannot cover all three.
 *
 * - `https://nubecita.app/profile/{handle}` is the verified App Link
 *   (autoVerify=true in the manifest). The OS routes these URLs to
 *   Nubecita without a chooser; used by surfaces WE control (push
 *   notifications, email links, etc).
 * - `https://bsky.app/profile/{handle}` is the chooser-only path. We
 *   don't control bsky.app's assetlinks.json so verification isn't
 *   available; Nubecita registers as a candidate, never the default.
 * - `nubecita://profile/{handle}` is the unambiguous custom scheme
 *   for in-app links / widgets / future share extensions.
 *
 * All three matchers:
 * - Filter on `Intent.ACTION_VIEW` at the request boundary — non-VIEW
 *   actions fall through to the unmatched-link log. (kf6k.5 §"Matcher
 *   filters").
 * - Validate the extracted `{handle}` against AT Protocol handle or
 *   DID grammar via [isValidActor] before publishing the NavKey.
 *   Malformed input is rejected at the matcher boundary so it never
 *   reaches `ProfileViewModel` / the `getProfile` XRPC. (kf6k.5
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
}
