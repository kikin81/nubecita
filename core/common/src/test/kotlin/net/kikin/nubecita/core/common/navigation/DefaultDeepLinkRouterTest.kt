package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private data class TestKey(
    val tag: String,
) : NavKey

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDeepLinkRouterTest {
    @Test
    fun `cold-start emission is buffered until a collector subscribes`() =
        runTest {
            val router = DefaultDeepLinkRouter()

            router.publish(TestKey("profile:alice"))

            val received = router.pendingDeepLinks.first()
            assertEquals(TestKey("profile:alice"), received)
        }

    @Test
    fun `emissions arrive in publish order to a single collector`() =
        runTest {
            val router = DefaultDeepLinkRouter()
            val targets = listOf(TestKey("a"), TestKey("b"), TestKey("c"))
            targets.forEach { router.publish(it) }

            val received = router.pendingDeepLinks.take(targets.size).toList()
            assertEquals(targets, received)
        }

    @Test
    fun `each emission is delivered to exactly one collector`() =
        runTest(UnconfinedTestDispatcher()) {
            val router = DefaultDeepLinkRouter()

            val firstCollector = async { router.pendingDeepLinks.first() }
            val secondCollector = async { router.pendingDeepLinks.first() }
            launch { router.publish(TestKey("once")) }

            router.publish(TestKey("twice"))

            val received =
                listOf(firstCollector.await(), secondCollector.await())
                    .map { it as TestKey }
                    .sortedBy { it.tag }
            assertEquals(listOf(TestKey("once"), TestKey("twice")), received)
        }
}
