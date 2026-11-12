package net.kikin.nubecita.bootstrap

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.mockk.mockk
import io.mockk.verify
import net.kikin.nubecita.core.feeds.FeedViewPreferencesCoordinator
import net.kikin.nubecita.core.moderation.ModerationPreferencesCoordinator
import net.kikin.nubecita.core.moderation.PostAudienceDefaultCoordinator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A process the OS starts in the background (an FCM push, a package update,
 * boot) must not run startup work that calls an authenticated endpoint: an
 * expired access token is then refreshed in a process that MIUI and other
 * aggressive OEMs freeze or kill moments later. If the server rotated the token
 * before the response was saved, the next process replays the old one and the
 * server deletes the session (nubecita-25a5).
 */
class StartOnFirstForegroundTest {
    private val owner: LifecycleOwner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
        }

    // createUnsafe skips the main-thread check, which has no Looper on the JVM.
    private val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(owner)

    @Test
    fun `does not start while the process has never been in the foreground`() {
        var starts = 0
        val initializer = StartOnFirstForeground(registry) { starts++ }

        initializer.start()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        assertEquals(0, starts)
    }

    @Test
    fun `starts once when the process first reaches the foreground`() {
        var starts = 0
        val initializer = StartOnFirstForeground(registry) { starts++ }

        initializer.start()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(1, starts)
    }

    @Test
    fun `later foreground cycles do not start it again`() {
        var starts = 0
        val initializer = StartOnFirstForeground(registry) { starts++ }

        initializer.start()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        assertEquals(1, starts)
    }

    @Test
    fun `starts immediately when the process is already in the foreground`() {
        var starts = 0
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val initializer = StartOnFirstForeground(registry) { starts++ }

        initializer.start()

        assertEquals(1, starts)
    }

    @Test
    fun `the startup preference coordinators wait for the foreground`() {
        val moderation = mockk<ModerationPreferencesCoordinator>(relaxed = true)
        val postAudience = mockk<PostAudienceDefaultCoordinator>(relaxed = true)
        val feedView = mockk<FeedViewPreferencesCoordinator>(relaxed = true)
        val initializers =
            listOf(
                ProductionBootstrapModule.provideModerationPreferencesInitializer(moderation, registry),
                ProductionBootstrapModule.providePostAudienceDefaultInitializer(postAudience, registry),
                ProductionBootstrapModule.provideFeedViewPreferencesInitializer(feedView, registry),
            )

        initializers.forEach { it.start() }
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        verify(exactly = 0) { moderation.start() }
        verify(exactly = 0) { postAudience.start() }
        verify(exactly = 0) { feedView.start() }

        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        verify(exactly = 1) { moderation.start() }
        verify(exactly = 1) { postAudience.start() }
        verify(exactly = 1) { feedView.start() }
    }
}
