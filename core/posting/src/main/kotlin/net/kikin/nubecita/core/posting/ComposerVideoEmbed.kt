package net.kikin.nubecita.core.posting

import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob

/**
 * A finished video ready to attach to a post.
 *
 * The composer supplies this once its upload pipeline reaches a terminal
 * success. Deliberately a plain data carrier rather than the pipeline's own
 * state type: `:core:posting` should not depend on `:core:video-upload` to
 * write a record, and the only thing it needs from that work is the result.
 *
 * Unlike [ComposerAttachment], no upload happens here. Bluesky video does not
 * go through `uploadBlob` — the blob is produced by an asynchronous
 * server-side transcode, so by the time a post can be created the work is
 * already done.
 *
 * @property blob the transcoded video blob returned by the video service.
 * @property alt accessibility description. Blank omits the field.
 * @property aspectRatio rotation-corrected source ratio, or `null` when the
 *   source dimensions could not be read. Null omits the field rather than
 *   substituting a placeholder — a wrong ratio is rendered by every AT
 *   Protocol client, while an absent one lets each measure for itself.
 */
data class ComposerVideoEmbed(
    val blob: Blob,
    val alt: String = "",
    val aspectRatio: AspectRatio? = null,
)
