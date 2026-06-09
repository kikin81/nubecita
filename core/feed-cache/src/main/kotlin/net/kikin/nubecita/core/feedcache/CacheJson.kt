package net.kikin.nubecita.core.feedcache

import kotlinx.serialization.json.Json

/**
 * `Json` used to (de)serialize the wire `app.bsky.feed.defs#postView` blob
 * stored in `feed_post.post_blob`.
 *
 * Mirrors `XrpcClient.DefaultJson` (`ignoreUnknownKeys = true`,
 * `explicitNulls = false`) so that:
 * - **encode** drops absent optional fields (no `"embed": null` noise), keeping
 *   blobs compact and idempotent on re-write;
 * - **decode** tolerates server schema additions the mapper doesn't read.
 *
 * The serialization spike (`WirePostSerializationSpikeTest`) proved a fully
 * populated `PostView` — including a KNOWN open-union `embed` variant and the
 * raw `record` JsonObject — round-trips losslessly through this config. The
 * open union re-injects its `$type` discriminator on encode, so the blob is a
 * faithful re-encoding of the wire shape.
 */
internal val CacheJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
