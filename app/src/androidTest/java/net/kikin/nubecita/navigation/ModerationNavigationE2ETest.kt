package net.kikin.nubecita.navigation

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.MainActivity
import net.kikin.nubecita.R
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import net.kikin.nubecita.feature.moderation.impl.R as ModerationR
import net.kikin.nubecita.feature.profile.impl.R as ProfileR
import net.kikin.nubecita.feature.settings.impl.R as SettingsR

/**
 * End-to-end navigation smoke for the moderation routes (`nubecita-33cb`).
 * Boots the REAL [MainActivity] under the bench Hilt graph (so the full `:app`
 * dependency aggregation is exercised, unlike the synthetic
 * `MainShellPersistenceTest`) and drives the actual UI through
 * **Settings → Moderation → Blocked accounts** — the exact path that crashed
 * with "Unknown screen BlockedAccounts" when `:feature:moderation:impl` was
 * absent from `:app`.
 *
 * Labels are resolved from the app's own string resources (not hardcoded
 * English) so the test stays locale-independent.
 *
 * Bench-only: `FakeSessionStateProvider` boots signed-in straight into
 * `MainShell`. On the production flavor the app opens Login, so this
 * `assumeTrue`-skips there (the CI `instrumented` job runs the production
 * flavor; the deterministic [MainShellRouteCoverageTest] is the CI-side guard,
 * this is the realistic-navigation smoke for bench / local connected runs).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ModerationNavigationE2ETest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val grantNotifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setUp() {
        assumeTrue("E2E navigation requires the bench flavor (boots signed-in)", BuildConfig.FLAVOR == "bench")
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun settingsModerationBlockedAccountsOpensWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Top-level tabs + the settings gear are content-described; settings
            // rows surface as text. All labels come from app resources so the
            // selectors don't break under a non-English device locale.
            tap(By.desc(context.getString(R.string.main_shell_tab_you)), "You tab")
            tap(By.desc(context.getString(ProfileR.string.profile_action_settings)), "Settings gear")
            tap(By.text(context.getString(SettingsR.string.settings_moderation_label)), "Moderation row")
            tap(By.text(context.getString(SettingsR.string.settings_blocked_accounts_label)), "Blocked accounts row")
            // If the push had crashed (the regression), the app would be on the
            // launcher and this element would never appear.
            assertNotNull(
                "Blocked accounts screen never rendered — moderation route likely unregistered (nubecita-33cb)",
                device.wait(Until.findObject(By.text(context.getString(ModerationR.string.blocked_accounts_unblock))), WAIT_MS),
            )
        }
    }

    private fun tap(
        selector: BySelector,
        label: String,
    ) {
        val target =
            device.wait(Until.findObject(selector), WAIT_MS)
                ?: error("E2E navigation: \"$label\" never appeared within ${WAIT_MS}ms (a crash on the prior step?)")
        target.click()
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
