package net.kikin.nubecita.bootstrap

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsInstanceIdProvider
import net.kikin.nubecita.core.analytics.IsPro
import net.kikin.nubecita.core.analytics.NotificationsEnabled
import net.kikin.nubecita.core.analytics.SelfHosted
import net.kikin.nubecita.core.analytics.Theme
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.RevenueCatInitializer
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.core.push.NotificationsEnabledSource
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.kikin.nubecita.core.analytics.ThemePreference as AnalyticsThemePreference
import net.kikin.nubecita.core.preferences.ThemePreference as PrefsThemePreference

/**
 * Verifies the coordinator mirrors each source flow into the right GA4 user
 * property — both the initial value at startup AND every later transition.
 * Uses a recording [AnalyticsClient] (the PII-free typed catalog), not a mock,
 * so the asserted values are the real property objects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProAnalyticsCoordinatorTest {
    @Test
    fun `start mirrors the initial value of every source into its user property`() =
        runTest(UnconfinedTestDispatcher()) {
            val analytics = RecordingAnalyticsClient()
            coordinatorWith(
                analytics = analytics,
                isPro = MutableStateFlow(false),
                theme = MutableStateFlow(PrefsThemePreference.SYSTEM),
                selfHosted = MutableStateFlow(false),
                notificationsEnabled = MutableStateFlow(false),
            ).start()
            runCurrent()

            assertTrue(analytics.properties.contains(IsPro(false)))
            assertTrue(analytics.properties.contains(Theme(AnalyticsThemePreference.System)))
            assertTrue(analytics.properties.contains(SelfHosted(false)))
            assertTrue(analytics.properties.contains(NotificationsEnabled(false)))
        }

    @Test
    fun `theme preference is mapped to the analytics wire enum and tracks changes`() =
        runTest(UnconfinedTestDispatcher()) {
            val analytics = RecordingAnalyticsClient()
            val theme = MutableStateFlow(PrefsThemePreference.SYSTEM)
            coordinatorWith(analytics = analytics, theme = theme).start()
            runCurrent()

            theme.value = PrefsThemePreference.DARK
            runCurrent()
            theme.value = PrefsThemePreference.LIGHT
            runCurrent()

            val themes = analytics.properties.filterIsInstance<Theme>()
            assertEquals(
                listOf(
                    AnalyticsThemePreference.System,
                    AnalyticsThemePreference.Dark,
                    AnalyticsThemePreference.Light,
                ),
                themes.map { it.preference },
            )
        }

    @Test
    fun `self-hosted and notifications transitions are mirrored`() =
        runTest(UnconfinedTestDispatcher()) {
            val analytics = RecordingAnalyticsClient()
            val selfHosted = MutableStateFlow(false)
            val notificationsEnabled = MutableStateFlow(false)
            coordinatorWith(
                analytics = analytics,
                selfHosted = selfHosted,
                notificationsEnabled = notificationsEnabled,
            ).start()
            runCurrent()

            selfHosted.value = true
            notificationsEnabled.value = true
            runCurrent()

            assertTrue(analytics.properties.contains(SelfHosted(true)))
            assertTrue(analytics.properties.contains(NotificationsEnabled(true)))
        }

    // Builds a coordinator wired to controllable source flows; collaborators the
    // user-property path doesn't exercise (RevenueCat link, instance-id) are
    // relaxed mocks. Collectors run on the test's backgroundScope so they're
    // cancelled with the test.
    private fun kotlinx.coroutines.test.TestScope.coordinatorWith(
        analytics: AnalyticsClient,
        isPro: MutableStateFlow<Boolean> = MutableStateFlow(false),
        theme: MutableStateFlow<PrefsThemePreference> = MutableStateFlow(PrefsThemePreference.SYSTEM),
        selfHosted: MutableStateFlow<Boolean> = MutableStateFlow(false),
        notificationsEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): ProAnalyticsCoordinator {
        val entitlement = mockk<EntitlementRepository>(relaxed = true)
        every { entitlement.isPro } returns isPro
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        every { prefs.themePreference } returns theme
        val session = mockk<SessionStateProvider>(relaxed = true)
        every { session.isSelfHosted } returns selfHosted
        val notifSource = mockk<NotificationsEnabledSource>(relaxed = true)
        every { notifSource.notificationsEnabled } returns notificationsEnabled
        val instanceId = mockk<AnalyticsInstanceIdProvider>(relaxed = true)
        coEvery { instanceId.appInstanceId() } returns null
        return ProAnalyticsCoordinator(
            entitlementRepository = entitlement,
            analyticsClient = analytics,
            instanceIdProvider = instanceId,
            revenueCatInitializer = mockk<RevenueCatInitializer>(relaxed = true),
            userPreferencesRepository = prefs,
            sessionStateProvider = session,
            notificationsEnabledSource = notifSource,
            scope = backgroundScope,
        )
    }
}
