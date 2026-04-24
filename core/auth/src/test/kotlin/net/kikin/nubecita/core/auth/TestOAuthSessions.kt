package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSession

internal fun sampleSession(
    accessToken: String = "access-token-abc123",
    refreshToken: String = "refresh-token-xyz789",
    did: String = "did:plc:samplesubject",
    handle: String = "example.bsky.social",
): OAuthSession =
    OAuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        did = did,
        handle = handle,
        pdsUrl = "https://pds.example",
        tokenEndpoint = "https://auth.example/token",
        revocationEndpoint = "https://auth.example/revoke",
        clientId = "https://client.example/metadata.json",
        dpopPrivateKey = ByteArray(32) { it.toByte() },
        dpopPublicKey = ByteArray(65) { (it * 2).toByte() },
        authServerNonce = "server-nonce-1",
        clockOffsetSeconds = 42L,
        pdsNonce = "pds-nonce-2",
    )
