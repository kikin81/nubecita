package net.kikin.nubecita.core.push.internal

import net.kikin.nubecita.core.common.session.SessionClearable
import net.kikin.nubecita.core.push.PushRegistrationCoordinator
import javax.inject.Inject

/**
 * Routes sign-out through [PushRegistrationCoordinator.signOut] so the gateway
 * `unregisterPush` call happens BEFORE `atOAuth.logout()` revokes the OAuth
 * tokens. Without this hook the unregister fired from the
 * [SessionState.SignedOut][net.kikin.nubecita.core.auth.SessionState.SignedOut]
 * branch in the coordinator's collector — but that branch only runs AFTER
 * `sessionStateProvider.refresh()` has flipped the StateFlow to SignedOut,
 * which happens AFTER the OAuth revocation. So `XrpcClientProvider.authenticated()`
 * threw `NoSessionException` and the unregister was a no-op,
 * leaving the device's FCM token registered indefinitely on the gateway.
 *
 * The collector branch stays as a safety net for non-explicit sign-out paths
 * (refresh-token failure, etc.), where the unregister call will fail but at
 * least the local store gets cleared.
 *
 * See [nubecita-1fy.8](https://github.com/kikin81/nubecita/issues) and the
 * smoke results recorded on nubecita-veqm for the original symptom.
 */
internal class PushRegistrationSessionClearable
    @Inject
    constructor(
        private val coordinator: PushRegistrationCoordinator,
    ) : SessionClearable {
        override suspend fun clearSession() {
            coordinator.signOut()
        }
    }
