package net.kikin.nubecita.core.video.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoPlaybackInfraTest {
    @Test
    fun bufferConfig_satisfiesDefaultLoadControlInvariants() {
        // DefaultLoadControl requires min >= bufferForPlayback, min >= afterRebuffer, max >= min.
        assertTrue(VideoBufferConfig.MIN_BUFFER_MS >= VideoBufferConfig.BUFFER_FOR_PLAYBACK_MS)
        assertTrue(VideoBufferConfig.MIN_BUFFER_MS >= VideoBufferConfig.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
        assertTrue(VideoBufferConfig.MAX_BUFFER_MS >= VideoBufferConfig.MIN_BUFFER_MS)
    }

    @Test
    fun bufferConfig_isShortVideoTuned() {
        // Guards the intent: fast start (small bufferForPlayback) + low ceiling vs the
        // long-video defaults (2500 / 50000). A change here should be deliberate.
        assertTrue(VideoBufferConfig.BUFFER_FOR_PLAYBACK_MS <= 1_000)
        assertTrue(VideoBufferConfig.MAX_BUFFER_MS <= 20_000)
    }

    @Test
    fun shortVideoLoadControl_buildsWithoutThrowing() {
        assertNotNull(shortVideoLoadControl())
    }

    @Test
    fun videoCacheKey_stripsQueryString() {
        assertEquals(
            "https://cdn.example/video/playlist.m3u8",
            videoCacheKey("https://cdn.example/video/playlist.m3u8?token=abc&exp=123"),
        )
    }

    @Test
    fun videoCacheKey_leavesQuerylessUrlUnchanged() {
        assertEquals(
            "https://cdn.example/video/playlist.m3u8",
            videoCacheKey("https://cdn.example/video/playlist.m3u8"),
        )
    }
}
