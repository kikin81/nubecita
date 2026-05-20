package net.kikin.nubecita.feature.onboarding.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * Pure-UI VM state for the onboarding flow. Today there is no observable
 * VM-owned state worth surfacing — the pager owns its `currentPage` in
 * the Composable per the project's "PagerState lives in the Composable,
 * like LazyListState" convention. We keep the `UiState` marker for
 * uniformity so the screen still wires up `viewModel.uiState` and any
 * future surface (e.g. an A/B copy variant tag) lands here without
 * reshaping the screen's collection plumbing.
 */
@Immutable data object OnboardingState : UiState

sealed interface OnboardingEvent : UiEvent {
    /**
     * User tapped Skip (visible on every page except the last). Skip and
     * [CompleteOnboarding] are intentionally distinct so VM-level
     * analytics or A/B branching can tell the two apart later without
     * inspecting page state at the call site.
     */
    data object Skip : OnboardingEvent

    /**
     * User tapped "Get started" on the final page.
     */
    data object CompleteOnboarding : OnboardingEvent
}

sealed interface OnboardingEffect : UiEffect {
    /**
     * Onboarding is done — the screen Composable replaces the back
     * stack with `Login`. The VM doesn't navigate itself; it persists
     * `hasSeenOnboarding=true`, then emits this effect. `MainActivity`'s
     * reactive collector would also re-fire on the flag flip, but the
     * screen-driven `replaceTo(Login)` lands first and is idempotent
     * with the collector's subsequent attempt.
     */
    data object NavigateToLogin : OnboardingEffect
}

/**
 * Per-page content. The enum is the single source of truth for "what
 * pages exist + in what order" — `totalPages` is `entries.size`, and the
 * pager reads `entries[index]` directly. Adding a third page (filed for
 * follow-up if the team retracts the 2-page decision) means appending
 * one enum entry plus three string resources.
 */
internal enum class OnboardingPage(
    @StringRes val eyebrow: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
) {
    Welcome(
        eyebrow = R.string.onboarding_page_welcome_eyebrow,
        title = R.string.onboarding_page_welcome_title,
        body = R.string.onboarding_page_welcome_body,
    ),
    Android(
        eyebrow = R.string.onboarding_page_android_eyebrow,
        title = R.string.onboarding_page_android_title,
        body = R.string.onboarding_page_android_body,
    ),
}
