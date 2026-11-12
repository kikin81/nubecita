package net.kikin.nubecita.wear.launch

import net.kikin.nubecita.wear.MainActivity
import net.kikin.nubecita.wear.navigation.WearConvos
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.navigation.WearLanding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearLaunchTest {
    @Test
    fun launchTargetOf_mapsEachAction() {
        assertEquals(WearLaunchTarget.Feed, launchTargetOf(MainActivity.ACTION_OPEN_FEED, restoring = false))
        assertEquals(WearLaunchTarget.Messages, launchTargetOf(MainActivity.ACTION_OPEN_MESSAGES, restoring = false))
        assertEquals(WearLaunchTarget.NewPost, launchTargetOf(MainActivity.ACTION_NEW_POST, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf(null, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf("bogus", restoring = false))
    }

    @Test
    fun launchTargetOf_ignoresActionWhenRestoring() {
        // A recreated activity restores its own back stack; replaying the link would reopen it.
        assertEquals(WearLaunchTarget.Landing, launchTargetOf(MainActivity.ACTION_NEW_POST, restoring = true))
    }

    @Test
    fun initialBackStack_keepsLandingUnderneath() {
        assertEquals(listOf(WearLanding), initialBackStack(WearLaunchTarget.Landing))
        assertEquals(listOf(WearLanding), initialBackStack(WearLaunchTarget.NewPost))
        assertEquals(listOf(WearLanding, WearHome), initialBackStack(WearLaunchTarget.Feed))
        assertEquals(listOf(WearLanding, WearConvos), initialBackStack(WearLaunchTarget.Messages))
    }

    @Test
    fun resolvePendingNewPost_waitsForThePhoneToAnswer() {
        // The feed loads from Room before the phone's capability check answers; until it does, wait.
        assertEquals(PendingNewPost.Wait, resolvePendingNewPost(postingChecked = false, postingAvailable = false))
        assertEquals(PendingNewPost.Open, resolvePendingNewPost(postingChecked = true, postingAvailable = true))
        assertEquals(PendingNewPost.Drop, resolvePendingNewPost(postingChecked = true, postingAvailable = false))
    }
}
