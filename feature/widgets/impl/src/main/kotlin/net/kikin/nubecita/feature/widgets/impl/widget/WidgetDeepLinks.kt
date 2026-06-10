package net.kikin.nubecita.feature.widgets.impl.widget

/**
 * Build the in-app deep link for a post row (D-C7). Translates a post AT-URI
 * (`at://<authority>/app.bsky.feed.post/<rkey>`) into the app's
 * `nubecita://profile/<authority>/post/<rkey>` form — the scheme/host
 * `MainActivity` registers and `:feature:profile:impl`'s matcher routes into a
 * `PostDetailRoute`. Pure (no Android), so the URI shaping is unit-testable; the
 * widget wraps the resulting `Uri` in `actionStartActivity` (trampoline-safe).
 */
internal fun widgetPostDeepLink(postUri: String): String {
    val authority = postUri.substringAfter("at://").substringBefore('/')
    val rkey = postUri.substringAfterLast('/')
    return "nubecita://profile/$authority/post/$rkey"
}
