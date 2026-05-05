package net.kikin.nubecita.buildconfig

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests for the `parseVersionCode` helper that lives in [app/build.gradle.kts](../../../../../../../build.gradle.kts).
 *
 * The parser cannot be imported here directly because it's a top-level
 * function in a Gradle build script — build scripts compile against
 * the buildscript classpath, not against `:app`'s production source
 * set, so there is no shared compilation unit. The function is tiny
 * (~15 lines, pure, no I/O) and the test is a pinned local copy of
 * the same algorithm. When the build-script implementation changes,
 * this copy MUST be updated in lockstep — PR review is the gate.
 *
 * Setting up a separate `buildSrc/` or extending `build-logic/convention`
 * with its own test source set is the cleaner long-term answer if the
 * project grows more pure-function build helpers worth testing in
 * isolation. Until that exists, duplication is the cheapest path to
 * coverage.
 */
class VersionCodeParserTest {
    @Test
    fun standardSemver_packsToFixedWidthBands() {
        assertEquals(1_037_001, parseVersionCode("1.37.1"))
    }

    @Test
    fun majorBumpIsAlwaysGreaterThanLowerMajor() {
        // The whole reason for the 1_000_000-band scheme: 2.0.0 must be
        // strictly greater than any 1.x.x, so Play Store accepts the
        // breaking-change upload.
        val lowerMajor = parseVersionCode("1.15.0")
        val breakingMajor = parseVersionCode("2.0.0")

        assertEquals(1_015_000, lowerMajor)
        assertEquals(2_000_000, breakingMajor)
        assertEquals(true, breakingMajor > lowerMajor)
    }

    @Test
    fun zeroBoundary_isAllowed() {
        assertEquals(0, parseVersionCode("0.0.0"))
        assertEquals(1_000_000, parseVersionCode("1.0.0"))
    }

    @Test
    fun maximumPerBand_isAllowed() {
        // 999 is the inclusive upper bound for minor and patch.
        assertEquals(1_999_999, parseVersionCode("1.999.999"))
    }

    @Test
    fun preReleaseSuffix_isStripped() {
        // RC and SNAPSHOT builds parse to the same versionCode as the
        // base — RC vs final ordering at the same versionName is out
        // of scope; handle via separate applicationId for beta tracks.
        assertEquals(1_038_000, parseVersionCode("1.38.0-rc.1"))
        assertEquals(1_037_001, parseVersionCode("1.37.1-SNAPSHOT"))
        assertEquals(2_000_000, parseVersionCode("2.0.0-alpha.5+build.42"))
    }

    @Test
    fun semverBuildMetadata_isStripped() {
        // SemVer 2.0 allows `+<build>` metadata after the version.
        // Strip at the first `+` so e.g. CI-injected build numbers
        // don't make the parser throw NumberFormatException on
        // `parts[2].toInt()`.
        assertEquals(1_002_003, parseVersionCode("1.2.3+build.4"))
        assertEquals(1_002_003, parseVersionCode("1.2.3+sha.abc1234"))
    }

    @Test
    fun outOfBandMinor_failsFast() {
        val ex =
            assertThrows<IllegalArgumentException> {
                parseVersionCode("1.1000.0")
            }
        assertEquals(true, ex.message!!.contains("Minor and patch must be in 0..999"))
    }

    @Test
    fun outOfBandPatch_failsFast() {
        assertThrows<IllegalArgumentException> {
            parseVersionCode("1.0.1000")
        }
    }

    @Test
    fun outOfBandMajor_failsFast() {
        // 2147 * 1_000_000 = 2_147_000_000 plus any non-zero minor or
        // patch overflows Int.MAX_VALUE = 2_147_483_647. The bound is
        // 2146 inclusive, so 2147.0.0 must be rejected.
        val ex =
            assertThrows<IllegalArgumentException> {
                parseVersionCode("2147.0.0")
            }
        assertEquals(true, ex.message!!.contains("Major must be in 0..2146"))
    }

    @Test
    fun maxAllowedMajor_isAllowed() {
        // 2146.999.999 is the largest valid versionCode under this
        // scheme — proves the bound is correct (one short of overflow).
        assertEquals(2_146_999_999, parseVersionCode("2146.999.999"))
    }

    @Test
    fun negativeMajor_failsFast() {
        // `-1.0.0` is caught earlier than the bounds check: the leading
        // `-` is consumed by `substringBefore('-')` (the pre-release
        // strip), leaving an empty core string and tripping the
        // `parts.size == 3` malformed-input guard. Either rejection
        // path is correct — the contract is "negative version names
        // are not accepted." A future parser pass that handles
        // semver pre-release more granularly would route this to the
        // major bound instead; both should be rejected.
        assertThrows<IllegalArgumentException> {
            parseVersionCode("-1.0.0")
        }
    }

    @Test
    fun missingPatch_failsFast() {
        val ex =
            assertThrows<IllegalArgumentException> {
                parseVersionCode("1.0")
            }
        assertEquals(true, ex.message!!.contains("Expected MAJOR.MINOR.PATCH"))
    }

    @Test
    fun extraComponent_failsFast() {
        assertThrows<IllegalArgumentException> {
            parseVersionCode("1.0.0.1")
        }
    }

    @Test
    fun nonNumericComponent_throws() {
        // Falls through to NumberFormatException from parts[i].toInt() —
        // the message identifies the bad input by class name.
        assertThrows<NumberFormatException> {
            parseVersionCode("1.x.0")
        }
    }

    /**
     * Pinned copy of the parser from `app/build.gradle.kts`. Keep in
     * lockstep with the build-script copy. See the class kdoc for why
     * this duplication exists.
     */
    private fun parseVersionCode(versionName: String): Int {
        val core = versionName.substringBefore('-').substringBefore('+')
        val parts = core.split('.')
        require(parts.size == 3) {
            "Expected MAJOR.MINOR.PATCH, got: $versionName"
        }
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        val patch = parts[2].toInt()
        require(major in 0..2146) {
            "Major must be in 0..2146 to fit a 32-bit signed versionCode: $versionName"
        }
        require(minor in 0..999 && patch in 0..999) {
            "Minor and patch must be in 0..999 to fit the 1_000_000-band scheme: $versionName"
        }
        val code = major.toLong() * 1_000_000 + minor.toLong() * 1_000 + patch
        check(code in 0..Int.MAX_VALUE) {
            "versionCode $code overflowed Int.MAX_VALUE; tighten the per-component bounds"
        }
        return code.toInt()
    }
}
