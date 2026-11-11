package net.kikin.nubecita.core.translation.internal

/**
 * Translate [text] one line at a time and put the lines back together.
 *
 * ML Kit drops every newline it is given. Handed a whole post it doesn't just
 * reflow the lines into one paragraph, it translates them as one sentence. For
 * a post whose lines are separate thoughts, which is most Japanese posts, the
 * result is a mistranslation. Measured on device (nubecita-qpx6):
 *
 * ```
 * イワタニはなんでもあるわね
 * もはやカセットガス冷蔵庫とか逆に冷やすのもつくれるんちゃうか
 *
 * whole:    Iwatani is anything that is no longer Cassett gas refrigerator
 *           or conversely to cool it
 * per line: Iwatani is everything
 *           It will also be a cassette gas refrigerator or conversely, it
 *           will be cold
 * ```
 *
 * So each non-blank line goes to [translateLine] on its own. Blank lines and
 * the whitespace around each line (indentation, a list's spacing, a `\r`)
 * belong to the author's layout and are put back untouched rather than sent.
 *
 * Lines are translated in order and the first failure fails the whole text:
 * a caller that can't translate a post shows the original, and half a
 * translation would be worse than either.
 */
internal suspend fun translateLineByLine(
    text: String,
    translateLine: suspend (String) -> Result<String>,
): Result<String> {
    val lines = text.split('\n')
    val translated = ArrayList<String>(lines.size)
    for (line in lines) {
        val start = line.indexOfFirst { !it.isWhitespace() }
        if (start < 0) {
            translated += line
            continue
        }
        val end = line.indexOfLast { !it.isWhitespace() } + 1
        val words = translateLine(line.substring(start, end)).getOrElse { return Result.failure(it) }
        translated += line.substring(0, start) + words + line.substring(end)
    }
    return Result.success(translated.joinToString("\n"))
}
