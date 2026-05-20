package net.kikin.nubecita.feature.onboarding.impl.testing

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.core.common.navigation.Navigator

/**
 * Test [Navigator] that records every `replaceTo` / `goTo` / `goBack`
 * call. Used by [net.kikin.nubecita.feature.onboarding.impl.OnboardingScreenInstrumentationTest]
 * to assert the screen-side failsafe path actually drives navigation
 * (the production navigator is also reachable via Hilt but using a
 * recorder gives the test cheap call-history assertions without
 * coupling to the real implementation's idempotency guard).
 *
 * [replaceToCalls] is a [SnapshotStateList] so it is safe to read
 * from the test thread while the Compose UI thread writes to it —
 * combined with the test reading inside `composeTestRule.runOnIdle { }`,
 * this avoids the visibility / ordering races a plain `MutableList`
 * would invite.
 */
internal class RecordingNavigator(
    start: NavKey,
) : Navigator {
    override val backStack: SnapshotStateList<NavKey> = mutableStateListOf(start)
    val replaceToCalls: SnapshotStateList<NavKey> = mutableStateListOf()

    override fun goTo(key: NavKey) {
        backStack.add(key)
    }

    override fun goBack() {
        backStack.removeLastOrNull()
    }

    override fun replaceTo(key: NavKey) {
        replaceToCalls.add(key)
        backStack.clear()
        backStack.add(key)
    }
}
