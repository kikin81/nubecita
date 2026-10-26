package net.kikin.nubecita.wear.gate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneConnection
import net.kikin.nubecita.wear.data.PhoneSession
import net.kikin.nubecita.wear.data.PhoneSessionSource
import net.kikin.nubecita.wear.data.sync.WatchSync
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class WearGateViewModelTest {
    private val sessions = MutableSharedFlow<PhoneSession>(replay = 1)
    private val phone = FakePhoneConnection()
    private val sync = FakeWatchSync()
    private val alice = PhoneSession.SignedIn(WearAccount(did = "did:plc:alice", handle = "alice.bsky.social"))

    private fun viewModel() =
        WearGateViewModel(
            sessionSource =
                object : PhoneSessionSource {
                    override val session = sessions
                },
            phoneConnection = phone,
            watchSync = sync,
        )

    @Test
    fun beforeAnythingIsKnown_isLoading() =
        runTest {
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(WearGateStatus.Loading, vm.uiState.value.status)
        }

    @Test
    fun signedInSession_showsContent_evenBeforeThePhoneIsChecked() =
        runTest {
            sessions.emit(alice)
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(WearGateStatus.SignedIn(did = "did:plc:alice", handle = "alice.bsky.social"), vm.uiState.value.status)
        }

    @Test
    fun signedInSession_winsOverAMissingPhoneApp() =
        runTest {
            sessions.emit(alice)
            phone.status = PhoneAppStatus.MissingOnAndroid
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(WearGateStatus.SignedIn(did = "did:plc:alice", handle = "alice.bsky.social"), vm.uiState.value.status)
        }

    @Test
    fun noSession_androidPhoneWithoutTheApp_offersInstall() =
        runTest {
            sessions.emit(PhoneSession.Unknown)
            phone.status = PhoneAppStatus.MissingOnAndroid
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(WearGateStatus.PhoneAppMissing, vm.uiState.value.status)
        }

    @Test
    fun iPhone_explainsAndroidIsNeeded() =
        runTest {
            sessions.emit(PhoneSession.Unknown)
            phone.status = PhoneAppStatus.IPhone
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(WearGateStatus.NeedsAndroidPhone, vm.uiState.value.status)
        }

    @Test
    fun phoneAppInstalled_butSignedOut_asksToSignIn_andRequestsASync() =
        runTest {
            sessions.emit(PhoneSession.SignedOut)
            phone.status = PhoneAppStatus.Installed
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(WearGateStatus.SignedOut, vm.uiState.value.status)
            assertEquals(1, phone.syncRequests)
        }

    @Test
    fun missingPhoneApp_doesNotRequestASync() =
        runTest {
            phone.status = PhoneAppStatus.MissingOnAndroid
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(0, phone.syncRequests)
        }

    @Test
    fun newerSchemaFromThePhone_asksForAnUpdate() =
        runTest {
            sessions.emit(PhoneSession.UpdateRequired)
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(WearGateStatus.UpdateRequired, vm.uiState.value.status)
        }

    @Test
    fun signOutOnThePhone_movesFromContentToSignIn() =
        runTest {
            sessions.emit(alice)
            phone.status = PhoneAppStatus.Installed
            val vm = viewModel()
            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            sessions.emit(PhoneSession.SignedOut)
            advanceUntilIdle()

            assertEquals(WearGateStatus.SignedOut, vm.uiState.value.status)
        }

    @Test
    fun openOnPhone_launchesThePhone_andShowsTheConfirmation_untilDismissed() =
        runTest {
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.OpenOnPhoneClicked)
            assertEquals(1, phone.opened)
            assertTrue(vm.uiState.value.showOpenOnPhone)

            vm.handleEvent(WearGateEvent.OpenOnPhoneDismissed)
            assertEquals(false, vm.uiState.value.showOpenOnPhone)
        }

    @Test
    fun aFailedPhoneCheck_keepsTheCurrentStatus() =
        runTest {
            sessions.emit(alice)
            phone.failCheck = true
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(WearGateStatus.SignedIn(did = "did:plc:alice", handle = "alice.bsky.social"), vm.uiState.value.status)
        }

    @Test
    fun resuming_catches_up_on_synced_items() =
        runTest {
            val vm = viewModel()

            vm.handleEvent(WearGateEvent.Resumed)
            advanceUntilIdle()

            assertEquals(1, sync.catchUps)
        }

    private class FakeWatchSync : WatchSync {
        var catchUps = 0

        override suspend fun catchUp() {
            catchUps++
        }
    }

    private class FakePhoneConnection : PhoneConnection {
        var status = PhoneAppStatus.Installed
        var failCheck = false
        var syncRequests = 0
        var opened = 0

        override suspend fun phoneAppStatus(): PhoneAppStatus {
            if (failCheck) error("Play services unavailable")
            return status
        }

        override suspend fun requestSync() {
            syncRequests++
        }

        override fun openNubecitaOnPhone() {
            opened++
        }

        override fun openUrlOnPhone(url: String) = Unit
    }
}
