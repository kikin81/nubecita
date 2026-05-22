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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.fromIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.navigation.DeepLinkRouter
import net.kikin.nubecita.core.common.navigation.NavKeyDeepLinkMatcher
import net.kikin.nubecita.core.common.navigation.Navigator
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import timber.log.Timber
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

    @Inject
    lateinit var deepLinkMatchers: Set<@JvmSuppressWildcards NavKeyDeepLinkMatcher>

    @Inject
    lateinit var deepLinkRouter: DeepLinkRouter

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
            NubecitaTheme {
                // testTagsAsResourceId surfaces Compose `Modifier.testTag(...)`
                // values to UIAutomator as Android resource ids — required so
                // the :benchmark Macrobenchmark module can locate Compose
                // nodes via the single-arg `By.res("<tag>")`. Compose tags
                // surface as bare `resource-id` values (no package qualifier),
                // so the two-arg `By.res(packageName, id)` form silently never
                // matches. The flag belongs at the topmost composable so
                // every descendant's testTag participates, regardless of
                // which feature module declared it.
                Surface(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .semantics { testTagsAsResourceId = true },
                    color = MaterialTheme.colorScheme.background,
                ) { MainNavigation() }
            }
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
        //
        // Side-effects live in `onEach`, not `collect`, so a throw inside the
        // routing logic is observed by the downstream `.catch` rather than
        // tearing the coroutine down silently. The `.catch` itself is a
        // last-resort backstop — by the time it fires, both flows already
        // absorb their expected failure modes (preferences swallow IOException
        // upstream; the inner try/catch wraps the opportunistic write). If
        // catch ever lands, the activity loses reactive routing for the rest
        // of its lifecycle, which is the correct degraded behavior: the user
        // is on *some* screen and we'd rather they see "stuck" than crash.
        combine(
            sessionStateProvider.state,
            userPreferences.hasSeenOnboarding,
        ) { session, seen -> session to seen }
            .distinctUntilChanged()
            .onEach { (session, seen) ->
                try {
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
                            // Don't let a transient preferences write failure cancel the
                            // routing of THIS emission — the user can re-see onboarding
                            // once on the next launch in the worst case; that's strictly
                            // better than failing to route to Main right now.
                            if (!seen) {
                                try {
                                    userPreferences.markOnboardingSeen()
                                } catch (error: Exception) {
                                    Timber.w(error, "Failed to persist hasSeenOnboarding=true for signed-in user")
                                }
                            }
                            navigator.replaceTo(Main)
                        }
                    }
                } catch (error: Exception) {
                    Timber.e(error, "Routing side-effect threw for ($session, seen=$seen)")
                }
            }.catch { error -> Timber.e(error, "Bootstrap routing flow threw upstream; navigation will not react to further state changes") }
            .launchIn(lifecycleScope)
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
            return
        }

        // Deep-link branch: matchers are sorted by `patternSpecificity`
        // descending (path-segment count from the URI pattern) so the
        // first non-null match in the scan is deterministically the
        // most-specific shape. Hilt's `Set<T>` iteration order is not a
        // contract, so we cannot rely on Provides-declaration order to
        // break ties between e.g. `/profile/{h}/post/{r}` (4 segments)
        // and `/profile/{h}` (2 segments) — sorting first makes the
        // outcome stable. The matcher set is empty in this spike PR —
        // children kf6k.2 / kf6k.3 add the profile and post matchers
        // via `@Provides @IntoSet`. Plumbing ships here so those
        // children only need to register matchers. See decision
        // nubecita-kf6k.4 for the rationale and the source citations.
        val request = DeepLinkRequest.fromIntent(intent)
        val matched =
            deepLinkMatchers
                .sortedByDescending { it.patternSpecificity }
                .firstNotNullOfOrNull { it.match(request) }
        if (matched != null) {
            lifecycleScope.launch { deepLinkRouter.publish(matched) }
            // Consume the data so a configuration change doesn't re-fire
            // the same deep link on the rebuilt MainShell.
            intent.data = null
        }
    }
}
