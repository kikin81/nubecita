package net.kikin.nubecita.wear.gate

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.sync.WatchSync
import timber.log.Timber
import javax.inject.Inject

/**
 * Decides what the watch shows from the phone session it last received and
 * whether the paired phone has Nubecita.
 *
 * A signed-in session wins outright: content stays readable even when the
 * phone is out of reach. Without one, a missing phone app (or an iPhone) is
 * explained before "sign in on your phone".
 */
@HiltViewModel
class WearGateViewModel
    @Inject
    constructor(
        sessionSource: PhoneSessionSource,
        private val phoneConnection: PhoneConnection,
        private val watchSync: WatchSync,
    ) : MviViewModel<WearGateState, WearGateEvent, WearGateEffect>(WearGateState()) {
        private var session: PhoneSession? = null
        private var phoneApp: PhoneAppStatus? = null

        init {
            viewModelScope.launch {
                sessionSource.session.collect {
                    session = it
                    updateStatus()
                }
            }
        }

        override fun handleEvent(event: WearGateEvent) {
            when (event) {
                WearGateEvent.Resumed -> {
                    catchUp()
                    checkPhone()
                }
                WearGateEvent.OpenOnPhoneClicked -> {
                    phoneConnection.openNubecitaOnPhone()
                    setState { copy(showOpenOnPhone = true) }
                }
                WearGateEvent.OpenOnPhoneDismissed -> setState { copy(showOpenOnPhone = false) }
            }
        }

        // Apply anything synced while the listener couldn't run (first install,
        // a force-stop). Cheap when there's nothing new: the ingester skips
        // snapshots it already has.
        private fun catchUp() {
            viewModelScope.launch {
                try {
                    watchSync.catchUp()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.w(throwable, "watch sync catch-up failed")
                }
            }
        }

        private fun checkPhone() {
            viewModelScope.launch {
                try {
                    val status = phoneConnection.phoneAppStatus()
                    phoneApp = status
                    updateStatus()
                    if (status == PhoneAppStatus.Installed) phoneConnection.requestSync()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    // Play services unavailable: keep showing what we have.
                    Timber.w(throwable, "phone check failed")
                }
            }
        }

        private fun updateStatus() {
            val status =
                when (val current = session) {
                    is PhoneSession.SignedIn -> WearGateStatus.SignedIn(did = current.account.did, handle = current.account.handle)
                    PhoneSession.UpdateRequired -> WearGateStatus.UpdateRequired
                    else ->
                        when (phoneApp) {
                            null -> WearGateStatus.Loading
                            PhoneAppStatus.MissingOnAndroid -> WearGateStatus.PhoneAppMissing
                            PhoneAppStatus.IPhone -> WearGateStatus.NeedsAndroidPhone
                            PhoneAppStatus.Installed -> WearGateStatus.SignedOut
                        }
                }
            setState { copy(status = status) }
        }
    }
