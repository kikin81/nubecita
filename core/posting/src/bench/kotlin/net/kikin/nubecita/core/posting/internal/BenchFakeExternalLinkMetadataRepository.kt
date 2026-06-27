package net.kikin.nubecita.core.posting.internal

import net.kikin.nubecita.core.image.EncodedImage
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.LinkPreview
import javax.inject.Inject

/**
 * Bench-flavor [ExternalLinkMetadataRepository]: a network-free no-op.
 *
 * The bench build is offline (`FakeXrpcClientProvider` for AT Proto; no CardyB
 * reachability), and bench journeys must not make network calls. Returning `null`
 * from both methods means typing a URL in the bench composer simply yields no
 * card — matching the production graceful-degradation path — without any HTTP.
 */
internal class BenchFakeExternalLinkMetadataRepository
    @Inject
    constructor() : ExternalLinkMetadataRepository {
        override suspend fun fetch(url: String): LinkPreview? = null

        override suspend fun downloadThumb(imageUrl: String): EncodedImage? = null
    }
