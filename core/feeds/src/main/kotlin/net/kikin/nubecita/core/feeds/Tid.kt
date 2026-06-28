package net.kikin.nubecita.core.feeds

/**
 * Minimal AT Protocol TID (Timestamp Identifier) generator.
 *
 * A TID is a 13-character base32-sortable string (`[2-7a-z]`) encoding a
 * 63-bit integer: bits 62–10 = microsecond timestamp since Unix epoch,
 * bits 9–0 = a clock-ID derived from the JVM nanosecond timer at init.
 *
 * See https://atproto.com/specs/tid.
 *
 * Used only when [DefaultPinnedFeedsRepository.pinFeed] inserts a brand-new
 * `SavedFeed` entry (a feed the server has never seen for this account). The
 * resulting TID is replaced by the server's canonical `id` after the next
 * [PinnedFeedsRepository.refresh] write-through.
 */
internal object Tid {
    // base32-sortable: digits 2-7 + all 26 lowercase letters (0, 1, 8, 9 excluded).
    private const val ALPHABET = "234567abcdefghijklmnopqrstuvwxyz"

    // Randomise the 10-bit clock-id per process start from the nanosecond timer.
    private val clockId: Long = System.nanoTime() and 0x3FFL

    /**
     * Returns a fresh, monotonically increasing TID string.
     */
    fun next(): String {
        val micros = System.currentTimeMillis() * 1000L
        // Pack timestamp (53 bits, shifted left 10) | clock-id (10 bits) into 63 bits.
        val n = (micros shl 10) or clockId
        return buildString(13) {
            // Decode 5 bits at a time from MSB to LSB.
            for (i in 12 downTo 0) {
                append(ALPHABET[((n ushr (i * 5)) and 0x1FL).toInt()])
            }
        }
    }
}
