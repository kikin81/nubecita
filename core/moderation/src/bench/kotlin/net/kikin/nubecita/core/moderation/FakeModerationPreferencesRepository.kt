package net.kikin.nubecita.core.moderation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic in-memory [ModerationPreferencesRepository] for the bench
 * flavor — no network, no account. Starts at [ModerationPrefs.DEFAULT] (adult
 * off) and mutates the cached value in place so the Content filters screen is
 * fully interactive in offline bench / smoke builds.
 */
@Singleton
internal class FakeModerationPreferencesRepository
    @Inject
    constructor() : ModerationPreferencesRepository {
        private val _prefs = MutableStateFlow(ModerationPrefs.DEFAULT)
        override val prefs: StateFlow<ModerationPrefs> = _prefs.asStateFlow()

        override suspend fun refresh() = Unit

        override suspend fun setAdultContentEnabled(enabled: Boolean) {
            _prefs.value = _prefs.value.copy(adultContentEnabled = enabled)
        }

        override suspend fun setVisibility(
            label: ContentLabel,
            visibility: LabelVisibility,
        ) {
            _prefs.value = _prefs.value.copy(visibilities = _prefs.value.visibilities + (label to visibility))
        }
    }
