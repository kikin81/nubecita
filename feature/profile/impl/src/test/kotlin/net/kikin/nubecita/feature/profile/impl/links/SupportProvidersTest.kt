package net.kikin.nubecita.feature.profile.impl.links

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SupportProvidersTest {
    @Test
    fun `account on a recognized provider is a support link`() {
        assertEquals("Patreon", supportProviderFor("https://www.patreon.com/sarah")?.name)
        assertEquals("Ko-fi", supportProviderFor("https://ko-fi.com/sarah")?.name)
    }

    @Test
    fun `account subdomain counts`() {
        assertEquals("itch.io", supportProviderFor("https://sarah.itch.io")?.name)
    }

    @Test
    fun `bare provider homepage does not count`() {
        assertFalse(isSupportLink("https://patreon.com"))
        assertFalse(isSupportLink("https://www.patreon.com/"))
    }

    @Test
    fun `look-alike domain is rejected`() {
        assertFalse(isSupportLink("https://ko-fi.com.example.net/x"))
        assertFalse(isSupportLink("https://notpatreon.com/sarah"))
    }

    @Test
    fun `path-prefix provider requires the prefix`() {
        assertTrue(isSupportLink("https://paypal.com/paypalme/sarah"))
        assertFalse(isSupportLink("https://paypal.com/signin"))
        assertFalse(isSupportLink("https://paypal.com/paypalme"))
        assertTrue(isSupportLink("https://chuffed.org/project/abc"))
    }

    @Test
    fun `specific-host provider matches only that host`() {
        assertTrue(isSupportLink("https://buy.stripe.com/abc"))
        assertFalse(isSupportLink("https://stripe.com/abc"))
    }

    @Test
    fun `host match is case-insensitive`() {
        assertTrue(isSupportLink("https://WWW.Patreon.COM/sarah"))
    }

    @Test
    fun `non-web and malformed urls are never support links`() {
        assertFalse(isSupportLink("mailto:me@patreon.com"))
        assertFalse(isSupportLink("not a url"))
        assertFalse(isSupportLink(""))
    }
}
