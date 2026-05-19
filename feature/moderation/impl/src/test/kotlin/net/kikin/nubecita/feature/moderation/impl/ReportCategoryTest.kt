package net.kikin.nubecita.feature.moderation.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Spec coverage: `ReportCategory` sealed sum models the 9 dialog cards
 * and their child reasons. Sub-reason ordering matches the lexicon's
 * `knownValues` ordering.
 */
class ReportCategoryTest {
    @Test
    fun spamCategoryUsesTheLegacyTokenAndHasNoSubReasonStep() {
        assertEquals(
            listOf(ReportReasons.REASON_LEGACY_SPAM),
            ReportCategory.Spam.reasons,
        )
    }

    @Test
    fun violenceCategoryExposesSevenGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_VIOLENCE_ANIMAL,
                ReportReasons.REASON_VIOLENCE_THREATS,
                ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT,
                ReportReasons.REASON_VIOLENCE_GLORIFICATION,
                ReportReasons.REASON_VIOLENCE_EXTREMIST_CONTENT,
                ReportReasons.REASON_VIOLENCE_TRAFFICKING,
                ReportReasons.REASON_VIOLENCE_OTHER,
            ),
            ReportCategory.Violence.reasons,
        )
    }

    @Test
    fun sexualCategoryExposesSixGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_SEXUAL_ABUSE_CONTENT,
                ReportReasons.REASON_SEXUAL_NCII,
                ReportReasons.REASON_SEXUAL_DEEPFAKE,
                ReportReasons.REASON_SEXUAL_ANIMAL,
                ReportReasons.REASON_SEXUAL_UNLABELED,
                ReportReasons.REASON_SEXUAL_OTHER,
            ),
            ReportCategory.Sexual.reasons,
        )
    }

    @Test
    fun childSafetyCategoryExposesFiveGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_CHILD_SAFETY_CSAM,
                ReportReasons.REASON_CHILD_SAFETY_GROOM,
                ReportReasons.REASON_CHILD_SAFETY_PRIVACY,
                ReportReasons.REASON_CHILD_SAFETY_HARASSMENT,
                ReportReasons.REASON_CHILD_SAFETY_OTHER,
            ),
            ReportCategory.ChildSafety.reasons,
        )
    }

    @Test
    fun harassmentCategoryExposesFiveGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_HARASSMENT_TROLL,
                ReportReasons.REASON_HARASSMENT_TARGETED,
                ReportReasons.REASON_HARASSMENT_HATE_SPEECH,
                ReportReasons.REASON_HARASSMENT_DOXXING,
                ReportReasons.REASON_HARASSMENT_OTHER,
            ),
            ReportCategory.Harassment.reasons,
        )
    }

    @Test
    fun misleadingCategoryExposesSixGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_MISLEADING_BOT,
                ReportReasons.REASON_MISLEADING_IMPERSONATION,
                ReportReasons.REASON_MISLEADING_SPAM,
                ReportReasons.REASON_MISLEADING_SCAM,
                ReportReasons.REASON_MISLEADING_ELECTIONS,
                ReportReasons.REASON_MISLEADING_OTHER,
            ),
            ReportCategory.Misleading.reasons,
        )
    }

    @Test
    fun ruleViolationCategoryExposesFourGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_RULE_SITE_SECURITY,
                ReportReasons.REASON_RULE_PROHIBITED_SALES,
                ReportReasons.REASON_RULE_BAN_EVASION,
                ReportReasons.REASON_RULE_OTHER,
            ),
            ReportCategory.RuleViolation.reasons,
        )
    }

    @Test
    fun selfHarmCategoryExposesFiveGranularReasonsInLexiconOrder() {
        assertEquals(
            listOf(
                ReportReasons.REASON_SELF_HARM_CONTENT,
                ReportReasons.REASON_SELF_HARM_ED,
                ReportReasons.REASON_SELF_HARM_STUNTS,
                ReportReasons.REASON_SELF_HARM_SUBSTANCES,
                ReportReasons.REASON_SELF_HARM_OTHER,
            ),
            ReportCategory.SelfHarm.reasons,
        )
    }

    @Test
    fun otherCategoryIsTheGranularCatchAll() {
        assertEquals(
            listOf(ReportReasons.REASON_OTHER),
            ReportCategory.Other.reasons,
        )
    }

    @Test
    fun allCategoriesCoverNineDistinctVariants() {
        val all: List<ReportCategory> =
            listOf(
                ReportCategory.Spam,
                ReportCategory.Violence,
                ReportCategory.Sexual,
                ReportCategory.ChildSafety,
                ReportCategory.Harassment,
                ReportCategory.Misleading,
                ReportCategory.RuleViolation,
                ReportCategory.SelfHarm,
                ReportCategory.Other,
            )
        assertEquals(9, all.size)
        assertEquals(9, all.distinct().size)
    }
}
