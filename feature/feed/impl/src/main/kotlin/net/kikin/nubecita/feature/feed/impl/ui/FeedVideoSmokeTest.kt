@file:androidx.annotation.OptIn(UnstableApi::class)

package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Phase-A throwaway smoke composable for `nubecita-sbc.1` — verifies the
 * Media3 / ExoPlayer + media3-ui-compose runtime path compiles and (when
 * invoked on-device) plays a Bluesky HLS stream silently without
 * claiming audio focus. Replaced in phase C (`nubecita-sbc.4`) by the
 * real `FeedVideoPlayerCoordinator` + `PostCardVideoEmbed`; delete this
 * file as part of that change.
 *
 * Pinned by `openspec/changes/add-feature-feed-video-embeds/tasks.md` §2.3.
 */
@Composable
internal fun FeedVideoSmokeTest(
    hlsUrl: String,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        FeedVideoSmokeTestPlaceholder(modifier = modifier)
    } else {
        FeedVideoSmokeTestPlayer(hlsUrl = hlsUrl, modifier = modifier)
    }
}

// ExoPlayer.Builder(...).build() crashes layoutlib; render a placeholder
// in inspection mode so the IDE preview is stable.
@Composable
private fun FeedVideoSmokeTestPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
private fun FeedVideoSmokeTestPlayer(
    hlsUrl: String,
    modifier: Modifier = Modifier,
) {
    val videoModifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)

    // applicationContext keeps the player from retaining the Activity
    // across config changes (per Copilot review on PR #57).
    val appContext = LocalContext.current.applicationContext
    val player =
        remember(appContext, hlsUrl) {
            ExoPlayer.Builder(appContext).build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ALL
                setMediaItem(MediaItem.fromUri(hlsUrl))
                prepare()
                playWhenReady = true
            }
        }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> player.play()
                    Lifecycle.Event.ON_STOP -> player.pause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = videoModifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun FeedVideoSmokeTestPreview() {
    NubecitaTheme {
        // Renders the inspection-mode placeholder. Substitute a real bsky
        // HLS URL (per task 1.3) when wiring this into a debug screen
        // for on-device smoke testing.
        FeedVideoSmokeTest(hlsUrl = "https://video.bsky.app/sample/playlist.m3u8")
    }
}
