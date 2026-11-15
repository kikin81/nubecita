package net.kikin.nubecita.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import net.kikin.nubecita.wear.launch.EXTRA_LAUNCH_TARGET
import net.kikin.nubecita.wear.launch.launchTargetOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() so the branded splash (WO-V15) hands
        // off to the post-splash theme without a flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: a recreated activity restores its own back stack.
        val launchTarget = launchTargetOf(intent?.getStringExtra(EXTRA_LAUNCH_TARGET), intent?.action, restoring = savedInstanceState != null)
        setContent { NubecitaWearApp(launchTarget = launchTarget) }
    }

    companion object {
        // Launch actions of earlier versions. Nothing sends them any more: the
        // complication and the launcher widget now name the target in an extra
        // (see launchIntent). They are still read, because a PendingIntent
        // handed out before an update keeps working after it.
        const val ACTION_OPEN_MESSAGES = "net.kikin.nubecita.wear.action.OPEN_MESSAGES"
        const val ACTION_OPEN_FEED = "net.kikin.nubecita.wear.action.OPEN_FEED"
        const val ACTION_NEW_POST = "net.kikin.nubecita.wear.action.NEW_POST"
    }
}
