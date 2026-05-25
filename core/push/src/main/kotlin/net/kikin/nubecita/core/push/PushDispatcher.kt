package net.kikin.nubecita.core.push

/**
 * Pure-JVM filter chain that decides whether an inbound FCM `data` map should
 * surface as a system notification. See the change's `design.md` for the
 * rationale behind each stage.
 *
 * The chain runs in this order, top-to-bottom:
 *
 * 1. **parse** — wire `Map<String, String>` → [PushPayload] via
 *    [PushPayload.parse]. Malformed shapes drop with [DropReason.ParseFailed].
 * 2. **recipient-DID match** — the payload's `recipientDid` must equal the
 *    currently signed-in `activeSessionDid`. Defends against stale tokens
 *    delivering pushes addressed to a previously signed-in account.
 * 3. **mute** — drop if `actorDid` is in `mutedActors`. The gateway can't
 *    apply this filter (mutes aren't published to Jetstream), so the client
 *    is the only place this happens.
 * 4. **trusted-verifier** — only applies to `verified` / `unverified`. Drops
 *    if `actorDid` is not in [TRUSTED_VERIFIERS], defending against spoofed
 *    `app.bsky.graph.verification` records.
 * 5. **foreground** — drop if `isAppForeground == true`. UX preference, not a
 *    security filter, so it runs last; the in-app notifications epic will
 *    replace this branch with a SharedFlow-backed in-app surface.
 */
class PushDispatcher {
    fun dispatch(
        data: Map<String, String>,
        activeSessionDid: String?,
        isAppForeground: Boolean,
        mutedActors: Set<String>,
    ): DispatchOutcome {
        val payload = PushPayload.parse(data) ?: return DispatchOutcome.Drop(DropReason.ParseFailed)

        if (payload.recipientDid != activeSessionDid) {
            return DispatchOutcome.Drop(DropReason.RecipientMismatch)
        }

        if (payload.actorDid in mutedActors) {
            return DispatchOutcome.Drop(DropReason.MutedActor)
        }

        val isVerification =
            payload.reason == PushPayload.Reason.Verified ||
                payload.reason == PushPayload.Reason.Unverified
        if (isVerification && payload.actorDid !in TRUSTED_VERIFIERS) {
            return DispatchOutcome.Drop(DropReason.UntrustedVerifier)
        }

        if (isAppForeground) {
            return DispatchOutcome.Drop(DropReason.AppForeground)
        }

        return DispatchOutcome.Show(payload)
    }
}

sealed interface DispatchOutcome {
    data class Show(
        val payload: PushPayload,
    ) : DispatchOutcome

    data class Drop(
        val reason: DropReason,
    ) : DispatchOutcome
}

enum class DropReason {
    ParseFailed,
    RecipientMismatch,
    MutedActor,
    UntrustedVerifier,
    AppForeground,
}
