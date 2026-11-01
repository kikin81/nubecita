package net.kikin.nubecita.core.profile

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/** [PinnedPostRepository] over the shared [ProfileRecordWriter]. */
internal class DefaultPinnedPostRepository
    @Inject
    constructor(
        private val profileRecordWriter: ProfileRecordWriter,
    ) : PinnedPostRepository {
        override suspend fun pin(
            uri: String,
            cid: String,
        ): Result<Unit> =
            profileRecordWriter
                .update { existing ->
                    val strongRef = JsonObject(mapOf(KEY_URI to JsonPrimitive(uri), KEY_CID to JsonPrimitive(cid)))
                    JsonObject((existing ?: JsonObject(emptyMap())) + (KEY_PINNED_POST to strongRef))
                }.map { }

        override suspend fun unpin(uri: String): Result<Unit> =
            profileRecordWriter
                .update { existing ->
                    val pinnedUri =
                        (existing?.get(KEY_PINNED_POST) as? JsonObject)
                            ?.get(KEY_URI)
                            ?.jsonPrimitive
                            ?.contentOrNull
                    if (existing == null || pinnedUri != uri) null else JsonObject(existing - KEY_PINNED_POST)
                }.map { }

        private companion object {
            const val KEY_PINNED_POST = "pinnedPost"
            const val KEY_URI = "uri"
            const val KEY_CID = "cid"
        }
    }
