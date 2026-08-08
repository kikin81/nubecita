package net.kikin.nubecita.theme

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.preferences.ThemePreference
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.designsystem.AppTheme
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime holder for the resolved [AppTheme], and the bridge between
 * the persisted [ThemePreference] and the design system's rendering type.
 *
 * `null` means "not resolved yet", not "no theme". The preference is read
 * asynchronously from DataStore, so a naive collect with a `Dynamic` seed would
 * flash a light first frame for anyone who chose `Dark`; `MainActivity` instead
 * holds the splash while this is `null`. It is a process-lifetime latch read by
 * an Activity, not a remote-data wrapper at a VM→UI boundary, so it does not
 * conflict with the MVI rule against `Async<T>`-style states.
 *
 * `@Singleton` rather than an Activity-local `stateIn`: an Activity-scoped flow
 * would reset to `null` on every recreation — rotation, unfolding, a locale
 * change — re-showing the splash gate or flashing on each one. Started eagerly
 * on the application scope, this resolves once at process start and is non-null
 * for every Activity thereafter.
 */
@Singleton
class AppThemeState
    @Inject
    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        @param:ApplicationScope scope: CoroutineScope,
    ) {
        val appTheme: StateFlow<AppTheme?> =
            userPreferencesRepository.themePreference
                .map { it.toAppTheme() }
                .stateIn(scope, SharingStarted.Eagerly, initialValue = null)
    }

/**
 * Storage type → rendering type. Exhaustive by construction, so adding a
 * `CUSTOM` preference is a compile error here rather than a silent default.
 */
internal fun ThemePreference.toAppTheme(): AppTheme =
    when (this) {
        ThemePreference.DYNAMIC -> AppTheme.Dynamic
        ThemePreference.LIGHT -> AppTheme.Light
        ThemePreference.DARK -> AppTheme.Dark
    }
