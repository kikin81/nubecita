package net.kikin.nubecita.feature.profile.impl

import android.net.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.profile.impl.data.EditableProfileLink
import net.kikin.nubecita.feature.profile.impl.links.MAX_LINK_TITLE_GRAPHEMES
import net.kikin.nubecita.feature.profile.impl.links.MAX_PROFILE_LINKS
import net.kikin.nubecita.feature.profile.impl.links.isInsecureLink
import net.kikin.nubecita.feature.profile.impl.links.isShortenerLink
import net.kikin.nubecita.feature.profile.impl.links.normalizeLinkUrl

/** `app.bsky.actor.profile` display-name limit (graphemes). */
internal const val MAX_DISPLAY_NAME_GRAPHEMES = 64

/** `app.bsky.actor.profile` description limit (graphemes). */
internal const val MAX_DESCRIPTION_GRAPHEMES = 256

/** Which profile image a pick / crop / remove targets. */
internal enum class ImageSlotKind { Avatar, Banner }

/**
 * The edit state of one image slot, projected to UI + mapped to `ImageChange`
 * at save time:
 * - [Original] — untouched; [url] is the current remote image (null = none set).
 * - [Cropped] — replaced with freshly cropped [bytes] of [mimeType].
 * - [Removed] — cleared.
 */
internal sealed interface ImageSlot {
    data class Original(
        val url: String?,
    ) : ImageSlot

    data class Cropped(
        val bytes: ByteArray,
        val mimeType: String,
    ) : ImageSlot {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Cropped) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
    }

    data object Removed : ImageSlot
}

/**
 * Lifecycle of the editor's link list, loaded asynchronously from the owner's
 * repo (the rest of the form is seeded synchronously from the route). Exactly
 * one is active, hence a sealed status rather than flags. While not [Loaded]
 * link editing is disabled and Save sends links as unchanged.
 */
internal sealed interface LinksLoadStatus {
    data object Loading : LinksLoadStatus

    data object Loaded : LinksLoadStatus

    data object Failed : LinksLoadStatus
}

/**
 * The add / edit link form. [editingId] is the draft link's `localId` being
 * edited, or null when adding. Validation is derived from the raw inputs;
 * [isDuplicate] is computed by the VM (it needs the other draft links).
 */
internal data class LinkEditorState(
    val editingId: String? = null,
    val urlInput: String = "",
    val titleInput: String = "",
    val titleGraphemes: Int = 0,
    val isDuplicate: Boolean = false,
    /** True while the favicon for a new / changed URL is being fetched on confirm. */
    val isFetchingIcon: Boolean = false,
) {
    /** The canonical URL, or null when the input isn't a usable link. */
    val normalizedUrl: String? get() = normalizeLinkUrl(urlInput)
    val isUrlInvalid: Boolean get() = urlInput.isNotBlank() && normalizedUrl == null
    val isInsecure: Boolean get() = normalizedUrl?.let(::isInsecureLink) == true
    val isShortener: Boolean get() = normalizedUrl?.let(::isShortenerLink) == true
    val isTitleOverLimit: Boolean get() = titleGraphemes > MAX_LINK_TITLE_GRAPHEMES
    val canConfirm: Boolean
        get() = normalizedUrl != null && !isDuplicate && !isTitleOverLimit && !isFetchingIcon
}

/** A pending crop: the picked [uri] for [slot], shown full-screen until confirmed/cancelled. */
internal data class CropRequest(
    val uri: Uri,
    val slot: ImageSlotKind,
)

/**
 * Flat UI state for the EditProfile screen. Text fields are owned here (plain
 * MVI — no cursor-aware reducer work, so no `TextFieldState` needed): the
 * grapheme counts are projected on each change by the reducer. The over-limit
 * and `canSave` flags are derived getters (they don't affect data-class
 * equality, so recomposition still skips on the stored fields).
 */
internal data class EditProfileViewState(
    val displayName: String = "",
    val description: String = "",
    val displayNameGraphemes: Int = 0,
    val descriptionGraphemes: Int = 0,
    val avatar: ImageSlot = ImageSlot.Original(null),
    val banner: ImageSlot = ImageSlot.Original(null),
    /** Non-null while the full-screen crop surface is shown for a picked image. */
    val croppingTarget: CropRequest? = null,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showDiscardDialog: Boolean = false,
    /** Draft links in display order. Only meaningful once [linksStatus] is [LinksLoadStatus.Loaded]. */
    val links: ImmutableList<EditableProfileLink> = persistentListOf(),
    val linksStatus: LinksLoadStatus = LinksLoadStatus.Loading,
    /** Non-null while the add / edit link form is open. */
    val linkEditor: LinkEditorState? = null,
) : UiState {
    /** Links can be edited only once loaded, below the cap, and with no save in flight. */
    val canEditLinks: Boolean get() = linksStatus == LinksLoadStatus.Loaded && !isSaving
    val canAddLink: Boolean get() = canEditLinks && links.size < MAX_PROFILE_LINKS

    val isDisplayNameOverLimit: Boolean get() = displayNameGraphemes > MAX_DISPLAY_NAME_GRAPHEMES
    val isDescriptionOverLimit: Boolean get() = descriptionGraphemes > MAX_DESCRIPTION_GRAPHEMES

    /** Save is allowed only with pending, in-limit edits and no in-flight write. */
    val canSave: Boolean
        get() = isDirty && !isSaving && !isDisplayNameOverLimit && !isDescriptionOverLimit
}

internal sealed interface EditProfileEvent : UiEvent {
    data class DisplayNameChanged(
        val value: String,
    ) : EditProfileEvent

    data class DescriptionChanged(
        val value: String,
    ) : EditProfileEvent

    /** A photo was picked for [slot]; opens the crop surface on its [uri]. */
    data class ImagePicked(
        val slot: ImageSlotKind,
        val uri: Uri,
    ) : EditProfileEvent

    /** The crop surface confirmed cropped [bytes] for [slot]. */
    data class ImageCropped(
        val slot: ImageSlotKind,
        val bytes: ByteArray,
        val mimeType: String,
    ) : EditProfileEvent

    /** The crop surface was cancelled (or the pick failed to decode). */
    data object CropCancelled : EditProfileEvent

    /** Clear the image in [slot] (persisted as removal on save). */
    data class RemoveImage(
        val slot: ImageSlotKind,
    ) : EditProfileEvent

    data object SaveTapped : EditProfileEvent

    /** Back affordance (app-bar up or system back). Gated by the dirty guard. */
    data object BackPressed : EditProfileEvent

    data object DiscardConfirmed : EditProfileEvent

    data object DiscardDismissed : EditProfileEvent

    /** Retry loading the link list after a failure. */
    data object RetryLinksLoad : EditProfileEvent

    data object AddLinkTapped : EditProfileEvent

    data class EditLinkTapped(
        val localId: String,
    ) : EditProfileEvent

    data class RemoveLink(
        val localId: String,
    ) : EditProfileEvent

    data class LinkUrlChanged(
        val value: String,
    ) : EditProfileEvent

    data class LinkTitleChanged(
        val value: String,
    ) : EditProfileEvent

    /** Confirm the link form: fetches the favicon for a new / changed URL, then applies. */
    data object LinkEditorConfirmed : EditProfileEvent

    data object LinkEditorDismissed : EditProfileEvent
}

internal sealed interface EditProfileEffect : UiEffect {
    /** Pop the EditProfile sub-route off MainShell's back stack. */
    data object NavigateBack : EditProfileEffect

    data class ShowError(
        val error: SaveError,
    ) : EditProfileEffect
}

/** Typed save failures; the screen maps each to a user-facing string resource. */
internal enum class SaveError {
    /** Profile changed elsewhere — stale swap CID. Prompt reload-and-retry. */
    SwapConflict,

    /** No signed-in session. */
    Unauthorized,

    /** Network / write failure. */
    Network,
}
