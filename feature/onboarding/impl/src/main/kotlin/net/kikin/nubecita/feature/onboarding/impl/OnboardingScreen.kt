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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaLogomark
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.designsystem.spacing

/**
 * Root composable for the onboarding flow. Wires the Hilt-injected
 * [OnboardingViewModel] to the stateless [OnboardingScreen] overload.
 *
 * Per the project's outer-nav convention, the screen does NOT navigate
 * itself — the VM persists `hasSeenOnboarding=true` (Skip / Get started),
 * and `MainActivity`'s combine collector observes the flag flip and
 * fires `navigator.replaceTo(Login)`. A single source of truth for
 * post-onboarding navigation avoids the double-`replaceTo` race that
 * would otherwise clear+re-add the Login entry and drop any
 * `rememberSaveable` state. The `OnboardingEffect.NavigateToLogin`
 * effect is collected here but intentionally a no-op for now; it stays
 * in the contract so analytics / one-shot UI work (toast, animation
 * trigger) can hook into it without re-shaping the VM.
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

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Intentionally no-op — MainActivity's bootstrap collector
                // drives the actual replaceTo(Login). See the KDoc above.
                OnboardingEffect.NavigateToLogin -> Unit
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

    // Use a Column layout instead of Scaffold's topBar/bottomBar slots.
    // M3 1.5.0-alpha20's Scaffold + HorizontalPager combination drives a
    // double-render glitch where the bottomBar slot's content gets re-drawn
    // inside the pager content area. Bisecting (no-pager → no ghost) and
    // (no-bottomBar → no ghost) confirmed the interaction. Column avoids
    // the slot-based subcomposition path entirely. Insets are applied
    // manually to the top + bottom siblings.
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                ),
    ) {
        OnboardingTopBar(
            showSkip = !isOnLastPage,
            onSkip = { onEvent(OnboardingEvent.Skip) },
        )
        // Adaptive width-cap: on tablets / unfolded foldables the pager
        // and bottom bar would otherwise stretch edge-to-edge and the
        // FAB would be visually divorced from the page copy. Capping at
        // 600dp centers the content with a backdrop on Expanded widths,
        // giving a modal-like read; on Compact widths the cap is a
        // no-op so phones render full-width as before. The TopAppBar
        // stays edge-to-edge — Skip is chrome and reads better aligned
        // with the right edge of the device.
        Column(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }
            OnboardingBottomBar(
                pagerState = pagerState,
                pageCount = pages.size,
                isOnLastPage = isOnLastPage,
                onComplete = { onEvent(OnboardingEvent.CompleteOnboarding) },
            )
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
        // The outer Column already consumes the horizontal portion of
        // safeDrawing. TopAppBar's default windowInsets adds horizontal
        // again, which double-pads on devices with non-zero side insets
        // (notch / curved edges / cutouts). Scope to top only — that's
        // the only inset bucket the outer Column doesn't already handle.
        windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
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

    // Bottom-edge safeDrawing inset only — horizontal insets are consumed
    // at the outer Column. The parent Column also handles its own top inset
    // via the TopAppBar's self-management, so no top inset is needed here.
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ).padding(
                    horizontal = MaterialTheme.spacing.s4,
                    vertical = MaterialTheme.spacing.s3,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3),
    ) {
        // Conditional rendering keeps the IconButton out of the
        // composition (and the a11y semantics tree) on page 0 — a
        // disabled IconButton with hidden content would otherwise leak
        // as an unlabeled button to TalkBack. The Spacer reserves the
        // same 48dp slot so the row layout doesn't reflow between
        // pages 0 and 1+.
        if (currentPage > 0) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                },
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back_content_description),
                    modifier = Modifier.mirror(),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
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
