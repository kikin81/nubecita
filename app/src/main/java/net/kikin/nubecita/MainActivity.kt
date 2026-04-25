package net.kikin.nubecita

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.navigation.Navigator
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.login.api.Login
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var oauthRedirectBroker: OAuthRedirectBroker

    @Inject
    lateinit var sessionStateProvider: SessionStateProvider

    @Inject
    lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run BEFORE super.onCreate so the keep-on-screen
        // condition takes effect on the first frame. setKeepOnScreenCondition is called
        // from the platform's frame callback (not a coroutine), so we read state.value
        // synchronously off the StateFlow.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            NubecitaTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
        }
        // Cold-start case: the OAuth redirect arrived while the app was dead and Android
        // launched MainActivity with the redirect intent. The broker buffers until
        // LoginViewModel's init-time collector subscribes.
        handleIntent(intent)

        // Drive the initial session state read off the splash. Once refresh() completes,
        // state transitions to SignedIn or SignedOut; the collector below reacts.
        lifecycleScope.launch { sessionStateProvider.refresh() }

        // Reactive routing — every state transition (cold-start resolution, future signOut)
        // calls navigator.replaceTo(...). Idempotent: re-emitting the same state with the
        // same destination already on top of the stack is a Compose no-op.
        lifecycleScope.launch {
            sessionStateProvider.state.collect { state ->
                when (state) {
                    SessionState.Loading -> Unit
                    SessionState.SignedOut -> navigator.replaceTo(Login)
                    is SessionState.SignedIn -> navigator.replaceTo(Main)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm-start case: launchMode="singleTask" + the intent filter route the redirect
        // back to this existing instance instead of spawning a new task.
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        // Defense in depth: the manifest intent filter constrains scheme + path, but
        // also re-validate here so a misconfigured filter (or a future second deep link
        // sharing the scheme) can't leak unrelated URIs into the OAuth completeLogin
        // path. Path must match the redirect_uri declared in client-metadata.json.
        if (uri.scheme == OAUTH_REDIRECT_SCHEME && uri.path == OAUTH_REDIRECT_PATH) {
            val redirectUri = uri.toString()
            lifecycleScope.launch { oauthRedirectBroker.publish(redirectUri) }
            // Consume so configuration changes (rotation, theme switch, dark-mode flip)
            // don't re-fire the redirect handler and double-invoke completeLogin.
            intent.data = null
        }
    }

    private companion object {
        const val OAUTH_REDIRECT_SCHEME = "net.kikin.nubecita"
        const val OAUTH_REDIRECT_PATH = "/oauth-redirect"
    }
}
