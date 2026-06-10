package net.kikin.nubecita.feature.widgets.impl.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.widgetsync.WidgetRefreshLauncher
import net.kikin.nubecita.feature.widgets.impl.image.WidgetThumbnailStore

/**
 * Hilt access seam for Jetpack Glance (D-C2).
 *
 * Glance widgets are **not** part of any Hilt composition — there is no
 * `@AndroidEntryPoint` receiver scope and `provideGlance` is a plain suspend
 * function. So the widgets reach the singleton graph through this `@EntryPoint`
 * resolved with [EntryPointAccessors.fromApplication], the documented Glance DI
 * pattern. Resolve it once per widget render (cheap) via [widgetEntryPoint] and
 * pull the collaborators the widget needs.
 *
 * Members are added as the sub-project lands its dependencies: the image
 * prefetch store (`WidgetThumbnailStore`) and the entitlement gate
 * (`WidgetEntitlementGate`) join here in tasks 4 and 7 respectively.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    /** The offline feed cache the widgets render via `head(feedKey, n)`. */
    fun feedRepository(): FeedRepository

    /** The signed-in account (its DID keys every [net.kikin.nubecita.core.feedcache.FeedKey]). */
    fun sessionStateProvider(): SessionStateProvider

    /** Saved/pinned feeds backing the configurable widget's configuration picker. */
    fun pinnedFeedsRepository(): PinnedFeedsRepository

    /** Pre-decoded thumbnails for the head posts (loaded off composition). */
    fun widgetThumbnailStore(): WidgetThumbnailStore

    /** On-demand refresh trigger (widget add / manual refresh). */
    fun widgetRefreshLauncher(): WidgetRefreshLauncher
}

/**
 * Resolves [WidgetEntryPoint] off the singleton graph. Call from a
 * `GlanceAppWidget` / receiver where no Hilt injection is available; safe to
 * call per render — `fromApplication` just reads the already-built component.
 */
internal fun Context.widgetEntryPoint(): WidgetEntryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
