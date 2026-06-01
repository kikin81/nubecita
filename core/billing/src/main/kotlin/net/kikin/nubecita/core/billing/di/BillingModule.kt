package net.kikin.nubecita.core.billing.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.billing.BillingRepository
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.RevenueCatBillingRepository
import net.kikin.nubecita.core.billing.RevenueCatEntitlementRepository
import javax.inject.Singleton

// Binds the RevenueCat implementations. They are inert until
// `RevenueCatInitializer.initialize(...)` runs `Purchases.configure` from the
// production-flavor `AppInitializer`, so the bench graph resolves these but
// issues no SDK/network calls. NOT `internal` so tests can swap it via
// `@TestInstallIn(replaces = [BillingModule::class])` to bind the in-memory
// fakes (mirrors `core/profile/.../ActorProfileModule`). The `@Binds` methods
// stay `internal` — Hilt's generated factory lives in this module.
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    internal abstract fun bindEntitlementRepository(impl: RevenueCatEntitlementRepository): EntitlementRepository

    @Binds
    @Singleton
    internal abstract fun bindBillingRepository(impl: RevenueCatBillingRepository): BillingRepository
}
