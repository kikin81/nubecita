package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the [isSelfHostedPds] classifier and the [SessionStateProvider.isSelfHosted]
 * stream derived from it. The classifier is the PII firewall for the
 * `is_self_hosted` user property — only the boolean is exposed, never the host.
 */
class IsSelfHostedPdsTest {
    @Test
    fun `bsky_social entryway host is not self-hosted`() {
        assertFalse(isSelfHostedPds("https://bsky.social"))
    }

    @Test
    fun `bsky network apex host is not self-hosted`() {
        assertFalse(isSelfHostedPds("https://host.bsky.network"))
    }

    @Test
    fun `bsky network per-user PDS subdomain is not self-hosted`() {
        // The common case: Bluesky-operated accounts live on *.host.bsky.network,
        // NOT bsky.social. A naive !contains("bsky.social") would misclassify these.
        assertFalse(isSelfHostedPds("https://hollowfoot.us-west.host.bsky.network"))
    }

    @Test
    fun `a third-party host is self-hosted`() {
        assertTrue(isSelfHostedPds("https://pds.example.com"))
    }

    @Test
    fun `a lookalike host that merely contains bsky_network is self-hosted`() {
        // Defends against a suffix-match shortcut: this host ENDS in a string that
        // is not ".host.bsky.network", so it must be treated as self-hosted.
        assertTrue(isSelfHostedPds("https://host.bsky.network.evil.example"))
    }

    @Test
    fun `host classification is case-insensitive`() {
        assertFalse(isSelfHostedPds("https://BSKY.SOCIAL"))
        assertFalse(isSelfHostedPds("https://Hollowfoot.US-West.Host.Bsky.Network"))
    }

    @Test
    fun `null url is not self-hosted`() {
        assertFalse(isSelfHostedPds(null))
    }

    @Test
    fun `blank or hostless url is not self-hosted`() {
        assertFalse(isSelfHostedPds(""))
        assertFalse(isSelfHostedPds("not a url"))
    }

    @Test
    fun `isSelfHosted flow is false while signed out`() =
        runTest {
            val provider = DefaultSessionStateProvider(EmptyStore())
            provider.refresh()
            assertFalse(provider.isSelfHosted.first())
        }

    @Test
    fun `isSelfHosted flow is true for a third-party PDS`() =
        runTest {
            val provider =
                DefaultSessionStateProvider(SeededStore(sampleSession(pdsUrl = "https://pds.example.com")))
            provider.refresh()
            assertTrue(provider.isSelfHosted.first())
        }

    @Test
    fun `isSelfHosted flow is false for a bsky network account`() =
        runTest {
            val provider =
                DefaultSessionStateProvider(
                    SeededStore(sampleSession(pdsUrl = "https://hollowfoot.us-west.host.bsky.network")),
                )
            provider.refresh()
            assertFalse(provider.isSelfHosted.first())
        }

    @Test
    fun `isSelfHosted flow is false when the session has no pds url`() =
        runTest {
            val provider = DefaultSessionStateProvider(SeededStore(sampleSession(pdsUrl = null)))
            provider.refresh()
            assertFalse(provider.isSelfHosted.first())
        }
}

private class EmptyStore : OAuthSessionStore {
    override suspend fun load(): OAuthSession? = null

    override suspend fun save(session: OAuthSession) = error("not under test")

    override suspend fun clear() = error("not under test")
}

private class SeededStore(
    private val session: OAuthSession,
) : OAuthSessionStore {
    override suspend fun load(): OAuthSession = session

    override suspend fun save(session: OAuthSession) = error("not under test")

    override suspend fun clear() = error("not under test")
}
