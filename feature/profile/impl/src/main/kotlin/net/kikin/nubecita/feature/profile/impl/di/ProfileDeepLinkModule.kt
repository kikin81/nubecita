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
 * Registers the deep-link matchers that translate `bsky.app/profile/...`
 * and `nubecita://profile/...` URIs into [Profile] NavKeys.
 *
 * Two matchers — one per scheme — both target the same NavKey shape.
 * The alpha03 [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher]
 * requires an exact scheme compare (see kf6k.4), so a single matcher
 * cannot cover both `https://` and `nubecita://` and each scheme gets
 * its own provider.
 *
 * Both matchers:
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
