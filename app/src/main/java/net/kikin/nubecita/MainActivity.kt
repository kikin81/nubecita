package net.kikin.nubecita

import android.content.Intent
import android.os.Build
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.navigation.Navigator
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
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

    @Inject
    lateinit var userPreferences: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // installSplashScreen() must run BEFORE super.onCreate so the splash claims the
        // first frame. The keep-on-screen predicate captures `sessionStateProvider`,
        // which Hilt's @AndroidEntryPoint generated base class injects during
        // super.onCreate — set the predicate AFTER super to keep the field-access edge
        // unambiguously safe (canonical Google SplashScreen sample order).
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // setKeepOnScreenCondition is invoked on every platform frame callback (not a
        // coroutine), so the lambda reads state.value synchronously off the StateFlow.
        splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }

        enableEdgeToEdge()
        // `enableEdgeToEdge` flips `isNavigationBarContrastEnforced` to `true`
        // by default, which has the system paint a translucent scrim under the
        // gesture-bar handle. With our `NavigationSuiteScaffold` already drawing
        // a `surfaceContainer` background to that region, the scrim layers on
        // top and creates a visible gap between the nav-suite labels and the
        // bottom edge of the screen. Disable it on API 29+ so the bottom-bar
        // surface extends fully to the gesture handle (per the edge-to-edge
        // skill checklist for any Activity hosting a `NavigationBar` /
        // `NavigationSuiteScaffold` bottom bar).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
        //
        // First-launch detection: a returning user (signed-in, or signed-out with the
        // flag persisted) goes to Main or Login as before. A fresh install sees Onboarding
        // first. Signed-in users implicitly count as "already onboarded" — if a session
        // exists but the flag was never set (e.g. upgrading from a pre-onboarding build),
        // we set it opportunistically so a future sign-out lands on Login, not Onboarding.
        lifecycleScope.launch {
            combine(
                sessionStateProvider.state,
                userPreferences.hasSeenOnboarding,
            ) { session, seen -> session to seen }
                .distinctUntilChanged()
                .collect { (session, seen) ->
                    when (session) {
                        SessionState.Loading -> Unit
                        SessionState.SignedOut ->
                            navigator.replaceTo(if (seen) Login else Onboarding)
                        // Signed-in users land on the adaptive `Main` shell (nubecita-8m4),
                        // which hosts NavigationSuiteScaffold + the inner NavDisplay. Feed
                        // is the start tab inside the shell, not a top-level destination on
                        // the outer back stack — Feed is now @MainShell-qualified and isn't
                        // installed in the outer NavDisplay's entry provider.
                        is SessionState.SignedIn -> {
                            if (!seen) userPreferences.markOnboardingSeen()
                            navigator.replaceTo(Main)
                        }
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
        // Defense in depth: the manifest intent filters constrain scheme + host + path
        // at the OS level, but re-validate here so a misconfigured filter (or a future
        // unrelated deep link sharing a scheme or host) can't leak unrelated URIs into
        // the OAuth completeLogin path. See [isOAuthRedirect] for the predicate and
        // its accompanying unit tests for the exact accepted shapes.
        if (isOAuthRedirect(scheme = uri.scheme, host = uri.host, path = uri.path)) {
            val redirectUri = uri.toString()
            lifecycleScope.launch { oauthRedirectBroker.publish(redirectUri) }
            // Consume so configuration changes (rotation, theme switch, dark-mode flip)
            // don't re-fire the redirect handler and double-invoke completeLogin.
            intent.data = null
        }
    }
}
