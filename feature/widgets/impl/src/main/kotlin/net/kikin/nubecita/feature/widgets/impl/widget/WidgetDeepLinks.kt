package net.kikin.nubecita.feature.widgets.impl.widget

/**
 * Build the in-app deep link for a post row (D-C7). Translates a post AT-URI
 * (`at://<authority>/app.bsky.feed.post/<rkey>`) into the app's
 * `nubecita://profile/<authority>/post/<rkey>` form — the scheme/host
 * `MainActivity` registers and `:feature:profile:impl`'s matcher routes into a
 * `PostDetailRoute`. Pure (no Android), so the URI shaping is unit-testable; the
 * widget wraps the resulting `Uri` in `actionStartActivity` (trampoline-safe).
 *
 * Returns `null` for anything that isn't a well-formed post AT-URI
 * (`at://<authority>/app.bsky.feed.post/<rkey>`) so the caller renders the row
 * non-clickable rather than launching a broken intent. In practice the cache
 * only holds valid post URIs, but the guard is cheap insurance.
 */
internal fun widgetPostDeepLink(postUri: String): String? {
    if (!postUri.startsWith("at://")) return null
    val parts = postUri.removePrefix("at://").split('/')
    if (parts.size < 3 || parts[1] != "app.bsky.feed.post") return null
    val authority = parts[0]
    val rkey = parts[2]
    if (authority.isEmpty() || rkey.isEmpty()) return null
    return "nubecita://profile/$authority/post/$rkey"
}
