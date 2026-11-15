package net.kikin.nubecita.wear.launch

import net.kikin.nubecita.wear.MainActivity
import net.kikin.nubecita.wear.navigation.WearConvos
import net.kikin.nubecita.wear.navigation.WearHome
import net.kikin.nubecita.wear.navigation.WearLanding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearLaunchTest {
    @Test
    fun launchTargetOf_mapsEachExtra() {
        assertEquals(WearLaunchTarget.Feed, launchTargetOf("feed", legacyAction = MAIN, restoring = false))
        assertEquals(WearLaunchTarget.Messages, launchTargetOf("messages", legacyAction = MAIN, restoring = false))
        assertEquals(WearLaunchTarget.NewPost, launchTargetOf("new_post", legacyAction = MAIN, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf("landing", legacyAction = MAIN, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf(null, legacyAction = MAIN, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf("bogus", legacyAction = MAIN, restoring = false))
    }

    @Test
    fun launchTargetOf_stillReadsTheActionsOfEarlierVersions() {
        // A complication can hold a PendingIntent from before the update.
        assertEquals(WearLaunchTarget.Feed, launchTargetOf(null, MainActivity.ACTION_OPEN_FEED, restoring = false))
        assertEquals(WearLaunchTarget.Messages, launchTargetOf(null, MainActivity.ACTION_OPEN_MESSAGES, restoring = false))
        assertEquals(WearLaunchTarget.NewPost, launchTargetOf(null, MainActivity.ACTION_NEW_POST, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf(null, null, restoring = false))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf(null, "bogus", restoring = false))
    }

    @Test
    fun launchTargetOf_ignoresTheLinkWhenRestoring() {
        // A recreated activity restores its own back stack; replaying the link would reopen it.
        assertEquals(WearLaunchTarget.Landing, launchTargetOf("new_post", legacyAction = MAIN, restoring = true))
        assertEquals(WearLaunchTarget.Landing, launchTargetOf(null, MainActivity.ACTION_NEW_POST, restoring = true))
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

    private companion object {
        const val MAIN = "android.intent.action.MAIN"
    }
}
