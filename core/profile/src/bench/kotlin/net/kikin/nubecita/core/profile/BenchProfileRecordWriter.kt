package net.kikin.nubecita.core.profile

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline profile record for the bench flavor: an in-memory record that the
 * transform reads and replaces, so pin / unpin round-trip without a network.
 */
@Singleton
class BenchProfileRecordWriter
    @Inject
    constructor() : ProfileRecordWriter {
        private var record: JsonObject? =
            JsonObject(mapOf("\$type" to JsonPrimitive("app.bsky.actor.profile"), "displayName" to JsonPrimitive("Nubecita Bench")))

        override suspend fun update(transform: suspend (existing: JsonObject?) -> JsonObject?): Result<Boolean> {
            delay(FAKE_LATENCY_MS)
            val next = transform(record) ?: return Result.success(false)
            record = next
            return Result.success(true)
        }

        private companion object {
            const val FAKE_LATENCY_MS = 150L
        }
    }
