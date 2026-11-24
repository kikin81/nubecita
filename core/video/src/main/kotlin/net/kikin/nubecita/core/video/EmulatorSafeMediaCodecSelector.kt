@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video

import android.os.Build
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

// Build properties are fixed for the process: check once, not per decoder lookup.
private val isEmulator: Boolean =
    Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
        Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
        Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
        Build.PRODUCT.contains("sdk_gphone", ignoreCase = true)

/**
 * The default decoder order, except on an emulator, where the software
 * decoders (`c2.android.*`, `OMX.google.*`) go first.
 *
 * The goldfish hardware decoder on ARM64 emulators fails in `queueInputBuffer`
 * after it has initialized, so decoder fallback never gets the chance to step
 * in. Every player this module builds uses this selector: the feed's
 * [SharedVideoPlayer] and the vertical feed's players from
 * [net.kikin.nubecita.core.video.playback.VideoPlayerFactory]. Real devices
 * keep the default order.
 */
internal val emulatorSafeMediaCodecSelector =
    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoders =
            MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
        if (isEmulator) {
            decoders.sortedBy { decoder ->
                if (decoder.name.startsWith("c2.android.") || decoder.name.startsWith("OMX.google.")) 0 else 1
            }
        } else {
            decoders
        }
    }
