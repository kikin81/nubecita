package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.parseOrNull

/**
 * Split a record's AT URI into the `(repo, rkey)` pair `deleteRecord` needs.
 *
 * Shared by every record-deleting path in this module — unlike, unrepost, and
 * deleting a post — so the parse and its failure message live in one place.
 *
 * Deliberately does **not** validate the collection segment: the caller is
 * responsible for pairing a URI with the right NSID (a like URI to unlike, a
 * post URI to delete). A mismatched pair is rejected by the PDS and surfaces
 * as a failure, which is the correct outcome.
 *
 * Fragments (`#…`) are stripped by the upstream parser. Record URIs don't
 * address sub-records and a fragment-bearing rkey would be rejected by the
 * PDS, so stripping references the same record either way.
 *
 * Uses `parseOrNull` plus local `requireNotNull` rather than upstream
 * `parse()` because that throws with a message quoting the URI — which
 * carries the viewer's DID, and these messages reach log surfaces.
 */
internal fun AtUri.repoAndRkey(): Pair<AtIdentifier, RecordKey> {
    val parts =
        requireNotNull(parseOrNull()) {
            "AT URI is not structurally valid"
        }
    val rkey =
        requireNotNull(parts.rkey) {
            "AT URI must be exactly at://<repo>/<collection>/<rkey>"
        }
    return parts.repo to rkey
}
