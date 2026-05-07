package net.kikin.nubecita.core.common.navigation

/**
 * Pure event payload broadcast by the composer to interested screens
 * after a successful submit. Collected via [LocalComposerSubmitEvents]
 * — typically by the feed screen so it can show a confirmation
 * snackbar and run an optimistic `replyCount + 1` on the parent post
 * when [replyToUri] is non-null.
 *
 * Lives in `:core:common:navigation` because both the composer
 * (`:feature:composer:impl`'s `ComposerNavigationModule` and `:app`'s
 * `ComposerOverlay`) and any downstream collector (e.g.
 * `:feature:feed:impl`'s `FeedScreen`) need to reference the type, and
 * none of those modules can depend on each other directly.
 *
 * @property newPostUri AT URI of the newly created post.
 * @property replyToUri AT URI of the parent post when this submit was
 *   a reply, `null` for top-level posts. Determines snackbar copy
 *   ("Reply sent" vs "Post sent") and whether collectors should run an
 *   optimistic stat increment on the parent.
 */
data class ComposerSubmitEvent(
    val newPostUri: String,
    val replyToUri: String?,
)
