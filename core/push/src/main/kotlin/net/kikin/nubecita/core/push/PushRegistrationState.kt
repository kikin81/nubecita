package net.kikin.nubecita.core.push

/**
 * Snapshot of the device's push-registration state with the gateway. Held
 * locally so [PushRegistrationCoordinator] can skip a redundant register call
 * on cold start when the stored `(accountDid, fcmToken, Succeeded)` triple
 * matches the current session + FCM token.
 *
 * See `design.md`'s "Cold-start re-register is gated by a dirty flag"
 * decision.
 */
data class PushRegistrationState(
    val accountDid: String?,
    val fcmToken: String?,
    val status: Status,
) {
    enum class Status {
        Pending,
        Succeeded,
        Failed,
    }

    companion object {
        /**
         * State returned by [PushRegistrationStateStore.read] when nothing has
         * ever been written, and what [PushRegistrationStateStore.clear]
         * resets the store to. `Pending` rather than a distinct
         * `NotAttempted` status keeps the variant set tight — the coordinator
         * already distinguishes "have stored credentials, status says
         * Succeeded" from everything else for its no-op shortcut, so any
         * other status drives a register attempt regardless of whether the
         * cause was first-launch, a prior clear, or an unfinished previous
         * attempt.
         */
        val Default: PushRegistrationState =
            PushRegistrationState(
                accountDid = null,
                fcmToken = null,
                status = Status.Pending,
            )
    }
}
