package net.kikin.nubecita.core.auth

/**
 * The signed-in viewer's DID, or `null` when nobody is signed in.
 *
 * Distinct from the `currentViewerDid()` helpers that repositories declare
 * privately for **writes** (like, repost, follow, block): those throw
 * `NoSessionException` because issuing a write with no session is a defect.
 * This one is for **projection**, where a missing session is ordinary — it
 * simply means no post can be the viewer's own, so nothing is offered that
 * only an owner may do.
 *
 * Reads `state.value` rather than collecting: projection happens per page of
 * wire data, and the DID cannot change without the session changing, which
 * re-drives the whole stream anyway.
 */
val SessionStateProvider.viewerDidOrNull: String?
    get() = (state.value as? SessionState.SignedIn)?.did
