package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Backs the Content filters screen. The UI is a pure projection of
 * [ModerationPreferencesRepository.prefs]: the VM observes that cached
 * `StateFlow` and re-projects on every emission, and writes flow straight back
 * through the repository (which updates the cache optimistically, so the screen
 * reflects a successful change without a refetch). A failed write surfaces a
 * snackbar via [ContentFiltersEffect.ShowSaveError]; the cache (and thus the
 * UI) stays on the last good value.
 *
 * `refresh()` runs once on open to pull the latest from the account; a failure
 * is a silent no-op — the cached value (defaulting to adult-off, the fail-safe)
 * remains. The VM never touches navigation state.
 */
@HiltViewModel
internal class ContentFiltersViewModel
    @Inject
    constructor(
        private val repository: ModerationPreferencesRepository,
    ) : MviViewModel<ContentFiltersState, ContentFiltersEvent, ContentFiltersEffect>(
            repository.prefs.value.toContentFiltersState(),
        ) {
        init {
            repository.prefs
                .onEach { prefs -> setState { prefs.toContentFiltersState() } }
                .launchIn(viewModelScope)
            viewModelScope.launch {
                // Pull the latest; a failure leaves the cached/default (adult-off)
                // value. Let cancellation propagate — don't swallow it.
                try {
                    repository.refresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Silent no-op — the cached/default prefs remain.
                }
            }
        }

        override fun handleEvent(event: ContentFiltersEvent) {
            when (event) {
                is ContentFiltersEvent.AdultContentToggled ->
                    persist { repository.setAdultContentEnabled(event.enabled) }
                is ContentFiltersEvent.VisibilitySelected ->
                    persist { repository.setVisibility(event.label, event.visibility) }
            }
        }

        private fun persist(write: suspend () -> Unit) {
            viewModelScope.launch {
                // Only a real write failure surfaces a save error; cancellation
                // (e.g. navigating away mid-write) must propagate, not snackbar.
                try {
                    write()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    sendEffect(ContentFiltersEffect.ShowSaveError)
                }
            }
        }
    }

/** Fixed display order: Adult Content, Sexually Suggestive, Graphic Media, Non-sexual Nudity. */
private val CATEGORY_ORDER =
    listOf(ContentLabel.PORN, ContentLabel.SEXUAL, ContentLabel.GRAPHIC_MEDIA, ContentLabel.NUDITY)

/**
 * Projects the resolved [ModerationPrefs] into [ContentFiltersState]. Adult
 * categories are disabled (their picker greyed) while the master gate is off;
 * non-sexual nudity is never gated. Pure — unit-tested via the VM.
 */
internal fun ModerationPrefs.toContentFiltersState(): ContentFiltersState =
    ContentFiltersState(
        adultContentEnabled = adultContentEnabled,
        categories =
            CATEGORY_ORDER
                .map { label ->
                    CategoryRowUi(
                        label = label,
                        titleRes = label.titleRes(),
                        descriptionRes = label.descriptionRes(),
                        visibility = visibilityFor(label),
                        enabled = !label.isAdult || adultContentEnabled,
                    )
                }.toImmutableList(),
    )

@androidx.annotation.StringRes
private fun ContentLabel.titleRes(): Int =
    when (this) {
        ContentLabel.PORN -> R.string.content_filters_cat_porn
        ContentLabel.SEXUAL -> R.string.content_filters_cat_sexual
        ContentLabel.GRAPHIC_MEDIA -> R.string.content_filters_cat_graphic
        ContentLabel.NUDITY -> R.string.content_filters_cat_nudity
    }

@androidx.annotation.StringRes
private fun ContentLabel.descriptionRes(): Int =
    when (this) {
        ContentLabel.PORN -> R.string.content_filters_cat_porn_desc
        ContentLabel.SEXUAL -> R.string.content_filters_cat_sexual_desc
        ContentLabel.GRAPHIC_MEDIA -> R.string.content_filters_cat_graphic_desc
        ContentLabel.NUDITY -> R.string.content_filters_cat_nudity_desc
    }
