package net.kikin.nubecita.feature.moderation.impl

/**
 * Canonical reason-token strings for `com.atproto.moderation.createReport`.
 *
 * Tokens are kept as plain `String` constants rather than a Kotlin
 * `enum class` for two reasons:
 *
 * 1. The lexicon vocabulary evolves on the server. New tokens added by
 *    Bluesky should be a one-file PR here, not a regenerated SDK
 *    release; an enum locks the wire vocabulary to the build artifact.
 * 2. The wire `reasonType` field in `CreateReportRequest` accepts any
 *    `String`; using strings end-to-end means an unknown reason
 *    (e.g. observed in a future server change) round-trips through
 *    state without `null`-ing out.
 *
 * The granular tokens come from `tools.ozone.report.defs#reasonType`
 * (knownValues). The legacy tokens come from
 * `com.atproto.moderation.defs#reasonType` — these are the older
 * 7-value flat list and act as a wire fallback for clients that don't
 * know the granular hierarchy.
 *
 * Audit against
 * https://github.com/bluesky-social/atproto/blob/main/lexicons/tools/ozone/report/defs.json
 * when this object is touched.
 */
object ReportReasons {
    // --- Granular: tools.ozone.report.defs ---

    /** Top-level granular catch-all. Forces the Details required gate. */
    const val REASON_OTHER: String = "tools.ozone.report.defs#reasonOther"
    const val REASON_APPEAL: String = "tools.ozone.report.defs#reasonAppeal"

    // Violence — 7 sub-reasons
    const val REASON_VIOLENCE_ANIMAL: String = "tools.ozone.report.defs#reasonViolenceAnimal"
    const val REASON_VIOLENCE_THREATS: String = "tools.ozone.report.defs#reasonViolenceThreats"
    const val REASON_VIOLENCE_GRAPHIC_CONTENT: String =
        "tools.ozone.report.defs#reasonViolenceGraphicContent"
    const val REASON_VIOLENCE_GLORIFICATION: String =
        "tools.ozone.report.defs#reasonViolenceGlorification"
    const val REASON_VIOLENCE_EXTREMIST_CONTENT: String =
        "tools.ozone.report.defs#reasonViolenceExtremistContent"
    const val REASON_VIOLENCE_TRAFFICKING: String =
        "tools.ozone.report.defs#reasonViolenceTrafficking"
    const val REASON_VIOLENCE_OTHER: String = "tools.ozone.report.defs#reasonViolenceOther"

    // Sexual content — 6 sub-reasons
    const val REASON_SEXUAL_ABUSE_CONTENT: String =
        "tools.ozone.report.defs#reasonSexualAbuseContent"
    const val REASON_SEXUAL_NCII: String = "tools.ozone.report.defs#reasonSexualNCII"
    const val REASON_SEXUAL_DEEPFAKE: String = "tools.ozone.report.defs#reasonSexualDeepfake"
    const val REASON_SEXUAL_ANIMAL: String = "tools.ozone.report.defs#reasonSexualAnimal"
    const val REASON_SEXUAL_UNLABELED: String = "tools.ozone.report.defs#reasonSexualUnlabeled"
    const val REASON_SEXUAL_OTHER: String = "tools.ozone.report.defs#reasonSexualOther"

    // Child safety — 5 sub-reasons (`CSAM` uppercase per the lexicon)
    const val REASON_CHILD_SAFETY_CSAM: String = "tools.ozone.report.defs#reasonChildSafetyCSAM"
    const val REASON_CHILD_SAFETY_GROOM: String = "tools.ozone.report.defs#reasonChildSafetyGroom"
    const val REASON_CHILD_SAFETY_PRIVACY: String =
        "tools.ozone.report.defs#reasonChildSafetyPrivacy"
    const val REASON_CHILD_SAFETY_HARASSMENT: String =
        "tools.ozone.report.defs#reasonChildSafetyHarassment"
    const val REASON_CHILD_SAFETY_OTHER: String = "tools.ozone.report.defs#reasonChildSafetyOther"

    // Harassment — 5 sub-reasons
    const val REASON_HARASSMENT_TROLL: String = "tools.ozone.report.defs#reasonHarassmentTroll"
    const val REASON_HARASSMENT_TARGETED: String =
        "tools.ozone.report.defs#reasonHarassmentTargeted"
    const val REASON_HARASSMENT_HATE_SPEECH: String =
        "tools.ozone.report.defs#reasonHarassmentHateSpeech"
    const val REASON_HARASSMENT_DOXXING: String = "tools.ozone.report.defs#reasonHarassmentDoxxing"
    const val REASON_HARASSMENT_OTHER: String = "tools.ozone.report.defs#reasonHarassmentOther"

    // Misleading — 6 sub-reasons (spam lives here in the ozone hierarchy)
    const val REASON_MISLEADING_BOT: String = "tools.ozone.report.defs#reasonMisleadingBot"
    const val REASON_MISLEADING_IMPERSONATION: String =
        "tools.ozone.report.defs#reasonMisleadingImpersonation"
    const val REASON_MISLEADING_SPAM: String = "tools.ozone.report.defs#reasonMisleadingSpam"
    const val REASON_MISLEADING_SCAM: String = "tools.ozone.report.defs#reasonMisleadingScam"
    const val REASON_MISLEADING_ELECTIONS: String =
        "tools.ozone.report.defs#reasonMisleadingElections"
    const val REASON_MISLEADING_OTHER: String = "tools.ozone.report.defs#reasonMisleadingOther"

    // Rule violation — 4 sub-reasons
    const val REASON_RULE_SITE_SECURITY: String = "tools.ozone.report.defs#reasonRuleSiteSecurity"
    const val REASON_RULE_PROHIBITED_SALES: String =
        "tools.ozone.report.defs#reasonRuleProhibitedSales"
    const val REASON_RULE_BAN_EVASION: String = "tools.ozone.report.defs#reasonRuleBanEvasion"
    const val REASON_RULE_OTHER: String = "tools.ozone.report.defs#reasonRuleOther"

    // Self-harm — 5 sub-reasons (`ED` = eating disorders per the lexicon)
    const val REASON_SELF_HARM_CONTENT: String = "tools.ozone.report.defs#reasonSelfHarmContent"
    const val REASON_SELF_HARM_ED: String = "tools.ozone.report.defs#reasonSelfHarmED"
    const val REASON_SELF_HARM_STUNTS: String = "tools.ozone.report.defs#reasonSelfHarmStunts"
    const val REASON_SELF_HARM_SUBSTANCES: String =
        "tools.ozone.report.defs#reasonSelfHarmSubstances"
    const val REASON_SELF_HARM_OTHER: String = "tools.ozone.report.defs#reasonSelfHarmOther"

    // --- Legacy: com.atproto.moderation.defs ---

    const val REASON_LEGACY_SPAM: String = "com.atproto.moderation.defs#reasonSpam"
    const val REASON_LEGACY_VIOLATION: String = "com.atproto.moderation.defs#reasonViolation"
    const val REASON_LEGACY_MISLEADING: String = "com.atproto.moderation.defs#reasonMisleading"
    const val REASON_LEGACY_SEXUAL: String = "com.atproto.moderation.defs#reasonSexual"
    const val REASON_LEGACY_RUDE: String = "com.atproto.moderation.defs#reasonRude"
    const val REASON_LEGACY_OTHER: String = "com.atproto.moderation.defs#reasonOther"
    const val REASON_LEGACY_APPEAL: String = "com.atproto.moderation.defs#reasonAppeal"

    /**
     * The seven `*Other` granular tokens plus the top-level granular
     * `REASON_OTHER`. A reason in this set forces the Details textarea
     * to be required at the UI layer. Legacy fallbacks are NOT included.
     */
    val OTHER_REPORT_REASONS: Set<String> =
        setOf(
            REASON_VIOLENCE_OTHER,
            REASON_SEXUAL_OTHER,
            REASON_CHILD_SAFETY_OTHER,
            REASON_HARASSMENT_OTHER,
            REASON_MISLEADING_OTHER,
            REASON_RULE_OTHER,
            REASON_SELF_HARM_OTHER,
            REASON_OTHER,
        )
}
