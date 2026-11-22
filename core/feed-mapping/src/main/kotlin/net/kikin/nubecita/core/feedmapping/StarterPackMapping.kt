package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.graph.StarterPackView
import io.github.kikin81.atproto.app.bsky.graph.StarterPackViewBasic
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.kikin.nubecita.data.models.StarterPackUi

/** How many member avatars a pack card or row shows at most. */
public const val STARTER_PACK_AVATAR_LIMIT: Int = 4

/**
 * Projects a `starterPackView` to a [StarterPackUi], or null when it has no usable
 * name (neither in the pack record nor on its list), so a nameless pack is not
 * drawn as a blank card. The member count is the list's `listItemCount` when the
 * server sent it; the avatars come from the member sample, skipping members
 * without one.
 */
public fun StarterPackView.toStarterPackUi(): StarterPackUi? {
    val name = record.packName() ?: list?.name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return StarterPackUi(
        uri = uri.raw,
        name = name,
        creatorHandle = creator.handle.raw,
        memberCount = list?.listItemCount?.toCount(),
        avatarUrls =
            listItemsSample
                .orEmpty()
                .mapNotNull { it.subject.avatar?.raw }
                .take(STARTER_PACK_AVATAR_LIMIT)
                .toImmutableList(),
    )
}

/** [toStarterPackUi] for the basic form, which carries a member count but no member sample. */
public fun StarterPackViewBasic.toStarterPackUi(): StarterPackUi? {
    val name = record.packName() ?: return null
    return StarterPackUi(
        uri = uri.raw,
        name = name,
        creatorHandle = creator.handle.raw,
        memberCount = listItemCount?.toCount(),
        avatarUrls = persistentListOf(),
    )
}

private fun JsonObject.packName(): String? =
    (this["name"] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun Long.toCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
