package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.toJavaInstant

/** What the focused post's Follow toggle shows. */
enum class FocusFollowState { Hidden, NotFollowing, Following }

/**
 * The focused post at the top of a post-detail thread.
 *
 * No container: it sits on the screen canvas with a single 20dp gutter — the
 * same inset as the [PostCard]s above and below it, so avatars line up down
 * the thread — and the body and media get the full width. Emphasis comes from layout and type
 * rather than a card — a two-line header (name, then `@handle · pronouns`),
 * a body one step larger than [PostCard]'s, the absolute date and time, and a
 * counts row — followed by the same action row every [PostCard] uses.
 *
 * Replies keep rendering as [PostCard]; this composable is only the focus.
 *
 * [followState] drives an M3 Expressive tonal toggle on the header: hidden,
 * "Follow" (pill), or "Following" (checked shape). [zoneId] is a seam for
 * screenshot determinism; production uses the device zone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusPostCard(
    post: PostUi,
    modifier: Modifier = Modifier,
    followState: FocusFollowState = FocusFollowState.Hidden,
    onFollowToggle: () -> Unit = {},
    callbacks: PostCallbacks = PostCallbacks.None,
    videoEmbedSlot: (@Composable (EmbedUi.Video, cover: MediaCover?) -> Unit)? = null,
    quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
    onQuotedImageClick: ((quotedPostUri: String, imageIndex: Int) -> Unit)? = null,
    animateLikeTap: Boolean = false,
    animateRepostTap: Boolean = false,
    isMediaRevealed: Boolean = false,
    onRevealMedia: () -> Unit = {},
    translation: PostTranslationUi? = null,
    onTranslate: () -> Unit = {},
    onToggleTranslation: () -> Unit = {},
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        FocusHeader(post = post, followState = followState, onFollowToggle = onFollowToggle, callbacks = callbacks)
        Spacer(Modifier.height(12.dp))
        val shown = translation as? PostTranslationUi.Shown
        BodyText(
            text = shown?.text ?: post.text,
            facets = shown?.facets ?: post.facets,
            authorHandle = post.author.handle,
            onFacetTap = callbacks.onFacetTap,
            shimmering = translation is PostTranslationUi.InProgress,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        )
        if (translation != null) {
            Spacer(Modifier.height(8.dp))
            // Same touch-target opt-out as PostCard's footer: see the comment there.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                TranslationFooter(translation = translation, onTranslate = onTranslate, onToggle = onToggleTranslation)
            }
        }
        EmbedSlot(
            embed = post.embed,
            postUri = post.id,
            callbacks = callbacks,
            videoEmbedSlot = videoEmbedSlot,
            quotedVideoEmbedSlot = quotedVideoEmbedSlot,
            onImageClick = onImageClick,
            onQuotedImageClick = onQuotedImageClick,
            isMediaRevealed = isMediaRevealed,
            onRevealMedia = onRevealMedia,
        )
        Spacer(Modifier.height(12.dp))
        FocusTimestamp(post = post, zoneId = zoneId)
        // Remembered: the card recomposes on every like / repost tap animation.
        val counts = remember(post.stats) { post.stats.focusCounts() }
        if (counts.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            FocusCountsRow(counts)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow(post = post, callbacks = callbacks, animateLikeTap = animateLikeTap, animateRepostTap = animateRepostTap)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusHeader(
    post: PostUi,
    followState: FocusFollowState,
    onFollowToggle: () -> Unit,
    callbacks: PostCallbacks,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Avatar + both identity lines open the author's profile, as in PostCard.
        Row(
            modifier = Modifier.weight(1f).clickable { callbacks.onAuthorTap(post.author) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Decorative here: the name is right beside it in the same tap target,
            // so a description would make TalkBack read the name twice.
            NubecitaAvatar(model = post.author.avatarUrl, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = post.author.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (post.author.verifiedBadge != VerifiedBadge.None) VerificationBadge(badge = post.author.verifiedBadge)
                }
                val pronouns = post.author.pronouns
                Text(
                    text =
                        if (pronouns == null) {
                            stringResource(R.string.postcard_handle, post.author.handle)
                        } else {
                            stringResource(R.string.focus_post_handle_pronouns, post.author.handle, pronouns)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (followState != FocusFollowState.Hidden) {
            val following = followState == FocusFollowState.Following
            FilledTonalToggleButton(checked = following, onCheckedChange = { onFollowToggle() }) {
                if (following) {
                    NubecitaIcon(name = NubecitaIconName.Check, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                }
                Text(stringResource(if (following) R.string.focus_post_following else R.string.focus_post_follow))
            }
        }
    }
}

@Composable
private fun FocusTimestamp(
    post: PostUi,
    zoneId: ZoneId,
) {
    val locale = LocalLocale.current.platformLocale
    val text =
        remember(post.createdAt, locale, zoneId) {
            val dateTime = post.createdAt.toJavaInstant().atZone(zoneId)
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(dateTime)
            val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(dateTime)
            "$time · $date"
        }
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FocusCountsRow(counts: List<Pair<FocusCountKind, Int>>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        counts.forEach { (kind, count) ->
            // Keyed by kind: the list grows and shrinks as counts cross zero, and
            // each entry's remembered state must stay with its own kind.
            key(kind) {
                val number = rememberCompactCount(count.toLong())
                // The number lives inside the localized string (word order and
                // plural rules are per-locale); bold it where it lands.
                val full = pluralStringResource(kind.pluralRes, count, number)
                val start = full.indexOf(number)
                Text(
                    text =
                        buildAnnotatedString {
                            append(full)
                            if (start >= 0) {
                                addStyle(
                                    SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                                    start,
                                    start + number.length,
                                )
                            }
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Kinds of count on the focused post's counts row. Replies live on the action row instead. */
internal enum class FocusCountKind(
    val pluralRes: Int,
) {
    Reposts(R.plurals.focus_post_reposts),
    Quotes(R.plurals.focus_post_quotes),
    Likes(R.plurals.focus_post_likes),
    Saves(R.plurals.focus_post_saves),
}

/** The non-zero counts for the counts row, in display order: reposts, quotes, likes, saves. */
internal fun PostStatsUi.focusCounts(): List<Pair<FocusCountKind, Int>> =
    listOf(
        FocusCountKind.Reposts to repostCount,
        FocusCountKind.Quotes to quoteCount,
        FocusCountKind.Likes to likeCount,
        FocusCountKind.Saves to bookmarkCount,
    ).filter { (_, count) -> count > 0 }
