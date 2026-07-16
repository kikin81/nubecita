package net.kikin.nubecita.core.posting

import java.io.File
import java.io.InputStream

/**
 * Pure, Android-free core of the shared-image copy path. Kept separate from
 * [SharedMediaStore] (the `Context`/`Uri`/`ContentResolver` glue) so the
 * security-critical logic — the hard byte ceiling, the magic-byte image
 * verification, and the orphan sweep — is unit-testable on the plain JVM.
 *
 * Nothing here trusts a caller-declared MIME type: [sniff] identifies the image
 * from its header bytes, and the accepted extension comes from the sniffed type,
 * not from the sender (share-target design Decision 5 §4).
 */
internal object MediaCopy {
    /** Image formats Nubecita accepts from a share, with the extension used for the app-owned copy. */
    enum class ImageType(
        val extension: String,
        val mimeType: String,
    ) {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        GIF("gif", "image/gif"),
        WEBP("webp", "image/webp"),
    }

    /**
     * Identify an image purely from its leading bytes, or `null` if [header] is
     * not a header for a supported, decodable image. The declared intent type is
     * never consulted — a sender can lie about it.
     */
    fun sniff(header: ByteArray): ImageType? {
        fun startsWith(vararg bytes: Int): Boolean = header.size >= bytes.size && bytes.withIndex().all { (i, b) -> header[i] == b.toByte() }

        return when {
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageType.PNG
            // JPEG: FF D8 FF
            startsWith(0xFF, 0xD8, 0xFF) -> ImageType.JPEG
            // GIF: "GIF87a" / "GIF89a"
            startsWith(0x47, 0x49, 0x46, 0x38) &&
                (header.size >= 6 && (header[4] == 0x37.toByte() || header[4] == 0x39.toByte()) && header[5] == 0x61.toByte()) ->
                ImageType.GIF
            // WEBP: "RIFF" .... "WEBP" (bytes 8-11) — reject RIFF containers that aren't WEBP (WAV/AVI).
            header.size >= 12 &&
                startsWith(0x52, 0x49, 0x46, 0x46) &&
                header[8] == 0x57.toByte() &&
                header[9] == 0x45.toByte() &&
                header[10] == 0x42.toByte() &&
                header[11] == 0x50.toByte() ->
                ImageType.WEBP
            else -> null
        }
    }

    /**
     * Copy [source] into [dest], enforcing a hard [maxBytes] ceiling. Returns
     * `true` when the whole stream fit; returns `false` — and deletes [dest] —
     * the moment the stream exceeds the cap, so an unbounded/malicious stream can
     * neither exhaust storage nor leave a truncated partial file behind
     * (design Decision 5 §3: reject, do not truncate).
     */
    fun copyBounded(
        source: InputStream,
        dest: File,
        maxBytes: Long,
    ): Boolean {
        var written = 0L
        var exceeded = false
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        dest.outputStream().use { out ->
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                written += read
                if (written > maxBytes) {
                    exceeded = true
                    break
                }
                out.write(buffer, 0, read)
            }
        }
        // Delete AFTER the stream is closed (outside `use`): deleting while the
        // output handle is still open fails on some filesystems (e.g. Windows,
        // where JVM unit tests may run).
        if (exceeded) dest.delete()
        return !exceeded
    }

    /**
     * Delete every file directly in [dir] whose last-modified time is older than
     * [maxAgeMillis] before [now]. Returns the number deleted. A missing [dir] is
     * a no-op (returns 0). This is the backstop that bounds the copy directory
     * when process death skips the in-session cleanup triggers.
     */
    fun sweep(
        dir: File,
        now: Long,
        maxAgeMillis: Long,
    ): Int {
        val files = dir.listFiles() ?: return 0
        var deleted = 0
        for (file in files) {
            if (file.isFile && now - file.lastModified() > maxAgeMillis) {
                if (file.delete()) deleted++
            }
        }
        return deleted
    }
}
