package net.kikin.nubecita.core.database.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Unit tests for [InstantConverter], the Room `TypeConverter` that stores
 * `kotlin.time.Instant` as an epoch-millisecond `Long`.
 *
 * The converter is pure (no Android / Room runtime needed), so it's exercised
 * directly as a JVM unit test — which also gives `:core:database` its first
 * JaCoCo-counted coverage (DAOs/migrations are only covered by `androidTest`,
 * which the aggregated report doesn't see).
 *
 * Coverage:
 *  - non-null values map both directions with millisecond fidelity.
 *  - `null` round-trips through both directions unchanged (nullable columns).
 *  - value round-trips (Instant→Long→Instant and Long→Instant→Long) are lossless.
 */
class InstantConverterTest {
    @Test
    fun fromEpochMillis_nonNull_returnsMatchingInstant() {
        assertEquals(
            Instant.fromEpochMilliseconds(1_700_000_000_000L),
            InstantConverter.fromEpochMillis(1_700_000_000_000L),
        )
    }

    @Test
    fun fromEpochMillis_null_returnsNull() {
        assertNull(InstantConverter.fromEpochMillis(null))
    }

    @Test
    fun toEpochMillis_nonNull_returnsMatchingLong() {
        assertEquals(
            1_700_000_000_000L,
            InstantConverter.toEpochMillis(Instant.fromEpochMilliseconds(1_700_000_000_000L)),
        )
    }

    @Test
    fun toEpochMillis_null_returnsNull() {
        assertNull(InstantConverter.toEpochMillis(null))
    }

    @Test
    fun fromEpochMillis_epochZero_returnsEpochInstant() {
        // The 0 sentinel is meaningful (Room's default for backfilled columns),
        // so pin that it maps to the epoch instant rather than being treated
        // like null.
        assertEquals(Instant.fromEpochMilliseconds(0L), InstantConverter.fromEpochMillis(0L))
    }

    @Test
    fun instantRoundTrip_isLossless() {
        val original = Instant.fromEpochMilliseconds(1_699_999_999_123L)
        val roundTripped = InstantConverter.fromEpochMillis(InstantConverter.toEpochMillis(original))
        assertEquals(original, roundTripped)
    }

    @Test
    fun longRoundTrip_isLossless() {
        val original = 1_699_999_999_123L
        val roundTripped = InstantConverter.toEpochMillis(InstantConverter.fromEpochMillis(original))
        assertEquals(original, roundTripped)
    }
}
