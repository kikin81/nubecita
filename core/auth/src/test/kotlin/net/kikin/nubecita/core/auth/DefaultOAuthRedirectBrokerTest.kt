package net.kikin.nubecita.core.auth

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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOAuthRedirectBrokerTest {
    @Test
    fun `cold-start emission is buffered until a collector subscribes`() =
        runTest {
            val broker = DefaultOAuthRedirectBroker()

            broker.publish("net.kikin.nubecita:/oauth-redirect?code=abc")

            // Collector subscribes after the publish — should still receive the buffered value.
            val received = broker.redirects.first()
            assertEquals("net.kikin.nubecita:/oauth-redirect?code=abc", received)
        }

    @Test
    fun `emissions arrive in publish order to a single collector`() =
        runTest {
            val broker = DefaultOAuthRedirectBroker()
            val urls =
                listOf(
                    "net.kikin.nubecita:/oauth-redirect?code=1",
                    "net.kikin.nubecita:/oauth-redirect?code=2",
                    "net.kikin.nubecita:/oauth-redirect?code=3",
                )
            urls.forEach { broker.publish(it) }

            val received = broker.redirects.take(urls.size).toList()
            assertEquals(urls, received)
        }

    @Test
    fun `each emission is delivered to exactly one collector`() =
        runTest(UnconfinedTestDispatcher()) {
            val broker = DefaultOAuthRedirectBroker()

            // Two concurrent collectors compete for one item; exactly one wins.
            val firstCollector = async { broker.redirects.first() }
            val secondCollector = async { broker.redirects.first() }
            launch { broker.publish("net.kikin.nubecita:/oauth-redirect?code=once") }

            // One coroutine receives the value; the other never resolves until we publish again.
            broker.publish("net.kikin.nubecita:/oauth-redirect?code=twice")

            val received = listOf(firstCollector.await(), secondCollector.await()).sorted()
            assertEquals(
                listOf(
                    "net.kikin.nubecita:/oauth-redirect?code=once",
                    "net.kikin.nubecita:/oauth-redirect?code=twice",
                ),
                received,
            )
        }
}
