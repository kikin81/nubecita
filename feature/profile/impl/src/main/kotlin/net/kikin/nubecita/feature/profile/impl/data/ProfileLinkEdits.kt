package net.kikin.nubecita.feature.profile.impl.data

import kotlinx.serialization.json.JsonObject

/**
 * Favicon bytes fetched for a new / URL-changed link but not yet uploaded.
 * They're uploaded during Save (not at confirm) so a long-open draft can't
 * point a record at a blob the PDS has already garbage-collected. Content
 * equality so draft-vs-baseline dirty checks compare bytes, not references.
 */
class PendingIcon(
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is PendingIcon && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = bytes.contentHashCode()
}

/**
 * One link in the Edit Profile draft.
 *
 * - [localId]: stable draft key (the record URI for saved links, a generated
 *   id for new ones) — drives list keys and reordering.
 * - [uri] / [cid]: the `app.bsky.actor.link` record's strongRef, null until saved.
 * - [record]: the record body as read from the PDS (saved links only). An edit
 *   merges `url` / `title` / `icon` onto it so self-labels and unknown future
 *   keys survive, same discipline as the profile record merge.
 * - [iconPreviewUrl]: the AppView's CDN URL for a saved link's icon.
 * - [pendingIcon]: favicon fetched for a new / URL-changed link, uploaded at Save.
 */
data class EditableProfileLink(
    val localId: String,
    val url: String,
    val title: String?,
    val uri: String? = null,
    val cid: String? = null,
    val record: JsonObject? = null,
    val iconPreviewUrl: String? = null,
    val pendingIcon: PendingIcon? = null,
)

/**
 * The links half of [ProfileRepository.updateProfile].
 *
 * - [Unchanged] — the default, and what the editor sends whenever its links
 *   haven't loaded: no link record is touched and the profile's `links` key is
 *   preserved verbatim by the merge. An unloaded list must never be read as
 *   "the user removed everything".
 * - [Replaced] — write [links] (in display order) and delete records in
 *   [original] that [links] no longer reference.
 */
sealed interface LinksChange {
    data object Unchanged : LinksChange

    data class Replaced(
        val links: List<EditableProfileLink>,
        val original: List<EditableProfileLink>,
    ) : LinksChange
}

/** What Save does with one draft link. */
internal sealed interface LinkWrite {
    val link: EditableProfileLink

    /** Saved and untouched — reuse its strongRef, no write. */
    data class Reuse(
        override val link: EditableProfileLink,
    ) : LinkWrite

    /** Saved but edited — `putRecord` at the same rkey. */
    data class Put(
        override val link: EditableProfileLink,
    ) : LinkWrite

    /** New — `createRecord`. */
    data class Create(
        override val link: EditableProfileLink,
    ) : LinkWrite
}

internal data class LinkWritePlan(
    val writes: List<LinkWrite>,
    /** Record URIs to delete after the profile stops referencing them. */
    val deletes: List<String>,
)

/**
 * Classifies each draft link against what was loaded. A link is reusable only
 * when it still carries its strongRef and its url, title, and icon match the
 * loaded version exactly; a saved link whose content changed is a [LinkWrite.Put];
 * anything without a URI is a [LinkWrite.Create]. Loaded links absent from the
 * draft are deleted.
 */
internal fun planLinkWrites(
    links: List<EditableProfileLink>,
    original: List<EditableProfileLink>,
): LinkWritePlan {
    val originalByUri = original.mapNotNull { link -> link.uri?.let { it to link } }.toMap()
    val writes =
        links.map { link ->
            val before = link.uri?.let(originalByUri::get)
            when {
                before == null || link.uri == null -> LinkWrite.Create(link)
                link.cid != null &&
                    link.url == before.url &&
                    link.title == before.title &&
                    link.pendingIcon == null -> LinkWrite.Reuse(link)
                else -> LinkWrite.Put(link)
            }
        }
    val kept = links.mapNotNull { it.uri }.toSet()
    val deletes = original.mapNotNull { it.uri }.filterNot { it in kept }
    return LinkWritePlan(writes = writes, deletes = deletes)
}
