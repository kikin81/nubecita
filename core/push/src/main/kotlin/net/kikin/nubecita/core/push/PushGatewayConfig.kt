package net.kikin.nubecita.core.push

/**
 * Identity of the push notifications gateway this build registers tokens
 * against, packaged as configuration so [DefaultPushRegistrationRepository]
 * doesn't bake the values into its class.
 *
 * Two fields, both `did:web:` identifiers:
 *
 *  - [serviceDid] — the DID the **PDS** persists in the user's
 *    `app.bsky.notification.pushSubscription` record. Reads as a stable
 *    foreign key identifying the gateway service that should receive
 *    delivery events for this token.
 *  - [proxyDid] — the value of the `atproto-proxy` header on every
 *    `register` / `unregister` call. Tells the PDS to forward this
 *    specific request to our gateway instead of bsky.app's notification
 *    service. Conventionally `<service-did>#<service-id>` where
 *    `service-id` matches the entry in the gateway's
 *    `/.well-known/did.json` (here: `#bsky_notif`).
 *
 * Today the only consumer is the production gateway at
 * `https://push.nubecita.app`, provided as [Nubecita]. The data class
 * exists so a future forkability epic can swap the value at the Hilt
 * provider seam without touching the repository — no behavior change in
 * this commit; just an architectural seam.
 */
data class PushGatewayConfig(
    val serviceDid: String,
    val proxyDid: String,
) {
    companion object {
        val Nubecita: PushGatewayConfig =
            PushGatewayConfig(
                serviceDid = "did:web:push.nubecita.app",
                proxyDid = "did:web:push.nubecita.app#bsky_notif",
            )
    }
}
