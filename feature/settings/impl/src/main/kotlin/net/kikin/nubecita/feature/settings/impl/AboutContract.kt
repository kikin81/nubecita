package net.kikin.nubecita.feature.settings.impl

import androidx.annotation.StringRes
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * A single "Special Thanks" row. The DID + blurb are a curated constant
 * ([net.kikin.nubecita.feature.settings.impl.AboutViewModel]); [handle] /
 * [displayName] / [avatarUrl] are hydrated live from `ActorRepository`, falling
 * back to the curated handle (and a null avatar) if the fetch fails.
 */
data class ThanksRowUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    @StringRes val blurbRes: Int,
)

/**
 * Flat, UI-ready state for the About screen. `isLoadingThanks` is true until the
 * first live hydration lands; the rows render immediately with their fallback
 * handle so the screen is never blank.
 */
data class AboutState(
    val thanks: ImmutableList<ThanksRowUi>,
    val isLoadingThanks: Boolean,
) : UiState

sealed interface AboutEvent : UiEvent {
    /** User tapped the "Source on GitHub" row. */
    data object SourceTapped : AboutEvent

    /** User tapped a Special Thanks row — open that contributor's profile. */
    data class ThanksRowTapped(
        val did: String,
    ) : AboutEvent

    /** User tapped the "Open source licenses" row. */
    data object LicensesTapped : AboutEvent

    /** User tapped the "Rate Nubecita" row. */
    data object RateAppTapped : AboutEvent
}

sealed interface AboutEffect : UiEffect {
    /** Open an external URL (Custom Tab) — the GitHub repository. */
    data class LaunchUri(
        val uri: String,
    ) : AboutEffect

    /**
     * Push a contributor's `Profile` onto MainShell's inner back stack. The
     * screen owns the `Profile` NavKey (the VM stays free of
     * `:feature:profile:api`), mirroring the Settings developer-profile push.
     */
    data class NavigateToProfile(
        val did: String,
    ) : AboutEffect

    /** Push the open-source licenses sub-route (`AboutLicenses`). */
    data object OpenLicenses : AboutEffect

    /** Open the app's Play Store listing (the manual rate path, not the in-app API). */
    data object OpenPlayStore : AboutEffect
}
