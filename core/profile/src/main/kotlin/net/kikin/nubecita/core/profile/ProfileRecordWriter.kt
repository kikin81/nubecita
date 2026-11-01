package net.kikin.nubecita.core.profile

import kotlinx.serialization.json.JsonObject

/**
 * The one read-modify-write path for the signed-in viewer's
 * `app.bsky.actor.profile/self` record, shared by Edit profile and pinning.
 *
 * Reads the record, hands it to the transform, and writes the result back with
 * `swapRecord` set to the CID that was read, so a concurrent write fails instead
 * of being overwritten. Every field the transform leaves alone is written back
 * byte-for-byte.
 */
interface ProfileRecordWriter {
    /**
     * Applies [transform] to the current record and writes the result.
     *
     * [transform] receives `null` when the account has no profile record yet;
     * the write then omits `swapRecord` and stamps `$type`. Returning `null`
     * from [transform] skips the write.
     *
     * @return `success(true)` when written, `success(false)` when skipped. Raw
     *   failures (`XrpcError`, `NoSessionException`, anything the transform
     *   throws) propagate unmapped; callers map them to their own error types.
     */
    suspend fun update(transform: suspend (existing: JsonObject?) -> JsonObject?): Result<Boolean>
}
