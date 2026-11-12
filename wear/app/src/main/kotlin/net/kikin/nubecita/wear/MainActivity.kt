package net.kikin.nubecita.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import net.kikin.nubecita.wear.launch.launchTargetOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() so the branded splash (WO-V15) hands
        // off to the post-splash theme without a flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: a recreated activity restores its own back stack.
        val launchTarget = launchTargetOf(intent?.action, restoring = savedInstanceState != null)
        setContent { NubecitaWearApp(launchTarget = launchTarget) }
    }

    companion object {
        /** Opens straight to the conversation list (the unread-DMs complication's and launcher widget's tap). */
        const val ACTION_OPEN_MESSAGES = "net.kikin.nubecita.wear.action.OPEN_MESSAGES"

        /** Opens straight to the feed (the launcher widget's Home). */
        const val ACTION_OPEN_FEED = "net.kikin.nubecita.wear.action.OPEN_FEED"

        /** Opens straight into the New post text input (the launcher widget's New post). */
        const val ACTION_NEW_POST = "net.kikin.nubecita.wear.action.NEW_POST"
    }
}
