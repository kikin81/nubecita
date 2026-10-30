package net.kikin.nubecita.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate() so the branded splash (WO-V15) hands
        // off to the post-splash theme without a flash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Only on a fresh launch: a recreated activity restores its own back stack.
        val openMessages = savedInstanceState == null && intent?.action == ACTION_OPEN_MESSAGES
        setContent { NubecitaWearApp(openMessages = openMessages) }
    }

    companion object {
        /** Opens straight to the conversation list (the unread-DMs complication's tap). */
        const val ACTION_OPEN_MESSAGES = "net.kikin.nubecita.wear.action.OPEN_MESSAGES"
    }
}
