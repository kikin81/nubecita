package net.kikin.nubecita.feature.widgets.impl.entitlement.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.widgets.impl.entitlement.AlwaysAllowedWidgetEntitlementGate
import net.kikin.nubecita.feature.widgets.impl.entitlement.WidgetEntitlementGate

/**
 * Binds the configurable-widget entitlement gate (D-C9). C binds the
 * always-allowed implementation; sub-project D rebinds it to an `isPro`-backed
 * gate. Ships in the production graph only (the configurable widget lives in
 * `:feature:widgets:impl`, which `:app` pulls via `productionImplementation`).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WidgetEntitlementModule {
    @Binds
    abstract fun bindWidgetEntitlementGate(impl: AlwaysAllowedWidgetEntitlementGate): WidgetEntitlementGate
}
