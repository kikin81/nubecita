package net.kikin.nubecita.feature.moderation.impl.data

import net.kikin.nubecita.feature.moderation.impl.SubjectPreview

/**
 * Resolves decorative metadata about the report subject — author handle
 * and post-text snippet for posts; handle and display name for accounts.
 *
 * Used by `ReportDialogViewModel`'s init block to populate
 * `ReportDialogState.subjectPreview` off-thread; resolution failures
 * leave the field null, and the Subject step renders a generic header
 * card instead. Best-effort by design — the dialog is functional
 * without the preview, and the user can always proceed to submission.
 *
 * Kept narrow on purpose: future moderation children (Block, Mute) that
 * also need subject metadata can reuse this resolver rather than each
 * re-implementing the `getProfile` / `getPosts` calls.
 */
internal interface SubjectPreviewResolver {
    suspend fun resolvePost(uri: String): Result<SubjectPreview.Post>

    suspend fun resolveAccount(did: String): Result<SubjectPreview.Account>
}
