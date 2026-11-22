package net.kikin.nubecita.feature.search.impl.data

import net.kikin.nubecita.core.auth.SessionState

internal fun signedIn(did: String): SessionState.SignedIn = SessionState.SignedIn(handle = "me.bsky.social", did = did)
