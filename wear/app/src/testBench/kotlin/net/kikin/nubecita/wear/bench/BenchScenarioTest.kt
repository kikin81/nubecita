package net.kikin.nubecita.wear.bench

import net.kikin.nubecita.wear.data.PhoneAppStatus
import net.kikin.nubecita.wear.data.PhoneSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Journeys select a bench state by name, so the names are a contract: every
 * one resolves, none collide, and a typo fails instead of quietly running the
 * happy path.
 */
internal class BenchScenarioTest {
    @Test
    fun `every scenario resolves from its own name`() {
        BenchScenario.entries.forEach { scenario -> assertEquals(scenario, BenchScenario.of(scenario.wire)) }
    }

    @Test
    fun `names are unique lower snake case`() {
        val names = BenchScenario.entries.map { it.wire }
        assertEquals(names.size, names.toSet().size)
        names.forEach { name -> assertTrue(Regex("[a-z]+(_[a-z]+)*").matches(name), name) }
    }

    @Test
    fun `an unknown name throws and lists the valid ones`() {
        val error = assertThrows(IllegalArgumentException::class.java) { BenchScenario.of("signed-out") }
        assertTrue(error.message.orEmpty().contains("signed_out"), error.message)
    }

    @Test
    fun `the default is the signed-in happy path`() {
        assertEquals(PhoneSession.SignedIn(BENCH_ACCOUNT), BenchScenario.Default.session())
        assertEquals(PhoneAppStatus.Installed, BenchScenario.Default.phoneAppStatus())
    }

    @Test
    fun `each gate scenario yields the session and phone the gate needs`() {
        // Signed out is only shown when the phone app is there to sign in to.
        assertEquals(PhoneSession.SignedOut to PhoneAppStatus.Installed, gateInputs(BenchScenario.SignedOut))
        // A missing app or an iPhone is only asked about when no session ever arrived.
        assertEquals(PhoneSession.Unknown to PhoneAppStatus.MissingOnAndroid, gateInputs(BenchScenario.PhoneAppMissing))
        assertEquals(PhoneSession.Unknown to PhoneAppStatus.IPhone, gateInputs(BenchScenario.IPhone))
        assertEquals(PhoneSession.UpdateRequired to PhoneAppStatus.Installed, gateInputs(BenchScenario.UpdateRequired))
    }

    @Test
    fun `content scenarios stay signed in`() {
        val gates = setOf(BenchScenario.SignedOut, BenchScenario.PhoneAppMissing, BenchScenario.IPhone, BenchScenario.UpdateRequired)
        (BenchScenario.entries - gates).forEach { scenario ->
            assertEquals(PhoneSession.SignedIn(BENCH_ACCOUNT) to PhoneAppStatus.Installed, gateInputs(scenario), scenario.wire)
        }
    }

    private fun gateInputs(scenario: BenchScenario) = scenario.session() to scenario.phoneAppStatus()
}
