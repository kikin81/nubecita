package net.kikin.nubecita.core.preferences

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        override val isMetered: Flow<Boolean> = state.asStateFlow()
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

                override val autoplayPreference: Flow<AutoplayPreference> = flowOf(preference)

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
    fun `gif autoplay ignores the connection entirely`() =
        runTest {
            assertEquals(true, policy(AutoplayPreference.NEVER, FakeNetwork(metered = true), gifs = true).gifAutoplayEnabled.first())
            assertEquals(false, policy(AutoplayPreference.ALWAYS, FakeNetwork(metered = false), gifs = false).gifAutoplayEnabled.first())
        }
}
