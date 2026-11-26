package net.kikin.nubecita.core.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenMediationConfigTest {
    @Test
    fun `no URL means token mediation is off`() {
        for (url in listOf("", " ", "\t\n")) {
            val config = TokenMediationConfig.from(url)

            assertFalse(config.isEnabled, "isEnabled for '$url'")
            assertNull(config.baseUrl, "baseUrl for '$url'")
        }
    }

    @Test
    fun `an https URL turns it on`() {
        val config = TokenMediationConfig.from("https://auth.nubecita.app")

        assertTrue(config.isEnabled)
        assertEquals("https://auth.nubecita.app", config.baseUrl)
    }

    @Test
    fun `surrounding space and trailing slashes are dropped`() {
        for (url in listOf("https://auth.nubecita.app/", " https://auth.nubecita.app// ")) {
            assertEquals("https://auth.nubecita.app", TokenMediationConfig.from(url).baseUrl, url)
        }
    }

    @Test
    fun `plain http is accepted only for a local emulator`() {
        val local =
            listOf(
                "http://localhost:5001/nubecita-2a4c1/us-central1/oauthMediator",
                "http://127.0.0.1:5001/x",
                "http://10.0.2.2:5001/x",
            )
        for (url in local) {
            assertEquals(url, TokenMediationConfig.from(url).baseUrl, url)
        }
    }

    @Test
    fun `anything else is a build mistake and fails loudly`() {
        val unusable =
            listOf(
                "http://auth.nubecita.app",
                "http://localhost.evil.example/x",
                "ftp://auth.nubecita.app",
                "auth.nubecita.app",
                "https://",
                "https://user:pass@auth.nubecita.app",
                "https://auth.nubecita.app?x=1",
                "https://auth.nubecita.app#frag",
            )
        for (url in unusable) {
            assertThrows(IllegalArgumentException::class.java, { TokenMediationConfig.from(url) }, url)
        }
    }
}
