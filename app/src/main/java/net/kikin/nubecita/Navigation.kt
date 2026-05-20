package net.kikin.nubecita

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.designsystem.component.NubecitaLogomark
import net.kikin.nubecita.navigation.NavigationEntryPoint
import net.kikin.nubecita.shell.MainShell

@Composable
fun MainNavigation(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint =
        remember(context) {
            EntryPointAccessors.fromApplication(context.applicationContext, NavigationEntryPoint::class.java)
        }
    val navigator = remember(entryPoint) { entryPoint.navigator() }
    val outerInstallers = remember(entryPoint) { entryPoint.outerEntryProviderInstallers() }
    val userPreferences = remember(entryPoint) { entryPoint.userPreferencesRepository() }

    CompositionLocalProvider(LocalAppNavigator provides navigator) {
        NavDisplay(
            modifier = modifier,
            backStack = navigator.backStack,
            onBack = { navigator.goBack() },
            // SceneSetupNavEntryDecorator is internal in nav3-ui 1.1.1 — NavDisplay applies
            // it itself. We supply the two decorators that are public + required for
            // hiltViewModel() inside NavEntries to work and for state to survive
            // recomposition / configuration change.
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    // The system SplashScreen API overlays this surface via
                    // setKeepOnScreenCondition while SessionStateProvider is still Loading.
                    // Once the bootstrap resolves, MainActivity's reactive collector calls
                    // navigator.replaceTo(Main or Login) and Splash leaves the back stack.
                    //
                    // We render the brand cloud here (rather than an empty Box) so the
                    // brief window between the system splash dismissing and the route
                    // swap shows brand identity instead of an empty background.
                    entry<Splash> {
                        // Background is painted by MainActivity's outer Surface
                        // (MaterialTheme.colorScheme.background) — no .background()
                        // here to avoid an extra draw pass on every cold start.
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NubecitaLogomark(modifier = Modifier.size(96.dp))
                        }
                    }
                    // Placeholder for the first-launch onboarding flow. The real screen
                    // lands in `:feature:onboarding:impl` (filed as `nubecita-lo3f.3`)
                    // and will replace this inline entry via an `@OuterShell`
                    // `EntryProviderInstaller` once that module exists. For now we
                    // render a minimal "skip" affordance so the flag-flip + navigation
                    // contract is exercisable end-to-end against the bootstrap routing
                    // in `MainActivity`.
                    entry<Onboarding> {
                        val scope = rememberCoroutineScope()
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                NubecitaLogomark(modifier = Modifier.size(96.dp))
                                Text(
                                    text = stringResource(R.string.onboarding_placeholder_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_placeholder_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(
                                    onClick = {
                                        // Flag-flip only. `MainActivity`'s
                                        // combine(sessionState, hasSeenOnboarding)
                                        // collector sees the upstream change and
                                        // drives the replaceTo(Login) itself —
                                        // single source of truth for post-
                                        // onboarding navigation. Calling
                                        // replaceTo here as well would clear+add
                                        // the Login entry twice and drop any
                                        // future rememberSaveable state on it.
                                        scope.launch { userPreferences.markOnboardingSeen() }
                                    },
                                ) {
                                    Text(stringResource(R.string.onboarding_placeholder_cta))
                                }
                            }
                        }
                    }
                    entry<Main> {
                        // No `safeDrawingPadding` here — `NavigationSuiteScaffold`
                        // inside `MainShell` consumes the bottom system inset
                        // itself and draws its `surfaceContainer` background
                        // under the gesture-bar / nav-bar region. Wrapping it
                        // in `safeDrawingPadding` carves the inset OUT of the
                        // scaffold's drawable area, leaving the outer
                        // MainActivity Surface (`background` color) bleeding
                        // through behind the gesture pill — exactly the gap
                        // `add-feed-scroll-to-top`'s manual smoke surfaced.
                        // Per the edge-to-edge skill: "DO NOT apply
                        // `safeDrawingPadding` or similar modifiers to the
                        // `NavigationSuiteScaffold` parent. This clips and
                        // prevents an edge-to-edge screen." Per-screen content
                        // inside the inner `NavDisplay` (FeedScreen, etc.)
                        // owns its own inset handling via Scaffold padding.
                        MainShell()
                    }
                    outerInstallers.forEach { installer -> installer() }
                },
        )
    }
}
