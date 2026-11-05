package net.kikin.nubecita.core.translation

import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList

/**
 * A translated post body together with the facets that still apply to it.
 *
 * [facets] index [text], never the original — see [restoreFacets] for why
 * carrying the original offsets across is not an option.
 */
public data class RestoredTranslation(
    val text: String,
    val facets: ImmutableList<Facet>,
)
