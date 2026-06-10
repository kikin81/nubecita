package net.kikin.nubecita.feature.widgets.impl.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.feature.widgets.impl.MAX_WIDGET_POSTS
import net.kikin.nubecita.feature.widgets.impl.R
import net.kikin.nubecita.feature.widgets.impl.di.WidgetEntryPoint
import net.kikin.nubecita.feature.widgets.impl.di.widgetEntryPoint
import net.kikin.nubecita.feature.widgets.impl.image.WidgetThumbnailStore
import net.kikin.nubecita.feature.widgets.impl.model.toWidgetItem
import net.kikin.nubecita.feature.widgets.impl.ui.FeedWidgetContent
import net.kikin.nubecita.feature.widgets.impl.ui.FeedWidgetUiState
import net.kikin.nubecita.feature.widgets.impl.ui.WidgetRow
import net.kikin.nubecita.feature.widgets.impl.ui.WidgetStrings
import kotlin.time.Clock

/**
 * Base feed-widget (D-C6): an update-driven `GlanceAppWidget` that renders the
 * offline cache head for a feed. `provideGlance` resolves the [WidgetEntryPoint]
 * off the singleton graph (Glance has no Hilt composition), reads
 * `head(feedKey, n)` + the pre-decoded thumbnails **off the main thread**, and
 * hands a flat [FeedWidgetUiState] to [FeedWidgetContent]. Zero network on this
 * path — fresh content is pushed by the background worker (B).
 */
internal abstract class FeedWidget : GlanceAppWidget() {
    final override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(180.dp, 110.dp), // ~4x2: a couple of posts
                DpSize(250.dp, 280.dp), // ~4x4: ~4–5 posts
            ),
        )

    @get:StringRes
    protected abstract val titleRes: Int

    protected abstract fun feedKey(accountDid: String): FeedKey

    final override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = context.widgetEntryPoint()
        val state = loadState(context, entryPoint)
        val title = context.getString(titleRes)
        val strings =
            WidgetStrings(
                loading = context.getString(R.string.widget_state_loading),
                signedOut = context.getString(R.string.widget_state_signed_out),
                empty = context.getString(R.string.widget_state_empty),
                refresh = context.getString(R.string.widget_refresh),
            )
        provideContent { FeedWidgetContent(title, state, strings) }
    }

    private suspend fun loadState(
        context: Context,
        entryPoint: WidgetEntryPoint,
    ): FeedWidgetUiState =
        withContext(Dispatchers.IO) {
            val did =
                (entryPoint.sessionStateProvider().state.value as? SessionState.SignedIn)?.did
                    ?: return@withContext FeedWidgetUiState.SignedOut

            val store = entryPoint.widgetThumbnailStore()
            // firstOrNull guards the edge case of a flow that completes without
            // emitting; a Room-backed head normally emits a (possibly empty) list.
            val posts =
                entryPoint
                    .feedRepository()
                    .head(feedKey(did), MAX_WIDGET_POSTS)
                    .firstOrNull()
                    .orEmpty()
            // Widget-add / not-yet-populated cache: kick an on-demand refresh so
            // the partition fills (the worker writes the cache while backgrounded).
            // THROTTLED per partition — a legitimately empty feed (new account,
            // empty custom feed) would otherwise re-enqueue on every render
            // (resize / theme / unlock / updater re-render), and KEEP only dedupes
            // *in-flight* work, so a completed-then-empty cycle would loop and
            // drain battery. First render per process enqueues; thereafter at most
            // once per refresh interval.
            if (posts.isEmpty()) {
                maybeEnqueueEmptyFeedRefresh(feedKey(did), entryPoint)
            }

            val now = Clock.System.now()

            val rows =
                posts.map { post ->
                    val item = post.toWidgetItem(now)
                    // Thumbnails are keyed by post.id (== item.postUri) — the same key
                    // the prefetcher writes under; use post.id to make that explicit.
                    val thumbnail = if (item.hasMedia) loadThumbnail(store, did, post.id) else null
                    WidgetRow(item, thumbnail, deepLinkIntent = deepLinkIntent(context, post.id))
                }
            FeedWidgetUiState.Loaded(rows)
        }

    /**
     * ACTION_VIEW intent into this post's thread, scoped to our package (D-C7),
     * or null when the post URI can't be translated (row stays non-clickable).
     * NEW_TASK | CLEAR_TASK so the deep-link data reliably reaches the
     * `singleTask` MainActivity even when a task already exists — same flags the
     * notification tap-intent uses (PushNotificationBuilder).
     */
    private fun deepLinkIntent(
        context: Context,
        postId: String,
    ): Intent? {
        val deepLink = widgetPostDeepLink(postId) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun loadThumbnail(
        store: WidgetThumbnailStore,
        accountDid: String,
        postId: String,
    ): Bitmap? {
        val file = store.thumbnailFile(accountDid, postId)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private suspend fun maybeEnqueueEmptyFeedRefresh(
        feedKey: FeedKey,
        entryPoint: WidgetEntryPoint,
    ) {
        val nowMs = SystemClock.elapsedRealtime()
        val last = emptyFeedRefreshAt[feedKey]
        if (last == null || nowMs - last >= EMPTY_FEED_REFRESH_THROTTLE_MS) {
            emptyFeedRefreshAt[feedKey] = nowMs
            entryPoint.widgetRefreshLauncher().refreshNow()
        }
    }
}

/**
 * Process-static throttle for the empty-feed on-demand refresh (see
 * [FeedWidget.maybeEnqueueEmptyFeedRefresh]). Widget instances are recreated per
 * broadcast, so this can't live on the instance. `elapsedRealtime` is monotonic
 * (immune to wall-clock changes). Bounds an empty feed to one enqueue per
 * process, then at most one per interval.
 */
private val emptyFeedRefreshAt = java.util.concurrent.ConcurrentHashMap<FeedKey, Long>()

private const val EMPTY_FEED_REFRESH_THROTTLE_MS = 15 * 60 * 1000L
