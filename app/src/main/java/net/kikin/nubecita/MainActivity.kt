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
import net.kikin.nubecita.core.push.PushNotificationBuilder
import net.kikin.nubecita.core.push.PushPayload
import net.kikin.nubecita.core.video.PipController
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.postdetail.api.toPostDetailRoute
import net.kikin.nubecita.pip.ActivityPipBridge
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

    @Inject
    lateinit var pipController: PipController

    @Inject
    lateinit var sharedVideoPlayer: SharedVideoPlayer

    /**
     * The Activity-side Picture-in-Picture bridge (design D5). Created in
     * [onCreate] once Hilt has injected [pipController] / [sharedVideoPlayer];
     * the Compose layer reaches it via a `CompositionLocal` in a later task.
     */
    private lateinit var pipBridge: ActivityPipBridge

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

        // Activity PiP bridge: mirrors system PiP mode into PipController and
        // services the in-window play/pause action. Inert until the Compose
        // layer (a later task) drives updateParams; auto-enter / onUserLeaveHint
        // only fire when PipController.isEnabled (device supports PiP AND Pro).
        pipBridge = ActivityPipBridge(this, pipController, sharedVideoPlayer)
        pipBridge.start()

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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // API 26–30 manual-entry fallback (no setAutoEnterEnabled); no-op on 31+.
        pipBridge.onUserLeaveHint()
    }

    override fun onDestroy() {
        pipBridge.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm-start case: launchMode="singleTask" + the intent filter route the redirect
        // back to this existing instance instead of spawning a new task.
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data
        if (uri == null) {
            // FCM auto-display fallback: when the push gateway sends a
            // `notification` payload alongside `data`, Firebase Messaging on
            // the device auto-displays the notification while the app is
            // backgrounded — our custom PushNotificationBuilder's PendingIntent
            // never fires; instead, the tap delivers the launcher intent with
            // the `data` map flattened as Bundle extras. Reconstruct the
            // deep-link target from those extras here so the user lands on
            // PostDetail / Profile per the gateway-side AT-URI instead of the
            // start tab. Confirmed via on-device diagnostics on Pixel Tablet
            // and Pixel 10 Pro XL — see nubecita-1fy.6.
            handleFcmExtrasTap(intent)
            return
        }
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
            // `PostDeepLinkKey` is a pure transport key — the alpha03
            // UriDeepLinkMatcher needs a NavKey whose serializable
            // fields exactly mirror the URI placeholders
            // (`{handle}`, `{rkey}`), but the back-stack-eligible
            // destination is `PostDetailRoute(postUri = "at://...")`.
            // Convert here so the router and MainShell never see the
            // intermediate shape. See `PostDeepLinkKey` kdoc for the
            // rationale. Any other matched NavKey publishes as-is.
            val target =
                when (matched) {
                    is PostDeepLinkKey -> matched.toPostDetailRoute()
                    else -> matched
                }
            lifecycleScope.launch { deepLinkRouter.publish(target) }
            // Consume the data so a configuration change doesn't re-fire
            // the same deep link on the rebuilt MainShell.
            intent.data = null
        } else {
            // A URI passed the OAuth gate but no registered NavKey matcher
            // claimed it. Likely a misconfigured filter or a path shape we
            // forgot to register — surface in Crashlytics breadcrumbs
            // without spamming the issue inbox. Logged at debug level on
            // purpose (kf6k.5 §"Observability").
            //
            // The URI is redacted before logging: any `did:<method>:<id>`
            // path segment is truncated to its first 8 identifier chars
            // (see `String.redactDid()` for the project's PII-grade DID
            // convention), and userinfo / query / fragment are dropped —
            // they're not useful for diagnosing a missing matcher and
            // could carry arbitrary attacker-controlled content. Scheme +
            // host + port + path-shape are preserved because they're
            // exactly what we need to spot the configuration drift.
            Timber.d("Deep link did not match any registered matcher: ${uri.redactForLog()}")
            // Mirror the matched branch: consume the URI so a
            // configuration change (rotation, theme switch) doesn't
            // re-fire `handleIntent` on the rebuild and double-log the
            // same unmatched URI.
            intent.data = null
        }
    }

    /**
     * Reconstructs the deep-link target from FCM `data` extras carried on
     * an auto-displayed notification's tap intent (action `MAIN`,
     * `data = null`). Parses the extras back into a [PushPayload], derives
     * the canonical `nubecita://...` URI via the same helper
     * [PushNotificationBuilder] uses to build its PendingIntent, then runs
     * the URI through the registered deep-link matcher set.
     *
     * Recipient-mismatch defence: if `recipientDid` in the extras doesn't
     * match the currently signed-in DID (e.g. the gateway delivered to all
     * tokens but only one matches the active session on this device), drop
     * the tap rather than routing the wrong account into PostDetail.
     */
    private fun handleFcmExtrasTap(intent: Intent) {
        val extras = intent.extras ?: return
        val data = extras.toFcmDataMap() ?: return
        val payload = PushPayload.parse(data) ?: return
        val activeDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
        if (activeDid != null && payload.recipientDid != activeDid) {
            // Multi-account leak guard: the gateway delivered this push to
            // every registered token, but only the recipient's session
            // should route into PostDetail. Drop without surfacing — the
            // OS-auto-displayed notification stays visible (FCM owns that),
            // but tapping it on the wrong-account device becomes a no-op.
            intent.replaceExtras(null as Bundle?)
            return
        }
        val deepLink = PushNotificationBuilder.deepLinkFor(payload) ?: return
        val synthetic =
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).apply {
                setPackage(packageName)
            }
        val request = DeepLinkRequest.fromIntent(synthetic)
        val matched =
            deepLinkMatchers
                .sortedByDescending { it.patternSpecificity }
                .firstNotNullOfOrNull { it.match(request) }
        if (matched != null) {
            val target =
                when (matched) {
                    is PostDeepLinkKey -> matched.toPostDetailRoute()
                    else -> matched
                }
            lifecycleScope.launch { deepLinkRouter.publish(target) }
        }
        // Consume the extras so a configuration change doesn't re-publish.
        intent.replaceExtras(null as Bundle?)
    }

    /**
     * Projects an FCM-carried Bundle into the `Map<String, String>` shape
     * `PushPayload.parse` expects. Returns null if the required FCM data
     * keys aren't present (i.e. this is some other intent, not an
     * FCM-auto-displayed-notification tap).
     */
    private fun Bundle.toFcmDataMap(): Map<String, String>? {
        if (!containsKey(FCM_KEY_REASON) ||
            !containsKey(FCM_KEY_URI) ||
            !containsKey(FCM_KEY_ACTOR_DID) ||
            !containsKey(FCM_KEY_RECIPIENT_DID)
        ) {
            return null
        }
        return keySet()
            .mapNotNull { key ->
                val value = getString(key) ?: return@mapNotNull null
                key to value
            }.toMap()
    }

    private companion object {
        private const val FCM_KEY_REASON = "reason"
        private const val FCM_KEY_URI = "uri"
        private const val FCM_KEY_ACTOR_DID = "actorDid"
        private const val FCM_KEY_RECIPIENT_DID = "recipientDid"
    }
}

private fun android.net.Uri.redactForLog(): String {
    val scheme = scheme.orEmpty()
    // Use host (not authority) — authority is `[userinfo@]host[:port]` and
    // userinfo is attacker-controlled on an inbound VIEW intent. An incoming
    // `https://attacker:secret@bsky.app/profile/x` would otherwise commit
    // the userinfo to the log. Reconstruct from host + port instead.
    val host = host.orEmpty()
    val portSuffix = if (port >= 0) ":$port" else ""
    val redactedPath = redactDidsInPath(path.orEmpty())
    val authoritySeparator = if (host.isNotEmpty()) "//" else ""
    return "$scheme:$authoritySeparator$host$portSuffix$redactedPath"
}
