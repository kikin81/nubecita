package net.kikin.nubecita.core.posttranslation

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Facets to and from the cache's `facets_json` column.
 *
 * Uses the atproto models' own serializer, so a cached link, mention or tag
 * round-trips as the same wire shape the app reads from the network. These are
 * the *reconciled* facets, indexing the translated text, not the post's
 * originals: storing them is what spares a cache hit from re-running
 * reconciliation.
 */
internal object FacetsCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Facet.serializer())

    fun encode(facets: List<Facet>): String = json.encodeToString(serializer, facets)

    fun decode(encoded: String): List<Facet> = json.decodeFromString(serializer, encoded)
}
