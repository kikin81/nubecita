package net.kikin.nubecita.core.posting.internal

import kotlinx.coroutines.delay
import net.kikin.nubecita.core.image.EncodedImage
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.LinkPreview
import javax.inject.Inject

/**
 * Bench-flavor [ExternalLinkMetadataRepository]: a network-free **synthetic**
 * preview so the offline bench build can exercise the composer's link-card UI on
 * a device/emulator (loading → loaded → dismiss, plus the card-XOR-images
 * interplay) with no CardyB call. Mirrors [BenchFakePostingRepository] /
 * `BenchFakeActorRepository` — fakes reads with canned data.
 *
 * - [fetch] returns a canned [LinkPreview] for any URL after a short delay (so
 *   the `Loading` state is visible in a walkthrough). `imageUrl` is `null` so
 *   Coil makes no network request for a thumbnail — the card renders text-only.
 * - [downloadThumb] returns `null`: there is no real thumbnail, and the bench
 *   submit path ([BenchFakePostingRepository]) ignores embeds anyway.
 */
internal class BenchFakeExternalLinkMetadataRepository
    @Inject
    constructor() : ExternalLinkMetadataRepository {
        override suspend fun fetch(url: String): LinkPreview? {
            delay(FAKE_FETCH_DELAY_MS)
            return LinkPreview(
                uri = url,
                title = "Bench preview — $url",
                description = "A synthetic link preview shown by the offline bench build (no CardyB call).",
                imageUrl = null,
            )
        }

        override suspend fun downloadThumb(imageUrl: String): EncodedImage? = null

        private companion object {
            // Brief, so a device walkthrough can see the Loading card flip to Loaded.
            const val FAKE_FETCH_DELAY_MS = 600L
        }
    }
