package net.kikin.nubecita.feature.profile.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the own-profile edit screen — a
 * full-screen `@MainShell` sub-route pushed onto the active tab's back
 * stack from the own-profile Edit button (like Settings).
 *
 * [displayName], [description], [avatarUrl], and [bannerUrl] pre-fill the
 * form from the current profile header so the screen renders populated
 * without a refetch; they are passed by value into [Serializable]-friendly
 * primitives and read by `EditProfileViewModel` via its assisted factory.
 * All are nullable because a brand-new account may have none of them set.
 * The image URLs seed the avatar/banner previews until the user picks a new
 * image or removes one.
 *
 * Editing is own-profile only, so this key carries no actor/handle — the
 * write path resolves the authenticated DID itself.
 */
@Serializable
data class EditProfile(
    val displayName: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
) : NavKey
