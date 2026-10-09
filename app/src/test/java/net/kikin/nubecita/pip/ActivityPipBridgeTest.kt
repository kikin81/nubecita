package net.kikin.nubecita.pip

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.video.PipController
import net.kikin.nubecita.core.video.SharedVideoPlayer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActivityPipBridgeTest {
    private val activity = mockk<ComponentActivity>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private val lifecycle = mockk<Lifecycle>(relaxed = true)
    private val pipController = mockk<PipController>(relaxed = true)
    private val sharedVideoPlayer = mockk<SharedVideoPlayer>(relaxed = true)

    private val isInPipFlow = MutableStateFlow(false)
    private val lifecycleObserverSlot = slot<DefaultLifecycleObserver>()
    private val pipModeListenerSlot = slot<Consumer<PictureInPictureModeChangedInfo>>()

    @BeforeEach
    fun setUp() {
        mockkStatic(androidx.core.content.ContextCompat::class)
        every {
            androidx.core.content.ContextCompat
                .registerReceiver(any(), any(), any(), any())
        } returns null

        every { activity.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) } returns true
        every { activity.lifecycle } returns lifecycle
        every { pipController.isInPip } returns isInPipFlow
        every { activity.lifecycle.addObserver(capture(lifecycleObserverSlot)) } returns Unit
        every { activity.addOnPictureInPictureModeChangedListener(capture(pipModeListenerSlot)) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(androidx.core.content.ContextCompat::class)
    }

    @Test
    fun `pipModeListener updates pipController isInPip`() {
        val bridge = ActivityPipBridge(activity, pipController, sharedVideoPlayer)
        bridge.start()

        assertTrue(pipModeListenerSlot.isCaptured)
        pipModeListenerSlot.captured.accept(PictureInPictureModeChangedInfo(true))
        verify { pipController.setInPip(true) }

        pipModeListenerSlot.captured.accept(PictureInPictureModeChangedInfo(false))
        verify { pipController.setInPip(false) }
    }

    @Test
    fun `onStop when in PiP resets isInPip and pauses playback`() {
        val bridge = ActivityPipBridge(activity, pipController, sharedVideoPlayer)
        bridge.start()

        isInPipFlow.value = true
        assertTrue(lifecycleObserverSlot.isCaptured)

        val owner = mockk<LifecycleOwner>()
        lifecycleObserverSlot.captured.onStop(owner)

        verify(exactly = 1) { pipController.setInPip(false) }
        verify(exactly = 1) { sharedVideoPlayer.pause() }
    }

    @Test
    fun `onStop when NOT in PiP does not pause playback or reset isInPip`() {
        val bridge = ActivityPipBridge(activity, pipController, sharedVideoPlayer)
        bridge.start()

        isInPipFlow.value = false
        assertTrue(lifecycleObserverSlot.isCaptured)

        val owner = mockk<LifecycleOwner>()
        lifecycleObserverSlot.captured.onStop(owner)

        verify(exactly = 0) { pipController.setInPip(false) }
        verify(exactly = 0) { sharedVideoPlayer.pause() }
    }

    @Test
    fun `stop unregisters observers and resets isInPip if in PiP`() {
        val bridge = ActivityPipBridge(activity, pipController, sharedVideoPlayer)
        bridge.start()

        isInPipFlow.value = true
        bridge.stop()

        verify { activity.lifecycle.removeObserver(lifecycleObserverSlot.captured) }
        verify { activity.removeOnPictureInPictureModeChangedListener(pipModeListenerSlot.captured) }
        verify(exactly = 1) { pipController.setInPip(false) }
        verify(exactly = 1) { sharedVideoPlayer.pause() }
    }
}
