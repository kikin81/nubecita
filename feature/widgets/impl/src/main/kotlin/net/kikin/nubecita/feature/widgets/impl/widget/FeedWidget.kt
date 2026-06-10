package net.kikin.nubecita.feature.widgets.impl.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    protected abstract fun feedKey(accountDid: String): net.kikin.nubecita.core.feedcache.FeedKey

    final override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = context.widgetEntryPoint()
        val state = loadState(entryPoint)
        val title = context.getString(titleRes)
        val strings =
            WidgetStrings(
                loading = context.getString(R.string.widget_state_loading),
                signedOut = context.getString(R.string.widget_state_signed_out),
                empty = context.getString(R.string.widget_state_empty),
            )
        provideContent { FeedWidgetContent(title, state, strings) }
    }

    private suspend fun loadState(entryPoint: WidgetEntryPoint): FeedWidgetUiState =
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
            val now = Clock.System.now()

            val rows =
                posts.map { post ->
                    val item = post.toWidgetItem(now)
                    // Thumbnails are keyed by post.id (== item.postUri) — the same key
                    // the prefetcher writes under; use post.id to make that explicit.
                    val thumbnail = if (item.hasMedia) loadThumbnail(store, did, post.id) else null
                    WidgetRow(item, thumbnail)
                }
            FeedWidgetUiState.Loaded(rows)
        }

    private fun loadThumbnail(
        store: WidgetThumbnailStore,
        accountDid: String,
        postId: String,
    ): Bitmap? {
        val file = store.thumbnailFile(accountDid, postId)
        return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }
}
