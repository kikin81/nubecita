package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatSettingsRepository
import javax.inject.Inject

/**
 * Presenter for the Chat settings screen ("Who can message you"). Loads the
 * account-global `allowIncoming` declaration on init and persists changes with
 * an **optimistic save-on-tap**: tapping an option reflects it immediately,
 * writes in the background, and reverts to the last server-confirmed value
 * (plus a [ChatSettingsEffect.ShowSaveError] snackbar) if the write fails.
 *
 * Writes are single-flight latest-wins: a rapid re-tap cancels the in-flight
 * write and starts a new one, so the persisted value always tracks the latest
 * tap. [lastConfirmed] holds the last value the server accepted, so a failed
 * write reverts to a real state rather than an interim optimistic one.
 */
@HiltViewModel
internal class ChatSettingsViewModel
    @Inject
    constructor(
        private val repository: ChatSettingsRepository,
    ) : MviViewModel<ChatSettingsViewState, ChatSettingsEvent, ChatSettingsEffect>(ChatSettingsViewState()) {
        /** Last value the server accepted; the revert target on a failed write. */
        private var lastConfirmed: AllowIncoming? = null
        private var saveJob: Job? = null
        private var loadJob: Job? = null

        init {
            load()
        }

        override fun handleEvent(event: ChatSettingsEvent) {
            when (event) {
                is ChatSettingsEvent.OptionSelected -> onOptionSelected(event.value)
                ChatSettingsEvent.RetryLoad -> load()
            }
        }

        private fun load() {
            setState { copy(status = ChatSettingsLoadStatus.Loading) }
            // Single-flight (latest-wins): cancel any prior in-flight load — e.g.
            // a fast double-tap on Retry — so a superseded slow response can't
            // race ahead and overwrite the newer load's result.
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    repository
                        .getAllowIncoming()
                        .onSuccess { value ->
                            lastConfirmed = value
                            setState { copy(status = ChatSettingsLoadStatus.Loaded(selected = value)) }
                        }.onFailure {
                            setState { copy(status = ChatSettingsLoadStatus.LoadError) }
                        }
                }
        }

        private fun onOptionSelected(value: AllowIncoming) {
            val loaded = uiState.value.status as? ChatSettingsLoadStatus.Loaded ?: return
            // No-op when re-selecting the current value and nothing is in flight.
            if (loaded.selected == value && !loaded.isSaving) return

            // Optimistic: reflect the choice immediately, flag the in-flight write.
            setState { copy(status = ChatSettingsLoadStatus.Loaded(selected = value, isSaving = true)) }

            // Latest-wins: cancel any prior in-flight write before starting this one.
            saveJob?.cancel()
            saveJob =
                viewModelScope.launch {
                    repository
                        .setAllowIncoming(value)
                        .onSuccess {
                            lastConfirmed = value
                            updateLoaded { it.copy(isSaving = false) }
                        }.onFailure {
                            // Revert to the last server-confirmed value (not an
                            // interim optimistic one) and surface the failure.
                            val revertTo = lastConfirmed ?: value
                            updateLoaded { it.copy(selected = revertTo, isSaving = false) }
                            sendEffect(ChatSettingsEffect.ShowSaveError)
                        }
                }
        }

        /** Apply [transform] to the current [ChatSettingsLoadStatus.Loaded], if any. */
        private fun updateLoaded(transform: (ChatSettingsLoadStatus.Loaded) -> ChatSettingsLoadStatus.Loaded) {
            setState {
                val loaded = status as? ChatSettingsLoadStatus.Loaded ?: return@setState this
                copy(status = transform(loaded))
            }
        }
    }
