package net.kikin.nubecita.core.video

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.preferences.AutoplayPreference
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class PipControllerTest {
    private class FakePreferences(
        initialPip: Boolean,
    ) : UserPreferencesRepository {
        val pipFlow = MutableStateFlow(initialPip)
        override val pipEnabled: Flow<Boolean> get() = pipFlow.asStateFlow()

        override suspend fun setPipEnabled(enabled: Boolean) {
            pipFlow.value = enabled
        }

        override val hasSeenOnboarding: Flow<Boolean> = flowOf(true)

        override suspend fun markOnboardingSeen() = Unit

        override val lastSelectedFeedUri: Flow<String?> = flowOf(null)

        override suspend fun setLastSelectedFeedUri(uri: String) = Unit

        override val themePreference: Flow<ThemePreference> = flowOf(ThemePreference.DYNAMIC)

        override suspend fun setThemePreference(preference: ThemePreference) = Unit

        override val autoplayPreference: Flow<AutoplayPreference> = flowOf(AutoplayPreference.ALWAYS)

        override suspend fun setAutoplayPreference(preference: AutoplayPreference) = Unit

        override val autoplayGifs: Flow<Boolean> = flowOf(true)

        override suspend fun setAutoplayGifs(enabled: Boolean) = Unit
    }

    private fun controller(
        deviceSupportsPip: Boolean,
        pipEnabled: Boolean = true,
        scope: CoroutineScope,
    ): Pair<PipController, FakePreferences> {
        val prefs = FakePreferences(pipEnabled)
        return PipController(deviceSupportsPip, prefs, scope) to prefs
    }

    // isEnabled truth table: enabled only when BOTH device support and user setting hold.

    @Test
    fun `isEnabled is false when the device does not support PiP even if setting enabled`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, _) = controller(deviceSupportsPip = false, pipEnabled = true, scope = backgroundScope)
            assertFalse(pip.isEnabled.value)
        }

    @Test
    fun `isEnabled is false when the device supports PiP but setting disabled`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, _) = controller(deviceSupportsPip = true, pipEnabled = false, scope = backgroundScope)
            assertFalse(pip.isEnabled.value)
        }

    @Test
    fun `isEnabled is true only when device support and setting both hold`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, _) = controller(deviceSupportsPip = true, pipEnabled = true, scope = backgroundScope)
            assertTrue(pip.isEnabled.value)
        }

    @Test
    fun `isEnabled reacts to pipEnabled transitions on a supported device`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, prefs) = controller(deviceSupportsPip = true, pipEnabled = true, scope = backgroundScope)

            pip.isEnabled.test {
                assertTrue(awaitItem()) // enabled initially

                prefs.pipFlow.value = false
                assertFalse(awaitItem())

                prefs.pipFlow.value = true
                assertTrue(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isEnabled never flips on an unsupported device regardless of setting changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val (pip, prefs) = controller(deviceSupportsPip = false, pipEnabled = true, scope = backgroundScope)

            prefs.pipFlow.value = false
            assertFalse(pip.isEnabled.value)

            prefs.pipFlow.value = true
            assertFalse(pip.isEnabled.value)
        }

    // isInPip is the Activity-driven flag (set by the PiP bridge in a later task).

    @Test
    fun `isInPip defaults to false and reflects setInPip`() =
        runTest {
            val (pip, _) = controller(deviceSupportsPip = true, scope = backgroundScope)
            assertFalse(pip.isInPip.value)

            pip.setInPip(true)
            assertTrue(pip.isInPip.value)

            pip.setInPip(false)
            assertFalse(pip.isInPip.value)
        }
}
