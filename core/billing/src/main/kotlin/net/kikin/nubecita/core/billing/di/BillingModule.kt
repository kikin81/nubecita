package net.kikin.nubecita.core.billing.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.billing.BillingRepository
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.FakeBillingRepository
import net.kikin.nubecita.core.billing.FakeEntitlementRepository
import javax.inject.Singleton

// Binds the in-memory fakes for now; `nubecita-q5ge.2` replaces these with the
// RevenueCat implementations. NOT `internal` so downstream instrumentation
// tests can swap it via `@TestInstallIn(replaces = [BillingModule::class])`
// (mirrors `core/profile/.../ActorProfileModule`). The `@Binds` methods stay
// `internal` — Hilt's generated factory lives in this module.
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    internal abstract fun bindEntitlementRepository(impl: FakeEntitlementRepository): EntitlementRepository

    @Binds
    @Singleton
    internal abstract fun bindBillingRepository(impl: FakeBillingRepository): BillingRepository
}
