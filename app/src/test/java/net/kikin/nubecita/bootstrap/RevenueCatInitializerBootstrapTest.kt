package net.kikin.nubecita.bootstrap

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.billing.RevenueCatInitializer
import org.junit.jupiter.api.Test

/**
 * The RevenueCat [AppInitializer] contribution must NOT run
 * `Purchases.configure` (via [RevenueCatInitializer.initialize]) on the thread
 * that invokes `start()`. `NubecitaApplication.onCreate` invokes every
 * initializer's `start()` on `Dispatchers.Main`, and `Purchases.configure`
 * synchronously builds Tink's Ed25519 verifier (heavy `BigInteger` crypto in a
 * static init) — doing that inline on Main ANRs on slow devices (regressed
 * Crashlytics issue ddc0a372, nubecita-wrld). So `start()` must only *dispatch*
 * the work onto the application scope and return immediately, mirroring
 * `provideSharedMediaSweepInitializer`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RevenueCatInitializerBootstrapTest {
    @Test
    fun `start dispatches configure to the app scope instead of running it inline`() =
        runTest {
            val initializer = mockk<RevenueCatInitializer>(relaxed = true)
            val proAnalytics = mockk<ProAnalyticsCoordinator>(relaxed = true)

            val appInitializer =
                ProductionBootstrapModule.provideRevenueCatInitializer(
                    initializer = initializer,
                    proAnalytics = proAnalytics,
                    applicationScope = backgroundScope,
                )

            // start() returns without touching the SDK: the caller thread (Main in
            // production) is never blocked by configure's crypto.
            appInitializer.start()
            verify(exactly = 0) { initializer.initialize(any(), any()) }

            // The dispatched work runs on the app scope once it's scheduled.
            runCurrent()
            verify(exactly = 1) { initializer.initialize(any(), any()) }
        }

    @Test
    fun `configure completes before pro-analytics starts`() =
        runTest {
            val initializer = mockk<RevenueCatInitializer>(relaxed = true)
            val proAnalytics = mockk<ProAnalyticsCoordinator>(relaxed = true)

            ProductionBootstrapModule
                .provideRevenueCatInitializer(
                    initializer = initializer,
                    proAnalytics = proAnalytics,
                    applicationScope = backgroundScope,
                ).start()
            runCurrent()

            // Ordering must survive the move off-main: configure sets
            // Purchases.sharedInstance synchronously, and ProAnalytics' Firebase
            // link reads Purchases.isConfigured, so it must run after initialize.
            verifyOrder {
                initializer.initialize(any(), any())
                proAnalytics.start()
            }
        }
}
