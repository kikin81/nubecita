package net.kikin.nubecita.feature.onboarding.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaLogomark
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.login.api.Login

/**
 * Root composable for the onboarding flow. Wires the Hilt-injected
 * [OnboardingViewModel] to the stateless [OnboardingScreen] overload and
 * collects [OnboardingEffect.NavigateToLogin] onto the outer Navigator.
 *
 * Per the project's outer-nav convention, the screen does not navigate
 * itself — it sends an effect and the screen-side `LaunchedEffect`
 * dispatches `replaceTo(Login)`. `MainActivity`'s combine collector also
 * sees the flag flip and re-fires the same `replaceTo(Login)`, which is
 * idempotent.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    // `uiState` is `OnboardingState` (an empty data object today). Collected
    // for shape uniformity with other screens — if onboarding ever grows
    // observable state, the screen already has the wiring.
    @Suppress("UNUSED_VARIABLE")
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalAppNavigator.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.NavigateToLogin -> navigator.replaceTo(Login)
            }
        }
    }

    OnboardingScreen(
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingScreen(
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pages = remember { OnboardingPage.entries }
    val pagerState =
        rememberPagerState(initialPage = initialPage) { pages.size }
    val isOnLastPage = pagerState.currentPage == pages.lastIndex

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            OnboardingTopBar(
                showSkip = !isOnLastPage,
                onSkip = { onEvent(OnboardingEvent.Skip) },
            )
        },
        bottomBar = {
            OnboardingBottomBar(
                pagerState = pagerState,
                pageCount = pages.size,
                isOnLastPage = isOnLastPage,
                onComplete = { onEvent(OnboardingEvent.CompleteOnboarding) },
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) { pageIndex ->
            OnboardingPageContent(page = pages[pageIndex])
        }
    }
}

@Composable
private fun OnboardingTopBar(
    showSkip: Boolean,
    onSkip: () -> Unit,
) {
    TopAppBar(
        title = {},
        actions = {
            // AnimatedVisibility keeps the slot stable while Skip fades
            // out on the last page — the content under the bar doesn't
            // reflow when the user reaches the end.
            AnimatedVisibility(
                visible = showSkip,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        },
    )
}

@Composable
private fun OnboardingBottomBar(
    pagerState: PagerState,
    pageCount: Int,
    isOnLastPage: Boolean,
    onComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentPage = pagerState.currentPage
    // 1-indexed progress so the wavy bar reads "you're on page N of M"
    // visually — first page is one segment filled, last page fully filled.
    val progress: () -> Float = { (currentPage + 1f) / pageCount }

    // Scaffold's `bottomBar` slot does NOT auto-apply safeDrawing insets to
    // its contents — the Material `BottomAppBar` self-manages insets but a
    // raw `Row` does not (per the edge-to-edge skill). Without the explicit
    // inset padding the progress bar + Next FAB draw under the gesture-bar
    // region. Apply only the horizontal + bottom edges; the top edge of
    // this row is already inside the screen so no top inset is needed.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).padding(
                    horizontal = MaterialTheme.spacing.s4,
                    vertical = MaterialTheme.spacing.s3,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3),
    ) {
        IconButton(
            onClick = {
                if (currentPage > 0) {
                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                }
            },
            enabled = currentPage > 0,
        ) {
            AnimatedVisibility(
                visible = currentPage > 0,
                enter = fadeIn(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back_content_description),
                    modifier = Modifier.mirror(),
                )
            }
        }

        LinearWavyProgressIndicator(
            progress = progress,
            modifier = Modifier.weight(1f),
        )

        if (isOnLastPage) {
            ExtendedFloatingActionButton(
                onClick = onComplete,
                icon = {
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.mirror(),
                    )
                },
                text = { Text(stringResource(R.string.onboarding_get_started)) },
            )
        } else {
            FloatingActionButton(
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                },
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.ArrowForward,
                    contentDescription = stringResource(R.string.onboarding_next_content_description),
                    modifier = Modifier.mirror(),
                )
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Hero: project logomark. Per the epic, we are not porting the
        // prototype's custom SVG clouds / feed-stack chips — brand mark +
        // native M3 typography do the work.
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center,
        ) {
            NubecitaLogomark(modifier = Modifier.size(120.dp))
        }

        Spacer(Modifier.height(MaterialTheme.spacing.s6))

        Text(
            text = stringResource(page.eyebrow).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.s2))

        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MaterialTheme.spacing.s3))

        Text(
            text = stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "First page · light", showBackground = true)
@Composable
private fun OnboardingScreenFirstPagePreview() {
    NubecitaTheme(dynamicColor = false) {
        OnboardingScreen(onEvent = {}, initialPage = 0)
    }
}

@Preview(name = "Last page · light", showBackground = true)
@Composable
private fun OnboardingScreenLastPagePreview() {
    NubecitaTheme(dynamicColor = false) {
        OnboardingScreen(onEvent = {}, initialPage = OnboardingPage.entries.lastIndex)
    }
}

@Preview(name = "Last page · dark", showBackground = true)
@Composable
private fun OnboardingScreenLastPageDarkPreview() {
    NubecitaTheme(darkTheme = true, dynamicColor = false) {
        OnboardingScreen(onEvent = {}, initialPage = OnboardingPage.entries.lastIndex)
    }
}
