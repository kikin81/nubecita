package net.kikin.nubecita.feature.moderation.impl

/**
 * The 9 top-level cards shown in the Report dialog's Category step.
 *
 * Each variant owns its `reasons` list — the sub-reason tokens the user
 * picks from in the SubReason step. Tokens come from [ReportReasons];
 * ordering matches the canonical lexicon's `knownValues` order.
 *
 * Notes:
 * - [Spam] uses the legacy `com.atproto.moderation.defs#reasonSpam` token
 *   because the granular `tools.ozone.report.defs` hierarchy has no
 *   top-level "Spam" — spam lives under [Misleading] as a sub-reason.
 *   Tapping the Spam card submits the legacy token directly and the
 *   dialog skips the SubReason step.
 * - [Other] is the granular catch-all (`reasonOther`). Selecting it
 *   forces the Details textarea (it appears in
 *   `ReportReasons.OTHER_REPORT_REASONS`).
 */
sealed interface ReportCategory {
    val reasons: List<String>

    data object Spam : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_LEGACY_SPAM,
            )
    }

    data object Violence : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_VIOLENCE_ANIMAL,
                ReportReasons.REASON_VIOLENCE_THREATS,
                ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT,
                ReportReasons.REASON_VIOLENCE_GLORIFICATION,
                ReportReasons.REASON_VIOLENCE_EXTREMIST_CONTENT,
                ReportReasons.REASON_VIOLENCE_TRAFFICKING,
                ReportReasons.REASON_VIOLENCE_OTHER,
            )
    }

    data object Sexual : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_SEXUAL_ABUSE_CONTENT,
                ReportReasons.REASON_SEXUAL_NCII,
                ReportReasons.REASON_SEXUAL_DEEPFAKE,
                ReportReasons.REASON_SEXUAL_ANIMAL,
                ReportReasons.REASON_SEXUAL_UNLABELED,
                ReportReasons.REASON_SEXUAL_OTHER,
            )
    }

    data object ChildSafety : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_CHILD_SAFETY_CSAM,
                ReportReasons.REASON_CHILD_SAFETY_GROOM,
                ReportReasons.REASON_CHILD_SAFETY_PRIVACY,
                ReportReasons.REASON_CHILD_SAFETY_HARASSMENT,
                ReportReasons.REASON_CHILD_SAFETY_OTHER,
            )
    }

    data object Harassment : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_HARASSMENT_TROLL,
                ReportReasons.REASON_HARASSMENT_TARGETED,
                ReportReasons.REASON_HARASSMENT_HATE_SPEECH,
                ReportReasons.REASON_HARASSMENT_DOXXING,
                ReportReasons.REASON_HARASSMENT_OTHER,
            )
    }

    data object Misleading : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_MISLEADING_BOT,
                ReportReasons.REASON_MISLEADING_IMPERSONATION,
                ReportReasons.REASON_MISLEADING_SPAM,
                ReportReasons.REASON_MISLEADING_SCAM,
                ReportReasons.REASON_MISLEADING_ELECTIONS,
                ReportReasons.REASON_MISLEADING_OTHER,
            )
    }

    data object RuleViolation : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_RULE_SITE_SECURITY,
                ReportReasons.REASON_RULE_PROHIBITED_SALES,
                ReportReasons.REASON_RULE_BAN_EVASION,
                ReportReasons.REASON_RULE_OTHER,
            )
    }

    data object SelfHarm : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_SELF_HARM_CONTENT,
                ReportReasons.REASON_SELF_HARM_ED,
                ReportReasons.REASON_SELF_HARM_STUNTS,
                ReportReasons.REASON_SELF_HARM_SUBSTANCES,
                ReportReasons.REASON_SELF_HARM_OTHER,
            )
    }

    data object Other : ReportCategory {
        override val reasons: List<String> =
            listOf(
                ReportReasons.REASON_OTHER,
            )
    }
}
