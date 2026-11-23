package net.kikin.nubecita.feature.search.impl.data

import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.postinteractions.FollowOverlay
import net.kikin.nubecita.core.postinteractions.FollowOverride

internal fun signedIn(did: String): SessionState.SignedIn = SessionState.SignedIn(handle = "me.bsky.social", did = did)

/** Test-only: writes [override] for [did] as the account every test session signs in as. */
internal fun FollowOverlay.seed(
    did: String,
    override: FollowOverride,
    account: String = "did:plc:me",
) = write(did, override, account)
