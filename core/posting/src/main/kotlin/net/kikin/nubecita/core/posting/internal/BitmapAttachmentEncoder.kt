package net.kikin.nubecita.core.posting.internal

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.DefaultDispatcher
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Production [AttachmentEncoder] backed by `android.graphics.ImageDecoder`
 * (decode + EXIF-aware orientation + in-flight downsample) and
 * `Bitmap.compress(WEBP_LOSSY, …)` (encode). Both APIs are in
 * `android.graphics` core — no third-party dependencies.
 *
 * Pipeline (matches the design in `nubecita-uii`'s description):
 *
 * 1. **Pass-through gate.** If the raw bytes already fit under
 *    [TARGET_BYTES] (50 KB headroom under the wire-level
 *    [BLUESKY_BLOB_LIMIT_BYTES]), return them verbatim with the
 *    original MIME type.
 *
 * 2. **Decode + dimension cap.** `ImageDecoder.decodeBitmap(...)` with
 *    `setOnHeaderDecodedListener` so the dimension cap is applied
 *    *during* decode, not after. This caps heap allocation at ~16 MB
 *    worst case for a 12 MP HEIC; without it, a full-res decode of
 *    that same source is ~200 MB on heap. EXIF orientation is auto-
 *    applied by `ImageDecoder` so portrait shots don't end up
 *    sideways.
 *
 * 3. **Quality step.** Encode at decreasing WebP quality
 *    [QUALITY_LADDER] (85 → 45) until the encoded byte count drops
 *    under [TARGET_BYTES]. Each iteration only re-encodes the already-
 *    decoded bitmap; we don't pay a second decode cost.
 *
 * 4. **Dimension fallback.** If quality 45 still exceeds the cap (rare
 *    — heavy compression-resistant content like fine textures or
 *    text-heavy screenshots), re-decode at successively smaller
 *    target sizes [DIMENSION_FALLBACKS] (1536 → 1024) and run the
 *    quality ladder again. Two iterations is the practical worst
 *    case; more would mean the source isn't really a photo.
 *
 * 5. **Bitmap recycle.** Bitmaps are explicitly recycled at scope end
 *    so the heap isn't held by GC roots until the next GC cycle.
 *
 * Concurrency: a [Semaphore] permit count of [MAX_PARALLEL_ENCODES]
 * gates simultaneous encodes. The composer's parallel `awaitAll()`
 * over 4 attachments would otherwise hold 4 large bitmaps on heap
 * simultaneously; serializing to two-at-a-time keeps memory
 * predictable on low-RAM devices without measurably hurting the wall-
 * clock submission time (encode is fast compared to upload).
 *
 * Dispatcher: CPU-bound work, so [DefaultDispatcher] not [IoDispatcher].
 *
 * `@RequiresApi(28)` because `ImageDecoder` is API 28+. Our minSdk is
 * 28, so this is a no-op annotation in practice — included for
 * defensive future-proofing if someone ever lowers minSdk.
 */
@Singleton
@RequiresApi(Build.VERSION_CODES.P)
internal class BitmapAttachmentEncoder
    @Inject
    constructor(
        @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    ) : AttachmentEncoder {
        // Two simultaneous encodes is the right balance: enough to keep
        // the upload pipeline fed while the network is in flight, few
        // enough to bound peak heap on low-RAM devices.
        private val gate = Semaphore(permits = MAX_PARALLEL_ENCODES)

        override suspend fun encodeForUpload(
            bytes: ByteArray,
            sourceMimeType: String,
            maxBytes: Long,
        ): EncodedAttachment {
            // Pass-through. The repository's submit pipeline expects an
            // EncodedAttachment back; constructing one here avoids a
            // redundant byte copy on the (very rare) already-small case.
            if (bytes.size <= effectiveTarget(maxBytes)) {
                return EncodedAttachment(bytes = bytes, mimeType = sourceMimeType)
            }
            return gate.withPermit {
                withContext(dispatcher) {
                    encodeUnderCap(bytes = bytes, maxBytes = maxBytes)
                }
            }
        }

        private fun encodeUnderCap(
            bytes: ByteArray,
            maxBytes: Long,
        ): EncodedAttachment {
            val target = effectiveTarget(maxBytes)
            // Try the primary dimension cap first; fall back to smaller
            // ceilings only if the entire quality ladder fails.
            for (dimensionCap in listOf(MAX_DIMENSION) + DIMENSION_FALLBACKS) {
                val encoded = decodeAndCompressLadder(bytes = bytes, dimensionCap = dimensionCap, target = target)
                if (encoded != null) {
                    return EncodedAttachment(bytes = encoded, mimeType = OUTPUT_MIME)
                }
            }
            // Last resort: quality 1 at the smallest fallback dimension.
            // Visual quality is poor at this point but the upload still
            // succeeds, which beats failing the whole submit.
            val lastResort =
                decodeAndCompress(
                    bytes = bytes,
                    dimensionCap = DIMENSION_FALLBACKS.last(),
                    quality = MIN_QUALITY,
                )
            return EncodedAttachment(bytes = lastResort, mimeType = OUTPUT_MIME)
        }

        /**
         * Decodes once at [dimensionCap], then walks the quality ladder
         * until an encoded result fits under [target]. Returns null
         * (NOT throws) when no quality step under the ladder fits, so
         * the caller can drop to the next dimension fallback.
         */
        private fun decodeAndCompressLadder(
            bytes: ByteArray,
            dimensionCap: Int,
            target: Long,
        ): ByteArray? {
            val bitmap = decode(bytes = bytes, dimensionCap = dimensionCap)
            try {
                for (quality in QUALITY_LADDER) {
                    val encoded = compress(bitmap = bitmap, quality = quality)
                    if (encoded.size <= target) return encoded
                }
                return null
            } finally {
                bitmap.recycle()
            }
        }

        private fun decodeAndCompress(
            bytes: ByteArray,
            dimensionCap: Int,
            quality: Int,
        ): ByteArray {
            val bitmap = decode(bytes = bytes, dimensionCap = dimensionCap)
            try {
                return compress(bitmap = bitmap, quality = quality)
            } finally {
                bitmap.recycle()
            }
        }

        private fun decode(
            bytes: ByteArray,
            dimensionCap: Int,
        ): Bitmap {
            val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Compute target size during the header-decoded callback
                // so the bitmap is allocated already-resized.
                val (sourceW, sourceH) = info.size.width to info.size.height
                val longest = max(sourceW, sourceH)
                if (longest > dimensionCap) {
                    val scale = dimensionCap.toFloat() / longest.toFloat()
                    val targetW = (sourceW * scale).toInt().coerceAtLeast(1)
                    val targetH = (sourceH * scale).toInt().coerceAtLeast(1)
                    decoder.setTargetSize(targetW, targetH)
                }
                // Software allocator — `Bitmap.compress` requires a
                // mutable `ARGB_8888` bitmap. The default allocator
                // (`ALLOCATOR_DEFAULT`) may produce a hardware bitmap
                // on capable devices, which throws when passed to
                // `compress`. Force software for the encode path.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Permit shrink-on-decode; we already set targetSize so
                // this is just defensive against zero-divide edge cases.
                decoder.isMutableRequired = false
            }
        }

        private fun compress(
            bitmap: Bitmap,
            quality: Int,
        ): ByteArray {
            // Initial buffer sized at the target — most encodes converge
            // around the cap, so we avoid both wasted reservation and
            // repeated buffer growth.
            val out = ByteArrayOutputStream(INITIAL_BUFFER_BYTES)
            // `Bitmap.CompressFormat.WEBP_LOSSY` is API 30+; the broader
            // `WEBP` enum (deprecated at API 30 but functionally identical
            // to lossy when `quality < 100`) covers our minSdk 28 floor.
            // WEBP_LOSSY at quality N is roughly equivalent to JPEG at
            // quality N+10 for photos, with smaller output.
            val format =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            bitmap.compress(format, quality, out)
            return out.toByteArray()
        }

        private fun effectiveTarget(maxBytes: Long): Long =
            // Reserve TARGET_HEADROOM_BYTES under the wire-level cap so
            // the WebP encoder's last-quality-step output can land
            // slightly above our internal target without breaching the
            // PDS cap.
            (maxBytes - TARGET_HEADROOM_BYTES).coerceAtLeast(MIN_TARGET_BYTES)

        companion object {
            // Output is always WebP — Bluesky's app.bsky.embed.images
            // accepts it and it dominates JPEG at equivalent quality.
            private const val OUTPUT_MIME = "image/webp"

            // Dimension caps. The primary cap is high enough to look
            // good on a Retina screen; fallbacks step down only when
            // the quality ladder can't get content under the cap at the
            // higher dimension.
            private const val MAX_DIMENSION = 2048
            private val DIMENSION_FALLBACKS = listOf(1536, 1024)

            // Quality ladder. Empirically, WebP_LOSSY at q=85 fits most
            // photos under 1 MB at 2048; q=45 is the lowest acceptable
            // floor before artifacts become objectionable to users.
            private val QUALITY_LADDER = listOf(85, 75, 65, 55, 45)
            private const val MIN_QUALITY = 1

            // Last-permit gate for parallel encodes. See class KDoc.
            private const val MAX_PARALLEL_ENCODES = 2

            // Headroom under the wire-level cap. WebP doesn't expose a
            // "encode to exactly N bytes" mode, so we under-target.
            private const val TARGET_HEADROOM_BYTES = 50_000L
            private const val MIN_TARGET_BYTES = 100_000L

            // Initial output-buffer size. Most images converge near the
            // target, so we save reallocation work by starting close.
            private const val INITIAL_BUFFER_BYTES = 256 * 1024
        }
    }
