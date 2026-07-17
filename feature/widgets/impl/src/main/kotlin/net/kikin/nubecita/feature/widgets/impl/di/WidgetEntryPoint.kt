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
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import net.kikin.nubecita.feature.widgets.impl.entitlement.WidgetEntitlementGate
import net.kikin.nubecita.feature.widgets.impl.image.WidgetThumbnailLoader

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

    /**
     * Render-time thumbnail resolution: pre-decoded store hit, or the
     * local-cache-only self-heal (nubecita-iqpc).
     */
    fun widgetThumbnailLoader(): WidgetThumbnailLoader

    /** On-demand refresh trigger (widget add / manual refresh). */
    fun widgetRefreshLauncher(): WidgetRefreshLauncher

    /**
     * Full re-render of every placed widget (no network) — used by
     * [net.kikin.nubecita.feature.widgets.impl.widget.WidgetPackageReplacedReceiver]
     * to replace a stale collection template after an app update (nubecita-ew77).
     */
    fun widgetUpdater(): WidgetUpdater

    /** Gate for the configurable (Pro) widget — always-allowed in C, isPro in D. */
    fun widgetEntitlementGate(): WidgetEntitlementGate
}

/**
 * Resolves [WidgetEntryPoint] off the singleton graph. Call from a
 * `GlanceAppWidget` / receiver where no Hilt injection is available; safe to
 * call per render — `fromApplication` just reads the already-built component.
 */
internal fun Context.widgetEntryPoint(): WidgetEntryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
