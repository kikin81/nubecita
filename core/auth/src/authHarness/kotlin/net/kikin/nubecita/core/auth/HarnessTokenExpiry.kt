package net.kikin.nubecita.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The expiry arithmetic behind [AuthTestHarness.Scenario.ExpiredAccessToken]
 * (nubecita-zh7p), as pure functions so it can be unit-tested.
 *
 * It is split out because getting it wrong is not a loud failure. If [of]
 * returns null for a token that really is a JWT, the stash refuses and the run
 * stops — annoying, but obvious. If [secondsLeft] is off by a sign or by the
 * offset, the harness writes back a token the SDK still accepts, nothing
 * refreshes, and the run reads as a clean pass that tested nothing. Each run
 * costs a sign-in cycle, so that silent case is the one worth a test.
 */
internal object HarnessTokenExpiry {
    /**
     * The `exp` claim of a JWT access token, or null when the token is opaque
     * or malformed. Mirrors `DpopAuthProvider.accessTokenExpiry` deliberately —
     * a different decoding here could call a token expired that the SDK does
     * not, and the point is to agree with the SDK.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun of(token: String): Long? =
        try {
            val parts = token.split('.')
            if (parts.size != JWT_PART_COUNT) {
                null
            } else {
                val payload = Base64.UrlSafe.decode(parts[1].padBase64()).toString(Charsets.UTF_8)
                JSON
                    .parseToJsonElement(payload)
                    .jsonObject["exp"]
                    ?.jsonPrimitive
                    ?.long
            }
        } catch (_: Exception) {
            null
        }

    /**
     * How long the SDK will still accept a token expiring at [expEpochSeconds],
     * measured against the clock the SDK itself compares to: wall time shifted
     * by the offset it calibrated from the server's `Date` header. Plain wall
     * time would disagree with `isAccessTokenExpired()` on a device whose clock
     * is off — exactly the case the offset exists for, and the case where a
     * wrong answer here is invisible.
     *
     * Zero or negative means `exp` itself is past. The SDK calls a token
     * expired 30 s BEFORE `exp`, so requiring this to be at or below zero is
     * strictly stronger than the SDK's own test: anything that passes is
     * certain to trigger a refresh. Do not relax it to match the SDK's skew —
     * the margin is the point.
     */
    fun secondsLeft(
        expEpochSeconds: Long,
        nowMillis: Long,
        clockOffsetSeconds: Long,
    ): Long = expEpochSeconds - ((nowMillis / MILLIS_PER_SECOND) + clockOffsetSeconds)

    private fun String.padBase64(): String =
        when (length % BASE64_BLOCK) {
            0 -> this
            2 -> "$this=="
            3 -> "$this="
            else -> this
        }

    private val JSON = Json { ignoreUnknownKeys = true }

    const val MILLIS_PER_SECOND = 1_000L
    const val SECONDS_PER_MINUTE = 60L
    private const val JWT_PART_COUNT = 3
    private const val BASE64_BLOCK = 4
}
