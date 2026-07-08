package net.kikin.nubecita.designsystem.icon

/**
 * Identity of a glyph in the Material Symbols Rounded font shipped at
 * `R.font.material_symbols_rounded`. Vendor only the entries the app
 * actually uses — adding a new icon means adding a new entry here
 * (forces explicit, reviewed addition rather than a freebie from a
 * generated table).
 *
 * # Adding a new icon
 *
 * 1. Find the glyph at <https://fonts.google.com/icons?icon.style=Rounded>.
 * 2. Click the icon → side panel shows "Codepoint: e5cd" (4-char hex).
 * 3. Add `Foo("\uXXXX"),` below in alphabetical order. The Kotlin
 *    string literal escape format is `\uXXXX` (uppercase letters, no
 *    quotes around the hex itself).
 * 4. Run `./scripts/update_material_symbols.sh` to regenerate the
 *    subset font with the new glyph included.
 * 5. The unit test `NubecitaIconNameTest.every_codepoint_isASingleScalar`
 *    enforces validity at build time. Run
 *    `./gradlew :designsystem:testDebugUnitTest` to verify.
 *
 * Codepoints below sourced from Google's
 * `MaterialSymbolsRounded.codepoints` file at
 * <https://github.com/google/material-design-icons/blob/master/variablefont/MaterialSymbolsRounded%5BFILL%2CGRAD%2Copsz%2Cwght%5D.codepoints>.
 *
 * Filled/Outlined pairs in the legacy `Icons.Filled.X` / `Icons.Outlined.X`
 * library collapse to ONE entry here — the visual difference is
 * controlled by the `filled: Boolean` parameter on [NubecitaIcon].
 */
enum class NubecitaIconName(
    internal val codepoint: String,
) {
    Add("\uE145"),
    AddPhotoAlternate("\uE43E"),
    AlternateEmail("\uE0E6"),
    ArrowBack("\uE5C4"),
    ArrowForward("\uE5C8"),
    Article("\uEF42"),
    Block("\uE14B"),
    Bookmark("\uE8E7"),
    ChatBubble("\uE0CB"),
    Check("\uE5CA"),
    CheckCircle("\uF0BE"),
    ChevronRight("\uE5CC"),
    Close("\uE5CD"),
    Edit("\uF097"),
    Error("\uF8B6"),
    ExpandMore("\uE5CF"),
    Favorite("\uE87E"),
    Flag("\uE153"),
    FormatQuote("\uE244"),
    Forward10("\uE056"),
    Globe("\uE64C"),
    Home("\uE9B2"),
    Inbox("\uE156"),
    IosShare("\uE6B8"),
    Language("\uE894"),
    LocalFireDepartment("\uEF55"),
    LockPerson("\uF8F3"),
    Logout("\uE9BA"),
    Menu("\uE5D2"),
    MoreHoriz("\uE5D3"),
    MoreVert("\uE5D4"),

    // `e7f5` is aliased to both `notifications` and `notifications_none` in
    // the Material Symbols codepoint table — the visual difference between
    // filled (with the activity dot) and outlined (no dot) bell is encoded
    // as a SEPARATE glyph: `notifications_active` at `e7f7`. We vendor
    // `e7f7` so the FILL axis flips to bell-with-activity-dot when
    // `filled = true`, matching the notifications-surface design intent.
    Notifications("\uE7F7"),
    NotificationsOff("\uE7F6"),
    Pause("\uE034"),
    Person("\uF0D3"),
    PersonAdd("\uEA4D"),
    PictureInPictureAlt("\uE911"),
    PlayArrow("\uE037"),
    Public("\uE80B"),
    Repeat("\uE040"),
    Replay10("\uE059"),
    Reply("\uE15E"),
    Search("\uE8B6"),
    Send("\uE163"),
    Settings("\uE8B8"),
    Verified("\uEF76"),
    VolumeOff("\uE04F"),
    VolumeUp("\uE050"),
    WifiOff("\uE648"),

    // Medal/ribbon glyph for the Nubecita Pro "Supporter" badge. Chosen
    // over `Verified` (a checkmark) so the badge reads as a patron/
    // supporter marker, NOT as identity verification.
    WorkspacePremium("\uE7AF"),
}
