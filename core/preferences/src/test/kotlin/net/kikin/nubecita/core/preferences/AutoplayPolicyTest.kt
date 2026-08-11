package net.kikin.nubecita.core.preferences

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.common.network.NetworkStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The preference alone is not an answer — `WIFI_ONLY` depends on the
 * connection — so this pins the full (preference × network) matrix in the one
 * place that combines them.
 */
internal class AutoplayPolicyTest {
    private class FakeNetwork(
        metered: Boolean,
    ) : NetworkStatus {
        val state = MutableStateFlow(metered)

        /**
         * How many collectors have subscribed. The point of the policy using
         * `flatMapLatest` rather than `combine` is that this stays at zero for
         * the two preferences whose answer does not depend on the connection —
         * in production each subscription registers a system `NetworkCallback`.
         */
        var subscriptions = 0
            private set

        override val isMetered: Flow<Boolean> =
            state.asStateFlow().onSubscription { subscriptions++ }
    }

    private fun policy(
        preference: AutoplayPreference,
        network: NetworkStatus,
        gifs: Boolean = true,
    ) = AutoplayPolicy(
        preferences =
            object : UserPreferencesRepository {
                override val hasSeenOnboarding: Flow<Boolean> = flowOf(true)

                override suspend fun markOnboardingSeen() = Unit

                override val lastSelectedFeedUri: Flow<String?> = flowOf(null)

                override suspend fun setLastSelectedFeedUri(uri: String) = Unit

                override val themePreference: Flow<ThemePreference> = flowOf(ThemePreference.DYNAMIC)

                override suspend fun setThemePreference(preference: ThemePreference) = Unit

                // MutableStateFlow, not flowOf: DataStore-backed preference
                // flows never complete, and `videoAutoplayEnabled` switches on
                // this one, so a finite fake would make the policy flow
                // complete after a single value — an artifact of the fake that
                // production could never produce.
                override val autoplayPreference: Flow<AutoplayPreference> = MutableStateFlow(preference)

                override suspend fun setAutoplayPreference(preference: AutoplayPreference) = Unit

                override val autoplayGifs: Flow<Boolean> = flowOf(gifs)

                override suspend fun setAutoplayGifs(enabled: Boolean) = Unit
            },
        networkStatus = network,
    )

    @Test
    fun `the full preference by network matrix`() =
        runTest {
            val expected =
                listOf(
                    Triple(AutoplayPreference.ALWAYS, false, true),
                    Triple(AutoplayPreference.ALWAYS, true, true),
                    Triple(AutoplayPreference.NEVER, false, false),
                    Triple(AutoplayPreference.NEVER, true, false),
                    Triple(AutoplayPreference.WIFI_ONLY, false, true),
                    Triple(AutoplayPreference.WIFI_ONLY, true, false),
                )
            for ((preference, metered, allowed) in expected) {
                val actual = policy(preference, FakeNetwork(metered)).videoAutoplayEnabled.first()
                assertEquals(
                    allowed,
                    actual,
                    "$preference on a ${if (metered) "metered" else "unmetered"} connection",
                )
            }
        }

    // The reason this is a Flow and not a one-shot check: a wifi-only preference
    // has to stop applying the moment wifi does, without reopening the app.
    @Test
    fun `WIFI_ONLY stops allowing autoplay when the connection becomes metered`() =
        runTest {
            val network = FakeNetwork(metered = false)

            policy(AutoplayPreference.WIFI_ONLY, network).videoAutoplayEnabled.test {
                assertEquals(true, awaitItem(), "unmetered at start")

                network.state.value = true
                assertEquals(false, awaitItem(), "walking off wifi must stop autoplay")

                network.state.value = false
                assertEquals(true, awaitItem(), "and returning to wifi restores it")

                cancelAndIgnoreRemainingEvents()
            }
        }

    // ALWAYS and NEVER must not churn on network changes — a collector restarting
    // playback decisions on every capability callback would be a real cost.
    @Test
    fun `a network change emits nothing when the preference does not depend on it`() =
        runTest {
            val network = FakeNetwork(metered = false)

            policy(AutoplayPreference.ALWAYS, network).videoAutoplayEnabled.test {
                assertEquals(true, awaitItem())

                network.state.value = true
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a connection-independent preference never subscribes to the network`() =
        runTest {
            // Not a micro-optimisation: every subscription registers a
            // ConnectivityManager callback in production, and ALWAYS is the
            // default, so `combine` here would mean most installs paying for a
            // callback whose value is never read.
            for (preference in listOf(AutoplayPreference.ALWAYS, AutoplayPreference.NEVER)) {
                val network = FakeNetwork(metered = false)
                policy(preference, network).videoAutoplayEnabled.test {
                    awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
                assertEquals(0, network.subscriptions, "$preference subscribed to the network")
            }
        }

    @Test
    fun `a wifi-only preference does subscribe to the network`() =
        runTest {
            val network = FakeNetwork(metered = false)
            policy(AutoplayPreference.WIFI_ONLY, network).videoAutoplayEnabled.test {
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, network.subscriptions)
        }

    @Test
    fun `gif autoplay ignores the connection entirely`() =
        runTest {
            assertEquals(true, policy(AutoplayPreference.NEVER, FakeNetwork(metered = true), gifs = true).gifAutoplayEnabled.first())
            assertEquals(false, policy(AutoplayPreference.ALWAYS, FakeNetwork(metered = false), gifs = false).gifAutoplayEnabled.first())
        }
}
