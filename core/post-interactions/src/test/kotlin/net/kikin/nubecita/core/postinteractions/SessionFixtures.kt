package net.kikin.nubecita.core.postinteractions

import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.data.models.ActorUi

fun signedIn(did: String): SessionState.SignedIn = SessionState.SignedIn(handle = "me.bsky.social", did = did)

/** Test-only: writes [override] for [did] as the account every test session signs in as. */
fun FollowOverlay.seed(
    did: String,
    override: FollowOverride,
    account: String = "did:plc:me",
) = write(did, override, account)

/** Tiny actor fixture for follow tests. Minimal fields exercised by the follow rules. */
fun actorFixture(
    did: String = "did:plc:fake",
    handle: String = "fake.bsky.social",
    displayName: String? = "Fake",
    avatarUrl: String? = null,
): ActorUi = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl)
