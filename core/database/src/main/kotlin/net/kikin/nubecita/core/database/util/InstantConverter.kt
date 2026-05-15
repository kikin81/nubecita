package net.kikin.nubecita.core.database.util

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

/**
 * Persists `kotlinx.datetime.Instant` as the epoch-millisecond `Long`
 * Room natively supports. `null` round-trips through unchanged so DAOs
 * can declare nullable timestamp columns without a separate converter.
 *
 * Registered on [net.kikin.nubecita.core.database.NubecitaDatabase] via
 * `@TypeConverters(InstantConverter::class)`.
 */
internal object InstantConverter {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilliseconds()
}
