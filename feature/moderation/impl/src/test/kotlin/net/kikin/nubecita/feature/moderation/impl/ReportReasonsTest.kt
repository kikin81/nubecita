package net.kikin.nubecita.feature.moderation.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Spec coverage: openspec/changes/add-moderation-report-flow/specs/feature-moderation/spec.md
 *
 * Requirement: `ReportReasons` exposes granular `tools.ozone.report.defs`
 * and legacy `com.atproto.moderation.defs` tokens as `String` constants.
 *
 * The lexicon is the source of truth. If these tests fail because a
 * constant's value drifted, check the upstream lexicon at
 * https://github.com/bluesky-social/atproto/blob/main/lexicons/tools/ozone/report/defs.json
 * before changing the test — the test asserts the wire contract.
 */
class ReportReasonsTest {
    @Test
    fun granularCsamConstantMatchesCanonicalLexiconString() {
        assertEquals(
            "tools.ozone.report.defs#reasonChildSafetyCSAM",
            ReportReasons.REASON_CHILD_SAFETY_CSAM,
        )
    }

    @Test
    fun granularOtherIsTheOzoneFallback() {
        assertEquals(
            "tools.ozone.report.defs#reasonOther",
            ReportReasons.REASON_OTHER,
        )
    }

    @Test
    fun violenceGraphicContentMatchesLexiconCasing() {
        assertEquals(
            "tools.ozone.report.defs#reasonViolenceGraphicContent",
            ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT,
        )
    }

    @Test
    fun harassmentHateSpeechMatchesLexiconCasing() {
        assertEquals(
            "tools.ozone.report.defs#reasonHarassmentHateSpeech",
            ReportReasons.REASON_HARASSMENT_HATE_SPEECH,
        )
    }

    @Test
    fun legacySpamMapsToModerationDefsNamespace() {
        assertEquals(
            "com.atproto.moderation.defs#reasonSpam",
            ReportReasons.REASON_LEGACY_SPAM,
        )
    }

    @Test
    fun legacyRudeMapsToModerationDefsNamespace() {
        assertEquals(
            "com.atproto.moderation.defs#reasonRude",
            ReportReasons.REASON_LEGACY_RUDE,
        )
    }

    @Test
    fun otherReportReasonsContainsExactlyTheEightExpectedTokens() {
        val expected =
            setOf(
                ReportReasons.REASON_VIOLENCE_OTHER,
                ReportReasons.REASON_SEXUAL_OTHER,
                ReportReasons.REASON_CHILD_SAFETY_OTHER,
                ReportReasons.REASON_HARASSMENT_OTHER,
                ReportReasons.REASON_MISLEADING_OTHER,
                ReportReasons.REASON_RULE_OTHER,
                ReportReasons.REASON_SELF_HARM_OTHER,
                ReportReasons.REASON_OTHER,
            )
        assertEquals(expected, ReportReasons.OTHER_REPORT_REASONS)
        assertEquals(8, ReportReasons.OTHER_REPORT_REASONS.size)
    }

    @Test
    fun otherReportReasonsDoesNotContainLegacyTokens() {
        assertTrue(ReportReasons.REASON_LEGACY_OTHER !in ReportReasons.OTHER_REPORT_REASONS)
        assertTrue(ReportReasons.REASON_LEGACY_SPAM !in ReportReasons.OTHER_REPORT_REASONS)
    }

    @Test
    fun selfHarmEatingDisorderConstantMatchesLexiconAbbreviation() {
        assertEquals(
            "tools.ozone.report.defs#reasonSelfHarmED",
            ReportReasons.REASON_SELF_HARM_ED,
        )
    }
}
