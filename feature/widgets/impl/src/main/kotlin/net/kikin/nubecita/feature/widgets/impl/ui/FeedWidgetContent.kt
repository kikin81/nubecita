package net.kikin.nubecita.feature.widgets.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import net.kikin.nubecita.feature.widgets.impl.R
import net.kikin.nubecita.feature.widgets.impl.action.RefreshWidgetAction

/**
 * The shared feed-widget UI (D-C8): a titled card over the offline cache head,
 * with intentional loading / empty / signed-out states. Glance is
 * Compose-runtime → `RemoteViews`, so this can't reuse `PostCard`. Theming via
 * [GlanceTheme] (dynamic color on API 31+, Material baseline below); the root
 * uses `.appWidgetBackground()` + the system widget radius (D-C8). Tapping a
 * post row deep-links into its thread; the header refresh icon enqueues a
 * background refresh.
 */
@Composable
internal fun FeedWidgetContent(
    title: String,
    state: FeedWidgetUiState,
    strings: WidgetStrings,
) {
    GlanceTheme {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(R.dimen.widget_background_radius)
                    .padding(WIDGET_PADDING),
        ) {
            WidgetHeader(title, strings.refresh)
            when (state) {
                FeedWidgetUiState.Loading ->
                    CenteredState(WidgetTestTags.LOADING) { CenteredMessage(strings.loading) }
                FeedWidgetUiState.SignedOut ->
                    CenteredState(WidgetTestTags.SIGNED_OUT) { CenteredMessage(strings.signedOut) }
                is FeedWidgetUiState.Loaded ->
                    if (state.rows.isEmpty()) {
                        CenteredState(WidgetTestTags.EMPTY) { CenteredMessage(strings.empty) }
                    } else {
                        PostList(state.rows)
                    }
            }
        }
    }
}

@Composable
private fun WidgetHeader(
    title: String,
    refreshDescription: String,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(bottom = SPACING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = GlanceModifier.defaultWeight().semantics { testTag = WidgetTestTags.TITLE },
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = refreshDescription,
            modifier =
                GlanceModifier
                    .size(REFRESH_ICON_SIZE)
                    .semantics { testTag = WidgetTestTags.REFRESH }
                    .clickable(actionRunCallback<RefreshWidgetAction>()),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        )
    }
}

@Composable
private fun PostList(rows: List<WidgetRow>) {
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(items = rows, itemId = {
            it.item.postUri
                .hashCode()
                .toLong()
        }) { row ->
            PostRow(row)
        }
    }
}

@Composable
private fun PostRow(row: WidgetRow) {
    val item = row.item
    val rowModifier =
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = ROW_VERTICAL_PADDING)
            .semantics { testTag = WidgetTestTags.POST_ROW }
            // D-C7: tapping the row opens the thread via the existing deep-link
            // routing, launched through actionStartActivity (Android-12 trampoline-safe).
            .let { base -> row.deepLinkIntent?.let { base.clickable(actionStartActivity(it)) } ?: base }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.authorDisplay,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                text = item.text,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                maxLines = 2,
            )
            Text(
                text = item.relativeTime,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                maxLines = 1,
            )
        }
        if (item.hasMedia) {
            Box(modifier = GlanceModifier.padding(start = SPACING)) { Thumbnail(row) }
        }
    }
}

@Composable
private fun Thumbnail(row: WidgetRow) {
    val item = row.item
    Box(
        modifier =
            GlanceModifier
                .size(THUMB_SIZE)
                .cornerRadius(R.dimen.widget_inner_radius)
                .background(GlanceTheme.colors.surfaceVariant),
        contentAlignment = Alignment.BottomEnd,
    ) {
        val bitmap = row.thumbnail
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = item.mediaContentDescription,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(R.dimen.widget_inner_radius),
                contentScale = ContentScale.Crop,
            )
        }
        if (item.extraImageCount > 0) {
            Box(
                modifier =
                    GlanceModifier
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .background(GlanceTheme.colors.surface)
                        .cornerRadius(4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${item.extraImageCount}",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CenteredState(
    tag: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier.fillMaxSize().semantics { testTag = tag },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun CenteredMessage(message: String) {
    Text(
        text = message,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        maxLines = 2,
    )
}

private val WIDGET_PADDING = 12.dp
private val ROW_VERTICAL_PADDING = 10.dp
private val SPACING = 8.dp
private val THUMB_SIZE = 56.dp
private val REFRESH_ICON_SIZE = 24.dp

/** Stable semantics tags so `runGlanceAppWidgetUnitTest` can address nodes. */
internal object WidgetTestTags {
    const val TITLE = "widget_title"
    const val REFRESH = "widget_refresh"
    const val LOADING = "widget_state_loading"
    const val SIGNED_OUT = "widget_state_signed_out"
    const val EMPTY = "widget_state_empty"
    const val POST_ROW = "widget_post_row"
}
