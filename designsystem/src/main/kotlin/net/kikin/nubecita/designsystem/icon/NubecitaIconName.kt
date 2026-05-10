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
    ArrowBack("\uE5C4"),
    Article("\uEF42"),
    Bookmark("\uE8E7"),
    ChatBubble("\uE0CB"),
    Check("\uE5CA"),
    Close("\uE5CD"),
    Edit("\uF097"),
    Error("\uF8B6"),
    Favorite("\uE87E"),
    Home("\uE9B2"),
    Inbox("\uE156"),
    IosShare("\uE6B8"),
    Language("\uE894"),
    LockPerson("\uF8F3"),
    Menu("\uE5D2"),
    MoreHoriz("\uE5D3"),
    MoreVert("\uE5D4"),
    Notifications("\uE7F5"),
    Person("\uF0D3"),
    PersonAdd("\uEA4D"),
    PlayArrow("\uE037"),
    Public("\uE80B"),
    Repeat("\uE040"),
    Reply("\uE15E"),
    Search("\uE8B6"),
    VolumeOff("\uE04F"),
    VolumeUp("\uE050"),
    WifiOff("\uE648"),
}
