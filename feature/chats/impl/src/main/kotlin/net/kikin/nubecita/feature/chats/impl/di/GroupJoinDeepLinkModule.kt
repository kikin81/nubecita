package net.kikin.nubecita.feature.chats.impl.di

import android.content.Intent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.IntentActionFilter
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
import net.kikin.nubecita.core.common.navigation.uriDeepLinkMatcher
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview

/**
 * Registers the matcher that turns a tapped group invite link
 * (`https://nubecita.app/group/join/{code}`) into a [GroupJoinPreview] NavKey. The `{code}`
 * placeholder maps directly to the route's only field, so `MainActivity.handleIntent`'s generic
 * `else -> matched` branch publishes it to the `DeepLinkRouter` unchanged — no `MainActivity` edit.
 * `MainShell` is composed only when signed-in, and the router is a buffered-channel singleton, so a
 * link tapped while signed-out is held through login then drained. Mirrors `ChatDeepLinkModule`.
 *
 * `accept` rejects a blank code at the matcher boundary.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object GroupJoinDeepLinkModule {
    @Provides
    @IntoSet
    fun provideGroupJoinDeepLinkMatcher(): NavKeyDeepLinkMatcher =
        uriDeepLinkMatcher(
            uriPattern = "https://nubecita.app/group/join/{code}",
            serializer = GroupJoinPreview.serializer(),
            filters = listOf(IntentActionFilter(Intent.ACTION_VIEW)),
            accept = { it.code.isNotBlank() },
        )
}
