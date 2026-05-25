package net.kikin.nubecita.core.push

/**
 * Allow-list of `actorDid` values whose `verified` / `unverified` push
 * payloads the [PushDispatcher] will surface. Any payload whose `actorDid`
 * is NOT in this set is silently dropped — defense against an attacker
 * issuing spoofed `app.bsky.graph.verification` records.
 *
 * V1 ships exactly one entry, Bluesky's official verifier
 * (`did:plc:z72i7hdynmk6r22z27h6tvur`). When the verifier ecosystem grows
 * beyond a small handful, this graduates to a remote-config-fed list — see
 * the "Hardcoded trusted-verifier list" decision in the change's
 * `design.md`.
 */
internal val TRUSTED_VERIFIERS: Set<String> = setOf("did:plc:z72i7hdynmk6r22z27h6tvur")
