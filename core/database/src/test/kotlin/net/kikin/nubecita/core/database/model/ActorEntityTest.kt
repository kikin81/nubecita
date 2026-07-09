package net.kikin.nubecita.core.database.model

import net.kikin.nubecita.data.models.ActorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Unit tests for [ActorEntity]'s `:data:models` mappers — `asExternalModel()`
 * and `toCacheEntity()`. Pure functions, exercised as a JVM unit test.
 *
 * Coverage:
 *  - asExternalModel copies every UI-visible field and drops the cache-only
 *    `lastSeenAt`.
 *  - nullable displayName / avatarUrl survive as null.
 *  - the fail-closed `canMessage = false` is preserved (not forced open).
 *  - toCacheEntity copies the model fields and stamps the supplied lastSeenAt.
 *  - round-tripping ActorUi -> toCacheEntity -> asExternalModel is identity.
 */
class ActorEntityTest {
    private val lastSeenAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    @Test
    fun asExternalModel_copiesAllUiFieldsAndDropsLastSeenAt() {
        val entity =
            ActorEntity(
                did = "did:plc:abc",
                handle = "alice.bsky.social",
                displayName = "Alice",
                avatarUrl = "https://cdn.example/alice.jpg",
                lastSeenAt = lastSeenAt,
                canMessage = true,
            )

        val ui = entity.asExternalModel()

        assertEquals(
            ActorUi(
                did = "did:plc:abc",
                handle = "alice.bsky.social",
                displayName = "Alice",
                avatarUrl = "https://cdn.example/alice.jpg",
                canMessage = true,
            ),
            ui,
        )
    }

    @Test
    fun asExternalModel_preservesNullDisplayNameAndAvatar() {
        val entity =
            ActorEntity(
                did = "did:plc:abc",
                handle = "alice.bsky.social",
                displayName = null,
                avatarUrl = null,
                lastSeenAt = lastSeenAt,
            )

        val ui = entity.asExternalModel()

        assertNull(ui.displayName)
        assertNull(ui.avatarUrl)
    }

    @Test
    fun asExternalModel_preservesFailClosedCanMessage() {
        // A row cached as not-messageable must not be flipped open by the mapper.
        val entity =
            ActorEntity(
                did = "did:plc:abc",
                handle = "alice.bsky.social",
                displayName = null,
                avatarUrl = null,
                lastSeenAt = lastSeenAt,
                canMessage = false,
            )

        assertEquals(false, entity.asExternalModel().canMessage)
    }

    @Test
    fun toCacheEntity_copiesModelFieldsAndStampsLastSeenAt() {
        val ui =
            ActorUi(
                did = "did:plc:xyz",
                handle = "bob.bsky.social",
                displayName = "Bob",
                avatarUrl = "https://cdn.example/bob.jpg",
                canMessage = false,
            )

        val entity = ui.toCacheEntity(lastSeenAt)

        assertEquals(
            ActorEntity(
                did = "did:plc:xyz",
                handle = "bob.bsky.social",
                displayName = "Bob",
                avatarUrl = "https://cdn.example/bob.jpg",
                lastSeenAt = lastSeenAt,
                canMessage = false,
            ),
            entity,
        )
    }

    @Test
    fun roundTrip_uiToEntityToUi_isIdentity() {
        val ui =
            ActorUi(
                did = "did:plc:xyz",
                handle = "bob.bsky.social",
                displayName = null,
                avatarUrl = null,
                canMessage = false,
            )

        assertEquals(ui, ui.toCacheEntity(lastSeenAt).asExternalModel())
    }
}
