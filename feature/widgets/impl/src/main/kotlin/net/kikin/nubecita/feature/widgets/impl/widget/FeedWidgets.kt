package net.kikin.nubecita.feature.widgets.impl.widget

import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.widgetsync.WidgetFeeds
import net.kikin.nubecita.feature.widgets.impl.R

/** Free widget: the signed-in account's Following timeline head. */
internal class FollowingFeedWidget : FeedWidget() {
    override val titleRes: Int = R.string.widget_following_title

    override fun feedKey(accountDid: String): FeedKey = FeedKey.following(accountDid)
}

/** Free widget: the fixed Discover ("what's-hot") feed head. */
internal class DiscoverFeedWidget : FeedWidget() {
    override val titleRes: Int = R.string.widget_discover_title

    override fun feedKey(accountDid: String): FeedKey = FeedKey(accountDid, FeedType.DISCOVER, WidgetFeeds.DISCOVER_FEED_URI)
}
